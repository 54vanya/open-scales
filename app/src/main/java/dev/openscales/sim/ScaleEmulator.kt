package dev.openscales.sim

import dev.openscales.protocol.Cmd
import dev.openscales.protocol.Frame
import dev.openscales.protocol.FrameCodec
import dev.openscales.protocol.TimerState
import dev.openscales.protocol.WeightUnit
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Весы протокола 2025 без радио: отвечают на команды приложения и собирают кадры веса. Общее ядро
 * виртуальных весов debug-сборки и тестового `FakeBleTransport`. Кадры сами не шлёт — это делает транспорт.
 *
 * Вес считается от времени [nowMs]: опорный вес плюс идущий пролив, минус тара, плюс шум. Поэтому пролив
 * не зависит от того, как часто транспорт спрашивает кадры. Однопоточный, как всё состояние приложения.
 */
class ScaleEmulator(
    private val nowMs: () -> Long,
    private val defaultModel: String = DOT_MODEL,
    private val defaultName: String = DEFAULT_NAME,
    private val random: Random = Random.Default,
) {
    /** Готовые ответы на чтение поверх эмуляции: так тесты подменяют, что «прислали весы». */
    val readOverrides = mutableMapOf<Int, ByteArray>()

    /** Команды записи, на которые весы отвечают отказом. */
    val rejected = mutableSetOf<Int>()

    var model = defaultModel
    var name = defaultName
    var battery = DEFAULT_BATTERY
    var unit = WeightUnit.GRAM
    var noiseG = 0.0
    var standbySeconds = DEFAULT_STANDBY_S
    var sensitivity = 2
    var precision = 1
    var sound = true
    var brightness = 70

    private var baseG = 0.0
    private var pour: Pour? = null
    var tareG = 0.0
        private set

    private var timerState = TimerState.RESET
    private var timerStartedAt = 0L
    private var timerAccumulatedMs = 0L

    private class Pour(val startMs: Long, val durationMs: Long, val amountG: Double)

    // region вес

    /** Вес на платформе без учёта тары. */
    fun grossG(now: Long = nowMs()): Double {
        val p = pour ?: return baseG
        val fraction = ((now - p.startMs).toDouble() / p.durationMs).coerceIn(0.0, 1.0)
        return baseG + p.amountG * fraction
    }

    /** Показание весов: с тарой и шумом. */
    fun shownG(now: Long = nowMs()): Double {
        val noise = if (noiseG > 0) random.nextDouble(-noiseG, noiseG) else 0.0
        return grossG(now) - tareG + noise
    }

    /** Поток — изменение веса за последнюю секунду, без шума. */
    fun flowGps(now: Long = nowMs()): Double = grossG(now) - grossG(now - 1_000)

    fun setWeight(grams: Double) {
        baseG = grams
        pour = null
    }

    /** Плавно добавить [grams] за [seconds]; идущий пролив сначала «застывает» на текущем весе. */
    fun pour(grams: Double, seconds: Double) {
        val now = nowMs()
        baseG = grossG(now)
        pour = Pour(now, (seconds * 1_000).toLong().coerceAtLeast(1), grams)
    }

    fun tare() {
        tareG = grossG()
    }

    // endregion

    // region таймер

    val timerSeconds: Int get() = (timerElapsedMs() / 1_000).toInt()

    val timer: TimerState get() = timerState

    private fun timerElapsedMs(now: Long = nowMs()): Long =
        timerAccumulatedMs + if (timerState == TimerState.RUNNING) now - timerStartedAt else 0L

    private fun applyTimer(target: TimerState) {
        val now = nowMs()
        when (target) {
            TimerState.RUNNING -> if (timerState != TimerState.RUNNING) {
                timerStartedAt = now
                timerState = TimerState.RUNNING
            }
            TimerState.PAUSED -> {
                timerAccumulatedMs = timerElapsedMs(now)
                timerState = TimerState.PAUSED
            }
            TimerState.RESET -> {
                // Как у DOT: «сбрасывать нечего» прошивка понимает как тару.
                if (timerState != TimerState.RUNNING && timerElapsedMs(now) == 0L) tare()
                timerState = TimerState.RESET
                timerAccumulatedMs = 0
            }
        }
    }

    // endregion

    /** Ответ весов на кадр приложения. [disconnect] — после ответа весы рвут связь (выключение, сброс). */
    class Response(val frames: List<Frame>, val disconnect: Boolean = false)

    fun onFrame(frame: Frame): Response = when (frame.type) {
        Frame.TYPE_READ -> Response(listOfNotNull(read(frame.cmd)?.let { Frame(Frame.TYPE_READ, frame.cmd, it) }))
        Frame.TYPE_WRITE -> write(frame.cmd, frame.payload)
        else -> Response(emptyList())
    }

    private fun read(cmd: Int): ByteArray? = readOverrides[cmd] ?: when (cmd) {
        Cmd.WEIGHT -> weightPayload()
        Cmd.TIMER -> byteArrayOf(timerState.code.toByte())
        Cmd.BATTERY -> byteArrayOf(3, battery.toByte())
        Cmd.WEIGHT_UNIT -> byteArrayOf(unit.code.toByte())
        Cmd.SOUND -> byteArrayOf(if (sound) 1 else 0)
        Cmd.MODE_STAGE -> byteArrayOf(1, 1)
        Cmd.DEVICE_NAME -> name.toByteArray(Charsets.UTF_8)
        Cmd.FIRMWARE_VERSION -> FIRMWARE.toByteArray(Charsets.US_ASCII)
        Cmd.MODEL -> model.toByteArray(Charsets.US_ASCII)
        Cmd.STANDBY_TIME -> byteArrayOf((standbySeconds ushr 8).toByte(), standbySeconds.toByte())
        Cmd.SENSITIVITY -> byteArrayOf(5, sensitivity.toByte())
        Cmd.PRECISION -> byteArrayOf(precision.toByte())
        Cmd.BRIGHTNESS -> byteArrayOf(brightness.toByte())
        // DOT на запрос серийного номера молчит (docs/protocol.md).
        else -> null
    }

    private fun write(cmd: Int, p: ByteArray): Response {
        fun ack(ok: Boolean = true) = listOf(Frame(Frame.TYPE_WRITE, cmd, byteArrayOf(if (ok) 1 else 0)))
        if (cmd == Cmd.DISCONNECT) return Response(emptyList(), disconnect = true)
        if (cmd in rejected) return Response(ack(false))
        when (cmd) {
            Cmd.TARE -> tare()
            Cmd.TIMER -> p.firstOrNull()?.let { TimerState.fromCode(it.toInt() and 0xFF) }?.let(::applyTimer)
            Cmd.WEIGHT_UNIT -> unit = WeightUnit.fromCode(p.firstOrNull()?.toInt() ?: 0)
            Cmd.SOUND -> sound = p.firstOrNull()?.toInt() == 1
            Cmd.DEVICE_NAME -> name = p.decodeToString().trimEnd(Char(0))
            Cmd.STANDBY_TIME -> if (p.size >= 2) standbySeconds = (p[0].toInt() and 0xFF shl 8) or (p[1].toInt() and 0xFF)
            Cmd.SENSITIVITY -> if (p.size >= 2) sensitivity = p[1].toInt()
            Cmd.PRECISION -> p.firstOrNull()?.let { precision = it.toInt() }
            Cmd.BRIGHTNESS -> p.firstOrNull()?.let { brightness = it.toInt() and 0xFF }
            Cmd.POWER_OFF, Cmd.FACTORY_RESET -> {
                if (cmd == Cmd.FACTORY_RESET) reset()
                return Response(ack(), disconnect = true)
            }
        }
        return Response(ack())
    }

    /** Кадр веса, какой весы шлют сами ~10 раз в секунду. */
    fun weightFrame(): Frame = Frame(Frame.TYPE_REPORT, Cmd.WEIGHT, weightPayload())

    /** Сообщение о смене единиц, как после нажатия на корпусе. */
    fun unitFrame(): Frame = Frame(Frame.TYPE_READ, Cmd.WEIGHT_UNIT, byteArrayOf(unit.code.toByte()))

    fun batteryFrame(): Frame = Frame(Frame.TYPE_READ, Cmd.BATTERY, byteArrayOf(3, battery.toByte()))

    private fun weightPayload(): ByteArray {
        val now = nowMs()
        val weight = raw(shownG(now))
        val flow = raw(flowGps(now)).coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
        val seconds = timerSeconds.coerceIn(0, 0xFFFF)
        return byteArrayOf(
            (weight ushr 24).toByte(), (weight ushr 16).toByte(), (weight ushr 8).toByte(), weight.toByte(),
            (flow ushr 8).toByte(), flow.toByte(),
            (seconds ushr 8).toByte(), seconds.toByte(),
        )
    }

    /** Граммы в единицы кадра: ×10 для граммов, ×100 для унций. */
    private fun raw(grams: Double): Int {
        val value = if (unit == WeightUnit.OUNCE) grams / GRAMS_PER_OUNCE else grams
        return (value * unit.divisor).roundToInt()
    }

    /** Исходное состояние: пустые весы, граммы, таймер сброшен, без отказов и подмен. */
    fun reset() {
        baseG = 0.0
        pour = null
        tareG = 0.0
        noiseG = 0.0
        unit = WeightUnit.GRAM
        battery = DEFAULT_BATTERY
        model = defaultModel
        name = defaultName
        standbySeconds = DEFAULT_STANDBY_S
        sensitivity = 2
        precision = 1
        sound = true
        brightness = 70
        timerState = TimerState.RESET
        timerAccumulatedMs = 0
        rejected.clear()
    }

    companion object {
        const val DOT_MODEL = "TES017"
        const val DEFAULT_NAME = "Virtual DOT"
        const val FIRMWARE = "v1.0.4"
        const val DEFAULT_BATTERY = 80
        const val DEFAULT_STANDBY_S = 900
        const val GRAMS_PER_OUNCE = 28.349523

        /** Кадр так, как его шлют весы: CRC у DOT всегда нулевой. */
        fun encodeLikeScale(frame: Frame): ByteArray =
            FrameCodec.encode(frame.type, frame.cmd, frame.payload).also {
                it[it.size - 2] = 0
                it[it.size - 1] = 0
            }
    }
}
