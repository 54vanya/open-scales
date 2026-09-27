package dev.openscales.ui.editor

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.openscales.R
import dev.openscales.protocol.WeightUnit
import dev.openscales.recipe.BuiltInRecipes
import dev.openscales.recipe.Difficulty
import dev.openscales.recipe.DraftError
import dev.openscales.recipe.DraftField
import dev.openscales.recipe.DraftItem
import dev.openscales.recipe.DraftProblem
import dev.openscales.recipe.RecipeCategory
import dev.openscales.recipe.RecipeDraft
import dev.openscales.recipe.toDraft
import dev.openscales.ui.components.ConnectedChoice
import dev.openscales.ui.components.formatTime
import dev.openscales.ui.components.symbolRes
import dev.openscales.ui.recipes.HintRow
import dev.openscales.ui.recipes.StepCard
import dev.openscales.ui.recipes.labelRes
import dev.openscales.ui.recipes.titleRes
import dev.openscales.ui.theme.OpenScalesTheme

/** Действия редактора — всё, что экран отдаёт наверх. */
interface EditorActions {
    fun setTitle(value: String)
    fun setDose(value: String)
    fun setCategory(value: RecipeCategory)
    fun setDifficulty(value: Difficulty)
    fun setDescription(value: String)
    fun updateItem(key: Long, change: (DraftItem) -> DraftItem)
    fun insert(index: Int, step: Boolean)
    fun move(key: Long, delta: Int)
    fun remove(key: Long)
    fun toggle(key: Long)
    fun onSave()
    fun onCancel()
}

/** Позиция элемента [key] в списке редактора: карточка рецепта, «+», затем по паре «элемент, +». */
fun editorListIndex(draft: RecipeDraft, key: Long?): Int =
    if (key == null) 0 else 2 + 2 * draft.indexOf(key).coerceAtLeast(0)

/**
 * Редактор рецепта: карточка рецепта (всегда раскрыта), под ней элементы — свёрнутые как на экране варки,
 * раскрытые с полями. Между элементами «+» (шаг или подпись). Внизу закреплены «Отменить» и «Сохранить».
 */
