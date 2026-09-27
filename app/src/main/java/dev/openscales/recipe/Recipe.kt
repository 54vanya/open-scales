package dev.openscales.recipe

import androidx.annotation.StringRes

/** Текст рецепта: у встроенного — ресурс (переводится с интерфейсом), у пользовательского — своя строка. */
sealed interface Text {
    data class Res(@StringRes val id: Int) : Text
    data class Plain(val value: String) : Text
}

/** Элемент рецепта: шаг действия со временем или просто подсказка. */
sealed interface RecipeItem {
    /**
     * Шаг действия. [targetG] — накопительный рубеж воды в граммах при дозе рецепта по умолчанию: сколько всего
     * должно быть налито к концу шага. Целое число, как в источниках и в редакторе, — без дробей в хранимых данных.
     * У шагов без воды (покачать, подождать) его нет. [showTime] — табло варки показывает на этом шаге крупно
     * не воду, а сколько времени осталось до конца шага (аэропресс: ожидание перед прожимом, сам прожим).
     */
    data class Step(
        val title: Text,
        val durationS: Int,
        val note: Text? = null,
        val targetG: Int? = null,
        val showTime: Boolean = false,
    ) : RecipeItem {
        init {
            require(durationS > 0) { "step duration must be positive" }
        }
    }

    data class Hint(val text: Text) : RecipeItem
}

/**
 * Оборудование — группа на вкладке «Рецепты», в порядке показа. [FLAT_BOTTOM] — плоскодонные воронки (Kalita Wave,
 * Orea). В JSON — имя в нижнем регистре; прежнее `pour_over` читается как [V60].
 */
enum class RecipeCategory { V60, FLAT_BOTTOM, SWITCH, CHEMEX, AEROPRESS, FRENCH_PRESS }

/** Сложность рецепта для того, кто варит: сколько действий и насколько точно их надо выдержать. */
enum class Difficulty { EASY, MEDIUM, HARD }

data class Recipe(
    val id: String,
    val title: Text,
    val defaultDoseG: Int,
    val description: Text?,
    val items: List<RecipeItem>,
    val category: RecipeCategory = RecipeCategory.V60,
    val difficulty: Difficulty = Difficulty.MEDIUM,
) {
    init {
        require(defaultDoseG > 0) { "default dose must be positive" }
        require(defaultTargetsG.zipWithNext().all { (a, b) -> a <= b }) { "step targets must not decrease" }
    }

    /** Рубежи шагов при дозе по умолчанию сверху вниз, только у шагов с целью. */
    val defaultTargetsG: List<Int> get() = items.mapNotNull { (it as? RecipeItem.Step)?.targetG }

    /** Рубеж [targetG] (при дозе по умолчанию), пересчитанный на дозу [doseG]. */
    fun scaled(targetG: Int, doseG: Double): Double = targetG * doseG / defaultDoseG

    /** Цели шагов в граммах при дозе [doseG]: накопительные рубежи сверху вниз. */
    fun targetsG(doseG: Double): List<Double> = defaultTargetsG.map { scaled(it, doseG) }

    /** Сколько воды всего при дозе [doseG]: рубеж последнего шага с целью. */
    fun totalWaterG(doseG: Double): Double = defaultTargetsG.lastOrNull()?.let { scaled(it, doseG) } ?: 0.0
}
