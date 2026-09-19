package dev.openscales.ble

import dev.openscales.protocol.Cmd
import dev.openscales.protocol.FrameCodec
import dev.openscales.protocol.GattIds
import dev.openscales.protocol.toHex
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.onEach
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/** Кольцевой журнал BLE-обмена для отладки на реальных весах. Используется только в debug-сборке. */
class BleJournal(
    private val capacity: Int = DEFAULT_CAPACITY,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    enum class Kind { PHASE, BOND, MTU, NOTIFY, TX, RX, ERROR, INFO }

    data class Entry(val timeMs: Long, val kind: Kind, val text: String)

    private val buffer = ArrayDeque<Entry>(capacity)
    private val _entries = MutableStateFlow<List<Entry>>(emptyList())
    val entries: StateFlow<List<Entry>> = _entries.asStateFlow()

    fun add(kind: Kind, text: String) {
        synchronized(buffer) {
            if (buffer.size == capacity) buffer.removeFirst()
            buffer.addLast(Entry(clock(), kind, text))
            _entries.value = buffer.toList()
        }
    }

    fun clear() {
        synchronized(buffer) {
            buffer.clear()
            _entries.value = emptyList()
        }
    }

    fun export(header: String): String = buildString {
        appendLine(header)
        appendLine()
        entries.value.forEach { appendLine(format(it)) }
    }

    companion object {
        const val DEFAULT_CAPACITY = 2000

        private val timeFormat = ThreadLocal.withInitial { SimpleDateFormat("HH:mm:ss.SSS", Locale.US) }

        fun format(e: Entry): String = "${timeFormat.get()!!.format(Date(e.timeMs))} ${e.kind.name.padEnd(6)} ${e.text}"

        /** Hex кадра + расшифровка команд протокола 2025, например `A5 5A 02 05 … [read BATTERY]`. */
        fun describe(characteristic: UUID, bytes: ByteArray): String {
            val hex = bytes.toHex()
            if (characteristic != GattIds.NOTIFY_2025 && characteristic != GattIds.WRITE_2025) return hex
            val frames = FrameCodec.split(bytes)
            if (frames.isEmpty()) return hex
            val names = frames.joinToString(", ") { f ->
                val type = when (f.type) { 1 -> "report"; 2 -> "read"; 3 -> "write"; else -> "type${f.type}" }
                "$type ${commandName(f.cmd)}"
            }
            return "$hex  [$names]"
        }

        private val commandNames: Map<Int, String> by lazy {
            Cmd::class.java.declaredFields
                .filter { it.type == Int::class.javaPrimitiveType && java.lang.reflect.Modifier.isStatic(it.modifiers) }
                .associate { it.getInt(null) to it.name }
        }

        fun commandName(cmd: Int): String = commandNames[cmd] ?: "0x%02X".format(cmd)
    }
}

/** Decorator над [BleTransport], пишущий всё в [BleJournal]. */
class LoggingBleTransport(
    private val delegate: BleTransport,
    private val journal: BleJournal,
) : BleTransport by delegate {

    override val events: Flow<TransportEvent> = delegate.events.onEach { e ->
        when (e) {
            is TransportEvent.Notification -> journal.add(BleJournal.Kind.RX, BleJournal.describe(e.characteristic, e.value))
            is TransportEvent.BondChanged -> journal.add(BleJournal.Kind.BOND, "${e.previous} -> ${e.state}")
            is TransportEvent.Disconnected -> journal.add(BleJournal.Kind.ERROR, "disconnected, status=${e.status}")
        }
    }

    private suspend fun <T> logged(kind: BleJournal.Kind, what: String, block: suspend () -> T): T =
        try {
            block().also { journal.add(kind, "$what ok${if (it is Unit) "" else " -> $it"}") }
        } catch (e: Exception) {
            journal.add(BleJournal.Kind.ERROR, "$what failed: ${e.message}")
            throw e
        }

    override suspend fun connect() = logged(BleJournal.Kind.INFO, "connect ${delegate.address}") { delegate.connect() }

    override suspend fun discoverServices() = logged(BleJournal.Kind.INFO, "discover services") { delegate.discoverServices() }

    override fun createBond(): Boolean = delegate.createBond().also { journal.add(BleJournal.Kind.BOND, "createBond -> $it") }

    override suspend fun requestMtu(mtu: Int) = logged(BleJournal.Kind.MTU, "request MTU $mtu") { delegate.requestMtu(mtu) }

    override suspend fun enableNotifications(service: UUID, characteristic: UUID, indication: Boolean) =
        logged(BleJournal.Kind.NOTIFY, "${if (indication) "indicate" else "notify"} $characteristic") {
            delegate.enableNotifications(service, characteristic, indication)
        }

    override suspend fun write(service: UUID, characteristic: UUID, value: ByteArray, withResponse: Boolean) {
        journal.add(BleJournal.Kind.TX, BleJournal.describe(characteristic, value))
        try {
            delegate.write(service, characteristic, value, withResponse)
        } catch (e: Exception) {
            journal.add(BleJournal.Kind.ERROR, "write failed: ${e.message}")
            throw e
        }
    }

    override fun requestHighPriority() {
        journal.add(BleJournal.Kind.INFO, "connection priority HIGH")
        delegate.requestHighPriority()
    }

    override fun close() {
        journal.add(BleJournal.Kind.INFO, "close GATT")
        delegate.close()
    }
}
