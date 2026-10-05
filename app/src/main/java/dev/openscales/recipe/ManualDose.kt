package dev.openscales.recipe

import dev.openscales.protocol.WeightUnit
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.pow

/**
 * Ручной ввод дозы: число в единицах весов с их точностью (граммы — десятые, унции — сотые), разделитель — точка
 * или запятая. Доза в граммах — от [MIN_G] до [MAX_G].
 */
object ManualDose {
    const val MIN_G = 1.0

    /** Больше — лишнее нажатие, а не доза кофе. */
    const val MAX_G = 100.0

    fun inRange(grams: Double): Boolean = grams >= MIN_G && grams <= MAX_G

    /** Поле принимает текст: не больше трёх цифр до разделителя и не больше точности весов после. */
    fun accepts(text: String, unit: WeightUnit): Boolean = Regex("""\d{0,3}([.,]\d{0,${unit.decimals}})?""").matches(text)

    /** Введённая доза в граммах; `null` — пусто, не число или вне диапазона. */
    fun parseGrams(text: String, unit: WeightUnit): Double? =
        text.replace(',', '.').toDoubleOrNull()?.let { toGrams(it, unit) }?.takeIf(::inRange)

    /** Подстановка в поле: вес с точностью весов. */
    fun text(grams: Double, unit: WeightUnit): String = formatReadingWeight(grams, unit)

    /** Границы для подсказки в единицах весов, округлённые внутрь диапазона: показанный максимум принимается. */
    fun bounds(unit: WeightUnit): Pair<String, String> {
        val scale = 10.0.pow(boundDecimals(unit))
        val min = ceil(fromGrams(MIN_G, unit) * scale - EPSILON) / scale
        val max = floor(fromGrams(MAX_G, unit) * scale + EPSILON) / scale
        return format(min, unit) to format(max, unit)
    }

    /** Граммы — целыми («От 1 до 100 г»), унции — с сотыми. */
    private fun boundDecimals(unit: WeightUnit) = if (unit == WeightUnit.OUNCE) unit.decimals else 0

    private fun format(value: Double, unit: WeightUnit) = String.format(Locale.US, "%.${boundDecimals(unit)}f", value)

    private const val EPSILON = 1e-9
}
