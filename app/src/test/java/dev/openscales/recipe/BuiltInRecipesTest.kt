package dev.openscales.recipe

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Встроенные рецепты по таблице спеки: итог воды при дозе по умолчанию и длительность. */
class BuiltInRecipesTest {

    private data class Expected(
        val recipe: Recipe,
        val doseG: Double,
        val waterG: Double,
        val totalS: Int,
        val category: RecipeCategory,
        val difficulty: Difficulty,
    )

    private val v60 = RecipeCategory.V60
    private val flat = RecipeCategory.FLAT_BOTTOM
    private val switch = RecipeCategory.SWITCH
    private val aeropress = RecipeCategory.AEROPRESS

    private val expected = listOf(
        Expected(BuiltInRecipes.hoffmannV60, 15.0, 250.0, 210, v60, Difficulty.MEDIUM),
        Expected(BuiltInRecipes.hoffmannOneCup, 15.0, 250.0, 180, v60, Difficulty.HARD),
        Expected(BuiltInRecipes.kasuya46, 20.0, 300.0, 210, v60, Difficulty.MEDIUM),
        Expected(BuiltInRecipes.raoV60, 22.0, 360.0, 210, v60, Difficulty.MEDIUM),
        Expected(BuiltInRecipes.hedrickV60, 15.0, 225.0, 150, v60, Difficulty.EASY),
        Expected(BuiltInRecipes.kalitaWave, 21.0, 345.0, 180, flat, Difficulty.EASY),
        Expected(BuiltInRecipes.woelflPourOver, 17.0, 270.0, 145, flat, Difficulty.MEDIUM),
        Expected(BuiltInRecipes.hsuPourOver, 14.0, 200.0, 150, flat, Difficulty.HARD),
        Expected(BuiltInRecipes.kasuyaSwitch, 20.0, 280.0, 180, switch, Difficulty.HARD),
        Expected(BuiltInRecipes.fukahoriSwitch, 14.0, 200.0, 140, switch, Difficulty.EASY),
        Expected(BuiltInRecipes.chemex, 42.0, 700.0, 240, RecipeCategory.CHEMEX, Difficulty.EASY),
        Expected(BuiltInRecipes.hoffmannAeropress, 11.0, 200.0, 180, aeropress, Difficulty.EASY),
        Expected(BuiltInRecipes.invertedAeropress, 15.0, 200.0, 160, aeropress, Difficulty.MEDIUM),
        Expected(BuiltInRecipes.littleAeropress, 18.0, 94.0, 130, aeropress, Difficulty.MEDIUM),
        Expected(BuiltInRecipes.vanBunnikAeropress, 30.0, 200.0, 100, aeropress, Difficulty.MEDIUM),
        Expected(BuiltInRecipes.hoffmannFrenchPress, 30.0, 500.0, 585, RecipeCategory.FRENCH_PRESS, Difficulty.EASY),
    )

    @Test
    fun `every built-in recipe matches the table`() {
        assertEquals(BuiltInRecipes.all, expected.map { it.recipe })
        for (e in expected) {
            val r = e.recipe
            assertEquals(r.id, e.doseG, r.defaultDoseG.toDouble(), 0.0)
            assertEquals(r.id, e.waterG, r.totalWaterG(r.defaultDoseG.toDouble()), 1e-9)
            assertEquals(r.id, e.totalS, RecipeTimeline(r).total)
            assertEquals(r.id, e.category, r.category)
            assertEquals(r.id, e.difficulty, r.difficulty)
            assertTrue(r.id, r.defaultTargetsG.zipWithNext().all { (a, b) -> a <= b })
        }
        assertEquals(BuiltInRecipes.all.size, BuiltInRecipes.all.map { it.id }.toSet().size)
    }

