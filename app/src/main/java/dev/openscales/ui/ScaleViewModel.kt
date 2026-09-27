package dev.openscales.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.openscales.OpenScalesApp
import dev.openscales.data.AppSettings
import dev.openscales.data.BeepNote
import dev.openscales.data.SavedDevice
import dev.openscales.protocol.Precision
import dev.openscales.protocol.ScaleModel
import dev.openscales.protocol.Sensitivity
import dev.openscales.protocol.WeightUnit
import dev.openscales.recipe.StepWeightMode
import dev.openscales.session.ScaleSession
import dev.openscales.session.ScaleState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/** Общая ViewModel главного экрана и настроек: состояние весов + команды. */
class ScaleViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as OpenScalesApp
    private val repository = app.repository

    val state: StateFlow<ScaleState> = repository.state
    val savedDevice: StateFlow<SavedDevice?> = repository.savedDevice

    /** Ошибки команд — строковые ресурсы: текст на языке интерфейса выбирает Activity. */
    private val _errors = Channel<Int>(Channel.BUFFERED)
    val errors: Flow<Int> = _errors.receiveAsFlow()

    fun autoConnect() = repository.autoConnect()

    fun connect(address: String, name: String, model: ScaleModel) = repository.connect(address, name, model)

    val appSettings: StateFlow<AppSettings> get() = app.buttonSound.settings

    /** Главный экран на виду: пока он открыт, звуковой тракт держим готовым. */
    fun setDashboardVisible(visible: Boolean) = app.buttonSound.setDashboardVisible(visible)

    // Сигнал — до команды: звук не ждёт BLE-обмена.
    fun tare() {
        app.buttonSound.onControlPressed()
        command { tare() }
    }

    // Секундомер ведёт приложение: кнопки работают и без весов, ошибки соединения тут не при чём.
    fun toggleTimer() {
        app.buttonSound.onControlPressed()
        launchReporting { repository.toggleTimer() }
    }

    fun resetTimer() {
        app.buttonSound.onControlPressed()
        launchReporting { repository.resetTimer() }
    }

    fun setBeepEnabled(enabled: Boolean) {
        app.appScope.launch { app.appSettingsStore.setBeepEnabled(enabled) }
    }

    fun setKeepScreenOn(keep: Boolean) {
        app.appScope.launch { app.appSettingsStore.setKeepScreenOn(keep) }
    }

    fun setSlashedZero(slashed: Boolean) {
        app.appScope.launch { app.appSettingsStore.setSlashedZero(slashed) }
    }

    fun setSyncTimerWithScale(sync: Boolean) {
        app.appScope.launch { app.appSettingsStore.setSyncTimerWithScale(sync) }
    }

    fun setTriggerOnPress(onPress: Boolean) {
        app.appScope.launch { app.appSettingsStore.setTriggerOnPress(onPress) }
    }

    fun setStepWeightMode(mode: StepWeightMode) {
        app.appScope.launch { app.appSettingsStore.setStepWeightMode(mode) }
    }

    fun setStepSignals(enabled: Boolean) {
        app.appScope.launch { app.appSettingsStore.setStepSignals(enabled) }
    }

    fun setBeepNote(note: BeepNote) {
        app.buttonSound.preview(note)
        app.appScope.launch { app.appSettingsStore.setBeepNote(note) }
    }

    fun loadSettings() = command { loadSettings() }
    fun setUnit(unit: WeightUnit) = command { setUnit(unit) }
    fun setSound(enabled: Boolean) = command { setSound(enabled) }
    fun setSensitivity(value: Sensitivity) = command { setSensitivity(value) }
    fun setPrecision(value: Precision) = command { setPrecision(value) }
    fun setStandbyMinutes(minutes: Int) = command { setStandbyMinutes(minutes) }
    fun setBrightness(percent: Int) = command { setBrightness(percent) }
    fun rename(name: String) = command { rename(name) }
    fun powerOff() = command { powerOff() }
    fun factoryReset() = command { factoryReset() }

    fun disconnect() = launchReporting { repository.disconnect() }
    /** В скоупе приложения: экран настроек закрывается сразу, а забывание должно дойти до конца. */
    fun forget() {
        app.appScope.launch { runCatching { repository.forget() } }
    }

    private fun command(block: suspend ScaleSession.() -> Unit) = launchReporting { repository.withSession(block) }

    private fun launchReporting(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                commandErrorRes(e, state.value.isReady)?.let { _errors.trySend(it) }
            }
        }
    }
}
