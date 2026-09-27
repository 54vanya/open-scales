package dev.openscales.session

import dev.openscales.ble.BleException
import dev.openscales.ble.BleTransport
import dev.openscales.ble.BondState
import dev.openscales.ble.TransportEvent
import dev.openscales.protocol.Cmd
import dev.openscales.protocol.Frame
import dev.openscales.protocol.FrameCodec
import dev.openscales.protocol.GattIds
import dev.openscales.sim.ScaleEmulator
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import java.util.UUID

/**
 * Весы протокола 2025 для тестов: протокол — общий с виртуальными весами [ScaleEmulator], поверх него —
 * подменённые ответы [readResponses] и сбои транспорта. Кадры веса сам не шлёт: тесты подают их вручную.
 */
class FakeBleTransport(
    override val address: String = "C8:47:8C:00:11:22",
    var services: Set<UUID> = setOf(GattIds.SERVICE_2025),
    var bond: BondState = BondState.BONDED,
) : BleTransport {

    override val events = MutableSharedFlow<TransportEvent>(extraBufferCapacity = 256)

    /**
     * Протокол отыгрывает общее с виртуальными весами ядро; готовые ответы ниже важнее эмуляции —
     * тесты подменяют их, чтобы задать, что «прислали весы».
     */
    val emulator = ScaleEmulator(nowMs = { 0L }, defaultModel = "TES016", defaultName = "Basic 3")

    val readResponses: MutableMap<Int, ByteArray> = emulator.readOverrides.apply {
        putAll(
            mapOf(
                Cmd.BATTERY to byteArrayOf(3, 80),
                Cmd.MODEL to "TES016".toByteArray(),
                Cmd.WEIGHT_UNIT to byteArrayOf(0),
                Cmd.MODE_STAGE to byteArrayOf(1, 1),
                Cmd.TIMER to byteArrayOf(3),
                Cmd.DEVICE_NAME to "Basic 3".toByteArray(),
            ),
        )
    }

    /** Коды, на запись которых весы отвечают отказом. */
    val rejectedWrites: MutableSet<Int> get() = emulator.rejected

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
        // Разрыв связи после выключения или сброса тесты задают сами — здесь только ответы.
        emulator.onFrame(frame).frames.forEach { emitFrame(it.type, it.cmd, it.payload) }
    }

    fun emitFrame(type: Int, cmd: Int, payload: ByteArray) {
        events.tryEmit(TransportEvent.Notification(GattIds.NOTIFY_2025, FrameCodec.encode(type, cmd, payload)))
    }

    /** Кадр веса 18.3 г, поток 2.4 г/с с временем таймера весов [seconds]. */
    fun emitWeight(seconds: Int) = emitFrame(
        Frame.TYPE_READ,
        Cmd.WEIGHT,
        byteArrayOf(0, 0, 0, 0xB7.toByte(), 0, 0x18, (seconds ushr 8).toByte(), seconds.toByte()),
    )

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
