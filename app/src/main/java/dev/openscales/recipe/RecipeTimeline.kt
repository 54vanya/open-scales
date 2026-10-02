package dev.openscales.recipe

/**
 * Шаги рецепта на оси времени. Индексы — позиции в [Recipe.items] (вместе с подсказками), как их рисует список.
 * Время `null` — пролив ещё не начался.
 */
class RecipeTimeline(val recipe: Recipe) {

    private val starts = IntArray(recipe.items.size)
    private val ends = IntArray(recipe.items.size)

    /** Общая длительность рецепта, с. */
    val total: Int

    init {
        var t = 0
        recipe.items.forEachIndexed { i, item ->
            starts[i] = t
            if (item is RecipeItem.Step) t += item.durationS
            ends[i] = t
        }
        total = t
    }

    fun isStep(index: Int) = recipe.items[index] is RecipeItem.Step

    fun startOf(index: Int): Int = starts[index]

    fun endOf(index: Int): Int = ends[index]

    /** Текущий шаг действия; нет до начала пролива и когда время рецепта кончилось. */
    fun stepAt(seconds: Int?): Int? {
        if (seconds == null || seconds < 0 || seconds >= total) return null
        return recipe.items.indices.firstOrNull { isStep(it) && seconds < ends[it] }
    }

    /**
     * Часть рецепта, в которой идёт время [seconds]: часть текущего шага, после конца времени — последняя,
     * до начала пролива — первая.
     */
    fun partAt(seconds: Int?): Int {
        if (seconds == null || seconds < 0) return 0
        val step = stepAt(seconds) ?: return recipe.partCount - 1
        return recipe.partOf[step]
    }

    /** Сколько секунд осталось в шаге [index] ко времени [seconds]. */
    fun remainingIn(index: Int, seconds: Int): Int = (ends[index] - seconds).coerceAtLeast(0)

    /**
     * Показывать ли элемент полупрозрачным: пройденный шаг, а подсказка — вместе с ближайшим шагом после неё.
     * Подсказки после последнего шага не бледнеют никогда.
     */
    fun isFaded(index: Int, seconds: Int?): Boolean {
        if (seconds == null) return false
        val step = if (isStep(index)) index else (index + 1..recipe.items.lastIndex).firstOrNull { isStep(it) } ?: return false
        return seconds >= ends[step]
    }
}
