package dev.openscales.ui.recipes

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.openscales.R
import dev.openscales.protocol.WeightUnit
import dev.openscales.recipe.BuiltInRecipes
import dev.openscales.recipe.Recipe
import androidx.compose.material3.MaterialTheme
import dev.openscales.session.ConnectionPhase
import dev.openscales.session.ScaleState
import dev.openscales.ui.theme.OpenScalesTheme

/**
 * Вкладка «Рецепты»: свои рецепты группой «Мои» (по названию), за ними встроенные по способу заваривания.
 * Без готовых весов над списком баннер подключения ([connectBanner], тот же, что на вкладке «Весы»); рецепт
 * при этом открывается как предпросмотр — варку без весов не начать. Долгое нажатие — меню действий:
 * у своего «Изменить», «Копировать», «Удалить» (с подтверждением), у встроенного только «Копировать».
 */
@Composable
fun RecipesScreen(
    recipes: List<Recipe>,
    state: ScaleState,
    padding: PaddingValues,
    onOpen: (Recipe) -> Unit,
    connectBanner: @Composable () -> Unit,
    userRecipes: List<Recipe> = emptyList(),
    onEdit: (Recipe) -> Unit = {},
    onCopy: (Recipe) -> Unit = {},
    onDelete: (Recipe) -> Unit = {},
) {
    var menuFor by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf<Recipe?>(null) }
    val groups = recipeGroups(userRecipes, recipes)

    @Composable
    fun Item(recipe: Recipe, index: Int, size: Int, own: Boolean) {
        // Где палец коснулся рецепта: меню долгого нажатия открывается там, а не у края строки.
        var pressAt by remember { mutableStateOf(Offset.Zero) }
        Box(
            Modifier.pointerInput(Unit) {
                awaitEachGesture {
                    pressAt = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial).position
                }
            },
        ) {
            SegmentedListItem(
                onClick = { onOpen(recipe) },
                shapes = ListItemDefaults.segmentedShapes(index, size),
                supportingContent = { Text(recipe.summary(state.unit)) },
                trailingContent = {
                    Text(
                        stringResource(recipe.difficulty.labelRes()),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                onLongClick = { menuFor = recipe.id },
                onLongClickLabel = stringResource(R.string.recipe_actions),
            ) {
                Text(recipe.title.resolve())
            }
            // Невидимый якорь в точке касания: DropdownMenu раскрывается от него.
            Box(Modifier.offset { IntOffset(pressAt.x.roundToInt(), pressAt.y.roundToInt()) }) {
                DropdownMenu(expanded = menuFor == recipe.id, onDismissRequest = { menuFor = null }) {
                    if (own) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.recipe_action_edit)) },
                            leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                            onClick = { menuFor = null; onEdit(recipe) },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.recipe_action_copy)) },
                        leadingIcon = { Icon(Icons.Rounded.ContentCopy, contentDescription = null) },
                        onClick = { menuFor = null; onCopy(recipe) },
                    )
                    if (own) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.recipe_action_delete)) },
                            leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null) },
                            onClick = { menuFor = null; confirmDelete = recipe },
                        )
                    }
                }
            }
        }
    }

    confirmDelete?.let { recipe ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text(stringResource(R.string.recipe_delete_title, recipe.title.resolve())) },
            confirmButton = {
                TextButton(onClick = { confirmDelete = null; onDelete(recipe) }) {
                    Text(stringResource(R.string.recipe_action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp, end = 16.dp,
            top = padding.calculateTopPadding() + 8.dp,
            // Под кнопкой «Новый рецепт» последний рецепт не прячется.
            bottom = padding.calculateBottomPadding() + FAB_CLEARANCE,
        ),
        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
    ) {
        item(key = "banner") {
            AnimatedVisibility(
                visible = !state.isReady,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                Box(Modifier.padding(bottom = 16.dp)) { connectBanner() }
            }
        }
        groups.forEach { group ->
            item(key = "header:${group.key}") { GroupHeader(stringResource(group.titleRes)) }
            itemsIndexed(group.recipes, key = { _, recipe -> recipe.id }) { index, recipe ->
                Item(recipe, index, group.recipes.size, own = group.own)
            }
        }
    }
}

@Composable
internal fun GroupHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp, start = 4.dp),
    )
}

/** Кнопка «Новый рецепт» для вкладки: показывается в `Scaffold` главного экрана. */
@Composable
fun NewRecipeButton(onClick: () -> Unit) {
    ExtendedFloatingActionButton(
        onClick = onClick,
        icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
        text = { Text(stringResource(R.string.recipe_new)) },
    )
}

private val FAB_CLEARANCE = 88.dp


@Preview(showBackground = true, heightDp = 900)
@Composable
private fun RecipesReadyPreview() {
    OpenScalesTheme(dynamicColor = false) {
        RecipesScreen(
            recipes = BuiltInRecipes.all,
            state = ScaleState(phase = ConnectionPhase.READY),
            padding = PaddingValues(),
            onOpen = {},
            connectBanner = {},
        )
    }
}

@Preview(showBackground = true, locale = "ru")
@Composable
private fun RecipesNoScalePreview() {
    OpenScalesTheme(dynamicColor = false) {
        RecipesScreen(
            recipes = BuiltInRecipes.all,
            state = ScaleState(unit = WeightUnit.GRAM),
            padding = PaddingValues(),
            onOpen = {},
            connectBanner = { Text(stringResource(R.string.recipes_need_scale), Modifier.padding(16.dp)) },
        )
    }
}
