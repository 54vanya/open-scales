package dev.openscales.recipe

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Остановка пролива: вес за 1,5 с вырос меньше чем на 0,5 г. */
class PourStillTest {

    @Test
    fun `before any weight the pour is not still`() {
        val still = PourStill()
        assertFalse(still.isStill(10_000))
        assertNull(still.stillAtMs)
    }

    @Test
    fun `a steady pour is never still`() {
        val still = PourStill()
        for (i in 0..40) {
            still.onWeight(i * 1.0, i * 250L)
            assertFalse("${i * 250} ms", still.isStill(i * 250L))
        }
    }

    @Test
    fun `still exactly 1,5 s after the last rise`() {
        val still = PourStill()
        still.onWeight(0.0, 0)
        still.onWeight(30.0, 1_000)
        assertEquals(2_500L, still.stillAtMs)
        assertFalse(still.isStill(2_499))
        assertTrue(still.isStill(2_500))
    }

    @Test
    fun `scale noise does not hold the pour`() {
        val still = PourStill()
        still.onWeight(30.0, 0)
        listOf(30.2, 29.8, 30.1, 29.9, 30.2).forEachIndexed { i, w -> still.onWeight(w, (i + 1) * 100L) }
        assertTrue(still.isStill(1_500))
    }

    @Test
    fun `a falling weight does not restart the wait`() {
        val still = PourStill()
        still.onWeight(30.0, 0)
        still.onWeight(28.0, 1_000)
        assertTrue(still.isStill(1_500))
        // Опора опустилась до 28 г: рост на 0,5 г от неё — снова пролив.
        still.onWeight(28.6, 1_600)
        assertFalse(still.isStill(2_000))
        assertEquals(3_100L, still.stillAtMs)
    }

    @Test
    fun `a trickle under 0,5 g in 1,5 s counts as stopped`() {
        val still = PourStill()
        still.onWeight(30.0, 0)
        still.onWeight(30.15, 500)
        still.onWeight(30.3, 1_000)
        still.onWeight(30.45, 1_500)
        assertTrue(still.isStill(1_500))
    }

    @Test
    fun `half a gram in float counts as a rise`() {
        val still = PourStill()
        still.onWeight(30.0f.toDouble(), 0)
        still.onWeight(30.5f.toDouble() - 1e-4, 1_000)
        assertEquals(2_500L, still.stillAtMs)
    }
}
