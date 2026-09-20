package dev.openscales.ui.settings

import android.media.AudioManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.openscales.ui.ScaleViewModel
import dev.openscales.ui.theme.OpenScalesTheme

class SettingsActivity : ComponentActivity() {

    private val viewModel: ScaleViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // «Пик» идёт как системный звук — пусть клавиши громкости в приложении меняют именно его.
        volumeControlStream = AudioManager.STREAM_SYSTEM
        setContent {
            OpenScalesTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()
                val appSettings by viewModel.appSettings.collectAsStateWithLifecycle()
                val snackbar = remember { SnackbarHostState() }
                LaunchedEffect(state.isReady) { if (state.isReady) viewModel.loadSettings() }
                LaunchedEffect(Unit) { viewModel.errors.collect { snackbar.showSnackbar(it) } }

                SettingsScreen(
                    state = state,
                    appSettings = appSettings,
                    snackbarHostState = snackbar,
                    actions = SettingsActions(
                        onBack = ::finish,
                        onUnit = viewModel::setUnit,
                        onSound = viewModel::setSound,
                        onSensitivity = viewModel::setSensitivity,
                        onPrecision = viewModel::setPrecision,
                        onStandby = viewModel::setStandbyMinutes,
                        onBrightness = viewModel::setBrightness,
                        onRename = viewModel::rename,
                        onDisconnect = viewModel::disconnect,
                        onPowerOff = viewModel::powerOff,
                        onFactoryReset = viewModel::factoryReset,
                        onForget = {
                            viewModel.forget()
                            finish()
                        },
                        onBeepEnabled = viewModel::setBeepEnabled,
                        onBeepNote = viewModel::setBeepNote,
                        onTriggerOnPress = viewModel::setTriggerOnPress,
                        onKeepScreenOn = viewModel::setKeepScreenOn,
                        onSyncTimer = viewModel::setSyncTimerWithScale,
                    ),
                )
            }
        }
    }
}
