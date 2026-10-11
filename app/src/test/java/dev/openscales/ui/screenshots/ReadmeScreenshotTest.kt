package dev.openscales.ui.screenshots

import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import dev.openscales.data.AppSettings
import dev.openscales.data.SavedDevice
import dev.openscales.protocol.Precision
import dev.openscales.protocol.ScaleModel
import dev.openscales.session.ScaleSettings
import dev.openscales.protocol.Sensitivity
import dev.openscales.protocol.TimerState
import dev.openscales.recipe.BuiltInRecipes
import dev.openscales.recipe.Difficulty
import dev.openscales.recipe.DraftItem
import dev.openscales.recipe.ItemTemplate
import dev.openscales.recipe.RecipeCategory
import dev.openscales.recipe.RecipeTimeline
import dev.openscales.recipe.StepWeightMode
import dev.openscales.recipe.toDraft
import dev.openscales.session.ConnectionPhase
import dev.openscales.session.ScaleState
import dev.openscales.ui.brew.BeansScreen
import dev.openscales.ui.brew.BrewPhase
import dev.openscales.ui.brew.BrewUi
import dev.openscales.ui.brew.StepsScreen
import dev.openscales.ui.dashboard.DashboardScreen
import dev.openscales.ui.dashboard.MainTab
import dev.openscales.ui.editor.EditorActions
import dev.openscales.ui.editor.EditorUi
import dev.openscales.ui.editor.RecipeEditorScreen
import dev.openscales.ui.recipes.NewRecipeButton
import dev.openscales.ui.recipes.RecipesScreen
import dev.openscales.ui.scale.ScaleDetailsActions
import dev.openscales.ui.scale.ScaleDetailsScreen
import dev.openscales.ui.settings.SettingsActions
import dev.openscales.ui.settings.SettingsScreen
import org.junit.Test
import org.robolectric.annotation.Config

/**
 * Скриншоты для README: те же экраны, что видит пользователь, по-английски, в `docs/screenshots`. Сверяются
 * вместе с остальными эталонами — изменился экран, значит, картинка в README устарела и её надо перезаписать
 * (`recordRoborazziDebug --tests '*ReadmeScreenshotTest'`).
 */
@Config(qualifiers = ReadmeScreenshotTest.README_PHONE)
class ReadmeScreenshotTest : ScreenshotTest() {

    private val address = "C8:47:8C:00:11:22"

    private val scale = ScaleState(
        phase = ConnectionPhase.READY, name = "TIMEMORE_Dot", model = ScaleModel.DOT, address = address,
        weight = 18.3f, flowRate = 2.4f, timeSeconds = 75, timerState = TimerState.RUNNING, batteryPercent = 89,
        settings = ScaleSettings(standbyMinutes = 5, sensitivity = Sensitivity.MEDIUM, precision = Precision.HIGH, firmware = "v1.0.4"),
    )

    private fun readme(name: String, dark: Boolean = false, content: @Composable () -> Unit) =
        snapshot(name, dark, README_DIR, content)

    @Composable
    private fun Main(tab: MainTab) = DashboardScreen(
        state = scale,
        hasSavedDevice = true,
        snackbarHostState = SnackbarHostState(),
        onTare = {}, onToggleTimer = {}, onResetTimer = {}, onConnect = {}, onOpenScan = {}, onOpenSettings = {},
        selectedTab = tab,
        recipesTab = { padding ->
            RecipesScreen(
                recipes = BuiltInRecipes.all,
                state = scale,
                padding = padding,
                onOpen = {},
                connectBanner = { Text("") },
            )
        },
        recipesFab = { NewRecipeButton {} },
    )

    @Test
    fun mainLight() = readme("main-light") { Main(MainTab.SCALE) }

    @Test
    fun mainDark() = readme("main-dark", dark = true) { Main(MainTab.SCALE) }

    @Test
    fun recipes() = readme("recipes") { Main(MainTab.RECIPES) }

    private fun brew(phase: BrewPhase, weight: Float, seconds: Int = 0, dose: Double? = 15.0) = BrewUi(
        phase = phase,
        scale = scale.copy(
            weight = weight,
            flowRate = if (phase == BrewPhase.RUNNING) 4.2f else 0f,
            timerState = if (phase == BrewPhase.RUNNING) TimerState.RUNNING else TimerState.PAUSED,
            timeSeconds = seconds,
        ),
        doseG = dose,
        weightG = weight.toDouble(),
        lastWeightG = weight.toDouble(),
        seconds = seconds,
    )

    @Test
    fun brewBeans() = readme("brew-beans") {
        BeansScreen(
            recipe = BuiltInRecipes.hoffmannV60,
            ui = brew(BrewPhase.BEANS, weight = 15f, dose = null),
            snackbarHostState = SnackbarHostState(),
            onBack = {}, onTare = {}, onNext = {},
        )
    }

    @Test
    fun brewPour() = readme("brew-pour", dark = true) {
        val recipe = BuiltInRecipes.hoffmannV60
        StepsScreen(
            recipe = recipe,
            timeline = RecipeTimeline(recipe),
            ui = brew(BrewPhase.RUNNING, weight = 96f, seconds = 55),
            mode = StepWeightMode.REMAINING,
            snackbarHostState = SnackbarHostState(),
            onBack = {}, onTare = {}, onStart = {}, onPause = {}, onStop = {},
        )
    }

    private object NoActions : EditorActions {
        override fun setTitle(value: String) = Unit
        override fun setDose(value: String) = Unit
        override fun setCategory(value: RecipeCategory) = Unit
        override fun setDifficulty(value: Difficulty) = Unit
        override fun setDescription(value: String) = Unit
        override fun updateItem(key: Long, change: (DraftItem) -> DraftItem) = Unit
        override fun insert(index: Int, template: ItemTemplate, title: String) = Unit
        override fun duplicate(key: Long) = Unit
        override fun focusConsumed() = Unit
        override fun move(key: Long, delta: Int) = Unit
        override fun remove(key: Long) = Unit
        override fun toggle(key: Long) = Unit
        override fun onSave() = Unit
        override fun onCancel() = Unit
    }

    @Test
    fun editor() = readme("editor") {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val draft = BuiltInRecipes.hoffmannV60.toDraft({ text ->
            when (text) {
                is dev.openscales.recipe.Text.Res -> context.getString(text.id)
                is dev.openscales.recipe.Text.Plain -> text.value
            }
        }, id = "user:copy").copy(title = "My V60")
        RecipeEditorScreen(ui = EditorUi(draft), isNew = false, actions = NoActions)
    }

    @Test
    fun settings() = readme("settings-light") {
        SettingsScreen(appSettings = AppSettings(), actions = SettingsActions({}))
    }

    @Test
    fun scaleDetails() = readme("scale-dark", dark = true) {
        ScaleDetailsScreen(
            address = address,
            state = scale,
            saved = SavedDevice(address, "TIMEMORE_Dot", ScaleModel.DOT),
            snackbarHostState = SnackbarHostState(),
            actions = ScaleDetailsActions({}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}),
        )
    }

    companion object {
        /** Телефон для README: по-английски, 360×780 dp, вдвое плотнее, чем картинка в README. */
        const val README_PHONE = "en-w360dp-h780dp-xhdpi"
        const val README_DIR = "../docs/screenshots"
    }
}
