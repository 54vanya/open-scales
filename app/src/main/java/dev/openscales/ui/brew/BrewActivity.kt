package dev.openscales.ui.brew

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import android.os.SystemClock
import dev.openscales.OpenScalesApp
import dev.openscales.SecondsProbe
import dev.openscales.ble.BleJournal
import dev.openscales.R
import dev.openscales.recipe.BuiltInRecipes
import dev.openscales.recipe.Recipe
import dev.openscales.recipe.RecipeDraft
import dev.openscales.ui.editor.RecipeEditorActivity
import dev.openscales.ui.OpenScalesActivity
import dev.openscales.ui.theme.AppTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Экран варки по рецепту: «Зерно», затем «Шаги». Отдельная Activity, чтобы жест «назад» анимировала система,
 * а навигация главного экрана не мешала. Варка живёт в [BrewViewModel] и переживает поворот, но не смерть процесса.
 */
class BrewActivity : OpenScalesActivity() {

    private val app get() = application as OpenScalesApp

    private val recipe: Recipe? by lazy { app.recipeById(intent.getStringExtra(EXTRA_RECIPE_ID)) }

    private val viewModel: BrewViewModel by viewModels {
        viewModelFactory {
            initializer {
                BrewViewModel(
                    repository = app.repository,
                    recipe = checkNotNull(recipe),
                    scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
                    longScope = app.appScope,
                    beep = app.buttonSound::onControlPressed,
                    signal = app.buttonSound::onStepSignal,
                )
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (recipe == null) return finish()
        // «Пик» идёт как системный звук — пусть клавиши громкости в приложении меняют именно его.
        volumeControlStream = AudioManager.STREAM_SYSTEM
        setContent {
            AppTheme {
                val ui by viewModel.ui.collectAsStateWithLifecycle()
                val recipe by viewModel.recipeState.collectAsStateWithLifecycle()
                val userRecipes by app.recipeStore.recipes.collectAsStateWithLifecycle()
                var showPicker by rememberSaveable { mutableStateOf(false) }
                var confirmLeave by rememberSaveable { mutableStateOf(false) }
                // Ручной ввод дозы открыт с этой подстановкой; `null` — закрыт.
                var manualDoseG by rememberSaveable { mutableStateOf<Double?>(null) }
                // Сменить рецепт — на «Шагах» до «Старт», когда доза уже зафиксирована. С «Зерна» проще выйти.
                val swap: (() -> Unit)? = if (ui.phase == BrewPhase.READY) ({ showPicker = true }) else null
                // Свой рецепт поправили в редакторе, открытом отсюда, — на «Зерне» показываем новую версию.
                LaunchedEffect(Unit) {
                    app.recipeStore.recipes.collect { app.recipeById(recipe.id)?.let(viewModel::updateRecipe) }
                }
                val appSettings by app.buttonSound.settings.collectAsStateWithLifecycle()
                val snackbar = remember { SnackbarHostState() }
                var confirmFinish by rememberSaveable { mutableStateOf(false) }
                LaunchedEffect(Unit) { viewModel.errors.collect { snackbar.showSnackbar(getString(it)) } }

                // Звуковой тракт держим готовым, пока экран на виду, — как на главном экране.
                LifecycleEventEffect(Lifecycle.Event.ON_START) { app.buttonSound.setDashboardVisible(true) }
                LifecycleEventEffect(Lifecycle.Event.ON_STOP) { app.buttonSound.setDashboardVisible(false) }

                // Удержание экрана — по той же настройке, что на главном экране.
                val view = LocalView.current
                DisposableEffect(view, appSettings.keepScreenOn) {
                    view.keepScreenOn = appSettings.keepScreenOn
                    onDispose { view.keepScreenOn = false }
                }

                // Только debug: отрисовки времени варки в журнал, «замерло и догнало» — важным событием.
                val probe = remember { app.bleJournal?.let { SecondsProbe(maxGapMs = MAX_DRAW_GAP_MS) } }
                // Сравниваем только отрисовки идущего времени: 0:00 до пролива и стоящее на паузе время — не «замерло».
                val running by rememberUpdatedState(ui.phase == BrewPhase.RUNNING)
                val drawProbe: ((Int) -> Unit)? = remember(probe) {
                    val journal = app.bleJournal
                    if (probe == null || journal == null) {
                        null
                    } else {
                        { shown: Int ->
                            val now = SystemClock.elapsedRealtime()
                            val skip = if (running) probe.onValue(shown, now) else null.also { probe.pause() }
                            journal.add(
                                BleJournal.Kind.UI,
                                "drawn=$shown at=$now" + (skip?.let { " FREEZE $it" } ?: ""),
                                notable = skip != null,
                            )
                        }
                    }
                }
                // Время рецепта вышло — варка окончена, спрашивать «Завершить варку?» незачем.
                val askToFinish = {
                    if (ui.phase == BrewPhase.FINISHED) {
                        viewModel.finish()
                        finish()
                    } else {
                        confirmFinish = true
                    }
                }
                if (ui.phase == BrewPhase.BEANS) {
                    // Без обработчика: системный «назад» закрывает экран варки.
                    BeansScreen(
                        recipe = recipe,
                        ui = ui,
                        snackbarHostState = snackbar,
                        onBack = ::finish,
                        onTare = viewModel::tare,
                        onNext = viewModel::fixDose,
                        onEdit = if (recipe.id.startsWith(RecipeDraft.USER_ID_PREFIX)) {
                            { startActivity(RecipeEditorActivity.edit(this, recipe)) }
                        } else {
                            null
                        },
                        triggerOnPress = appSettings.triggerOnPress,
                        slashedZero = appSettings.slashedZero,
                        onManualDose = { manualDoseG = viewModel.manualDosePrefillG() },
                    )
                } else {
                    val beforeStart = ui.phase == BrewPhase.READY
                    // Доза зафиксирована — выход только через подтверждение: до «Старт» «Выйти из рецепта?», после —
                    // «Завершить варку?».
                    val leave = { if (beforeStart) confirmLeave = true else askToFinish() }
                    BackHandler { leave() }
                    StepsScreen(
                        recipe = recipe,
                        timeline = viewModel.timeline,
                        ui = ui,
                        mode = appSettings.stepWeightMode,
                        snackbarHostState = snackbar,
                        onBack = leave,
                        onTare = viewModel::tare,
                        onStart = viewModel::startOrCancel,
                        onPause = viewModel::togglePause,
                        onStop = leave,
                        onTarePart = viewModel::tarePart,
                        elapsedMs = viewModel::brewElapsedMs,
                        triggerOnPress = appSettings.triggerOnPress,
                        slashedZero = appSettings.slashedZero,
                        onTimeDrawn = drawProbe,
                        onSwap = swap,
                    )
                }

                if (showPicker) {
                    RecipePickerDialog(
                        current = recipe,
                        userRecipes = userRecipes,
                        builtIn = BuiltInRecipes.all,
                        unit = ui.scale.unit,
                        onSelect = { picked ->
                            showPicker = false
                            viewModel.updateRecipe(picked)
                        },
                        onDismiss = { showPicker = false },
                    )
                }

                val prefill = manualDoseG
                if (prefill != null && ui.phase == BrewPhase.BEANS) {
                    ManualDoseDialog(
                        recipe = recipe,
                        unit = ui.scale.unit,
                        initialG = prefill,
                        scaleReady = ui.scale.isReady,
                        onConfirm = { grams ->
                            manualDoseG = null
                            viewModel.fixManualDose(grams)
                        },
                        onDismiss = { manualDoseG = null },
                    )
                }

                if (confirmLeave) {
                    LeaveRecipeDialog(
                        onLeave = {
                            confirmLeave = false
                            finish()
                        },
                        onReweigh = {
                            confirmLeave = false
                            viewModel.backToBeans()
                        },
                        onStay = { confirmLeave = false },
                    )
                }

                if (confirmFinish) {
                    AlertDialog(
                        onDismissRequest = { confirmFinish = false },
                        title = { Text(stringResource(R.string.brew_finish_title)) },
                        text = { Text(stringResource(R.string.brew_finish_text)) },
                        confirmButton = {
                            TextButton(onClick = {
                                confirmFinish = false
                                viewModel.finish()
                                finish()
                            }) { Text(stringResource(R.string.brew_finish)) }
                        },
                        dismissButton = {
                            TextButton(onClick = { confirmFinish = false }) { Text(stringResource(R.string.brew_continue)) }
                        },
                    )
                }
            }
        }
    }

    companion object {
        private const val EXTRA_RECIPE_ID = "recipe_id"

        /** Время идёт, а экран не перерисовывал его дольше — «замерло». */
        private const val MAX_DRAW_GAP_MS = 1_200L

        fun intent(context: Context, recipe: Recipe): Intent =
            Intent(context, BrewActivity::class.java).putExtra(EXTRA_RECIPE_ID, recipe.id)
    }
}
