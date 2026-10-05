package dev.openscales.ui.screenshots

import androidx.compose.material3.SnackbarHostState
import dev.openscales.protocol.TimerState
import dev.openscales.recipe.BuiltInRecipes
import dev.openscales.recipe.Recipe
import dev.openscales.recipe.RecipeItem
import dev.openscales.recipe.RecipeTimeline
import dev.openscales.recipe.StepWeightMode
import dev.openscales.recipe.TareState
import dev.openscales.recipe.Text
import dev.openscales.session.ConnectionPhase
import dev.openscales.session.ScaleState
import dev.openscales.ui.brew.BeansScreen
import dev.openscales.ui.brew.BrewPhase
import dev.openscales.ui.brew.BrewUi
import dev.openscales.ui.brew.LeaveRecipeDialog
import dev.openscales.ui.brew.ManualDoseDialog
import dev.openscales.ui.brew.RecipePickerDialog
import dev.openscales.ui.brew.StepsScreen
import org.junit.Test
import org.robolectric.annotation.Config

/** Экран варки: «Зерно», все состояния табло на «Шагах», окна выхода, смены рецепта и ручной дозы. */
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
            ui = ui(BrewPhase.BEANS, weight = 18f, dose = null),
            snackbarHostState = SnackbarHostState(),
            onBack = {}, onTare = {}, onNext = {},
        )
    }

    /** До «Старт»: пустое табло «Нажмите «Старт»», описание над первым шагом, иконка «Сменить рецепт». */
    @Test
    fun stepsBeforeStart() = steps("brew_steps_ready", BuiltInRecipes.hoffmannV60, ui(BrewPhase.READY, 315f), swap = true)

    /** Пролив с отставанием: крупно остаток воды шага, на полосе бледный хвост до отметки идеального уровня. */
    @Test
    fun stepsPouring() = steps("brew_steps_pouring", BuiltInRecipes.hoffmannV60, ui(BrewPhase.RUNNING, 60f, seconds = 60))

    /** Пролив с опережением: налитое ушло за отметку идеального уровня. */
    @Test
    fun stepsAhead() = steps("brew_steps_ahead", BuiltInRecipes.hoffmannV60, ui(BrewPhase.RUNNING, 110f, seconds = 50))

    /** Ожидание: крупно время до конца шага, в углу итог последнего шага с водой. */
    @Test
    fun stepsWaiting() = steps("brew_steps_waiting", BuiltInRecipes.hoffmannV60, ui(BrewPhase.RUNNING, 25f, seconds = 21))

    /** Цветение ещё льётся на «Взболтайте»: крупно вода с названием её шага, остаток «Взболтайте» в углу. */
    @Test
    fun stepsHolding() = steps(
        "brew_steps_holding",
        BuiltInRecipes.hoffmannV60,
        ui(BrewPhase.RUNNING, 26f, seconds = 14).copy(holdWater = true),
    )

    /** Аэропресс: время до прожима. */
    @Test
    fun aeropressWaiting() =
        steps("brew_aeropress_waiting", BuiltInRecipes.hoffmannAeropress, ui(BrewPhase.RUNNING, 200f, seconds = 80, dose = 11.0))

    /** Шаг «Тара» ван Бюнника на 1:16: вместо воды крупная кнопка «Тара», она же в нижнем ряду. */
    @Test
    fun tareStep() = steps(
        "brew_tare_step",
        BuiltInRecipes.vanBunnikAeropress,
        ui(BrewPhase.RUNNING, 70f, seconds = 76, dose = 30.0).copy(
            tare = TareState().freezeBefore(1, 100.0),
            awaitingTare = true,
        ),
    )

    /** Итог после конца времени: вес на весах и время варки. */
    @Test
    fun finished() = steps("brew_finished", BuiltInRecipes.hoffmannV60, ui(BrewPhase.FINISHED, 318.6f, seconds = 210))

    /** Время вышло, а последний пролив ещё не улёгся: «Готово» и остаток до его рубежа вместо итога. */
    @Test
    fun finishedHolding() {
        val onePour = Recipe(
            id = "user:one-pour",
            title = Text.Plain("Дрипчик"),
            defaultDoseG = 15,
            description = null,
            items = listOf(RecipeItem.Step(Text.Plain("Налейте"), 105, targetG = 180)),
        )
        steps("brew_finished_holding", onePour, ui(BrewPhase.FINISHED, 170f, seconds = 105).copy(holdWater = true))
    }

    // region Альбомная ориентация: кнопки столбцом у правого края

    @Test
    @Config(qualifiers = LANDSCAPE)
    fun beansLandscape() = snapshot("brew_beans_landscape") {
        BeansScreen(
            recipe = BuiltInRecipes.hoffmannV60,
            ui = ui(BrewPhase.BEANS, weight = 18f, dose = null),
            snackbarHostState = SnackbarHostState(),
            onBack = {}, onTare = {}, onNext = {},
        )
    }

    @Test
    @Config(qualifiers = LANDSCAPE)
    fun stepsLandscapeBeforeStart() =
        steps("brew_steps_landscape", BuiltInRecipes.hoffmannV60, ui(BrewPhase.READY, 315f), swap = true)

    @Test
    @Config(qualifiers = LANDSCAPE)
    fun stepsLandscapePouring() =
        steps("brew_steps_landscape_pouring", BuiltInRecipes.hoffmannV60, ui(BrewPhase.RUNNING, 60f, seconds = 60))

    // endregion

    @Test
    fun leaveDialog() = screen("brew_leave_dialog") {
        LeaveRecipeDialog(onLeave = {}, onReweigh = {}, onStay = {})
    }

    /** Зерно уже унесли с весов: в поле с фокусом устоявшийся вес, под ним вода от него. */
    @Test
    @Config(qualifiers = DIALOG_WITH_FIELD)
    fun manualDose() = screen("brew_manual_dose", focusedField = true) {
        ManualDoseDialog(
            recipe = BuiltInRecipes.hoffmannV60,
            unit = dev.openscales.protocol.WeightUnit.GRAM,
            initialG = 18.2,
            scaleReady = true,
            onConfirm = {},
            onDismiss = {},
        )
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
