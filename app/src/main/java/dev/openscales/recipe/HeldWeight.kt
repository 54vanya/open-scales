package dev.openscales.recipe

/**
 * Последний устоявшийся вес — для подстановки в ручной ввод дозы: вес, который не отходил от опоры серии больше
 * чем на [TOLERANCE_G] хотя бы [HOLD_MS], и не меньше [MIN_G]. Показания не буферизуем — храним опору серии,
 * её последний кадр и время начала. Устоялась ли серия, решается в момент запроса: при неизменном весе новых
 * кадров может не быть, а вес всё равно держится. Промежуточные кадры, пока чашку снимают, секунду не держатся.
 */
class HeldWeight {
    private var anchorG: Double? = null
    private var lastG = 0.0
    private var sinceMs = 0L
    private var heldG: Double? = null

    fun onWeight(weightG: Double, nowMs: Long) {
        val anchor = anchorG
        if (anchor != null && kotlin.math.abs(weightG - anchor) <= TOLERANCE_G + EPSILON_G) {
            lastG = weightG
            return
        }
        // Серия кончилась: если она устоялась, её вес и есть последний устоявшийся.
        current(nowMs)?.let { heldG = it }
        anchorG = weightG
        lastG = weightG
        sinceMs = nowMs
    }

    /** Последний устоявшийся вес; `null` — такого ещё не было. */
    fun held(nowMs: Long): Double? = current(nowMs) ?: heldG

    /** Текущая серия, если она уже устоялась. */
    private fun current(nowMs: Long): Double? =
        lastG.takeIf { anchorG != null && nowMs - sinceMs >= HOLD_MS && it >= MIN_G - EPSILON_G }

    companion object {
        /** Чуть больше шага унций (0.01 oz ≈ 0.28 г): дрожание на единицу в унциях серию не рвёт. */
        const val TOLERANCE_G = 0.3
        const val HOLD_MS = 1_000L
        const val MIN_G = 1.0

        /** Вес приходит `Float`: 18.5 − 18.2 в нём может быть чуть больше 0.3. */
        private const val EPSILON_G = 1e-3
    }
}
