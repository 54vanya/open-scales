package dev.openscales.data

import dev.openscales.recipe.Recipe
import dev.openscales.recipe.RecipeItem
import dev.openscales.recipe.Text
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class RecipeStoreTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val dir: File get() = File(folder.root, "recipes")

    private fun recipe(uuid: String, title: String = "Мой V60") = Recipe(
        id = "user:$uuid",
        title = Text.Plain(title),
        defaultDoseG = 15,
        description = null,
        items = listOf(RecipeItem.Step(Text.Plain("Налейте"), 30, targetG = 250)),
    )

    private fun TestScope.store(): RecipeStore {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        return RecipeStore(dir, CoroutineScope(backgroundScope.coroutineContext + dispatcher), io = dispatcher)
    }

    @Test
    fun `saved recipes survive a new store`() = runTest {
        val first = store()
        first.save(recipe("a"))
        first.save(recipe("b", "Второй"))
        first.save(recipe("a", "Переименован"))
        assertEquals(listOf("user:b", "user:a"), first.recipes.value.map { it.id })

        val second = store()
        assertEquals(setOf(recipe("a", "Переименован"), recipe("b", "Второй")), second.recipes.value.toSet())
        assertEquals(listOf("a.json", "b.json"), dir.list()!!.sorted())
    }

    @Test
    fun `delete removes the file`() = runTest {
        val first = store()
        first.save(recipe("a"))
        first.delete("user:a")
        assertTrue(first.recipes.value.isEmpty())
        assertTrue(store().recipes.value.isEmpty())
        assertEquals(emptyList<String>(), dir.list()!!.toList())
    }

    @Test
    fun `broken file does not block the others`() = runTest {
        store().save(recipe("a"))
        File(dir, "broken.json").writeText("{ nope")
        // Документ под чужим именем: удаление по id не нашло бы его.
        File(dir, "c.json").writeText(File(dir, "a.json").readText())
        assertEquals(listOf("user:a"), store().recipes.value.map { it.id })
    }

    @Test
    fun `get reads the file before the list is loaded`() = runTest {
        store().save(recipe("a"))
        val lazy = RecipeStore(dir, backgroundScope) // загрузка ещё не прошла: главного потока в тесте нет
        assertEquals(recipe("a"), lazy.get("user:a"))
        assertNull(lazy.get("user:missing"))
        assertNull(lazy.get("user:../a"))
        assertNull(lazy.get("builtin:hoffmann-v60"))
    }

    @Test
    fun `missing directory means no recipes`() = runTest {
        assertTrue(store().recipes.value.isEmpty())
    }
}
