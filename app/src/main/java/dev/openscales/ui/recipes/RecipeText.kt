package dev.openscales.ui.recipes

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.openscales.R
import dev.openscales.protocol.WeightUnit
import dev.openscales.recipe.Difficulty
import dev.openscales.recipe.Recipe
import dev.openscales.recipe.RecipeCategory
import dev.openscales.recipe.RecipeTimeline
import dev.openscales.recipe.Text
import dev.openscales.recipe.formatStepWeight
import dev.openscales.ui.components.formatTime
import dev.openscales.ui.components.symbolRes

/** Текст рецепта на языке интерфейса. */
@Composable
fun Text.resolve(): String = when (this) {
    is Text.Res -> stringResource(id)
    is Text.Plain -> value
}

/** «Просто», «Средне», «Сложно». */
fun Difficulty.labelRes(): Int = when (this) {
    Difficulty.EASY -> R.string.difficulty_easy
    Difficulty.MEDIUM -> R.string.difficulty_medium
    Difficulty.HARD -> R.string.difficulty_hard
}

/** Название группы рецептов по оборудованию. */
fun RecipeCategory.titleRes(): Int = when (this) {
    RecipeCategory.V60 -> R.string.recipe_group_v60
    RecipeCategory.FLAT_BOTTOM -> R.string.recipe_group_flat_bottom
    RecipeCategory.SWITCH -> R.string.recipe_group_switch
    RecipeCategory.CHEMEX -> R.string.recipe_group_chemex
    RecipeCategory.AEROPRESS -> R.string.recipe_group_aeropress
    RecipeCategory.FRENCH_PRESS -> R.string.recipe_group_french_press
}

/** Вес шага рецепта с единицей весов: «250 г». */
@Composable
fun stepWeightWithUnit(grams: Double, unit: WeightUnit): String =
    stringResource(R.string.value_with_unit, formatStepWeight(grams, unit), stringResource(unit.symbolRes()))

/** «15 г · 250 г · 3:30»: доза по умолчанию, вода при ней и длительность шагов. */
@Composable
fun Recipe.summary(unit: WeightUnit): String = stringResource(
    R.string.recipe_summary,
    stepWeightWithUnit(defaultDoseG.toDouble(), unit),
    stepWeightWithUnit(totalWaterG(defaultDoseG.toDouble()), unit),
    formatTime(RecipeTimeline(this).total),
)