@Composable
fun RecipeEditorScreen(
    ui: EditorUi,
    isNew: Boolean,
    actions: EditorActions,
    listState: LazyListState = rememberLazyListState(),
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    val draft = ui.draft
    val times = draft.times()
    val gram = stringResource(WeightUnit.GRAM.symbolRes())

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (isNew) R.string.editor_title_new else R.string.editor_title_edit)) },
                navigationIcon = {
                    IconButton(onClick = actions::onCancel) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back))
                    }
                },
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedButton(onClick = actions::onCancel, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.editor_cancel))
                    }
                    Button(onClick = actions::onSave, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.editor_save))
                    }
                }
            }
        },
    ) { padding ->
        LazyColumn(
            state = listState,
            // Список кончается над кнопками, а при открытой клавиатуре — над ней (кнопки уходят под клавиатуру).
            // Сжимается только список, а не весь экран: так при скрытии клавиатуры не видно подложки окна,
            // и поле в фокусе список сам подтягивает в видимую часть.
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = padding.calculateBottomPadding())
                .consumeWindowInsets(PaddingValues(bottom = padding.calculateBottomPadding()))
                .imePadding(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 8.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item(key = "recipe") { RecipeCard(draft, ui.errors, gram, actions) }
            item(key = "add:first") {
                Column(Modifier.animateItem()) {
                    AddButton(onAdd = { step -> actions.insert(0, step) })
                    if (ui.errors.any { it == DraftError.NoSteps }) {
                        Text(
                            stringResource(R.string.editor_error_no_steps),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                    }
                }
            }
            draft.items.forEachIndexed { index, item ->
                item(key = item.key) {
                    val expanded = item.key in ui.expanded
                    val errors = ui.errors.filterIsInstance<DraftError.Field>().filter { it.key == item.key }
                    // Свёрнутая и раскрытая карточка перетекают друг в друга: старый вид гаснет одновременно
                    // с проявлением нового (без паузы «через пустоту»), высота меняется плавно.
                    AnimatedContent(
                        targetState = expanded,
                        modifier = Modifier.animateItem(),
                        transitionSpec = {
                            (fadeIn(tween(EXPAND_MS)) togetherWith fadeOut(tween(EXPAND_MS)))
                                .using(SizeTransform(clip = true) { _, _ -> tween(EXPAND_MS) })
                        },
                        contentAlignment = Alignment.TopStart,
                        label = "expand",
                    ) { open ->
                        when {
                            open -> ExpandedItem(
                                item = item,
                                errors = errors,
                                gram = gram,
                                canMoveUp = index > 0,
                                canMoveDown = index < draft.items.lastIndex,
                                actions = actions,
                            )
                            item is DraftItem.Step -> StepCard(
                                title = item.title.ifBlank { stringResource(R.string.editor_item_step) },
                                note = item.note.ifBlank { null },
                                range = formatTime(times[index].first) + "–" + formatTime(times[index].last),
                                remaining = null,
                                water = null,
                                unit = WeightUnit.GRAM,
                                target = item.targetG.ifBlank { null }?.let { stringResource(R.string.value_with_unit, it, gram) },
                                onClick = { actions.toggle(item.key) },
                                showsTime = item.showTime,
                            )
                            item is DraftItem.Hint -> HintRow(
                                item.text.ifBlank { stringResource(R.string.editor_item_hint) },
                                Modifier.clickable { actions.toggle(item.key) }.padding(vertical = 8.dp),
                            )
                        }
                    }
                }
                item(key = "add:${item.key}") {
                    Box(Modifier.animateItem()) { AddButton(onAdd = { step -> actions.insert(index + 1, step) }) }
                }
            }
        }
    }
}

/** Карточка рецепта: название, доза, способ, описание. Всегда раскрыта. */
@Composable
private fun RecipeCard(draft: RecipeDraft, errors: List<DraftError>, gram: String, actions: EditorActions) {
    val mine = errors.filterIsInstance<DraftError.Field>().filter { it.key == null }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            EditorField(
                value = draft.title,
                onValueChange = actions::setTitle,
                label = stringResource(R.string.editor_field_title),
                error = mine.firstOrNull { it.field == DraftField.TITLE },
            )
            EditorField(
                value = draft.doseG,
                onValueChange = { actions.setDose(it.filter(Char::isDigit)) },
                label = stringResource(R.string.editor_field_dose),
                error = mine.firstOrNull { it.field == DraftField.DOSE },
                suffix = gram,
                keyboardType = KeyboardType.Number,
            )
            CategoryField(draft.category, actions::setCategory)
            ConnectedChoice(
                options = Difficulty.entries.map { it to stringResource(it.labelRes()) },
                selected = draft.difficulty,
                onSelect = actions::setDifficulty,
                // На узком экране подписи уменьшаются, а не обрезаются («Френч-пресс»).
                compact = true,
            )
            EditorField(
                value = draft.description,
                onValueChange = actions::setDescription,
                label = stringResource(R.string.editor_field_description),
                error = null,
                singleLine = false,
            )
        }
    }
}

/** Оборудование рецепта: вариантов больше, чем помещается в ряд кнопок, — выпадающий список. */
@Composable
private fun CategoryField(selected: RecipeCategory, onSelect: (RecipeCategory) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = stringResource(selected.titleRes()),
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text(stringResource(R.string.editor_field_category)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.exposedDropdownSize(),
        ) {
            RecipeCategory.entries.forEach { category ->
                DropdownMenuItem(
                    text = { Text(stringResource(category.titleRes())) },
                    onClick = {
                        expanded = false
                        onSelect(category)
                    },
                )
            }
        }
    }
}

