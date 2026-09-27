package dev.openscales.recipe

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecipeFormatTest {

    private val recipe = Recipe(
        id = "user:6f1c",
        title = Text.Plain("Мой V60"),
        defaultDoseG = 15,
        description = Text.Plain("Помол средне-мелкий"),
        items = listOf(
            RecipeItem.Step(Text.Plain("Цветение"), 12, Text.Plain("Лейте медленно"), 30),
            RecipeItem.Hint(Text.Plain("Всё цветение — 45 секунд")),
            RecipeItem.Step(Text.Plain("Подождите"), 33, showTime = true),
            RecipeItem.Step(Text.Plain("Налейте"), 30, targetG = 250),
        ),
        category = RecipeCategory.AEROPRESS,
        difficulty = Difficulty.HARD,
    )

    @Test
    fun `french press category round trip`() {
        val press = recipe.copy(category = RecipeCategory.FRENCH_PRESS)
        assertTrue(RecipeFormat.encode(press).contains("\"category\": \"french_press\""))
        assertEquals(press, RecipeFormat.decode(RecipeFormat.encode(press)))
    }

    @Test
    fun `round trip`() {
        assertEquals(recipe, RecipeFormat.decode(RecipeFormat.encode(recipe)))
    }

    @Test
    fun `document is readable and has whole grams`() {
        val text = RecipeFormat.encode(recipe)
        assertTrue(text, text.contains("\"format\": 1"))
        assertTrue(text, text.contains("\"id\": \"6f1c\""))
        assertTrue(text, text.contains("\"category\": \"aeropress\""))
        assertTrue(text, text.contains("\"doseG\": 15,"))
        assertTrue(text, text.contains("\"targetG\": 250"))
        assertTrue(text, text.contains("\"type\": \"hint\""))
        assertTrue(text, !text.contains("15.0"))
        // Признак времени пишется только там, где включён.
        assertEquals(1, Regex("showTime").findAll(text).count())
        assertTrue(text, text.contains("\"showTime\": true"))
        assertTrue(text, text.contains("\"difficulty\": \"hard\""))
    }

    @Test
    fun `document from the design`() {
        val decoded = RecipeFormat.decode(
            """
            { "format": 1, "id": "abc", "title": "T", "category": "pour_over", "doseG": 15, "extra": true,
              "items": [ { "type": "step", "title": "S", "durationS": 12, "targetG": 30 }, { "type": "hint", "text": "H" } ] }
            """,
        )!!
        assertEquals("user:abc", decoded.id)
        // Документ до групп по оборудованию: «pour_over» — это V60.
        assertEquals(RecipeCategory.V60, decoded.category)
        assertNull(decoded.description)
        // Документ без сложности — «средне».
        assertEquals(Difficulty.MEDIUM, decoded.difficulty)
        // Документ без признака времени — признак выключен.
        assertTrue(decoded.items.filterIsInstance<RecipeItem.Step>().none { it.showTime })
        assertEquals(listOf(30.0), decoded.targetsG(15.0))
    }

    @Test
    fun `broken or newer documents are rejected quietly`() {
        assertNull(RecipeFormat.decode(""))
        assertNull(RecipeFormat.decode("{ not json"))
        assertNull(RecipeFormat.decode("""{ "format": 1, "id": "a" }"""))
        assertNull(RecipeFormat.decode(RecipeFormat.encode(recipe).replace("\"format\": 1", "\"format\": 2")))
        // Правильный JSON, но неверный рецепт: нулевая длительность, убывающие рубежи.
        assertNull(RecipeFormat.decode(RecipeFormat.encode(recipe).replace("\"durationS\": 33", "\"durationS\": 0")))
        assertNull(RecipeFormat.decode(RecipeFormat.encode(recipe).replace("\"targetG\": 250", "\"targetG\": 20")))
        assertNull(RecipeFormat.decode(RecipeFormat.encode(recipe).replace("\"doseG\": 15", "\"doseG\": 0")))
    }
}
