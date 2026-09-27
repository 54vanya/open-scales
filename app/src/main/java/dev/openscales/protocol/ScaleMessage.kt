package dev.openscales.protocol

/** Декодированное сообщение от весов. */
sealed interface ScaleMessage {
    /** Кадр, к которому относится сообщение (нужен очереди команд для сопоставления ответа). */
    val frameType: Int
    val cmd: Int

    data class Weight(
        val grams: Float,
        val flowRate: Float,
        val timeSeconds: Int,
        val overload: Boolean,
        val unit: WeightUnit,
        override val frameType: Int = Frame.TYPE_READ,
    ) : ScaleMessage { override val cmd get() = Cmd.WEIGHT }

    data class Timer(val state: TimerState, override val frameType: Int = Frame.TYPE_READ) : ScaleMessage {
        override val cmd get() = Cmd.TIMER
    }

    /**
     * [status] — первый байт ответа 0x05; оригинал его не использует, смысл не подтверждён
     * (на DOT приходит `3`). Процент — второй байт (`DeviceStateView.updateBatteryInfo`).
     */
    data class Battery(val percent: Int, val status: Int, override val frameType: Int = Frame.TYPE_READ) :
        ScaleMessage { override val cmd get() = Cmd.BATTERY }

    data class Unit(val unit: WeightUnit, override val frameType: Int = Frame.TYPE_READ) : ScaleMessage {
        override val cmd get() = Cmd.WEIGHT_UNIT
    }

    data class Sound(val enabled: Boolean, override val frameType: Int = Frame.TYPE_READ) : ScaleMessage {
        override val cmd get() = Cmd.SOUND
    }

    data class ModeStage(val mode: Int, val stage: Int, override val frameType: Int = Frame.TYPE_READ) :
        ScaleMessage { override val cmd get() = Cmd.MODE_STAGE }

    data class Name(val name: String, override val frameType: Int = Frame.TYPE_READ) : ScaleMessage {
        override val cmd get() = Cmd.DEVICE_NAME
    }

    data class Firmware(val version: String, override val frameType: Int = Frame.TYPE_READ) : ScaleMessage {
        override val cmd get() = Cmd.FIRMWARE_VERSION
    }

    data class SerialNumber(val serial: String, override val frameType: Int = Frame.TYPE_READ) : ScaleMessage {
        override val cmd get() = Cmd.SERIAL_NUMBER
    }

    data class Model(val model: ScaleModel, override val frameType: Int = Frame.TYPE_READ) : ScaleMessage {
        override val cmd get() = Cmd.MODEL
    }

    data class StandbyTime(val seconds: Int, override val frameType: Int = Frame.TYPE_READ) : ScaleMessage {
        override val cmd get() = Cmd.STANDBY_TIME
    }

    data class SensitivityLevel(val sensitivity: Sensitivity, override val frameType: Int = Frame.TYPE_READ) :
        ScaleMessage { override val cmd get() = Cmd.SENSITIVITY }

    data class PrecisionLevel(val precision: Precision, override val frameType: Int = Frame.TYPE_READ) :
        ScaleMessage { override val cmd get() = Cmd.PRECISION }

    data class Brightness(val percent: Int, override val frameType: Int = Frame.TYPE_READ) : ScaleMessage {
        override val cmd get() = Cmd.BRIGHTNESS
    }

    /** Подтверждение записи (`type == 0x03`). */
    data class WriteAck(override val cmd: Int, val success: Boolean) : ScaleMessage {
        override val frameType: Int get() = Frame.TYPE_WRITE
    }

    /** Ответ, который мы не интерпретируем, но который всё равно закрывает запрос в очереди. */
    data class Raw(override val frameType: Int, override val cmd: Int, val payload: ByteArray) : ScaleMessage {
        override fun equals(other: Any?) =
            other is Raw && other.frameType == frameType && other.cmd == cmd && other.payload.contentEquals(payload)

        override fun hashCode() = 31 * (31 * frameType + cmd) + payload.contentHashCode()
    }
}

/** Разбор кадров протокола 2025 в [ScaleMessage]. */
object MessageDecoder {

    /** Никогда не возвращает null: некорректный payload превращается в [ScaleMessage.Raw]. */
    fun decode(frame: Frame, currentUnit: WeightUnit): ScaleMessage =
        decodeKnown(frame, currentUnit) ?: ScaleMessage.Raw(frame.type, frame.cmd, frame.payload)

    private fun decodeKnown(frame: Frame, currentUnit: WeightUnit): ScaleMessage? {
        val p = frame.payload
        val t = frame.type
        if (frame.isWriteAck) {
            return ScaleMessage.WriteAck(frame.cmd, success = p.isNotEmpty() && p.u8(0) == 1)
        }
        return when (frame.cmd) {
            Cmd.WEIGHT -> if (p.size < 8) null else ScaleMessage.Weight(
                grams = p.s32be(0) / currentUnit.divisor,
                flowRate = p.s16be(4) / currentUnit.divisor,
                timeSeconds = p.u16be(6),
                overload = p.size >= 9 && p.u8(8) == 1,
                unit = currentUnit,
                frameType = t,
            )

            Cmd.TIMER -> p.firstOrNull()?.let { TimerState.fromCode(it.toInt() and 0xFF) }
                ?.let { ScaleMessage.Timer(it, t) }

            Cmd.BATTERY -> if (p.size < 2) null else ScaleMessage.Battery(
                percent = p.u8(1).coerceIn(0, 100),
                status = p.u8(0),
                frameType = t,
            )

            Cmd.WEIGHT_UNIT -> ScaleMessage.Unit(WeightUnit.fromCode(if (p.isEmpty()) 0 else p.u8(0)), t)
            Cmd.SOUND -> ScaleMessage.Sound(p.isNotEmpty() && p.u8(0) == 1, t)
            Cmd.MODE_STAGE -> if (p.size < 2) null else ScaleMessage.ModeStage(p.u8(0), p.u8(1), t)
            Cmd.DEVICE_NAME -> ScaleMessage.Name(p.decodeToString().trimEnd(Char(0)), t)
            Cmd.FIRMWARE_VERSION -> ScaleMessage.Firmware(p.decodeToString().trimEnd(Char(0)), t)
            Cmd.SERIAL_NUMBER -> ScaleMessage.SerialNumber(p.decodeToString().trimEnd(Char(0)), t)
            Cmd.MODEL -> ScaleMessage.Model(ScaleModel.fromModelString(String(p, Charsets.US_ASCII)), t)
            Cmd.STANDBY_TIME -> if (p.size < 2) null else ScaleMessage.StandbyTime(p.u16be(0), t)
            // Оригинал: при длине 1 уровень в первом байте, иначе — во втором.
            Cmd.SENSITIVITY -> when {
                p.isEmpty() -> null
                p.size == 1 -> ScaleMessage.SensitivityLevel(Sensitivity.fromLevel(p.u8(0)), t)
                else -> ScaleMessage.SensitivityLevel(Sensitivity.fromLevel(p.u8(1)), t)
            }

            Cmd.PRECISION -> if (p.isEmpty()) null else ScaleMessage.PrecisionLevel(Precision.fromCode(p.u8(0)), t)
            Cmd.BRIGHTNESS -> if (p.isEmpty() || p.u8(0) > 100) null else ScaleMessage.Brightness(p.u8(0), t)
            else -> ScaleMessage.Raw(t, frame.cmd, p)
        }
    }
}
