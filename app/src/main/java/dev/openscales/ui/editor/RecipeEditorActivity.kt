package dev.openscales.ui.editor

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.openscales.OpenScalesApp
import dev.openscales.R
import dev.openscales.recipe.Difficulty
import dev.openscales.recipe.DraftItem
import dev.openscales.recipe.Recipe
import dev.openscales.recipe.RecipeCategory
import dev.openscales.recipe.RecipeDraft
import dev.openscales.recipe.Text as RecipeText
import dev.openscales.recipe.toDraft
import dev.openscales.ui.OpenScalesActivity
import dev.openscales.ui.theme.AppTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Редактор своего рецепта: новый, «Изменить» или копия ([Mode]). Отдельная Activity, как остальные экраны.
 * Выход с несохранёнными правками («Отменить», стрелка, системный «назад») спрашивает подтверждение.
 */
class RecipeEditorActivity : OpenScalesActivity() {

    enum class Mode { NEW, EDIT, COPY }

    private val app get() = application as OpenScalesApp

    private val mode: Mode by lazy { Mode.valueOf(intent.getStringExtra(EXTRA_MODE) ?: Mode.NEW.name) }

    /** Черновик при открытии; `null` — рецепт для «Изменить»/«Копировать» не нашёлся. */
    private fun initialDraft(): RecipeDraft? {
        val resolve: (RecipeText) -> String = { text ->
            when (text) {
                is RecipeText.Res -> getString(text.id)
                is RecipeText.Plain -> text.value
            }
        }
        if (mode == Mode.NEW) return RecipeDraft(id = RecipeDraft.newId())
        val source = app.recipeById(intent.getStringExtra(EXTRA_RECIPE_ID)) ?: return null
        return when (mode) {
            Mode.EDIT -> source.toDraft(resolve).takeIf { source.id.startsWith(RecipeDraft.USER_ID_PREFIX) }
            else -> source.toDraft(resolve, id = RecipeDraft.newId()).let {
                it.copy(title = getString(R.string.recipe_copy_title, it.title))
            }
        }
    }

    private val viewModel: RecipeEditorViewModel by viewModels {
        viewModelFactory {
            initializer {
                RecipeEditorViewModel(
                    initial = checkNotNull(initialDraft()),
                    save = app.recipeStore::save,
                    scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
                )
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Рецепт удалён или id неверный — открывать нечего.
        if (savedInstanceState == null && mode != Mode.NEW && initialDraft() == null) return finish()
        setContent {
            AppTheme {
                val ui by viewModel.ui.collectAsStateWithLifecycle()
                val listState = rememberLazyListState()
                val snackbar = remember { SnackbarHostState() }
                var confirmDiscard by rememberSaveable { mutableStateOf(false) }

                LaunchedEffect(Unit) {
                    viewModel.events.collect { event ->
                        when (event) {
                            EditorEvent.Saved -> finish()
                            EditorEvent.SaveFailed -> snackbar.showSnackbar(getString(R.string.editor_save_failed))
                            is EditorEvent.ScrollTo ->
                                listState.animateScrollToItem(if (event.noSteps) 0 else editorListIndex(viewModel.ui.value.draft, event.key))
                        }
                    }
                }

                val cancel = { if (viewModel.ui.value.dirty) confirmDiscard = true else finish() }
                BackHandler(enabled = ui.dirty) { confirmDiscard = true }

                RecipeEditorScreen(
                    ui = ui,
                    isNew = mode != Mode.EDIT,
                    listState = listState,
                    snackbarHostState = snackbar,
                    actions = object : EditorActions {
                        override fun setTitle(value: String) = viewModel.setTitle(value)
                        override fun setDose(value: String) = viewModel.setDose(value)
                        override fun setCategory(value: RecipeCategory) = viewModel.setCategory(value)
                        override fun setDifficulty(value: Difficulty) = viewModel.setDifficulty(value)
                        override fun setDescription(value: String) = viewModel.setDescription(value)
                        override fun updateItem(key: Long, change: (DraftItem) -> DraftItem) = viewModel.updateItem(key, change)
                        override fun insert(index: Int, step: Boolean) = viewModel.insert(index, step)
                        override fun move(key: Long, delta: Int) = viewModel.move(key, delta)
                        override fun remove(key: Long) = viewModel.remove(key)
                        override fun toggle(key: Long) = viewModel.toggle(key)
                        override fun onSave() = viewModel.onSave()
                        override fun onCancel() = cancel()
                    },
                )

                if (confirmDiscard) {
                    AlertDialog(
                        onDismissRequest = { confirmDiscard = false },
                        title = { Text(stringResource(R.string.editor_discard_title)) },
                        confirmButton = {
                            TextButton(onClick = { confirmDiscard = false; finish() }) {
                                Text(stringResource(R.string.editor_discard))
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { confirmDiscard = false }) {
                                Text(stringResource(R.string.editor_keep_editing))
                            }
                        },
                    )
                }
            }
        }
    }

    companion object {
        private const val EXTRA_MODE = "mode"
        private const val EXTRA_RECIPE_ID = "recipe_id"

        fun newRecipe(context: Context): Intent =
            Intent(context, RecipeEditorActivity::class.java).putExtra(EXTRA_MODE, Mode.NEW.name)

        fun edit(context: Context, recipe: Recipe): Intent =
            Intent(context, RecipeEditorActivity::class.java).putExtra(EXTRA_MODE, Mode.EDIT.name).putExtra(EXTRA_RECIPE_ID, recipe.id)

        fun copy(context: Context, recipe: Recipe): Intent =
            Intent(context, RecipeEditorActivity::class.java).putExtra(EXTRA_MODE, Mode.COPY.name).putExtra(EXTRA_RECIPE_ID, recipe.id)
    }
}
