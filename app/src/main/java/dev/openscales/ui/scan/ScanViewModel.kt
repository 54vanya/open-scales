package dev.openscales.ui.scan

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.openscales.OpenScalesApp
import dev.openscales.ble.DiscoveredScale
import dev.openscales.ui.ScanError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

data class ScanUiState(
    val scanning: Boolean = false,
    val devices: List<DiscoveredScale> = emptyList(),
    val error: ScanError? = null,
)

class ScanViewModel(application: Application) : AndroidViewModel(application) {

    private val scanner = (application as OpenScalesApp).scanner
    private val _state = MutableStateFlow(ScanUiState())
    val state: StateFlow<ScanUiState> = _state.asStateFlow()
    private var job: Job? = null

    fun start() {
        if (job?.isActive == true) return
        _state.update { it.copy(scanning = true, error = null) }
        job = viewModelScope.launch {
            try {
                withTimeoutOrNull(SCAN_DURATION_MS) {
                    scanner.scan().collect { found ->
                        _state.update { s ->
                            val others = s.devices.filterNot { it.address == found.address }
                            s.copy(devices = (others + found).sortedByDescending { it.rssi })
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(error = ScanError.of(e)) }
            } finally {
                _state.update { it.copy(scanning = false) }
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    private companion object {
        const val SCAN_DURATION_MS = 30_000L
    }
}
