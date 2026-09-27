package dev.openscales.recipe

import org.junit.Assert.assertEquals
import org.junit.Test

/** Табло пролива по сценариям спеки; встроенный рецепт, доза 15 г: цели 30/150/250 у элементов 0, 4, 5. */
class PourFocusTest {

    private val timeline = RecipeTimeline(BuiltInRecipes.hoffmannV60)

    private fun focus(weight: Double, seconds: Int?) = PourFocus.of(timeline, 15.0, weight, seconds)!!

    @Test
    fun `middle of a pour`() {
        val f = focus(70.0, 50)
        assertEquals(4, f.itemIndex)
        assertEquals(150.0, f.targetG, 1e-9)
        assertEquals(80.0, f.leftG, 1e-9)
        assertEquals(TargetState.BELOW, f.state)
    }

    @Test
    fun `reached target does not jump to the next pour before its time`() {
        val f = focus(149.5, 65)
        assertEquals(4, f.itemIndex)
        assertEquals(0.5, f.leftG, 1e-9)
        assertEquals(TargetState.AT, f.state)
    }

    @Test
    fun `overpour`() {
        val f = focus(158.0, 70)
        assertEquals(4, f.itemIndex)
        assertEquals(-8.0, f.leftG, 1e-9)
        assertEquals(TargetState.OVER, f.state)
    }

    @Test
    fun `late pour sums what is left of the previous steps`() {
        // 0:50 — идёт первый пролив (до 150 г), а в цветение налито 20 из 30: 10 + 120 = 130.
        val f = focus(20.0, 50)
        assertEquals(4, f.itemIndex)
        assertEquals(150.0, f.targetG, 1e-9)
        assertEquals(130.0, f.leftG, 1e-9)
        assertEquals(TargetState.BELOW, f.state)
    }

    @Test
    fun `wait between pours shows the finished bloom`() {
        val f = focus(30.0, 30)
        assertEquals(0, f.itemIndex)
        assertEquals(TargetState.AT, f.state)
    }

    @Test
    fun `before the pour and after the end`() {
        assertEquals(0, focus(0.0, null).itemIndex)
        assertEquals(30.0, focus(0.0, null).leftG, 1e-9)
        val end = focus(255.0, 210)
        assertEquals(5, end.itemIndex)
        assertEquals(TargetState.OVER, end.state)
    }

    @Test
    fun `second pour follows once its time starts`() {
        assertEquals(5, focus(150.0, 75).itemIndex)
    }
}
