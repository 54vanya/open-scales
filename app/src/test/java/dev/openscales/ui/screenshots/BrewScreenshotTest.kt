package dev.openscales.ui.screenshots

import androidx.compose.material3.SnackbarHostState
import dev.openscales.protocol.TimerState
import dev.openscales.recipe.BuiltInRecipes
import dev.openscales.recipe.Recipe
import dev.openscales.recipe.RecipeTimeline
import dev.openscales.recipe.StepWeightMode
import dev.openscales.session.ConnectionPhase
import dev.openscales.session.ScaleState
import dev.openscales.ui.brew.BeansScreen
import dev.openscales.ui.brew.BrewPhase
import dev.openscales.ui.brew.BrewUi
import dev.openscales.ui.brew.LeaveRecipeDialog
import dev.openscales.ui.brew.RecipePickerDialog
import dev.openscales.ui.brew.StepsScreen
import org.junit.Test

/** Экран варки: «Зерно», все состояния табло на «Шагах», окна выхода и смены рецепта. */
class BrewScreenshotTest : ScreenshotTest() {

    private fun ui(phase: BrewPhase, weight: Float, seconds: Int = 0, dose: Double? = 15.0) = BrewUi(
        phase = phase,
        scale = ScaleState(
            phase = ConnectionPhase.READY,
            name = "TIMEMORE_Dot",
            weight = weight,
            timerState = if (phase == BrewPhase.RUNNING) TimerState.RUNNING else TimerState.PAUSED,
            timeSeconds = seconds,
        ),
        doseG = dose,
        weightG = weight.toDouble(),
        lastWeightG = weight.toDouble(),
        seconds = seconds,
    )

    private fun steps(name: String, recipe: Recipe, ui: BrewUi, swap: Boolean = false) = snapshot(name) {
        StepsScreen(
            recipe = recipe,
            timeline = RecipeTimeline(recipe),
            ui = ui,
            mode = StepWeightMode.REMAINING,
            snackbarHostState = SnackbarHostState(),
            onBack = {}, onTare = {}, onStart = {}, onPause = {}, onStop = {},
            onSwap = if (swap) ({}) else null,
        )
    }

    @Test
    fun beans() = snapshot("brew_beans") {
        BeansScreen(
            recipe = BuiltInRecipes.hoffmannV60,
            ui = ui(BrewPhase.BEANS, weight = 15f, dose = null),
            snackbarHostState = SnackbarHostState(),
            onBack = {}, onTare = {}, onNext = {},
        )
    }

    /** До «Старт»: пустое табло «Нажмите «Старт»», описание над первым шагом, иконка «Сменить рецепт». */
    @Test
    fun stepsBeforeStart() = steps("brew_steps_ready", BuiltInRecipes.hoffmannV60, ui(BrewPhase.READY, 315f), swap = true)

    /** Пролив: крупно остаток воды шага. */
    @Test
    fun stepsPouring() = steps("brew_steps_pouring", BuiltInRecipes.hoffmannV60, ui(BrewPhase.RUNNING, 70f, seconds = 50))

    /** Ожидание: крупно время до конца шага, в углу итог последнего шага с водой. */
    @Test
    fun stepsWaiting() = steps("brew_steps_waiting", BuiltInRecipes.hoffmannV60, ui(BrewPhase.RUNNING, 25f, seconds = 21))

    /** Аэропресс: время до прожима. */
    @Test
    fun aeropressWaiting() =
        steps("brew_aeropress_waiting", BuiltInRecipes.hoffmannAeropress, ui(BrewPhase.RUNNING, 200f, seconds = 80, dose = 11.0))

    /** Итог после конца времени: вес на весах и время варки. */
    @Test
    fun finished() = steps("brew_finished", BuiltInRecipes.hoffmannV60, ui(BrewPhase.FINISHED, 318.6f, seconds = 210))

    @Test
    fun leaveDialog() = screen("brew_leave_dialog") {
        LeaveRecipeDialog(onLeave = {}, onReweigh = {}, onStay = {})
    }

    @Test
    fun recipePicker() = screen("brew_recipe_picker") {
        RecipePickerDialog(
            current = BuiltInRecipes.hoffmannV60,
            userRecipes = emptyList(),
            builtIn = BuiltInRecipes.all,
            unit = dev.openscales.protocol.WeightUnit.GRAM,
            onSelect = {},
            onDismiss = {},
        )
    }
}
