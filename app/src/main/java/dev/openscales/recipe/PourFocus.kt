package dev.openscales.recipe

import kotlin.math.abs

/** Где вес относительно цели шага воды: лить дальше, хватит (±[PourFocus.TOLERANCE_G]) или перелив. */
enum class TargetState { BELOW, AT, OVER }

/**
 * Шаг, в который сейчас льётся вода, — для табло пролива. [itemIndex] — позиция в `Recipe.items`,
 * [targetG] — накопительная цель шага, [leftG] — сколько до неё осталось (без ограничения снизу: перелив < 0).
 */
data class PourFocus(val itemIndex: Int, val targetG: Double, val leftG: Double) {

    val state: TargetState
        get() = when {
            leftG > TOLERANCE_G -> TargetState.BELOW
            abs(leftG) <= TOLERANCE_G -> TargetState.AT
            else -> TargetState.OVER
        }

    companion object {
        /** «В пределах 1 г от цели». */
        const val TOLERANCE_G = 1.0

        /**
         * Шаг воды — последний шаг с целью, который уже начался по времени ([seconds] `null` — пролив не начался,
         * берётся первый). Цель накопительная, поэтому при запоздалом проливе недолитое в прошлых шагах
         * складывается с текущим: табло показывает, сколько налить всего к концу этого шага. Набранная цель
         * не сменяется следующей, пока не начнётся её шаг. `null` — у рецепта нет шагов с целью.
         */
        fun of(timeline: RecipeTimeline, doseG: Double, weightG: Double, seconds: Int?): PourFocus? {
            val recipe = timeline.recipe
            val targets = recipe.items.indices.mapNotNull { i ->
                (recipe.items[i] as? RecipeItem.Step)?.targetG?.let { i to recipe.scaled(it, doseG) }
            }
            if (targets.isEmpty()) return null
            val byTime = if (seconds == null) {
                0
            } else {
                targets.indexOfLast { (i, _) -> timeline.startOf(i) <= seconds }.coerceAtLeast(0)
            }
            val (index, target) = targets[byTime]
            return PourFocus(index, target, target - weightG)
        }
    }
}
