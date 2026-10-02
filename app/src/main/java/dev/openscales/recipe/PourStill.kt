package dev.openscales.recipe

/**
 * Пролив остановился: вес за последние [WINDOW_MS] вырос меньше чем на [RISE_G]. Показания не буферизуем —
 * храним только опорный вес и время, когда он был задан. Рост от опоры на [RISE_G] и больше переставляет опору
 * и время; спад опускает опору, не трогая время, — шум и капли не сбрасывают ожидание.
 */
class PourStill {
    private var baseG: Double? = null
    private var sinceMs = 0L

    fun onWeight(weightG: Double, nowMs: Long) {
        val base = baseG
        when {
            base == null || weightG >= base + RISE_G - EPSILON_G -> {
                baseG = weightG
                sinceMs = nowMs
            }
            weightG < base -> baseG = weightG
        }
    }

    /** До первого веса пролив не считается остановившимся. */
    fun isStill(nowMs: Long): Boolean = baseG != null && nowMs - sinceMs >= WINDOW_MS

    /** Когда пролив станет остановившимся, если вес больше не вырастет; `null` — веса ещё не было. */
    val stillAtMs: Long? get() = if (baseG != null) sinceMs + WINDOW_MS else null

    companion object {
        const val RISE_G = 0.5
        const val WINDOW_MS = 1_500L

        /** Вес приходит `Float`: 30.5 − 30.0 в нём может быть чуть меньше 0,5. */
        private const val EPSILON_G = 1e-3
    }
}
