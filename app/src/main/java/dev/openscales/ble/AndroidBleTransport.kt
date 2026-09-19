package dev.openscales.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import dev.openscales.BuildConfig
import dev.openscales.protocol.GattIds
import dev.openscales.protocol.toHex
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException
import java.util.UUID

/**
 * [BleTransport] поверх [BluetoothGatt]. Android допускает одну GATT-операцию в полёте,
 * поэтому каждая операция берёт [opMutex] и ждёт свой колбэк через [pending].
 * Разрешения проверяются на уровне UI до создания транспорта.
 */
@SuppressLint("MissingPermission")
class AndroidBleTransport(
    private val context: Context,
    adapter: BluetoothAdapter,
    override val address: String,
) : BleTransport {

    private val device: BluetoothDevice = adapter.getRemoteDevice(address)
    private val opMutex = Mutex()
    private var gatt: BluetoothGatt? = null

    @Volatile
    private var pending: Pending? = null

    private val _events = MutableSharedFlow<TransportEvent>(
        extraBufferCapacity = 128,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val events: SharedFlow<TransportEvent> = _events

    private sealed class Pending {
        val result = CompletableDeferred<Any>()

        class Connect : Pending()
        class Discover : Pending()
        class Mtu : Pending()
        class Descriptor(val uuid: UUID) : Pending()
        class Write(val uuid: UUID) : Pending()
    }

    private val bondReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val dev = intent.bluetoothDevice() ?: return
            if (!dev.address.equals(address, ignoreCase = true)) return
            val state = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR)
            val prev = intent.getIntExtra(BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE, BluetoothDevice.ERROR)
            Log.i(TAG, "bond ${prev.toBondState()} -> ${state.toBondState()}")
            _events.tryEmit(TransportEvent.BondChanged(state.toBondState(), prev.toBondState()))
        }
    }

    private var receiverRegistered = false

    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            Log.i(TAG, "connection state=$newState status=$status")
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> complete<Pending.Connect>(status, Unit)
                BluetoothProfile.STATE_DISCONNECTED -> {
                    pending?.result?.completeExceptionally(BleException("disconnected, status=$status"))
                    _events.tryEmit(TransportEvent.Disconnected(status))
                }
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            complete<Pending.Discover>(status, g.services.map { it.uuid }.toSet())
        }

        override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) {
            Log.i(TAG, "mtu=$mtu status=$status")
            complete<Pending.Mtu>(status, mtu)
        }

        override fun onDescriptorWrite(g: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            complete<Pending.Descriptor>(status, Unit)
        }

        override fun onCharacteristicWrite(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) {
            complete<Pending.Write>(status, Unit)
        }

        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic, value: ByteArray) {
            if (BuildConfig.DEBUG) Log.d(TAG, "RX ${c.uuid.toString().substring(4, 8)} ${value.toHex()}")
            _events.tryEmit(TransportEvent.Notification(c.uuid, value))
        }

        @Deprecated("API < 33")
        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                @Suppress("DEPRECATION")
                _events.tryEmit(TransportEvent.Notification(c.uuid, c.value ?: ByteArray(0)))
            }
        }
    }

    private inline fun <reified T : Pending> complete(status: Int, value: Any) {
        val p = pending as? T ?: return
        if (status == BluetoothGatt.GATT_SUCCESS) {
            p.result.complete(value)
        } else {
            p.result.completeExceptionally(BleException("${T::class.simpleName} failed, status=$status"))
        }
    }

    private suspend fun <R> operation(timeoutMs: Long, op: Pending, start: () -> Boolean): R =
        opMutex.withLock {
            pending = op
            try {
                if (!start()) throw BleException("${op::class.simpleName} could not start")
                @Suppress("UNCHECKED_CAST")
                withTimeout(timeoutMs) { op.result.await() } as R
            } catch (e: TimeoutCancellationException) {
                throw BleException("${op::class.simpleName} timeout ${timeoutMs}ms")
            } finally {
                pending = null
            }
        }

    override suspend fun connect() {
        if (!receiverRegistered) {
            ContextCompat.registerReceiver(
                context, bondReceiver,
                IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED),
                ContextCompat.RECEIVER_EXPORTED,
            )
            receiverRegistered = true
        }
        operation<Unit>(CONNECT_TIMEOUT_MS, Pending.Connect()) {
            gatt = device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
            gatt != null
        }
    }

    override suspend fun discoverServices(): Set<UUID> =
        operation(OP_TIMEOUT_MS, Pending.Discover()) { gatt?.discoverServices() == true }

    override fun bondState(): BondState = device.bondState.toBondState()

    override fun createBond(): Boolean = device.createBond()

    override fun removeBond(): Boolean = runCatching {
        device.javaClass.getMethod("removeBond").invoke(device) as Boolean
    }.getOrDefault(false)

    override suspend fun requestMtu(mtu: Int): Int =
        operation(MTU_TIMEOUT_MS, Pending.Mtu()) { gatt?.requestMtu(mtu) == true }

    override fun requestHighPriority() {
        val ok = gatt?.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH) == true
        Log.i(TAG, "connection priority HIGH requested=$ok")
    }

    override suspend fun enableNotifications(service: UUID, characteristic: UUID, indication: Boolean) {
        val g = gatt ?: throw BleException("not connected")
        val c = g.getService(service)?.getCharacteristic(characteristic)
            ?: throw BleException("characteristic $characteristic not found")
        if (!g.setCharacteristicNotification(c, true)) throw BleException("setCharacteristicNotification failed")
        val d = c.getDescriptor(GattIds.CCCD) ?: throw BleException("CCCD not found")
        val value = if (indication) {
            BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
        } else {
            BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        }
        operation<Unit>(OP_TIMEOUT_MS, Pending.Descriptor(characteristic)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                g.writeDescriptor(d, value) == BluetoothStatusCodes.SUCCESS
            } else {
                @Suppress("DEPRECATION")
                d.value = value
                @Suppress("DEPRECATION")
                g.writeDescriptor(d)
            }
        }
    }

    override suspend fun write(service: UUID, characteristic: UUID, value: ByteArray, withResponse: Boolean) {
        val g = gatt ?: throw BleException("not connected")
        val c = g.getService(service)?.getCharacteristic(characteristic)
            ?: throw BleException("characteristic $characteristic not found")
        val supportsWrite = c.properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0
        val type = if (withResponse && supportsWrite) {
            BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        } else {
            BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        }
        if (BuildConfig.DEBUG) Log.d(TAG, "TX ${characteristic.toString().substring(4, 8)} ${value.toHex()}")
        operation<Unit>(OP_TIMEOUT_MS, Pending.Write(characteristic)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                g.writeCharacteristic(c, value, type) == BluetoothStatusCodes.SUCCESS
            } else {
                @Suppress("DEPRECATION")
                c.writeType = type
                @Suppress("DEPRECATION")
                c.value = value
                @Suppress("DEPRECATION")
                g.writeCharacteristic(c)
            }
        }
    }

    override fun close() {
        pending?.result?.completeExceptionally(BleException("closed"))
        gatt?.let {
            runCatching { it.disconnect() }
            runCatching { it.close() }
        }
        gatt = null
        if (receiverRegistered) {
            runCatching { context.unregisterReceiver(bondReceiver) }
            receiverRegistered = false
        }
    }

    private companion object {
        const val TAG = "BleTransport"
        const val CONNECT_TIMEOUT_MS = 15_000L
        const val OP_TIMEOUT_MS = 5_000L
        const val MTU_TIMEOUT_MS = 2_000L
    }
}

internal fun Int.toBondState(): BondState = when (this) {
    BluetoothDevice.BOND_BONDED -> BondState.BONDED
    BluetoothDevice.BOND_BONDING -> BondState.BONDING
    else -> BondState.NONE
}

private fun Intent.bluetoothDevice(): BluetoothDevice? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
    } else {
        @Suppress("DEPRECATION")
        getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
    }
