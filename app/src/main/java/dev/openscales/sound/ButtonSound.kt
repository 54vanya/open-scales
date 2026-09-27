package dev.openscales.sound

import dev.openscales.data.AppSettings
import dev.openscales.data.AppSettingsStore
import dev.openscales.data.BeepNote
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Решает, звучать ли кнопкам, и на какой ноте — по настройкам приложения. */
class ButtonSound(
    private val scope: CoroutineScope,
    store: AppSettingsStore,
    private val beeper: Beeper,
) {
    val settings: StateFlow<AppSettings> = store.settings.stateIn(scope, SharingStarted.Eagerly, AppSettings())

    private var visibleDashboards = 0

    init {
        scope.launch { settings.collect { updateWarmUp() } }
    }

    /**
     * Главный экран на виду — держим аудиовыход готовым, чтобы «пик» не ждал выхода тракта из standby.
     * Вне главного экрана удержание снимается: батарею тратить не за что.
     *
     * Считаем показы, а не последнее событие: при пересоздании экрана `ON_STOP` старого экземпляра
     * приходит уже после `ON_START` нового, и по последнему событию удержание бы снялось.
     */
    fun setDashboardVisible(visible: Boolean) {
        visibleDashboards = (visibleDashboards + if (visible) 1 else -1).coerceAtLeast(0)
        updateWarmUp()
    }

    private fun updateWarmUp() {
        val s = settings.value
        beeper.warm(visibleDashboards > 0 && (s.beepEnabled || s.stepSignals))
    }

    /** Нажатие кнопки управления весами. */
    fun onControlPressed() {
        val s = settings.value
        if (s.beepEnabled) beeper.beep(s.beepNote)
    }

    /**
     * Сигнал хода рецепта на экране варки — по своей настройке, независимо от звука кнопок:
     * один короткий на смене шага, два — при наборе цели шага.
     */
    fun onStepSignal(signal: StepSignal) {
        val s = settings.value
        if (!s.stepSignals) return
        beeper.beep(s.beepNote)
        if (signal == StepSignal.TARGET) {
            scope.launch {
                delay(DOUBLE_BEEP_GAP_MS)
                beeper.beep(s.beepNote)
            }
        }
    }

    /** Пример звука при выборе ноты в настройках (выбор ноты доступен только при включённом звуке). */
    fun preview(note: BeepNote) {
        if (settings.value.beepEnabled) beeper.beep(note)
    }

    private companion object {
        const val DOUBLE_BEEP_GAP_MS = 150L
    }
}

/** Сигнал экрана варки: [STEP] — сменился шаг или кончилось время, [TARGET] — набрана цель шага. */
enum class StepSignal { STEP, TARGET }
