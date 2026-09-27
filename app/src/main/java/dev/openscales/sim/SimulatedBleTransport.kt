package dev.openscales.sim

import dev.openscales.ble.BleException
import dev.openscales.ble.BleTransport
import dev.openscales.ble.BondState
import dev.openscales.ble.TransportEvent
import dev.openscales.protocol.Frame
import dev.openscales.protocol.FrameCodec
import dev.openscales.protocol.GattIds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Соединение с виртуальными весами [ScaleSimulator]: вместо радио — общий на процесс [ScaleEmulator].
 * После включения уведомлений сам шлёт кадры веса, как DOT, ~10 раз в секунду.
 */
internal class SimulatedBleTransport(
    private val simulator: ScaleSimulator,
    private val scope: CoroutineScope,
) : BleTransport {

    override val address: String = ScaleSimulator.ADDRESS
    override val events = MutableSharedFlow<TransportEvent>(extraBufferCapacity = 256)

    private var ticker: Job? = null
    private var closed = false

    /** Связь установлена и не разорвана — к этому транспорту идут кадры и команды из терминала. */
    val isLinked: Boolean get() = !closed && linked

    private var linked = false

    override suspend fun connect() {
        delay(CONNECT_LATENCY_MS)
        if (!simulator.available) throw BleException("virtual scale is off")
        linked = true
    }

    override suspend fun discoverServices(): Set<UUID> = setOf(GattIds.SERVICE_2025)

    override fun bondState(): BondState = BondState.BONDED

    override fun createBond(): Boolean = true

    override fun removeBond(): Boolean = true

    override suspend fun requestMtu(mtu: Int): Int = mtu

    override suspend fun enableNotifications(service: UUID, characteristic: UUID, indication: Boolean) {
        ticker?.cancel()
        ticker = scope.launch {
            while (isActive) {
                delay(FRAME_INTERVAL_MS)
                send(simulator.emulator.weightFrame())
            }
        }
    }

    override suspend fun write(service: UUID, characteristic: UUID, value: ByteArray, withResponse: Boolean) {
        if (!isLinked) throw BleException("virtual scale is not connected")
        FrameCodec.split(value).forEach { frame ->
            val response = simulator.emulator.onFrame(frame)
            response.frames.forEach(::send)
            if (response.disconnect) simulator.onDeviceDisconnect(frame.cmd)
        }
    }

    fun send(frame: Frame) {
        if (isLinked) events.tryEmit(TransportEvent.Notification(GattIds.NOTIFY_2025, ScaleEmulator.encodeLikeScale(frame)))
    }

    /** Весы пропали: связь рвётся, как при выключении весов или уходе из зоны. */
    fun drop() {
        if (!isLinked) return
        linked = false
        ticker?.cancel()
        events.tryEmit(TransportEvent.Disconnected(STATUS_LINK_LOST))
    }

    override fun close() {
        closed = true
        ticker?.cancel()
        simulator.detach(this)
    }

    companion object {
        /** Как у `FakeBleTransport`: подключение не мгновенное, фазы успевают показаться. */
        const val CONNECT_LATENCY_MS = 300L
        const val FRAME_INTERVAL_MS = 100L

        /** GATT_CONN_TIMEOUT — так Android сообщает о пропавших весах. */
        private const val STATUS_LINK_LOST = 8
    }
}
