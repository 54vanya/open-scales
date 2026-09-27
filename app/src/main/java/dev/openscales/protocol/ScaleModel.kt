package dev.openscales.protocol

/**
 * Модели весов Timemore Black Mirror. [code] — номер модели в хранилище запомненных весов.
 * Возможности (звук, яркость) — какие настройки есть у модели.
 */
enum class ScaleModel(
    val code: Int,
    val displayName: String,
    /** Работает по протоколу 2025 (FFF0/FFF1/FFF2). */
    val modernProtocol: Boolean,
    val hasSoundSwitch: Boolean,
    val hasBrightness: Boolean,
) {
    OLD_DOUBLE(0, "Black Mirror (TES08)", modernProtocol = false, hasSoundSwitch = true, hasBrightness = false),
    DOT(1, "Black Mirror DOT", modernProtocol = true, hasSoundSwitch = false, hasBrightness = false),
    ESPRO(2, "Black Mirror ESPRO", modernProtocol = true, hasSoundSwitch = true, hasBrightness = true),
    BASIC3(3, "Black Mirror Basic 3", modernProtocol = true, hasSoundSwitch = true, hasBrightness = false),
    UNKNOWN(-1, "Timemore", modernProtocol = true, hasSoundSwitch = false, hasBrightness = false);

    val isKnown: Boolean get() = this != UNKNOWN

    companion object {
        fun fromCode(code: Int): ScaleModel = entries.firstOrNull { it.code == code } ?: UNKNOWN

        /** Ответ на чтение модели (cmd 0x13), например `TES015`. */
        fun fromModelString(model: String): ScaleModel =
            when (model.filter { it.isLetterOrDigit() }) {
                "TES015" -> ESPRO
                "TES016" -> BASIC3
                "TES017" -> DOT
                "TES08" -> OLD_DOUBLE
                else -> UNKNOWN
            }
    }
}
