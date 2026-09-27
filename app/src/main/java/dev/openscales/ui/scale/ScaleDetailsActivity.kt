package dev.openscales.ui.scale

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.openscales.ui.OpenScalesActivity
import dev.openscales.ui.ScaleViewModel
import dev.openscales.ui.theme.AppTheme

/** Карточка весов (адрес — в [EXTRA_ADDRESS]): сведения, настройки и действия одних конкретных весов. */
class ScaleDetailsActivity : OpenScalesActivity() {

    private val viewModel: ScaleViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val address = intent.getStringExtra(EXTRA_ADDRESS) ?: return finish()
        setContent {
            AppTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()
                val saved by viewModel.savedDevice.collectAsStateWithLifecycle()
                val snackbar = remember { SnackbarHostState() }
                val connected = scaleLink(address, state) == ScaleLink.CONNECTED
                // Настройки хранятся в самих весах: читаем при открытии и когда весы подключились при открытой карточке.
                LaunchedEffect(connected) { if (connected) viewModel.loadSettings() }
                LaunchedEffect(Unit) { viewModel.errors.collect { snackbar.showSnackbar(getString(it)) } }

                ScaleDetailsScreen(
                    address = address,
                    state = state,
                    saved = saved?.takeIf { it.address.equals(address, ignoreCase = true) },
                    snackbarHostState = snackbar,
                    actions = ScaleDetailsActions(
                        onBack = ::finish,
                        onConnect = {
                            saved?.takeIf { it.address.equals(address, ignoreCase = true) }
                                ?.let { viewModel.connect(it.address, it.name, it.model) }
                        },
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
                    ),
                )
            }
        }
    }

    companion object {
        const val EXTRA_ADDRESS = "address"

        fun intent(context: Context, address: String): Intent =
            Intent(context, ScaleDetailsActivity::class.java).putExtra(EXTRA_ADDRESS, address)
    }
}
