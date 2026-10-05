package dev.openscales.recipe

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Устоявшийся вес: держится в пределах 0.3 г хотя бы секунду и не меньше 1 г. */
class HeldWeightTest {

    @Test
    fun `nothing held before any weight`() {
        assertNull(HeldWeight().held(10_000))
    }

    @Test
    fun `lifting the cup keeps the weighed beans`() {
        val held = HeldWeight()
        for (i in 0..20) held.onWeight(18.2, i * 100L)
        held.onWeight(9.6, 2_100)
        held.onWeight(0.1, 2_200)
        assertEquals(18.2, held.held(5_000)!!, 1e-9)
    }

    @Test
    fun `a series shorter than a second does not count`() {
        val held = HeldWeight()
        held.onWeight(12.0, 0)
        held.onWeight(18.2, 500)
        held.onWeight(0.1, 1_400)
        assertNull(held.held(1_450))
    }

    @Test
    fun `an unchanged weight holds without new frames`() {
        val held = HeldWeight()
        held.onWeight(18.2, 0)
        assertNull(held.held(999))
        assertEquals(18.2, held.held(1_000)!!, 1e-9)
    }

    @Test
    fun `ounce jitter does not break the series`() {
        val held = HeldWeight()
        // 0.64 и 0.65 oz — соседние показания в унциях.
        listOf(0.64, 0.65, 0.64, 0.65, 0.64).forEachIndexed { i, oz -> held.onWeight(oz * GRAMS_PER_OUNCE, i * 250L) }
        assertEquals(0.64 * GRAMS_PER_OUNCE, held.held(1_000)!!, 1e-9)
    }

    @Test
    fun `an empty scale is not a dose`() {
        val held = HeldWeight()
        held.onWeight(0.4, 0)
        assertNull(held.held(5_000))
    }

    @Test
    fun `the last held weight wins`() {
        val held = HeldWeight()
        held.onWeight(15.0, 0)
        held.onWeight(18.2, 2_000)
        held.onWeight(0.0, 4_000)
        assertEquals(18.2, held.held(6_000)!!, 1e-9)
    }
}
