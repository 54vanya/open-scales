package dev.openscales.ui.recipes

import androidx.annotation.StringRes
import dev.openscales.R
import dev.openscales.recipe.Recipe
import dev.openscales.recipe.RecipeCategory
import dev.openscales.recipe.Text

/** Группа списка рецептов: заголовок и рецепты по порядку. [own] — свои рецепты пользователя. */
data class RecipeGroup(val key: String, @StringRes val titleRes: Int, val recipes: List<Recipe>, val own: Boolean)

/**
 * Группы списка рецептов — одни и те же на вкладке «Рецепты» и в окне «Сменить рецепт»: сначала «Мои»
 * (по названию без учёта регистра), затем встроенные по оборудованию в порядке [RecipeCategory]. Пустых групп нет.
 */
fun recipeGroups(userRecipes: List<Recipe>, builtIn: List<Recipe>): List<RecipeGroup> {
    val mine = userRecipes.sortedBy { (it.title as? Text.Plain)?.value?.lowercase() }
    val groups = mutableListOf<RecipeGroup>()
    if (mine.isNotEmpty()) groups += RecipeGroup("mine", R.string.recipe_group_mine, mine, own = true)
    RecipeCategory.entries.forEach { category ->
        val group = builtIn.filter { it.category == category }
        if (group.isNotEmpty()) groups += RecipeGroup(category.name, category.titleRes(), group, own = false)
    }
    return groups
}