    @Test
    fun `targets at the default dose are the source milestones`() {
        fun targets(r: Recipe) = r.targetsG(r.defaultDoseG.toDouble()).map { Math.round(it).toInt() }
        assertEquals(listOf(60, 120, 180, 240, 300), targets(BuiltInRecipes.kasuya46))
        assertEquals(listOf(66, 360), targets(BuiltInRecipes.raoV60))
        assertEquals(listOf(45, 90, 225), targets(BuiltInRecipes.hedrickV60))
        assertEquals(listOf(60, 200, 345), targets(BuiltInRecipes.kalitaWave))
        assertEquals(listOf(150, 450, 700), targets(BuiltInRecipes.chemex))
        assertEquals(listOf(200), targets(BuiltInRecipes.hoffmannAeropress))
        assertEquals(listOf(30, 200), targets(BuiltInRecipes.invertedAeropress))
        assertEquals(listOf(50, 100, 150, 200, 250), targets(BuiltInRecipes.hoffmannOneCup))
        assertEquals(listOf(60, 120, 280), targets(BuiltInRecipes.kasuyaSwitch))
        assertEquals(listOf(500), targets(BuiltInRecipes.hoffmannFrenchPress))
        assertEquals(listOf(94), targets(BuiltInRecipes.littleAeropress))
        assertEquals(listOf(100, 100), targets(BuiltInRecipes.vanBunnikAeropress))
        assertEquals(listOf(60, 120, 170, 270), targets(BuiltInRecipes.woelflPourOver))
        assertEquals(listOf(50, 100, 150, 200), targets(BuiltInRecipes.hsuPourOver))
        assertEquals(listOf(50, 200), targets(BuiltInRecipes.fukahoriSwitch))
    }

    @Test
    fun `every step without water shows the time, steps with water never`() {
        fun timed(r: Recipe) = r.items.filterIsInstance<RecipeItem.Step>().map { it.showTime }
        assertEquals(listOf(false, true, true, true, true), timed(BuiltInRecipes.hoffmannAeropress))
        assertEquals(listOf(false, true, true, false, true, true, true), timed(BuiltInRecipes.invertedAeropress))
        assertEquals(listOf(false, true, true, false, false, true, true, true), timed(BuiltInRecipes.hoffmannV60))
        assertEquals(listOf(false, true, false, true, false, true, false, true, false, true), timed(BuiltInRecipes.kasuya46))
        for (r in BuiltInRecipes.all) {
            for (step in r.items.filterIsInstance<RecipeItem.Step>()) {
                // У шага «Тара» крупного времени нет: на табло вместо него кнопка тары.
                assertEquals("${r.id}: ${step.title}", step.targetG == null && !step.tare, step.showTime)
            }
        }
    }

    @Test
    fun `a pour lasts only as long as the pouring, the wait is its own step`() {
        val recipes = listOf(
            BuiltInRecipes.kasuya46, BuiltInRecipes.raoV60, BuiltInRecipes.hedrickV60, BuiltInRecipes.hsuPourOver,
            BuiltInRecipes.woelflPourOver, BuiltInRecipes.kasuyaSwitch,
        )
        for (r in recipes) {
            for (step in r.items.filterIsInstance<RecipeItem.Step>().filter { it.targetG != null }) {
                assertTrue("${r.id}: ${step.title} ${step.durationS} s", step.durationS <= 30)
            }
        }
    }

    @Test
    fun `woelfl pours at the times from his video`() {
        val r = BuiltInRecipes.woelflPourOver
        val timeline = RecipeTimeline(r)
        val pourStarts = r.items.indices.filter { (r.items[it] as? RecipeItem.Step)?.targetG != null }.map { timeline.startOf(it) }
        assertEquals(listOf(0, 40, 80, 120), pourStarts)
    }

    @Test
    fun `kasuya waits between pours`() {
        val timeline = RecipeTimeline(BuiltInRecipes.kasuya46)
        // 0:20 — «Подождите» 0:10–0:45: табло показывает время до следующего пролива.
        val waiting = timeline.stepAt(20)!!
        assertEquals(1, waiting)
        assertTrue((BuiltInRecipes.kasuya46.items[waiting] as RecipeItem.Step).showTime)
        assertEquals(25, timeline.remainingIn(waiting, 20))
    }

    @Test
    fun `van bunnik dilutes after a tare`() {
        val r = BuiltInRecipes.vanBunnikAeropress
        assertEquals(listOf(0, 0, 0, 0, 0, 1, 1), r.partOf)
        assertEquals(listOf(listOf(0 to 50.0), listOf(6 to 50.0)), r.targetsByPart(15.0))
        assertEquals(100.0, r.totalWaterG(15.0), 1e-9)
        val tare = r.items[5] as RecipeItem.Step
        assertTrue(tare.tare)
        assertEquals(75, RecipeTimeline(r).startOf(5))
    }

    @Test
    fun `kasuya at 18 grams`() {
        assertEquals(listOf(54.0, 108.0, 162.0, 216.0, 270.0), BuiltInRecipes.kasuya46.targetsG(18.0).map { Math.round(it * 1e6) / 1e6 })
    }
}
