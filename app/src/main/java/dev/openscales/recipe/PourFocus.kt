package dev.openscales.recipe

import kotlin.math.abs

/** Где вес относительно цели шага воды: лить дальше, хватит (±[PourFocus.TOLERANCE_G]) или перелив. */
enum class TargetState { BELOW, AT, OVER }

/**
 * Шаг, в который сейчас льётся вода, — для табло пролива. [itemIndex] — позиция в `Recipe.items`,
 * [targetG] — накопительная цель шага, [leftG] — сколько до неё осталось (без ограничения снизу: перелив < 0),
 * [previousG] — цель шага выше в той же части рецепта (у первого — ноль).
 *
 * У шага с диапазоном [targetG] — нижняя граница, [maxG] — верхняя, а [leftG] — расстояние до зоны между ними:
 * сколько осталось до [targetG], ноль внутри зоны, минус от [maxG] при переливе. [roomG] — сколько ещё можно налить
 * до [maxG], пока вес внутри зоны; вне зоны и у шага без диапазона — `null`.
 */
data class PourFocus(
    val itemIndex: Int,
    val targetG: Double,
    val leftG: Double,
    val previousG: Double = 0.0,
    val maxG: Double = targetG,
    val roomG: Double? = null,
) {

    /** Налитое по весу: [leftG] считается от ближайшей границы зоны. */
    private val pouredG: Double
        get() = when {
            leftG > 0 -> targetG - leftG
            leftG < 0 -> maxG - leftG
            else -> maxG - (roomG ?: 0.0)
        }

    /** Доля налитого в шаг для полосы налива: от цели выше до цели шага (у диапазона — до его максимума), 0..1. */
    val fraction: Float
        get() {
            val span = maxG - previousG
            if (span <= 0.0) return if (state == TargetState.BELOW) 0f else 1f
            return ((pouredG - previousG) / span).coerceIn(0.0, 1.0).toFloat()
        }

    /** Где на полосе налива начинается зона диапазона, 0..1; `null` — у шага одна цель. */
    val zoneStart: Float?
        get() = if (maxG > targetG) ((targetG - previousG) / (maxG - previousG)).coerceIn(0.0, 1.0).toFloat() else null

    /**
     * Идеальный уровень для полосы налива: доля прошедшего времени шага воды ко времени [seconds] (с долями секунды — отметка на полосе движется почти непрерывно), 0..1 — сколько
     * было бы налито при ровном проливе. `null` секунд (пролив не начался) — 0. У шага с диапазоном ровный пролив
     * идёт к нижней границе: к концу шага отметка стоит у начала зоны.
     */
    fun paceFraction(timeline: RecipeTimeline, seconds: Double?): Float {
        if (seconds == null) return 0f
        val end = zoneStart ?: 1f
        val start = timeline.startOf(itemIndex)
        val length = timeline.endOf(itemIndex) - start
        if (length <= 0) return end
        return ((seconds - start) / length).coerceIn(0.0, 1.0).toFloat() * end
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
            val max = timeline.recipe.maxTargetG(index, doseG) ?: target
            val poured = tare.pouredIn(part, weightG)
            val inZone = poured in target..max
            val left = when {
                poured < target -> target - poured
                poured > max -> max - poured
                else -> 0.0
            }
            return PourFocus(index, target, left, previous, max, if (inZone && max > target) max - poured else null)
        }
    }
}
