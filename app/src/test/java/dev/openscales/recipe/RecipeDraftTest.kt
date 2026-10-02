package dev.openscales.recipe

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class RecipeDraftTest {

    /** Встроенный рецепт в тестах без ресурсов: id строки вместо текста. */
    private val resolve: (Text) -> String = { text ->
        when (text) {
            is Text.Res -> "r${text.id}"
            is Text.Plain -> text.value
        }
    }

    private val hoffmannCopy = BuiltInRecipes.hoffmannV60.toDraft(resolve, id = "user:copy")

    /** Шаг с водой — пролив, без воды — действие. */
    private fun step(key: Long, duration: String = "030", target: String = "", title: String = "s$key") =
        DraftItem.Step(key, title = title, duration = duration, targetG = target, kind = if (target.isEmpty()) StepKind.ACTION else StepKind.POUR)

    private fun draft(vararg items: DraftItem) = RecipeDraft(id = "user:x", title = "x", items = items.toList())

    // region копия и сборка

    @Test
    fun `copy keeps the numbers as they are`() {
        assertEquals("15", hoffmannCopy.doseG)
        assertEquals(listOf("30", "150", "250"), hoffmannCopy.items.filterIsInstance<DraftItem.Step>().map { it.targetG }.filter { it.isNotEmpty() })
        assertEquals("012", (hoffmannCopy.items[0] as DraftItem.Step).duration)
        assertEquals("127", (hoffmannCopy.items[8] as DraftItem.Step).duration)
    }

    @Test
    fun `copy builds the same recipe`() {
        val recipe = hoffmannCopy.toRecipe()
        assertEquals("user:copy", recipe.id)
        assertEquals(BuiltInRecipes.hoffmannV60.targetsG(15.0), recipe.targetsG(15.0))
        assertEquals(210, RecipeTimeline(recipe).total)
        assertEquals(BuiltInRecipes.hoffmannV60.items.size, recipe.items.size)
        assertEquals(RecipeCategory.V60, recipe.category)
        assertEquals(Difficulty.MEDIUM, recipe.difficulty)
        assertEquals(Difficulty.EASY, BuiltInRecipes.hoffmannFrenchPress.toDraft(resolve).toRecipe().difficulty)
    }

    @Test
    fun `recipe to draft and back is lossless`() {
        val recipe = hoffmannCopy.toRecipe()
        assertEquals(recipe, recipe.toDraft(resolve).toRecipe())
        assertEquals(hoffmannCopy, recipe.toDraft(resolve))
    }

    @Test
    fun `copy keeps the time flag`() {
        val copy = BuiltInRecipes.hoffmannAeropress.toDraft(resolve, id = "user:aero")
        assertEquals(
            listOf(false, true, true, true, true),
            copy.items.filterIsInstance<DraftItem.Step>().map { it.kind == StepKind.ACTION },
        )
        assertEquals(BuiltInRecipes.hoffmannAeropress.items.map { (it as RecipeItem.Step).showTime },
            copy.toRecipe().items.map { (it as RecipeItem.Step).showTime })
    }

    @Test
    fun `texts are trimmed and blank optional ones dropped`() {
        val recipe = RecipeDraft(
            id = "user:x", title = "  Мой  ", description = "  ",
            items = listOf(DraftItem.Step(0, title = " Подождите ", duration = "010", note = " ", kind = StepKind.ACTION)),
        ).toRecipe()
        assertEquals(Text.Plain("Мой"), recipe.title)
        assertNull(recipe.description)
        val s = recipe.items.single() as RecipeItem.Step
        assertEquals(Text.Plain("Подождите"), s.title)
        assertNull(s.note)
        assertNull(s.targetG)
    }

    @Test
    fun `new draft defaults`() {
        val d = RecipeDraft(id = RecipeDraft.newId())
        assertTrue(d.id.startsWith("user:"))
        assertEquals("15", d.doseG)
        assertEquals(RecipeCategory.V60, d.category)
        assertTrue(d.items.isEmpty())
        assertEquals("030", DraftItem.Step(0).duration)
    }

    // endregion

    // region операции

    @Test
    fun `insert into empty and between items`() {
        val empty = RecipeDraft(id = "user:x")
        assertEquals(0L, empty.nextKey)
        val one = empty.insert(0, DraftItem.Hint(empty.nextKey))
        assertEquals(listOf(0L), one.items.map { it.key })
        val d = draft(step(0), step(1))
        val inserted = d.insert(1, step(d.nextKey))
        assertEquals(listOf(0L, 2L, 1L), inserted.items.map { it.key })
        assertEquals(listOf(0..30, 30..60, 60..90), inserted.times())
        assertEquals(listOf(0L, 1L, 3L), d.insert(2, step(3)).items.map { it.key })
    }

    @Test
    fun `move swaps with a neighbour and ignores the edges`() {
        val d = draft(step(0), DraftItem.Hint(1), step(2, duration = "010"))
        assertEquals(listOf(0L, 2L, 1L), d.move(2, -1).items.map { it.key })
        assertEquals(listOf(1L, 0L, 2L), d.move(0, +1).items.map { it.key })
        assertSame(d, d.move(0, -1))
        assertSame(d, d.move(2, +1))
        assertEquals(listOf(0..0, 0..10, 10..40), d.move(0, +1).move(0, +1).times())
    }

    @Test
    fun `remove and update`() {
        val d = draft(step(0), step(1))
        assertEquals(listOf(1L), d.remove(0).items.map { it.key })
        val changed = d.update(1) { (it as DraftItem.Step).copy(duration = "130") }
        assertEquals(listOf(0..30, 30..120), changed.times())
    }

    @Test
    fun `unparsable duration counts as zero in times`() {
        assertEquals(listOf(0..0, 0..30), draft(step(0, duration = "13"), step(1)).times())
    }

    // endregion

    // region заготовки, прибавки, копия, сводка

    @Test
    fun `templates fill the new item`() {
        val pour = ItemTemplate.POUR.newItem(5, "Налейте") as DraftItem.Step
        assertEquals(DraftItem.Step(5, title = "Налейте", duration = "030"), pour)
        assertEquals(StepKind.POUR, pour.kind)
        assertEquals(DraftItem.Step(6, title = "Подождите", kind = StepKind.ACTION), ItemTemplate.WAIT.newItem(6, "Подождите"))
        assertEquals(DraftItem.Step(7, title = "", kind = StepKind.ACTION), ItemTemplate.ACTION.newItem(7, "ignored"))
        assertEquals(DraftItem.Hint(8), ItemTemplate.HINT.newItem(8))
    }

    @Test
    fun `increments of the hoffmann copy`() {
        // Ключи — позиции элементов: цветение 0, пролив 4, пролив 5.
        assertEquals(mapOf(0L to 30, 4L to 120, 5L to 100), hoffmannCopy.increments())
    }

    @Test
    fun `increment is skipped below the target above and for unparsable ones`() {
        val d = draft(step(0, target = "150"), step(1, target = "100"), step(2, target = "1x"), step(3, target = "200"))
        assertEquals(mapOf(0L to 150, 3L to 50), d.increments())
    }

    @Test
    fun `duplicating a pour builds a series`() {
        val once = draft(step(0, duration = "045", target = "60", title = "Налейте")).duplicate(0, 1)
        val twice = once.duplicate(1, 2)
        assertEquals(listOf("60", "120", "180"), twice.items.map { (it as DraftItem.Step).targetG })
        assertEquals(listOf("Налейте", "Налейте", "Налейте"), twice.items.map { (it as DraftItem.Step).title })
        assertEquals(135, twice.summary().totalS)
    }

    @Test
    fun `duplicate in the middle leaves the rest and shows up in validation`() {
        val d = draft(step(0, target = "30"), step(1, target = "150"), step(2, target = "250")).duplicate(1, 3)
        assertEquals(listOf(0L, 1L, 3L, 2L), d.items.map { it.key })
        assertEquals(listOf("30", "150", "270", "250"), d.items.map { (it as DraftItem.Step).targetG })
        assertEquals(
            listOf(DraftError.Field(2, DraftField.TARGET, DraftProblem.BELOW_PREVIOUS, 270)),
            validate(d),
        )
    }

    @Test
    fun `duplicate without an increment copies the field`() {
        val wait = DraftItem.Step(0, title = "Подождите", duration = "015", kind = StepKind.ACTION)
        assertEquals(wait.copy(key = 1), draft(wait).duplicate(0, 1).items[1])
        val bad = draft(step(0, target = "150"), step(1, target = "100")).duplicate(1, 2)
        assertEquals("100", (bad.items[2] as DraftItem.Step).targetG)
        val hint = draft(DraftItem.Hint(0, "текст")).duplicate(0, 1)
        assertEquals(DraftItem.Hint(1, "текст"), hint.items[1])
    }

    @Test
    fun `summary of the hoffmann copy`() {
        assertEquals(DraftSummary(250, 250.0 / 15, 210), hoffmannCopy.summary())
        assertEquals(12.5, hoffmannCopy.copy(doseG = "20").summary().ratio!!, 1e-9)
    }

    private val vanBunnikCopy = BuiltInRecipes.vanBunnikAeropress.toDraft(resolve, id = "user:vb")

    @Test
    fun `pour needs water, action cannot hold it`() {
        val pour = DraftItem.Step(0, title = "Налейте", kind = StepKind.POUR)
        assertEquals(listOf(DraftError.Field(0, DraftField.TARGET, DraftProblem.EMPTY)), validate(draft(pour)))
        val action = DraftItem.Step(0, title = "Подождите", targetG = "50", kind = StepKind.ACTION)
        assertTrue(validate(draft(action)).isEmpty())
        assertNull((draft(action).toRecipe().items[0] as RecipeItem.Step).targetG)
        assertTrue((draft(action).toRecipe().items[0] as RecipeItem.Step).showTime)
    }

    @Test
    fun `kinds of the hoffmann copy`() {
        assertEquals(
            listOf(StepKind.POUR, StepKind.ACTION, StepKind.ACTION, StepKind.POUR, StepKind.POUR, StepKind.ACTION, StepKind.ACTION, StepKind.ACTION),
            hoffmannCopy.items.filterIsInstance<DraftItem.Step>().map { it.kind },
        )
        assertEquals(StepKind.TARE, (vanBunnikCopy.items[5] as DraftItem.Step).kind)
    }

    @Test
    fun `tare template`() {
        assertEquals(DraftItem.Step(3, title = "Тара", duration = "015", kind = StepKind.TARE), ItemTemplate.TARE.newItem(3, "Тара"))
    }

    @Test
    fun `van bunnik copy counts targets from the tare`() {
        // «Налейте» 100 — элемент 0, «Тара» — 5, «Разбавьте» 100 — 6.
        assertEquals(mapOf(0L to 100, 6L to 100), vanBunnikCopy.increments())
        val summary = vanBunnikCopy.summary()
        assertEquals(200, summary.waterG)
        assertEquals(200.0 / 30, summary.ratio!!, 1e-9)
        assertEquals(100, summary.totalS)
        assertTrue(validate(vanBunnikCopy).isEmpty())
        assertEquals(vanBunnikCopy.toRecipe(), vanBunnikCopy.toRecipe().toDraft(resolve).toRecipe())
        assertTrue((vanBunnikCopy.toRecipe().items[5] as RecipeItem.Step).tare)
    }

    @Test
    fun `target after a tare may be lower and duplicates from the tare zero`() {
        val tare = DraftItem.Step(1, title = "Тара", duration = "015", kind = StepKind.TARE)
        val d = draft(step(0, target = "100"), tare, step(2, target = "50"))
        assertTrue(validate(d).isEmpty())
        assertEquals(mapOf(0L to 100, 2L to 50), d.increments())
        assertEquals("100", (d.duplicate(2, 3).items[3] as DraftItem.Step).targetG)
        assertEquals(tare.copy(key = 4), d.duplicate(1, 4).items[2])
        assertEquals(150, d.summary().waterG)
        // Ниже тары рубеж по-прежнему не убывает внутри своей части.
        assertEquals(
            listOf(DraftError.Field(3, DraftField.TARGET, DraftProblem.BELOW_PREVIOUS, 50)),
            validate(d.insert(3, step(3, target = "40"))),
        )
    }

    @Test
    fun `tare step drops a stray target and time flag`() {
        val d = draft(DraftItem.Step(0, title = "Тара", targetG = "50", kind = StepKind.TARE))
        val step = d.toRecipe().items[0] as RecipeItem.Step
        assertEquals(null, step.targetG)
        assertEquals(false, step.showTime)
    }

    @Test
    fun `summary without water or dose`() {
        assertEquals(DraftSummary(null, null, 30), draft(step(0)).summary())
        assertNull(hoffmannCopy.copy(doseG = "").summary().ratio)
        assertNull(hoffmannCopy.copy(doseG = "0").summary().ratio)
        assertEquals(30, draft(step(0, duration = "1"), step(1)).summary().totalS)
    }

    // endregion

    // region разбор и проверка

    @Test
    fun `durations`() {
        // Маска «м:сс»: последние две цифры — секунды, перед ними минуты.
        assertEquals(90, parseDuration("130"))
        assertEquals(45, parseDuration("045"))
        assertEquals(0, parseDuration("000"))
        assertEquals(599, parseDuration("959"))
        assertEquals(600, parseDuration("1000"))
        assertNull(parseDuration("1"))
        assertNull(parseDuration("13"))
        assertNull(parseDuration("090"))
        assertNull(parseDuration("1:30"))
        assertNull(parseDuration("abc"))
        assertNull(parseDuration(""))
        assertEquals(listOf("012", "130", "000", "959"), listOf(12, 90, 0, 599).map(::durationDigits))
        assertTrue((0..5999).all { parseDuration(durationDigits(it)) == it })
    }

    @Test
    fun `grams are whole numbers only`() {
        assertEquals(150, parseGrams("150"))
        assertNull(parseGrams("150,5"))
        assertNull(parseGrams("150.5"))
        assertNull(parseGrams(""))
    }

    private fun fields(d: RecipeDraft) = validate(d).map { e ->
        when (e) {
            is DraftError.Field -> Triple(e.key, e.field, e.problem)
            DraftError.NoSteps -> null
        }
    }

    @Test
    fun `valid copy has no errors`() {
        assertEquals(emptyList<DraftError>(), validate(hoffmannCopy))
    }

    @Test
    fun `recipe card fields`() {
        val d = draft(step(0)).copy(title = "  ", doseG = "0")
        assertEquals(
            listOf(Triple(null, DraftField.TITLE, DraftProblem.EMPTY), Triple(null, DraftField.DOSE, DraftProblem.ZERO)),
            fields(d),
        )
        assertEquals(listOf(Triple(null, DraftField.DOSE, DraftProblem.EMPTY)), fields(draft(step(0)).copy(doseG = "")))
        assertEquals(listOf(Triple(null, DraftField.DOSE, DraftProblem.INVALID)), fields(draft(step(0)).copy(doseG = "15.5")))
    }

    @Test
    fun `hints only is not a recipe`() {
        assertEquals(listOf(DraftError.NoSteps), validate(draft(DraftItem.Hint(0, "подсказка"))))
        assertEquals(listOf(DraftError.NoSteps), validate(draft()))
    }

    @Test
    fun `step and hint fields`() {
        val d = draft(step(0, duration = "000", title = " "), step(1, duration = "075"), DraftItem.Hint(2, " "), step(3, target = "0"))
        assertEquals(
            listOf(
                Triple(0L, DraftField.STEP_TITLE, DraftProblem.EMPTY),
                Triple(0L, DraftField.DURATION, DraftProblem.ZERO),
                Triple(1L, DraftField.DURATION, DraftProblem.INVALID),
                Triple(2L, DraftField.HINT_TEXT, DraftProblem.EMPTY),
                Triple(3L, DraftField.TARGET, DraftProblem.ZERO),
            ),
            fields(d),
        )
    }

    @Test
    fun `target below the one above`() {
        val d = draft(step(0, target = "150"), step(1), step(2, target = "100"), step(3, target = "150"))
        assertEquals(listOf(DraftError.Field(2, DraftField.TARGET, DraftProblem.BELOW_PREVIOUS, 150)), validate(d))
    }

    @Test
    fun `target compares with the highest one above`() {
        val d = draft(step(0, target = "150"), step(1, target = "100"), step(2, target = "120"))
        assertEquals(
            listOf(
                DraftError.Field(1, DraftField.TARGET, DraftProblem.BELOW_PREVIOUS, 150),
                DraftError.Field(2, DraftField.TARGET, DraftProblem.BELOW_PREVIOUS, 150),
            ),
            validate(d),
        )
    }

    @Test(expected = IllegalStateException::class)
    fun `invalid draft does not build`() {
        draft().toRecipe()
    }

    // endregion
}
