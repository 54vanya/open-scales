package dev.openscales.recipe

import kotlin.math.abs

/** Где вес относительно цели шага воды: лить дальше, хватит (±[PourFocus.TOLERANCE_G]) или перелив. */
enum class TargetState { BELOW, AT, OVER }

/**
 * Шаг, в который сейчас льётся вода, — для табло пролива. [itemIndex] — позиция в `Recipe.items`,
 * [targetG] — накопительная цель шага, [leftG] — сколько до неё осталось (без ограничения снизу: перелив < 0),
 * [previousG] — цель шага выше в той же части рецепта (у первого — ноль).
 */
data class PourFocus(val itemIndex: Int, val targetG: Double, val leftG: Double, val previousG: Double = 0.0) {

    /** Доля налитого в шаг для полосы налива: от цели выше до цели шага, 0..1. */
    val fraction: Float
        get() {
            val span = targetG - previousG
            if (span <= 0.0) return if (state == TargetState.BELOW) 0f else 1f
            return ((targetG - leftG - previousG) / span).coerceIn(0.0, 1.0).toFloat()
        }

    /**
     * Идеальный уровень для полосы налива: доля прошедшего времени шага воды ко времени [seconds] (с долями секунды — отметка на полосе движется почти непрерывно), 0..1 — сколько
     * было бы налито при ровном проливе. `null` секунд (пролив не начался) — 0.
     */
    fun paceFraction(timeline: RecipeTimeline, seconds: Double?): Float {
        if (seconds == null) return 0f
        val start = timeline.startOf(itemIndex)
        val length = timeline.endOf(itemIndex) - start
        if (length <= 0) return 1f
        return ((seconds - start) / length).coerceIn(0.0, 1.0).toFloat()
    }

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
         * Вода улеглась, когда пролив остановился и осталось налить меньше этого (перелив тоже подходит): табло
         * отпускает воду к отсчёту шага без воды или к итогу. Шире [TOLERANCE_G]: цвет «попал» строже, чем «хватит».
         */
        const val SETTLE_LEFT_G = 3.0

        /**
         * Шаг воды — последний шаг с целью, который уже начался по времени ([seconds] `null` — пролив не начался,
         * берётся первый). Цель накопительная, поэтому при запоздалом проливе недолитое в прошлых шагах
         * складывается с текущим: табло показывает, сколько налить всего к концу этого шага. Набранная цель
         * не сменяется следующей, пока не начнётся её шаг.
         *
         * Шаг выбирается только в части рецепта, где идёт время, и налитое считается от нуля этой части ([tare]):
         * недолитое в прошлых частях не переносится. `null` — в части нет шагов с целью или она ждёт тары.
         */
        fun of(
            timeline: RecipeTimeline,
            doseG: Double,
            weightG: Double,
            seconds: Int?,
            tare: TareState = TareState(),
        ): PourFocus? {
            val part = timeline.partAt(seconds)
            if (!tare.isTared(part)) return null
            val targets = timeline.recipe.targetsByPart(doseG)[part]
            if (targets.isEmpty()) return null
            val byTime = if (seconds == null) {
                0
            } else {
                targets.indexOfLast { (i, _) -> timeline.startOf(i) <= seconds }.coerceAtLeast(0)
            }
            val (index, target) = targets[byTime]
            val previous = if (byTime > 0) targets[byTime - 1].second else 0.0
            return PourFocus(index, target, target - tare.pouredIn(part, weightG), previous)
        }
    }
}
