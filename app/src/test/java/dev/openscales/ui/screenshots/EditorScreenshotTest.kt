package dev.openscales.ui.screenshots

import dev.openscales.recipe.BuiltInRecipes
import dev.openscales.recipe.Difficulty
import dev.openscales.recipe.DraftItem
import dev.openscales.recipe.ItemTemplate
import dev.openscales.recipe.RecipeCategory
import dev.openscales.recipe.RecipeDraft
import dev.openscales.recipe.Text
import dev.openscales.recipe.toDraft
import dev.openscales.ui.editor.EditorActions
import dev.openscales.ui.editor.EditorUi
import dev.openscales.ui.editor.RecipeEditorScreen
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import org.junit.Test
import org.robolectric.annotation.Config

/** Редактор рецепта: карточка рецепта, свёрнутые и раскрытый шаг, ошибки, узкий экран. */
class EditorScreenshotTest : ScreenshotTest() {

    private object NoActions : EditorActions {
        override fun setTitle(value: String) = Unit
        override fun setDose(value: String) = Unit
        override fun setCategory(value: RecipeCategory) = Unit
        override fun setDifficulty(value: Difficulty) = Unit
        override fun setDescription(value: String) = Unit
        override fun updateItem(key: Long, change: (DraftItem) -> DraftItem) = Unit
        override fun insert(index: Int, template: ItemTemplate, title: String) = Unit
        override fun duplicate(key: Long) = Unit
        override fun focusConsumed() = Unit
        override fun move(key: Long, delta: Int) = Unit
        override fun remove(key: Long) = Unit
        override fun toggle(key: Long) = Unit
        override fun onSave() = Unit
        override fun onCancel() = Unit
    }

    /** Тексты встроенного рецепта без ресурсов Android: берём из строк приложения через контекст теста. */
    private fun hoffmannCopy(): RecipeDraft {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        return BuiltInRecipes.hoffmannV60.toDraft({ text ->
            when (text) {
                is Text.Res -> context.getString(text.id)
                is Text.Plain -> text.value
            }
        }, id = "user:copy").copy(title = "V60 по Хоффману (копия)")
    }

    @Test
    fun editorWithExpandedStep() = snapshot("editor_expanded_step") {
        RecipeEditorScreen(ui = EditorUi(hoffmannCopy(), expanded = setOf(0L)), isNew = false, actions = NoActions)
    }

    @Test
    fun editorErrors() = snapshot("editor_errors") {
        RecipeEditorScreen(
            ui = EditorUi(RecipeDraft(id = "user:new", title = ""), showErrors = true),
            isNew = true,
            actions = NoActions,
        )
    }

    /** Узкий экран: карточка рецепта со сводкой и раскрытый шаг — четыре кнопки в заголовке на 320 dp. */
    @Test
    @Config(qualifiers = NARROW)
    fun editorNarrow() = snapshot("editor_narrow") {
        val draft = RecipeDraft(id = "user:new", items = listOf(DraftItem.Step(0, title = "Налейте", targetG = "60")))
        RecipeEditorScreen(ui = EditorUi(draft, expanded = setOf(0L)), isNew = true, actions = NoActions)
    }

    /** Копия ван Бюнника: шаг «Тара» свёрнутым и раскрытым, прибавка «Разбавьте» от тары, сводка по частям. */
    @Test
    fun editorTareStep() = snapshot("editor_tare_step") {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val draft = BuiltInRecipes.vanBunnikAeropress.toDraft({ text ->
            when (text) {
                is Text.Res -> context.getString(text.id)
                is Text.Plain -> text.value
            }
        }, id = "user:vb")
        val listState = androidx.compose.foundation.lazy.rememberLazyListState(initialFirstVisibleItemIndex = 10)
        RecipeEditorScreen(ui = EditorUi(draft, expanded = setOf(5L)), isNew = false, actions = NoActions, listState = listState)
    }

    /** Меню «+» с заготовками. */
    @Test
    fun editorAddMenu() = screen(
        "editor_add_menu",
        act = { onAllNodesWithContentDescription("Добавить шаг").onFirst().performClick() },
    ) {
        RecipeEditorScreen(ui = EditorUi(hoffmannCopy()), isNew = false, actions = NoActions)
    }
}
