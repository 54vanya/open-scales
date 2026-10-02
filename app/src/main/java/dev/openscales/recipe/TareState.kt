package dev.openscales.recipe

/**
 * Нули частей рецепта при варке. [zeros] — ноль каждой оттарированной части (у первой — ноль тары «Старт»),
 * [frozen] — вес на весах, на котором часть застыла, когда время ушло в следующую часть. Часть без нуля ещё
 * ждёт тары: налитым в неё считается ноль.
 */
data class TareState(
    val zeros: Map<Int, Double> = mapOf(0 to 0.0),
    val frozen: Map<Int, Double> = emptyMap(),
) {
    fun isTared(part: Int): Boolean = part in zeros

    /** Сколько налито в часть [part] при весе на весах [weightG]: от её нуля, у застывшей — на момент застывания. */
    fun pouredIn(part: Int, weightG: Double): Double {
        val zero = zeros[part] ?: return 0.0
        return (frozen[part] ?: weightG) - zero
    }

    /** Время ушло в часть [part]: все части до неё застывают на весе [weightG], если ещё не застыли. */
    fun freezeBefore(part: Int, weightG: Double): TareState {
        val add = (0 until part).filter { it !in frozen }
        return if (add.isEmpty()) this else copy(frozen = frozen + add.associateWith { weightG })
    }

    /** Весы оттарированы на части [part]; её ноль — [zeroG]. */
    fun tared(part: Int, zeroG: Double): TareState = copy(zeros = zeros + (part to zeroG))
}