/** Раскрытый элемент: заголовок (касание сворачивает) с «Выше», «Ниже», «Удалить», под ним поля. */
@Composable
private fun ExpandedItem(
    item: DraftItem,
    errors: List<DraftError.Field>,
    gram: String,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    actions: EditorActions,
) {
    fun error(field: DraftField) = errors.firstOrNull { it.field == field }
    // Поле в фокусе уходит вместе с карточкой — снимаем фокус, иначе на экране остаётся ручка курсора.
    val focus = LocalFocusManager.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth()
                .clickable { focus.clearFocus(); actions.toggle(item.key) }
                .padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                if (item is DraftItem.Step) Icons.Rounded.Timer else Icons.Rounded.Info,
                contentDescription = null,
                modifier = Modifier.padding(end = 12.dp),
            )
            Text(
                stringResource(if (item is DraftItem.Step) R.string.editor_item_step else R.string.editor_item_hint),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { actions.move(item.key, -1) }, enabled = canMoveUp) {
                Icon(Icons.Rounded.KeyboardArrowUp, stringResource(R.string.editor_move_up))
            }
            IconButton(onClick = { actions.move(item.key, +1) }, enabled = canMoveDown) {
                Icon(Icons.Rounded.KeyboardArrowDown, stringResource(R.string.editor_move_down))
            }
            IconButton(onClick = { focus.clearFocus(); actions.remove(item.key) }) {
                Icon(Icons.Rounded.Delete, stringResource(R.string.editor_delete_item))
            }
        }
        Column(
            Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when (item) {
                is DraftItem.Step -> {
                    EditorField(
                        value = item.title,
                        onValueChange = { v -> actions.updateItem(item.key) { (it as DraftItem.Step).copy(title = v) } },
                        label = stringResource(R.string.editor_field_title),
                        error = error(DraftField.STEP_TITLE),
                    )
                    EditorField(
                        value = item.duration,
                        onValueChange = { v ->
                            // Маска «м:сс»: набираются только цифры, двоеточие ставит показ.
                            val digits = v.filter(Char::isDigit).take(MAX_DURATION_DIGITS)
                            actions.updateItem(item.key) { (it as DraftItem.Step).copy(duration = digits) }
                        },
                        label = stringResource(R.string.editor_field_duration),
                        error = error(DraftField.DURATION),
                        hint = stringResource(R.string.editor_duration_hint),
                        keyboardType = KeyboardType.Number,
                        visualTransformation = DurationTransformation,
                    )
                    EditorField(
                        value = item.targetG,
                        onValueChange = { v ->
                            actions.updateItem(item.key) { (it as DraftItem.Step).copy(targetG = v.filter(Char::isDigit)) }
                        },
                        label = stringResource(R.string.editor_field_target),
                        error = error(DraftField.TARGET),
                        hint = stringResource(R.string.editor_target_hint),
                        suffix = gram,
                        keyboardType = KeyboardType.Number,
                    )
                    EditorField(
                        value = item.note,
                        onValueChange = { v -> actions.updateItem(item.key) { (it as DraftItem.Step).copy(note = v) } },
                        label = stringResource(R.string.editor_field_note),
                        error = null,
                    )
                    // Табло варки на этом шаге показывает крупно время до конца шага, а не воду.
                    val setShowTime = { on: Boolean -> actions.updateItem(item.key) { (it as DraftItem.Step).copy(showTime = on) } }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .toggleable(value = item.showTime, role = Role.Switch, onValueChange = setShowTime)
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            stringResource(R.string.editor_show_time),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f),
                        )
                        Switch(checked = item.showTime, onCheckedChange = null)
                    }
                }
                is DraftItem.Hint -> EditorField(
                    value = item.text,
                    onValueChange = { v -> actions.updateItem(item.key) { (it as DraftItem.Hint).copy(text = v) } },
                    label = stringResource(R.string.editor_field_hint_text),
                    error = error(DraftField.HINT_TEXT),
                    singleLine = false,
                )
            }
        }
    }
}

