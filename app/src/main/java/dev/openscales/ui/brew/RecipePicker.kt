package dev.openscales.ui.brew

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import dev.openscales.R
import dev.openscales.protocol.WeightUnit
import dev.openscales.recipe.Recipe
import dev.openscales.ui.recipes.GroupHeader
import dev.openscales.ui.recipes.recipeGroups
import dev.openscales.ui.recipes.resolve
import dev.openscales.ui.recipes.summary

/**
 * Окно «Сменить рецепт»: те же группы и порядок, что на вкладке «Рецепты», текущий рецепт отмечен галочкой.
 * Касание другого рецепта — [onSelect], текущего или вне окна — [onDismiss].
 */
@Composable
fun RecipePickerDialog(
    current: Recipe,
    userRecipes: List<Recipe>,
    builtIn: List<Recipe>,
    unit: WeightUnit,
    onSelect: (Recipe) -> Unit,
    onDismiss: () -> Unit,
) {
    val groups = recipeGroups(userRecipes, builtIn)
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Column(Modifier.padding(top = 24.dp, bottom = 8.dp)) {
                Text(
                    stringResource(R.string.brew_swap_title),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 520.dp).padding(top = 8.dp)) {
                    groups.forEach { group ->
                        item(key = "header:${group.key}") {
                            Column(Modifier.padding(horizontal = 20.dp)) { GroupHeader(stringResource(group.titleRes)) }
                        }
                        items(group.recipes, key = { it.id }) { recipe ->
                            val isCurrent = recipe.id == current.id
                            ListItem(
                                headlineContent = { Text(recipe.title.resolve()) },
                                supportingContent = { Text(recipe.summary(unit)) },
                                trailingContent = if (isCurrent) {
                                    { Icon(Icons.Rounded.Check, stringResource(R.string.brew_swap_current)) }
                                } else {
                                    null
                                },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                modifier = Modifier
                                    .clickable { if (isCurrent) onDismiss() else onSelect(recipe) }
                                    .padding(horizontal = 8.dp),
                            )
                        }
                    }
                }
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End).padding(end = 16.dp),
                ) { Text(stringResource(R.string.cancel)) }
            }
        }
    }
}

/**
 * «Выйти из рецепта?» — до «Старт», когда доза уже зафиксирована. Три действия столбиком у правого края, как
 * советует Material для трёх кнопок: «Выйти», «Взвесить заново» (на «Зерно»), «Остаться».
 */
@Composable
fun LeaveRecipeDialog(onLeave: () -> Unit, onReweigh: () -> Unit, onStay: () -> Unit) {
    AlertDialog(
        onDismissRequest = onStay,
        title = { Text(stringResource(R.string.brew_leave_title)) },
        text = { Text(stringResource(R.string.brew_leave_text)) },
        confirmButton = {
            Column(horizontalAlignment = Alignment.End) {
                TextButton(onClick = onLeave) { Text(stringResource(R.string.brew_leave)) }
                TextButton(onClick = onReweigh) { Text(stringResource(R.string.brew_reweigh)) }
                TextButton(onClick = onStay) { Text(stringResource(R.string.brew_stay)) }
            }
        },
    )
}
