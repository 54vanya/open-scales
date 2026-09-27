package dev.openscales.ui.scan

import dev.openscales.ui.OpenScalesActivity
import dev.openscales.ui.scale.ScaleDetailsActivity
import dev.openscales.ui.scale.scaleLink
import android.os.Bundle
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
import dev.openscales.OpenScalesApp
import dev.openscales.ui.ScaleViewModel
import dev.openscales.ui.rememberBlePrerequisite
import dev.openscales.ui.theme.AppTheme

class ScanActivity : OpenScalesActivity() {

    private val scaleViewModel: ScaleViewModel by viewModels()
    private val scanViewModel: ScanViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            AppTheme {
                val scale by scaleViewModel.state.collectAsStateWithLifecycle()
                val saved by scaleViewModel.savedDevice.collectAsStateWithLifecycle()
                val scan by scanViewModel.state.collectAsStateWithLifecycle()
                val prerequisite = rememberBlePrerequisite()
                val simulator = (application as OpenScalesApp).simulator

                val autoScan = prerequisite.value == BlePrerequisite.OK && shouldAutoScan(scale.isReady)
                LaunchedEffect(prerequisite.value) { if (autoScan) scanViewModel.start() }
                LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { if (autoScan) scanViewModel.start() }
                LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { scanViewModel.stop() }
                // Новые весы из «Найденные» подключились — возвращаемся на главный экран. Запомненные весы
                // подключаются без возврата: пользователь пришёл сюда, возможно, за их карточкой.
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
                    onSavedClick = { device ->
                        when (savedRowAction(scaleLink(device.address, scale))) {
                            SavedRowAction.CONNECT -> {
                                scanViewModel.stop()
                                scaleViewModel.connect(device.address, device.name, device.model)
                            }
                            SavedRowAction.OPEN_DETAILS ->
                                startActivity(ScaleDetailsActivity.intent(this@ScanActivity, device.address))
                        }
                    },
                    onOpenScale = { startActivity(ScaleDetailsActivity.intent(this@ScanActivity, it)) },
                    virtualScale = simulator?.device,
                    onVirtualClick = {
                        simulator?.device?.let { device ->
                            when (savedRowAction(scaleLink(device.address, scale))) {
                                SavedRowAction.CONNECT -> {
                                    scanViewModel.stop()
                                    selected = device.address
                                    scaleViewModel.connect(device.address, device.name, device.model)
                                }
                                SavedRowAction.OPEN_DETAILS ->
                                    startActivity(ScaleDetailsActivity.intent(this@ScanActivity, device.address))
                            }
                        }
                    },
                )
            }
        }
    }
}
