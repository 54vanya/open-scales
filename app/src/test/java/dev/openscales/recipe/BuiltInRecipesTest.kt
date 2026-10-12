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
        Expected(BuiltInRecipes.littleAeropress, 18.0, 186.0, 155, aeropress, Difficulty.MEDIUM),
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
            // Рубежи не убывают внутри части: после шага «Тара» отсчёт начинается заново.
            val parts = r.targetsByPart(r.defaultDoseG.toDouble())
            assertTrue(r.id, parts.all { part -> part.zipWithNext().all { (a, b) -> a.second <= b.second } })
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
        assertEquals(listOf(94, 92), targets(BuiltInRecipes.littleAeropress))
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
    fun `little dilutes after a tare`() {
        val r = BuiltInRecipes.littleAeropress
        assertEquals(listOf(0, 0, 0, 0, 0, 0, 1, 1), r.partOf)
        val byPart = r.targetsByPart(20.0).map { part -> part.map { (i, g) -> i to Math.round(g).toInt() } }
        assertEquals(listOf(listOf(0 to 104), listOf(7 to 102)), byPart)
        assertTrue((r.items[6] as RecipeItem.Step).tare)
        assertEquals(130, RecipeTimeline(r).startOf(6))
        assertTrue(r.items.none { it is RecipeItem.Hint })
    }

    /**
     * Вес в тексте не пересчитывается на дозу и при другой дозе спорит с рубежом шага: сколько лить, показывает
     * только рубеж. Тексты встроенных рецептов — строковые ресурсы, поэтому проверяются сами файлы строк.
     */
    @Test
    fun `recipe strings carry no gram amounts`() {
        val grams = Regex("""\d\s?(г|g)(?!\p{L})""")
        val string = Regex("""<string name="(recipe_[a-z0-9_]+)">(.*?)</string>""")
        for (dir in listOf("values", "values-ru")) {
            val file = java.io.File("src/main/res/$dir/strings.xml")
            assertTrue("$file not found", file.isFile)
            val offenders = string.findAll(file.readText())
                .filter { grams.containsMatchIn(it.groupValues[2]) }
                .map { it.groupValues[1] }
                .toList()
            assertEquals(dir, emptyList<String>(), offenders)
        }
    }

    /** Авторское название идёт первым и не переводится, после « · » — инициал и фамилия автора; точка и автор переносятся только вместе. */
    @Test
    fun `author names come first`() {
        val nbsp = "\\u00A0"
        val expected = mapOf(
            "recipe_hoffmann_title" to Triple("The Ultimate V60 Technique", "J.${nbsp}Hoffmann", "Дж.${nbsp}Хоффман"),
            "recipe_hoffmann_one_cup_title" to Triple("A Better 1 Cup V60 Technique", "J.${nbsp}Hoffmann", "Дж.${nbsp}Хоффман"),
            "recipe_kasuya_title" to Triple("4:6 Method", "T.${nbsp}Kasuya", "Т.${nbsp}Касуя"),
            "recipe_hedrick_title" to Triple("One and Done", "L.${nbsp}Hedrick", "Л.${nbsp}Хедрик"),
            "recipe_kasuya_switch_title" to Triple("Devil Recipe", "T.${nbsp}Kasuya", "Т.${nbsp}Касуя"),
            "recipe_hoffmann_aeropress_title" to Triple("The Ultimate AeroPress Technique", "J.${nbsp}Hoffmann", "Дж.${nbsp}Хоффман"),
            "recipe_hoffmann_french_press_title" to
                Triple("The Ultimate French Press Technique", "J.${nbsp}Hoffmann", "Дж.${nbsp}Хоффман"),
        )
        val string = Regex("""<string name="(recipe_[a-z0-9_]+)">(.*?)</string>""")
        for (dir in listOf("values", "values-ru")) {
            val file = java.io.File("src/main/res/$dir/strings.xml")
            assertTrue("$file not found", file.isFile)
            val strings = string.findAll(file.readText()).associate { it.groupValues[1] to it.groupValues[2] }
            for ((name, title) in expected) {
                val author = if (dir == "values") title.second else title.third
                assertEquals("$dir/$name", "${title.first} ·$nbsp$author", strings[name])
            }
        }
    }

    @Test
    fun `kasuya at 18 grams`() {
        assertEquals(listOf(54.0, 108.0, 162.0, 216.0, 270.0), BuiltInRecipes.kasuya46.targetsG(18.0).map { Math.round(it * 1e6) / 1e6 })
    }

    @Test
    fun `only the hoffmann bloom is a range, two to three doses`() {
        val ranged = BuiltInRecipes.all.flatMap { r -> r.items.filterIsInstance<RecipeItem.Step>().filter { it.targetMaxG != null }.map { r.id to it } }
        assertEquals(listOf("builtin:hoffmann-v60"), ranged.map { it.first })
        val bloom = ranged.single().second
        assertEquals(30, bloom.targetG)
        assertEquals(45, bloom.targetMaxG)
    }
}
