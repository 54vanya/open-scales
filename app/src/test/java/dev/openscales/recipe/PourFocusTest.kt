package dev.openscales.recipe

import org.junit.Assert.assertEquals
import org.junit.Test

/** Табло пролива по сценариям спеки; встроенный рецепт, доза 15 г: цели 30/150/250 у элементов 0, 4, 5. */
class PourFocusTest {

    private val timeline = RecipeTimeline(BuiltInRecipes.hoffmannV60)

    private fun focus(weight: Double, seconds: Int?) = PourFocus.of(timeline, 15.0, weight, seconds)!!

    private val vanBunnik = RecipeTimeline(BuiltInRecipes.vanBunnikAeropress)

    @Test
    fun `part waiting for tare has no water step`() {
        // 1:16 — шаг «Тара», весы ещё не оттарированы.
        val tare = TareState().freezeBefore(1, 170.0)
        assertEquals(null, PourFocus.of(vanBunnik, 30.0, 170.0, 76, tare))
    }

    @Test
    fun `after tare the water step counts from the part zero`() {
        val tare = TareState().freezeBefore(1, 170.0).tared(1, 0.0)
        // На самом шаге «Тара» — первая цель части; недолитое в первой части не переносится.
        val onTare = PourFocus.of(vanBunnik, 30.0, 0.0, 80, tare)!!
        assertEquals(6, onTare.itemIndex)
        assertEquals(100.0, onTare.leftG, 1e-9)
        val diluting = PourFocus.of(vanBunnik, 30.0, 40.0, 90, tare)!!
        assertEquals(60.0, diluting.leftG, 1e-9)
    }

    @Test
    fun `pour bar fills with the water of the step`() {
        assertEquals(0.5f, focus(90.0, 50).fraction, 1e-6f)
        assertEquals(30.0, focus(90.0, 50).previousG, 1e-9)
        assertEquals(0f, focus(20.0, 50).fraction, 1e-6f)
        assertEquals(1f, focus(150.5, 65).fraction, 1e-6f)
        val over = focus(158.0, 70)
        assertEquals(1f, over.fraction, 1e-6f)
        assertEquals(TargetState.OVER, over.state)
        assertEquals(0f, focus(0.0, null).fraction, 1e-6f)
        val tare = TareState().freezeBefore(1, 170.0).tared(1, 0.0)
        assertEquals(0.25f, PourFocus.of(vanBunnik, 30.0, 25.0, 90, tare)!!.fraction, 1e-6f)
    }

    @Test
    fun `pace of a short pour runs over the pour itself`() {
        // 4:6 Касуи: первые 60 г наливают за 10 с, ожидание — отдельный шаг.
        val kasuya = RecipeTimeline(BuiltInRecipes.kasuya46)
        val pouring = PourFocus.of(kasuya, 20.0, 30.0, 5)!!
        assertEquals(0, pouring.itemIndex)
        assertEquals(0.5f, pouring.paceFraction(kasuya, 5.0), 1e-6f)
    }

    @Test
    fun `pace moves evenly through the water step`() {
        assertEquals(0.5f, focus(60.0, 60).paceFraction(timeline, 60.0), 1e-6f)
        assertEquals(1f / 6, focus(110.0, 50).paceFraction(timeline, 50.0), 1e-6f)
        assertEquals(0f, focus(0.0, null).paceFraction(timeline, null), 0f)
        assertEquals(1f, focus(150.0, 70).paceFraction(timeline, 80.0), 0f)
        // Доли секунды: 0:48.5 — 3,5 с от начала тридцатисекундного пролива с 0:45.
        assertEquals(0.1167f, focus(60.0, 48).paceFraction(timeline, 48.5), 1e-4f)
    }

    @Test
    fun `pour bar with a target equal to the one above`() {
        assertEquals(0f, PourFocus(0, 100.0, 5.0, previousG = 100.0).fraction, 0f)
        assertEquals(1f, PourFocus(0, 100.0, 0.5, previousG = 100.0).fraction, 0f)
    }

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
