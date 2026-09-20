package dev.openscales

import android.content.Intent
import android.media.AudioManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.openscales.ui.ScaleViewModel
import dev.openscales.ui.dashboard.DashboardScreen
import dev.openscales.ui.rememberBlePrerequisite
import dev.openscales.ui.scan.BlePrerequisite
import dev.openscales.ui.scan.ScanActivity
import dev.openscales.ui.settings.SettingsActivity
import dev.openscales.ui.theme.OpenScalesTheme

/**
 * Главный экран. Поиск и настройки — отдельные Activity, чтобы переходы и предиктивный
 * жест «назад» анимировала сама система.
 */
class MainActivity : ComponentActivity() {

    private val viewModel: ScaleViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // «Пик» идёт как системный звук — пусть клавиши громкости в приложении меняют именно его.
        volumeControlStream = AudioManager.STREAM_SYSTEM
        setContent {
            OpenScalesTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()
                val saved by viewModel.savedDevice.collectAsStateWithLifecycle()
                val appSettings by viewModel.appSettings.collectAsStateWithLifecycle()
                val prerequisite = rememberBlePrerequisite()
                val snackbar = remember { SnackbarHostState() }
                // На каждый показ экрана: если весы не подключены и попытка не идёт — пробуем заново,
                // чтобы пользователь не видел результат давно истёкшего таймаута.
                LifecycleEventEffect(Lifecycle.Event.ON_START) {
                    if (prerequisite.value == BlePrerequisite.OK) viewModel.autoConnect()
                }
                LaunchedEffect(prerequisite.value) {
                    if (prerequisite.value == BlePrerequisite.OK) viewModel.autoConnect()
                }
                LaunchedEffect(Unit) { viewModel.errors.collect { snackbar.showSnackbar(it) } }

                // Пока открыт главный экран, системный таймаут бездействия не гасит экран.
                // Флаг снимается при выходе из композиции, но на другие экраны она не диспозится:
                // там удержание кончается само — система игнорирует флаг у невидимого окна.
                val view = LocalView.current
                DisposableEffect(view, appSettings.keepScreenOn) {
                    view.keepScreenOn = appSettings.keepScreenOn
                    onDispose { view.keepScreenOn = false }
                }

                DashboardScreen(
                    state = state,
                    hasSavedDevice = saved != null,
                    snackbarHostState = snackbar,
                    onTare = viewModel::tare,
                    onToggleTimer = viewModel::toggleTimer,
                    onResetTimer = viewModel::resetTimer,
                    onConnect = {
                        val device = saved
                        if (prerequisite.value == BlePrerequisite.OK && device != null) {
                            viewModel.connect(device.address, device.name, device.model)
                        } else {
                            open(ScanActivity::class.java)
                        }
                    },
                    onOpenScan = { open(ScanActivity::class.java) },
                    onOpenSettings = { open(SettingsActivity::class.java) },
                    // Экран журнала есть только в debug source set.
                    triggerOnPress = appSettings.triggerOnPress,
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
