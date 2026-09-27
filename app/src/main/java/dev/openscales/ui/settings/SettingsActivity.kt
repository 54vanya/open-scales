package dev.openscales.ui.settings

import androidx.compose.runtime.getValue
import dev.openscales.ui.OpenScalesActivity
import android.media.AudioManager
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.openscales.OpenScalesApp
import dev.openscales.ui.ScaleViewModel
import dev.openscales.ui.theme.AppTheme

class SettingsActivity : OpenScalesActivity() {

    private val viewModel: ScaleViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // «Пик» идёт как системный звук — пусть клавиши громкости в приложении меняют именно его.
        volumeControlStream = AudioManager.STREAM_SYSTEM
        setContent {
            AppTheme {
                val appSettings by viewModel.appSettings.collectAsStateWithLifecycle()
                val app = application as OpenScalesApp
                // На смену языка и темы экран пересоздаётся (системой или приложением) и перечитывает выбор.
                val language = remember { app.languageStore.current() }
                val themeMode = remember { app.themeStore.current() }
                val ownColors by app.appearance.ownColors.collectAsStateWithLifecycle()
                SettingsScreen(
                    appSettings = appSettings,
                    language = language,
                    ownColors = ownColors,
                    themeMode = themeMode,
                    actions = SettingsActions(
                        onBack = ::finish,
                        onBeepEnabled = viewModel::setBeepEnabled,
                        onBeepNote = viewModel::setBeepNote,
                        onTriggerOnPress = viewModel::setTriggerOnPress,
                        onKeepScreenOn = viewModel::setKeepScreenOn,
                        onSyncTimer = viewModel::setSyncTimerWithScale,
                        onSlashedZero = viewModel::setSlashedZero,
                        onStepWeightMode = viewModel::setStepWeightMode,
                        onStepSignals = viewModel::setStepSignals,
                        onLanguage = app::setLanguage,
                        onOwnColors = app.appearance::setOwnColors,
                        onThemeMode = app::setThemeMode,
                    ),
                )
            }
        }
    }
}
