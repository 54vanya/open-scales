package dev.openscales.ui.screenshots

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Text
import dev.openscales.protocol.ScaleModel
import dev.openscales.recipe.BuiltInRecipes
import dev.openscales.recipe.Difficulty
import dev.openscales.recipe.RecipeItem
import dev.openscales.recipe.Text as RecipeText
import dev.openscales.recipe.Recipe
import dev.openscales.session.ConnectionPhase
import dev.openscales.session.ScaleState
import dev.openscales.ui.recipes.RecipesScreen
import org.junit.Test

/** Вкладка «Рецепты»: «Мои», группы по оборудованию, сложность справа, без иконок. */
class RecipesScreenshotTest : ScreenshotTest() {

    private val ready = ScaleState(phase = ConnectionPhase.READY, name = "TIMEMORE_Dot", model = ScaleModel.entries.first())

    private val dripchik = Recipe(
        id = "user:dripchik",
        title = RecipeText.Plain("Дрипчик"),
        defaultDoseG = 14,
        description = null,
        items = listOf(RecipeItem.Step(RecipeText.Plain("Налейте"), 105, targetG = 180)),
        difficulty = Difficulty.EASY,
    )

    @Test
    fun recipes() = snapshot("recipes") {
        RecipesScreen(
            recipes = BuiltInRecipes.all,
            userRecipes = listOf(dripchik),
            state = ready,
            padding = PaddingValues(),
            onOpen = {},
            connectBanner = { Text("") },
        )
    }
}
