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
     * У шагов без воды (покачать, подождать) его нет.
     * [tare] — шаг «Тара»: с него начинается новая часть рецепта, рубежи которой считаются от нуля тары на этом шаге
     * (снять аэропресс и разбавить концентрат в чашке). Цели и крупного времени у него нет.
     */
    data class Step(
        val title: Text,
        val durationS: Int,
        val note: Text? = null,
        val targetG: Int? = null,
        val tare: Boolean = false,
    ) : RecipeItem {
        init {
            require(durationS > 0) { "step duration must be positive" }
            require(!tare || targetG == null) { "tare step has no target" }
        }

        /**
         * Табло варки показывает на этом шаге крупно не воду, а сколько времени осталось до конца шага. Не задаётся
         * отдельно: так у каждого шага без воды (подождать, взболтать, прожать), кроме шага «Тара».
         */
        val showTime: Boolean get() = targetG == null && !tare
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
    /**
     * Часть рецепта каждого элемента: шаги «Тара» делят рецепт на части, шаг «Тара» — первый в своей части.
     * У рецепта без тары всё — часть 0.
     */
    val partOf: List<Int> = run {
        var part = 0
        items.map { item -> if ((item as? RecipeItem.Step)?.tare == true) ++part else part }
    }

    /** Сколько частей: на одну больше, чем шагов «Тара». */
    val partCount: Int get() = (partOf.lastOrNull() ?: 0) + 1

    init {
        require(defaultDoseG > 0) { "default dose must be positive" }
        require(targetsByPart(defaultDoseG.toDouble()).all { part -> part.zipWithNext().all { (a, b) -> a.second <= b.second } }) {
            "step targets must not decrease within a part"
        }
    }

    /** Рубежи шагов при дозе по умолчанию сверху вниз, только у шагов с целью (все части подряд). */
    val defaultTargetsG: List<Int> get() = items.mapNotNull { (it as? RecipeItem.Step)?.targetG }

    /** Цели шагов в граммах при дозе [doseG] сверху вниз (все части подряд). */
    fun targetsG(doseG: Double): List<Double> = defaultTargetsG.map { scaled(it, doseG) }

    /** Рубеж [targetG] (при дозе по умолчанию), пересчитанный на дозу [doseG]. */
    fun scaled(targetG: Int, doseG: Double): Double = targetG * doseG / defaultDoseG

    /** Цели шагов по частям при дозе [doseG]: индекс элемента и накопительный от нуля части рубеж. */
    fun targetsByPart(doseG: Double): List<List<Pair<Int, Double>>> {
        val parts = List(partCount) { mutableListOf<Pair<Int, Double>>() }
        items.forEachIndexed { i, item ->
            (item as? RecipeItem.Step)?.targetG?.let { parts[partOf[i]].add(i to scaled(it, doseG)) }
        }
        return parts
    }

    /** Сколько воды всего при дозе [doseG]: сумма последних рубежей всех частей. */
    fun totalWaterG(doseG: Double): Double = targetsByPart(doseG).sumOf { it.lastOrNull()?.second ?: 0.0 }
}
