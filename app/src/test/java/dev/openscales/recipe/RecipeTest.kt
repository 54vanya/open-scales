package dev.openscales.recipe

import dev.openscales.protocol.WeightUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecipeTest {

    private val hoffman = BuiltInRecipes.hoffmannV60
    private val timeline = RecipeTimeline(hoffman)

    private fun List<Double>.rounded() = map { Math.round(it).toInt() }

    @Test
    fun `built-in recipe lasts 3 30`() {
        assertEquals(210, timeline.total)
        assertEquals(210, hoffman.items.filterIsInstance<RecipeItem.Step>().sumOf { it.durationS })
    }

    @Test
    fun `targets scale with the dose`() {
        assertEquals(listOf(30, 150, 250), hoffman.targetsG(15.0).rounded())
        assertEquals(listOf(36, 180, 300), hoffman.targetsG(18.0).rounded())
        assertEquals(250.0, hoffman.targetsG(15.0).last(), 1e-9)
        assertEquals(200.0, hoffman.totalWaterG(12.0), 1e-9)
    }

    @Test
    fun `targets do not decrease`() {
        assertTrue(hoffman.defaultTargetsG.zipWithNext().all { (a, b) -> a <= b })
    }

    @Test(expected = IllegalArgumentException::class)
    fun `decreasing targets are rejected`() {
        Recipe(
            "x", Text.Plain("x"), 15, null,
            listOf(
                RecipeItem.Step(Text.Plain("a"), 10, targetG = 150),
                RecipeItem.Step(Text.Plain("b"), 10, targetG = 30),
            ),
        )
    }

    // region части рецепта

    private fun step(target: Int? = null, tare: Boolean = false, duration: Int = 10) =
        RecipeItem.Step(Text.Plain("s"), duration, targetG = target, tare = tare)

    private val parted = Recipe("x", Text.Plain("x"), 15, null, listOf(step(100), step(tare = true), step(50)))

    @Test
    fun `targets restart after a tare`() {
        assertEquals(listOf(0, 1, 1), parted.partOf)
        assertEquals(2, parted.partCount)
        assertEquals(listOf(listOf(0 to 100.0), listOf(2 to 50.0)), parted.targetsByPart(15.0))
        assertEquals(150.0, parted.totalWaterG(15.0), 1e-9)
        assertEquals(1, hoffman.partCount)
        assertEquals(250.0, hoffman.totalWaterG(15.0), 1e-9)
    }

    @Test
    fun `part by time`() {
        val t = RecipeTimeline(parted)
        assertEquals(0, t.partAt(null))
        assertEquals(0, t.partAt(5))
        assertEquals(1, t.partAt(10))
        assertEquals(1, t.partAt(30))
        assertEquals(0, timeline.partAt(100))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `targets decreasing within a part are rejected`() {
        Recipe("x", Text.Plain("x"), 15, null, listOf(step(100), step(tare = true), step(50), step(40)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `tare step has no target`() {
        step(100, tare = true)
    }

    @Test
    fun `tare state`() {
        val start = TareState()
        assertEquals(70.0, start.pouredIn(0, 70.0), 1e-9)
        assertEquals(0.0, start.pouredIn(1, 70.0), 1e-9)
        val frozen = start.freezeBefore(1, 70.0)
        assertEquals(70.0, frozen.pouredIn(0, 5.0), 1e-9)
        assertEquals(frozen, frozen.freezeBefore(1, 90.0))
        val tared = frozen.tared(1, 0.3)
        assertEquals(39.7, tared.pouredIn(1, 40.0), 1e-9)
        assertEquals(70.0, tared.pouredIn(0, 40.0), 1e-9)
    }

    @Test
    fun `distribution by part`() {
        val tare = TareState().freezeBefore(1, 90.0).tared(1, 0.0)
        val water = PourDistribution.values(parted, 15.0, { tare.pouredIn(it, 40.0) }, StepWeightMode.REMAINING)
        assertEquals(setOf(0, 2), water.keys)
        assertEquals(10.0, water.getValue(0).grams, 1e-9)
        assertEquals(10.0, water.getValue(2).grams, 1e-9)
        val waiting = PourDistribution.values(parted, 15.0, { TareState().pouredIn(it, 40.0) }, StepWeightMode.REMAINING)
        assertEquals(50.0, waiting.getValue(2).grams, 1e-9)
    }

    // endregion

    // region timeline

    @Test
    fun `middle of the recipe`() {
        // 0:20 — «Подождите» 0:17–0:45 (элемент 3), осталось 0:25.
        val current = timeline.stepAt(20)
        assertEquals(3, current)
        assertEquals(17, timeline.startOf(3))
        assertEquals(45, timeline.endOf(3))
        assertEquals(25, timeline.remainingIn(3, 20))
        assertTrue(timeline.isFaded(0, 20)) // Цветение
        assertTrue(timeline.isFaded(1, 20)) // подсказка про цветение
        assertTrue(timeline.isFaded(2, 20)) // Покачайте
        assertFalse(timeline.isFaded(3, 20))
        assertFalse(timeline.isFaded(4, 20))
    }

    @Test
    fun `hint fades together with the next step`() {
        assertFalse(timeline.isFaded(1, 12)) // Цветение прошло, «Покачайте» ещё идёт
        assertTrue(timeline.isFaded(0, 12))
        assertTrue(timeline.isFaded(1, 17))
    }

    @Test
    fun `before the pour there is no current step and nothing is faded`() {
        assertNull(timeline.stepAt(null))
        assertTrue(hoffman.items.indices.none { timeline.isFaded(it, null) })
        assertEquals(0, timeline.stepAt(0))
    }

    @Test
    fun `end of time`() {
        assertEquals(8, timeline.stepAt(209))
        assertNull(timeline.stepAt(210))
        assertNull(timeline.stepAt(300))
        val lastHint = hoffman.items.lastIndex
        hoffman.items.indices.filter { timeline.isStep(it) }.forEach { assertTrue(timeline.isFaded(it, 210)) }
        assertTrue(timeline.isFaded(1, 210))
        assertFalse(timeline.isFaded(lastHint, 210))
    }

    // endregion

    // region distribution

    private val targets15 = hoffman.targetsG(15.0)

    /** Крупное значение и цель под ним: «80 (150)». */
    private fun show(weight: Double, mode: StepWeightMode) =
        PourDistribution.values(targets15, weight, mode).map {
            assertEquals(mode, it.mode)
            formatStepWeight(it.grams, WeightUnit.GRAM) + " (" + formatStepWeight(it.targetG, WeightUnit.GRAM) + ")"
        }

    @Test
    fun `left to pour`() {
        assertEquals(listOf("0 (30)", "80 (150)", "100 (250)"), show(70.0, StepWeightMode.REMAINING))
        assertEquals(listOf("30 (30)", "120 (150)", "100 (250)"), show(0.0, StepWeightMode.REMAINING))
        assertEquals(listOf("0 (30)", "0 (150)", "−10 (250)"), show(260.0, StepWeightMode.REMAINING))
    }

    @Test
    fun `poured of target`() {
        assertEquals(listOf("30 (30)", "70 (150)", "150 (250)"), show(70.0, StepWeightMode.POURED))
        assertEquals(listOf("0 (30)", "30 (150)", "150 (250)"), show(0.0, StepWeightMode.POURED))
        assertEquals(listOf("30 (30)", "150 (150)", "260 (250)"), show(260.0, StepWeightMode.POURED))
    }

    @Test
    fun `negative weight counts as zero`() {
        assertEquals(PourDistribution.fill(targets15, 0.0), PourDistribution.fill(targets15, -3.2))
    }

    @Test
    fun `lowering the weight frees steps from the bottom`() {
        assertEquals(listOf(30.0, 150.0, 200.0), PourDistribution.fill(targets15, 200.0))
        assertEquals(listOf(30.0, 100.0, 150.0), PourDistribution.fill(targets15, 100.0))
    }

    @Test
    fun `steps without a target take no part`() {
        // Три шага с целью из девяти шагов рецепта.
        assertEquals(3, PourDistribution.values(targets15, 70.0, StepWeightMode.REMAINING).size)
    }

    // endregion

    // region formatting

    @Test
    fun `fractional dose rounds targets to whole grams`() {
        assertEquals(listOf("31", "154", "257"), hoffman.targetsG(15.4).map { formatStepWeight(it, WeightUnit.GRAM) })
    }

    @Test
    fun `rounded zero has no minus`() {
        assertEquals("0", formatStepWeight(-0.3, WeightUnit.GRAM))
        assertEquals("0.00", formatStepWeight(-0.001, WeightUnit.OUNCE))
        assertEquals("−1", formatStepWeight(-0.6, WeightUnit.GRAM))
    }

    @Test
    fun `board value keeps the scale precision`() {
        assertEquals("80.0", formatReadingWeight(80.0, WeightUnit.GRAM))
        assertEquals("0.5", formatReadingWeight(0.5, WeightUnit.GRAM))
        assertEquals("\u22128.0", formatReadingWeight(-8.0, WeightUnit.GRAM))
        assertEquals("0.0", formatReadingWeight(-0.04, WeightUnit.GRAM))
        assertEquals("8.82", formatReadingWeight(250.0, WeightUnit.OUNCE))
    }

    @Test
    fun `ounces with two decimals`() {
        assertEquals("8.82", formatStepWeight(250.0, WeightUnit.OUNCE))
        assertEquals(250.0, toGrams(fromGrams(250.0, WeightUnit.OUNCE), WeightUnit.OUNCE), 1e-9)
        assertEquals(28.349523, toGrams(1.0, WeightUnit.OUNCE), 1e-9)
        assertEquals(5.0, toGrams(5.0, WeightUnit.GRAM), 0.0)
    }

    // endregion

    // region pour detector

    @Test
    fun `pour starts at plus one gram`() {
        val detector = PourDetector(0.0)
        assertFalse(detector.isPour(0.4f.toDouble()))
        assertFalse(detector.isPour(0.7f.toDouble()))
        assertTrue(detector.isPour(1.3f.toDouble()))
    }

    @Test
    fun `pour threshold counts from the baseline`() {
        val detector = PourDetector(312.0)
        assertFalse(detector.isPour(312.9f.toDouble()))
        assertTrue(detector.isPour(313.0f.toDouble()))
        // Float: 1.3 − 0.3 чуть меньше 1, но это ровно +1 г.
        assertTrue(PourDetector(0.3f.toDouble()).isPour(1.3f.toDouble()))
    }

    // endregion
}
