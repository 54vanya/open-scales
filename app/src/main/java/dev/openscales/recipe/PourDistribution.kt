package dev.openscales.recipe

import dev.openscales.protocol.WeightUnit
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

/** Как показывать воду в шагах: настройка «Вес в шагах рецепта». */
enum class StepWeightMode {
    /** Сколько ещё налить в шаг: `c − f`, у последнего уходит в минус при переливе. */
    REMAINING,

    /** «Налито / цель»: `f / c`, у будущих шагов первое число — рубеж предыдущего. */
    POURED;

    companion object {
        val DEFAULT = REMAINING

        fun fromName(name: String?): StepWeightMode = entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}

object PourDistribution {

    /**
     * Налитое в каждый шаг с целью при весе [weightG]: вес, ограниченный рубежами соседних шагов.
     * `f[i] = clamp(w, c[i-1], c[i])`, у первого нижний рубеж 0, у последнего нет верхнего. Отрицательный вес — ноль.
     */
    fun fill(targetsG: List<Double>, weightG: Double): List<Double> {
        val w = max(weightG, 0.0)
        return targetsG.mapIndexed { i, c ->
            val low = if (i == 0) 0.0 else targetsG[i - 1]
            if (i == targetsG.lastIndex) max(w, low) else w.coerceIn(low, c)
        }
    }

    /**
     * Значения шагов с целью по частям рецепта: у каждой части свои рубежи и свой налитый вес [pouredIn].
     * Ключ — индекс элемента в `Recipe.items`.
     */
    fun values(recipe: Recipe, doseG: Double, pouredIn: (part: Int) -> Double, mode: StepWeightMode): Map<Int, StepWater> =
        buildMap {
            recipe.targetsByPart(doseG).forEachIndexed { part, targets ->
                val water = values(targets.map { it.second }, pouredIn(part), mode)
                targets.zip(water) { (index, _), value -> put(index, value) }
            }
        }

    /** Значения шагов с целью в выбранном режиме, в граммах. */
    fun values(targetsG: List<Double>, weightG: Double, mode: StepWeightMode): List<StepWater> =
        fill(targetsG, weightG).zip(targetsG) { f, c ->
            StepWater(if (mode == StepWeightMode.REMAINING) c - f else f, c, mode)
        }
}

/**
 * Вода шага к показу, всё в граммах: [grams] — крупное значение режима (остаток или налитое), [targetG] — цель шага,
 * она показывается мелко под ним с подписью режима. Единицы весов — при форматировании.
 */
data class StepWater(val grams: Double, val targetG: Double, val mode: StepWeightMode)

const val GRAMS_PER_OUNCE = 28.349523

fun toGrams(value: Double, unit: WeightUnit): Double = if (unit == WeightUnit.OUNCE) value * GRAMS_PER_OUNCE else value

fun fromGrams(grams: Double, unit: WeightUnit): Double = if (unit == WeightUnit.OUNCE) grams / GRAMS_PER_OUNCE else grams

/**
 * Вес для шагов рецепта в единицах весов: граммы — целыми, унции — с двумя знаками. Округлённый ноль без минуса;
 * минус — типографский (U+2212).
 */
fun formatStepWeight(grams: Double, unit: WeightUnit): String =
    formatSignedWeight(grams, unit, decimals = if (unit == WeightUnit.OUNCE) 2 else 0)

/** Вес с точностью весов — граммы с десятой, унции с сотыми (крупное значение табло пролива); ноль без минуса. */
fun formatReadingWeight(grams: Double, unit: WeightUnit): String = formatSignedWeight(grams, unit, unit.decimals)

private fun formatSignedWeight(grams: Double, unit: WeightUnit, decimals: Int): String {
    val text = String.format(Locale.US, "%.${decimals}f", abs(fromGrams(grams, unit)))
    val zero = text.all { it == '0' || it == '.' }
    return if (grams < 0 && !zero) "−$text" else text
}

