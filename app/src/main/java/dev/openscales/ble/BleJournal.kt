package dev.openscales.ble

import android.os.SystemClock
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

/**
 * Кольцевой журнал BLE-обмена для отладки на реальных весах. Используется только в debug-сборке.
 * Важные записи дублируются в отдельный буфер [notable]: поток кадров вытесняет общий журнал за ~3 минуты,
 * а редкий сбой должен дождаться, пока его посмотрят.
 */
class BleJournal(
    private val capacity: Int = DEFAULT_CAPACITY,
    private val notableCapacity: Int = NOTABLE_CAPACITY,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    /**
     * [TIMER] — тики секундомера, [STALL] — главный поток был занят дольше порога (сторож в debug-сборке),
     * [GAP] — весы дольше порога не присылали кадров.
     */
    enum class Kind(val notableByDefault: Boolean = true) {
        PHASE, BOND, MTU, NOTIFY, TX(false), RX(false), ERROR, INFO, TIMER(false), STALL, GAP, UI(false)
    }

    data class Entry(val timeMs: Long, val kind: Kind, val text: String)

    private val buffer = ArrayDeque<Entry>(capacity)
    private val notableBuffer = ArrayDeque<Entry>(notableCapacity)
    private val _entries = MutableStateFlow<List<Entry>>(emptyList())
    private val _notable = MutableStateFlow<List<Entry>>(emptyList())
    val entries: StateFlow<List<Entry>> = _entries.asStateFlow()
    val notable: StateFlow<List<Entry>> = _notable.asStateFlow()

    fun add(kind: Kind, text: String, notable: Boolean = kind.notableByDefault) {
        synchronized(buffer) {
            val entry = Entry(clock(), kind, text)
            buffer.addBounded(entry, capacity)
            _entries.value = buffer.toList()
            if (notable) {
                notableBuffer.addBounded(entry, notableCapacity)
                _notable.value = notableBuffer.toList()
            }
        }
    }

    fun clear() {
        synchronized(buffer) {
            buffer.clear()
            notableBuffer.clear()
            _entries.value = emptyList()
            _notable.value = emptyList()
        }
    }

    fun export(header: String): String = buildString {
        appendLine(header)
        appendLine()
        appendLine("== Notable events ==")
        notable.value.forEach { appendLine(format(it)) }
        appendLine()
        appendLine("== Full log ==")
        entries.value.forEach { appendLine(format(it)) }
    }

    private fun ArrayDeque<Entry>.addBounded(entry: Entry, limit: Int) {
        if (size == limit) removeFirst()
        addLast(entry)
    }

    companion object {
        const val DEFAULT_CAPACITY = 2000
        const val NOTABLE_CAPACITY = 500

        /** Промежуток между кадрами протокола 2025, после которого пишется [Kind.GAP]: обычно 90–120 мс. */
        const val FRAME_GAP_MS = 300L

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
    private val nowMs: () -> Long = SystemClock::elapsedRealtime,
) : BleTransport by delegate {

    /** Время прошлого кадра протокола 2025; весы шлют вес сами ~10 Гц, так что долгая тишина — сбой. */
    private var lastFrameAt: Long? = null

    override val events: Flow<TransportEvent> = delegate.events.onEach { e ->
        when (e) {
            is TransportEvent.Notification -> {
                if (e.characteristic == GattIds.NOTIFY_2025) noteFrame()
                journal.add(BleJournal.Kind.RX, BleJournal.describe(e.characteristic, e.value))
            }
            is TransportEvent.BondChanged -> journal.add(BleJournal.Kind.BOND, "${e.previous} -> ${e.state}")
            is TransportEvent.Disconnected -> {
                lastFrameAt = null
                journal.add(BleJournal.Kind.ERROR, "disconnected, status=${e.status}")
            }
        }
    }

    private fun noteFrame() {
        val now = nowMs()
        lastFrameAt?.let { last ->
            if (now - last > BleJournal.FRAME_GAP_MS) journal.add(BleJournal.Kind.GAP, "no frames for ${now - last}ms")
        }
        lastFrameAt = now
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