@Composable
private fun EditorField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    error: DraftError.Field?,
    hint: String? = null,
    suffix: String? = null,
    singleLine: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text,
    visualTransformation: VisualTransformation = VisualTransformation.None,
) {
    val message = error?.let { errorText(it) } ?: hint
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        isError = error != null,
        supportingText = message?.let { { Text(it) } },
        suffix = suffix?.let { { Text(it) } },
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 2,
        visualTransformation = visualTransformation,
        keyboardOptions = KeyboardOptions(
            keyboardType = keyboardType,
            capitalization = if (keyboardType == KeyboardType.Text) KeyboardCapitalization.Sentences else KeyboardCapitalization.None,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun errorText(error: DraftError.Field): String = when (error.problem) {
    DraftProblem.EMPTY -> stringResource(R.string.editor_error_empty)
    DraftProblem.ZERO -> stringResource(R.string.editor_error_zero)
    DraftProblem.INVALID -> stringResource(
        if (error.field == DraftField.DURATION) R.string.editor_error_duration else R.string.editor_error_grams,
    )
    DraftProblem.BELOW_PREVIOUS -> stringResource(
        R.string.editor_error_below_previous,
        stringResource(R.string.value_with_unit, error.previousG.toString(), stringResource(WeightUnit.GRAM.symbolRes())),
    )
}

private const val MAX_DURATION_DIGITS = 3

/** Раскрытие и сворачивание карточки: растворение и смена высоты идут вместе. */
private const val EXPAND_MS = 250

/**
 * Маска «м:сс» для цифр длительности: цифры пишутся слева направо как набраны, двоеточие стоит после минут
 * («1» → «1:», «13» → «1:3», «130» → «1:30»). Набирать двоеточие не нужно — на цифровой клавиатуре его нет.
 */
private object DurationTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val digits = text.text
        if (digits.isEmpty()) return TransformedText(text, OffsetMapping.Identity)
        // Двоеточие — перед двумя цифрами секунд; пока их не набрали, сразу после первой цифры.
        val colon = if (digits.length <= 2) 1 else digits.length - 2
        val shown = digits.substring(0, colon) + ":" + digits.substring(colon)
        return TransformedText(
            AnnotatedString(shown),
            object : OffsetMapping {
                override fun originalToTransformed(offset: Int): Int = if (offset < colon) offset else offset + 1

                override fun transformedToOriginal(offset: Int): Int =
                    (if (offset <= colon) offset else offset - 1).coerceIn(0, digits.length)
            },
        )
    }
}

/** «+» между элементами: меню «Шаг» / «Подпись». */
@Composable
private fun AddButton(onAdd: (step: Boolean) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box {
            FilledTonalIconButton(onClick = { menu = true }) {
                Icon(Icons.Rounded.Add, stringResource(R.string.editor_add))
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.editor_item_step)) },
                    leadingIcon = { Icon(Icons.Rounded.Timer, contentDescription = null) },
                    onClick = { menu = false; onAdd(true) },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.editor_item_hint)) },
                    leadingIcon = { Icon(Icons.Rounded.Info, contentDescription = null) },
                    onClick = { menu = false; onAdd(false) },
                )
            }
        }
    }
}

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

@Preview(showBackground = true, heightDp = 1400, locale = "ru")
@Composable
private fun EditorPreview() {
    val draft = BuiltInRecipes.hoffmannV60.toDraft({ "Шаг" }, id = "user:preview")
        .let { it.copy(items = it.items.toMutableList().apply { set(4, DraftItem.Step(4, "Налейте", "030", "100")) }) }
    OpenScalesTheme(dynamicColor = false) {
        RecipeEditorScreen(
            ui = EditorUi(draft, expanded = setOf(1L, 4L), showErrors = true),
            isNew = false,
            actions = NoActions,
        )
    }
}
