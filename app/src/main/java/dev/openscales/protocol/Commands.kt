package dev.openscales.protocol

/** Коды команд протокола 2025 (байт `cmd` кадра). */
object Cmd {
    const val WEIGHT = 0x01
    const val TIMER = 0x02
    const val BATTERY = 0x05
    const val WEIGHT_UNIT = 0x06
    const val SOUND = 0x07
    const val MODE_STAGE = 0x08
    const val POWER_OFF = 0x0B
    const val DEVICE_NAME = 0x0C
    const val TARE = 0x0D
    const val FIRMWARE_VERSION = 0x10
    const val SERIAL_NUMBER = 0x12
    const val MODEL = 0x13
    const val STANDBY_TIME = 0x16
    const val SENSITIVITY = 0x17
    const val PRECISION = 0x18
    const val FACTORY_RESET = 0x19
    const val FORGET_DEVICE = 0x1A
    const val DISCONNECT = 0x1C
    const val BRIGHTNESS = 0x25
}

/** Значения payload команды таймера [Cmd.TIMER] и статуса таймера. */
enum class TimerState(val code: Int) {
    RUNNING(1), PAUSED(2), RESET(3);

    companion object {
        fun fromCode(code: Int): TimerState? = entries.firstOrNull { it.code == code }
    }
}

/** Коды legacy-команд TES08. */
object LegacyCmd {
    const val TARE = 0x00
    const val START_TIMER = 0x08
    const val PAUSE_TIMER = 0x09
    const val RESET_TIMER = 0x0A
    const val DEVICE_NAME = 0x0B
    const val KEY_SOUND = 0x10
}

enum class WeightUnit(val code: Int, val symbol: String, val divisor: Float, val decimals: Int) {
    GRAM(0, "g", 10f, 1),
    OUNCE(1, "oz", 100f, 2);

    companion object {
        fun fromCode(code: Int) = if (code == 1) OUNCE else GRAM
    }
}

enum class Sensitivity(val level: Int) {
    HIGH(1), MEDIUM(2), LOW(3);

    companion object {
        fun fromLevel(level: Int) = entries.firstOrNull { it.level == level } ?: LOW
    }
}

enum class Precision(val code: Int) {
    HIGH(1), LOW(10);

    companion object {
        fun fromCode(code: Int) = if (code == 10) LOW else HIGH
    }
}
