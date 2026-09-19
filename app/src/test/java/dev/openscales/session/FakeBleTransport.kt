package dev.openscales.session

import dev.openscales.ble.BleException
import dev.openscales.ble.BleTransport
import dev.openscales.ble.BondState
import dev.openscales.ble.TransportEvent
import dev.openscales.protocol.Cmd
import dev.openscales.protocol.Frame
import dev.openscales.protocol.FrameCodec
import dev.openscales.protocol.GattIds
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import java.util.UUID

/**
 * Эмулятор весов протокола 2025 для тестов: отвечает на чтения из [readResponses],
 * подтверждает записи и позволяет сымитировать сбои.
 */
class FakeBleTransport(
    override val address: String = "C8:47:8C:00:11:22",
    var services: Set<UUID> = setOf(GattIds.SERVICE_2025),
    var bond: BondState = BondState.BONDED,
) : BleTransport {

    override val events = MutableSharedFlow<TransportEvent>(extraBufferCapacity = 256)

    val readResponses = mutableMapOf(
        Cmd.BATTERY to byteArrayOf(3, 80),
        Cmd.MODEL to "TES016".toByteArray(),
        Cmd.WEIGHT_UNIT to byteArrayOf(0),
        Cmd.MODE_STAGE to byteArrayOf(1, 1),
        Cmd.TIMER to byteArrayOf(3),
        Cmd.DEVICE_NAME to "Basic 3".toByteArray(),
    )

    /** Коды, на запись которых весы отвечают отказом. */
    val rejectedWrites = mutableSetOf<Int>()

    /** Коды, на которые весы не отвечают вовсе. */
    val silentCommands = mutableSetOf<Int>()

    var connectError: String? = null
    var cccdError: String? = null
    var onCreateBond: () -> Boolean = { bond = BondState.BONDING; true }

    val frames = mutableListOf<Frame>()
    val legacyWrites = mutableListOf<ByteArray>()
    var connectCount = 0
    var closeCount = 0
    var notificationsEnabled = mutableListOf<UUID>()

    override suspend fun connect() {
        connectCount++
        delay(CONNECT_LATENCY_MS)
        connectError?.let { throw BleException(it) }
    }

    override suspend fun discoverServices(): Set<UUID> = services

    override fun bondState(): BondState = bond

    override fun createBond(): Boolean = onCreateBond()

    override fun removeBond(): Boolean {
        bond = BondState.NONE
        return true
    }

    override suspend fun requestMtu(mtu: Int): Int = mtu

    override suspend fun enableNotifications(service: UUID, characteristic: UUID, indication: Boolean) {
        cccdError?.let { throw BleException(it) }
        notificationsEnabled += characteristic
    }

    override suspend fun write(service: UUID, characteristic: UUID, value: ByteArray, withResponse: Boolean) {
        if (characteristic == GattIds.COMMAND_LEGACY) {
            legacyWrites += value
            return
        }
        val frame = FrameCodec.split(value).single()
        frames += frame
        if (frame.cmd in silentCommands) return
        when (frame.type) {
            Frame.TYPE_READ -> readResponses[frame.cmd]?.let { emitFrame(Frame.TYPE_READ, frame.cmd, it) }
            Frame.TYPE_WRITE -> emitFrame(
                Frame.TYPE_WRITE,
                frame.cmd,
                byteArrayOf(if (frame.cmd in rejectedWrites) 0 else 1),
            )
        }
    }

    fun emitFrame(type: Int, cmd: Int, payload: ByteArray) {
        events.tryEmit(TransportEvent.Notification(GattIds.NOTIFY_2025, FrameCodec.encode(type, cmd, payload)))
    }

    fun disconnectFromDevice(status: Int = 19) {
        events.tryEmit(TransportEvent.Disconnected(status))
    }

    override fun close() {
        closeCount++
    }

    fun writesOf(cmd: Int) = frames.filter { it.cmd == cmd }

    companion object {
        /** Как у реального GATT: подключение не мгновенное. */
        const val CONNECT_LATENCY_MS = 300L
    }
}
