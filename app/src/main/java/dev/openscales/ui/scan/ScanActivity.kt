package dev.openscales.ui.scan

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.openscales.ui.ScaleViewModel
import dev.openscales.ui.rememberBlePrerequisite
import dev.openscales.ui.theme.OpenScalesTheme

class ScanActivity : ComponentActivity() {

    private val scaleViewModel: ScaleViewModel by viewModels()
    private val scanViewModel: ScanViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            OpenScalesTheme {
                val scale by scaleViewModel.state.collectAsStateWithLifecycle()
                val saved by scaleViewModel.savedDevice.collectAsStateWithLifecycle()
                val scan by scanViewModel.state.collectAsStateWithLifecycle()
                val prerequisite = rememberBlePrerequisite()

                LaunchedEffect(prerequisite.value) {
                    if (prerequisite.value == BlePrerequisite.OK) scanViewModel.start()
                }
                LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
                    if (prerequisite.value == BlePrerequisite.OK) scanViewModel.start()
                }
                LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { scanViewModel.stop() }
                // Как только выбранные здесь весы готовы — возвращаемся на главный экран.
                var selected by rememberSaveable { mutableStateOf<String?>(null) }
                LaunchedEffect(scale.isReady, scale.address, selected) {
                    if (scale.isReady && selected != null && scale.address.equals(selected, ignoreCase = true)) finish()
                }

                ScanScreen(
                    prerequisite = prerequisite.value,
                    scan = scan,
                    scale = scale,
                    saved = saved,
                    onBack = ::finish,
                    onRequestPermission = prerequisite.requestPermission,
                    onEnableBluetooth = prerequisite.enableBluetooth,
                    onStartScan = scanViewModel::start,
                    onStopScan = scanViewModel::stop,
                    onSelect = { address, name, model ->
                        scanViewModel.stop()
                        selected = address
                        scaleViewModel.connect(address, name, model)
                    },
                )
            }
        }
    }
}
