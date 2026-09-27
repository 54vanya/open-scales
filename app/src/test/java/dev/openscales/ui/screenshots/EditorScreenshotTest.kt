package dev.openscales.ui.screenshots

import dev.openscales.recipe.BuiltInRecipes
import dev.openscales.recipe.Difficulty
import dev.openscales.recipe.DraftItem
import dev.openscales.recipe.RecipeCategory
import dev.openscales.recipe.RecipeDraft
import dev.openscales.recipe.Text
import dev.openscales.recipe.toDraft
import dev.openscales.ui.editor.EditorActions
import dev.openscales.ui.editor.EditorUi
import dev.openscales.ui.editor.RecipeEditorScreen
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
        override fun insert(index: Int, step: Boolean) = Unit
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

    @Test
    @Config(qualifiers = NARROW)
    fun editorNarrow() = snapshot("editor_narrow") {
        RecipeEditorScreen(ui = EditorUi(RecipeDraft(id = "user:new")), isNew = true, actions = NoActions)
    }
}
