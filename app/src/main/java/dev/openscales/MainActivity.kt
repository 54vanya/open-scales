package dev.openscales

import dev.openscales.ui.OpenScalesActivity
import android.content.Intent
import android.media.AudioManager
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import dev.openscales.ui.ScaleViewModel
import dev.openscales.recipe.BuiltInRecipes
import dev.openscales.ui.brew.BrewActivity
import dev.openscales.ui.dashboard.ConnectBanner
import dev.openscales.ui.dashboard.DashboardScreen
import dev.openscales.ui.dashboard.MainTab
import dev.openscales.ui.recipes.NewRecipeButton
import dev.openscales.ui.recipes.RecipesScreen
import dev.openscales.ui.editor.RecipeEditorActivity
import dev.openscales.ui.rememberBlePrerequisite
import dev.openscales.ui.scan.BlePrerequisite
import dev.openscales.ui.scan.ScanActivity
import dev.openscales.ui.settings.SettingsActivity
import dev.openscales.ui.theme.AppTheme

/**
 * Главный экран. Поиск и настройки — отдельные Activity, чтобы переходы и предиктивный
 * жест «назад» анимировала сама система.
 */
class MainActivity : OpenScalesActivity() {

    private val viewModel: ScaleViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // «Пик» идёт как системный звук — пусть клавиши громкости в приложении меняют именно его.
        volumeControlStream = AudioManager.STREAM_SYSTEM
        setContent {
            AppTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()
                val saved by viewModel.savedDevice.collectAsStateWithLifecycle()
                val appSettings by viewModel.appSettings.collectAsStateWithLifecycle()
                val bluetooth = rememberBlePrerequisite()
                // Виртуальным весам debug-сборки Bluetooth и разрешения не нужны.
                val app = application as OpenScalesApp
                val userRecipes by app.recipeStore.recipes.collectAsStateWithLifecycle()
                val virtual = app.isVirtual(state.address ?: saved?.address)
                val prerequisite = if (virtual) bluetooth.copy(value = BlePrerequisite.OK) else bluetooth
                val snackbar = remember { SnackbarHostState() }
                // Переживает поворот; при возврате с других экранов Activity не пересоздаётся — вкладка та же.
                var tab by rememberSaveable { mutableStateOf(MainTab.SCALE) }
                // На каждый показ экрана: если весы не подключены и попытка не идёт — пробуем заново,
                // чтобы пользователь не видел результат давно истёкшего таймаута.
                LifecycleEventEffect(Lifecycle.Event.ON_START) {
                    if (prerequisite.value == BlePrerequisite.OK) viewModel.autoConnect()
                    viewModel.setDashboardVisible(true)
                }
                LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.setDashboardVisible(false) }
                LaunchedEffect(prerequisite.value) {
                    if (prerequisite.value == BlePrerequisite.OK) viewModel.autoConnect()
                }
                LaunchedEffect(Unit) { viewModel.errors.collect { snackbar.showSnackbar(getString(it)) } }

                // Пока открыт главный экран, системный таймаут бездействия не гасит экран.
                // Флаг снимается при выходе из композиции, но на другие экраны она не диспозится:
                // там удержание кончается само — система игнорирует флаг у невидимого окна.
                val view = LocalView.current
                DisposableEffect(view, appSettings.keepScreenOn) {
                    view.keepScreenOn = appSettings.keepScreenOn
                    onDispose { view.keepScreenOn = false }
                }

                val onConnect = {
                    val device = saved
                    if (prerequisite.value == BlePrerequisite.OK && device != null) {
                        viewModel.connect(device.address, device.name, device.model)
                    } else {
                        open(ScanActivity::class.java)
                    }
                }

                DashboardScreen(
                    selectedTab = tab,
                    onSelectTab = { tab = it },
                    recipesTab = { padding ->
                        RecipesScreen(
                            recipes = BuiltInRecipes.all,
                            userRecipes = userRecipes,
                            state = state,
                            padding = padding,
                            // Без весов рецепт открывается как предпросмотр: «Тара» и «Далее» там неактивны.
                            onOpen = { recipe -> startActivity(BrewActivity.intent(this, recipe)) },
                            onEdit = { recipe -> startActivity(RecipeEditorActivity.edit(this, recipe)) },
                            onCopy = { recipe -> startActivity(RecipeEditorActivity.copy(this, recipe)) },
                            // Удаление должно дойти, даже если экран сразу закроют.
                            onDelete = { recipe -> app.appScope.launch { app.recipeStore.delete(recipe.id) } },
                            connectBanner = {
                                ConnectBanner(
                                    state = state,
                                    hasSavedDevice = saved != null,
                                    prerequisite = prerequisite.value,
                                    onConnect = onConnect,
                                    onOpenScan = { open(ScanActivity::class.java) },
                                    onRequestPermission = prerequisite.requestPermission,
                                    onEnableBluetooth = prerequisite.enableBluetooth,
                                    message = stringResource(R.string.recipes_need_scale),
                                )
                            },
                        )
                    },
                    recipesFab = { NewRecipeButton { startActivity(RecipeEditorActivity.newRecipe(this)) } },
                    state = state,
                    hasSavedDevice = saved != null,
                    snackbarHostState = snackbar,
                    onTare = viewModel::tare,
                    onToggleTimer = viewModel::toggleTimer,
                    onResetTimer = viewModel::resetTimer,
                    onConnect = onConnect,
                    onOpenScan = { open(ScanActivity::class.java) },
                    onOpenSettings = { open(SettingsActivity::class.java) },
                    prerequisite = prerequisite.value,
                    onRequestPermission = prerequisite.requestPermission,
                    onEnableBluetooth = prerequisite.enableBluetooth,
                    // Экран журнала есть только в debug source set.
                    triggerOnPress = appSettings.triggerOnPress,
                    slashedZero = appSettings.slashedZero,
                    onOpenJournal = if (BuildConfig.DEBUG) {
                        { startActivity(Intent().setClassName(this, "dev.openscales.debug.JournalActivity")) }
                    } else {
                        null
                    },
                )
            }
        }
    }

    private fun open(activity: Class<*>) = startActivity(Intent(this, activity))
}
