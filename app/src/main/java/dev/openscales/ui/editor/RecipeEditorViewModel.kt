package dev.openscales.ui.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.openscales.recipe.Difficulty
import dev.openscales.recipe.DraftError
import dev.openscales.recipe.DraftItem
import dev.openscales.recipe.ItemTemplate
import dev.openscales.recipe.Recipe
import dev.openscales.recipe.RecipeCategory
import dev.openscales.recipe.RecipeDraft
import dev.openscales.recipe.validate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException

/** Всё, что рисует редактор. */
data class EditorUi(
    val draft: RecipeDraft,
    /** Ключи раскрытых элементов: раскрыто может быть сколько угодно. */
    val expanded: Set<Long> = emptySet(),
    /** Ошибки показываются после первого «Сохранить» и дальше пересчитываются на каждое изменение. */
    val showErrors: Boolean = false,
    /** Есть несохранённые правки: выход спрашивает подтверждение. */
    val dirty: Boolean = false,
    /** Шаг, в чьё поле рубежа поставить фокус (новый «Пролив»); сбрасывается, как только фокус поставлен. */
    val focusTarget: Long? = null,
) {
    val errors: List<DraftError> = if (showErrors) validate(draft) else emptyList()
}

sealed interface EditorEvent {
    /** Рецепт сохранён — экран закрывается. */
    data object Saved : EditorEvent

    /** Прокрутить к элементу [key] (`null` — к карточке рецепта) с первой ошибкой. */
    data class ScrollTo(val key: Long?, val noSteps: Boolean = false) : EditorEvent

    data object SaveFailed : EditorEvent
}

/**
 * Редактор своего рецепта. Черновик и раскрытые карточки живут здесь и переживают поворот. [save] пишет рецепт
 * в хранилище; выход без сохранения решает экран.
 */
class RecipeEditorViewModel(
    initial: RecipeDraft,
    private val save: suspend (Recipe) -> Unit,
    scope: CoroutineScope,
) : ViewModel(scope) {

    private val original = initial

    private val _ui = MutableStateFlow(EditorUi(initial))
    val ui: StateFlow<EditorUi> = _ui.asStateFlow()

    private val _events = Channel<EditorEvent>(Channel.BUFFERED)
    val events: Flow<EditorEvent> = _events.receiveAsFlow()

    private var saving = false

    private fun edit(change: (RecipeDraft) -> RecipeDraft) =
        _ui.update { ui -> change(ui.draft).let { ui.copy(draft = it, dirty = it != original) } }

    fun setTitle(value: String) = edit { it.copy(title = value) }
    fun setDose(value: String) = edit { it.copy(doseG = value) }
    fun setCategory(value: RecipeCategory) = edit { it.copy(category = value) }
    fun setDifficulty(value: Difficulty) = edit { it.copy(difficulty = value) }
    fun setDescription(value: String) = edit { it.copy(description = value) }

    fun updateItem(key: Long, change: (DraftItem) -> DraftItem) = edit { it.update(key, change) }

    /**
     * Вставить элемент из заготовки [template] на позицию [index]; [title] — название шага на языке интерфейса.
     * Новый элемент раскрыт, у «Пролива» фокус переходит в поле рубежа.
     */
    fun insert(index: Int, template: ItemTemplate, title: String) {
        val key = _ui.value.draft.nextKey
        edit { it.insert(index, template.newItem(key, title)) }
        _ui.update {
            it.copy(expanded = it.expanded + key, focusTarget = if (template == ItemTemplate.POUR) key else null)
        }
    }

    /** Копия элемента сразу после него, раскрытая. */
    fun duplicate(key: Long) {
        val copyKey = _ui.value.draft.nextKey
        edit { it.duplicate(key, copyKey) }
        _ui.update { it.copy(expanded = it.expanded + copyKey) }
    }

    fun focusConsumed() = _ui.update { it.copy(focusTarget = null) }

    fun move(key: Long, delta: Int) = edit { it.move(key, delta) }

    fun remove(key: Long) {
        edit { it.remove(key) }
        _ui.update { it.copy(expanded = it.expanded - key) }
    }

    fun toggle(key: Long) = _ui.update { ui ->
        ui.copy(expanded = if (key in ui.expanded) ui.expanded - key else ui.expanded + key)
    }

    /**
     * С ошибками — показать их: раскрыть карточки с ошибками и прокрутить к первой. Без ошибок — сохранить
     * и закрыть экран.
     */
    fun onSave() {
        if (saving) return
        val draft = _ui.value.draft
        val errors = validate(draft)
        if (errors.isNotEmpty()) {
            val keys = errors.mapNotNull { (it as? DraftError.Field)?.key }
            _ui.update { it.copy(showErrors = true, expanded = it.expanded + keys) }
            val first = errors.first()
            _events.trySend(
                when (first) {
                    is DraftError.Field -> EditorEvent.ScrollTo(first.key)
                    DraftError.NoSteps -> EditorEvent.ScrollTo(null, noSteps = true)
                },
            )
            return
        }
        saving = true
        viewModelScope.launch {
            try {
                save(draft.toRecipe())
                _events.send(EditorEvent.Saved)
            } catch (e: IOException) {
                saving = false
                _events.send(EditorEvent.SaveFailed)
            }
        }
    }
}
