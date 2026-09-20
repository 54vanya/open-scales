package dev.openscales.sound

import dev.openscales.data.AppSettings
import dev.openscales.data.AppSettingsStore
import dev.openscales.data.BeepNote
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Решает, звучать ли кнопкам, и на какой ноте — по настройкам приложения. */
class ButtonSound(
    scope: CoroutineScope,
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
        beeper.warm(visibleDashboards > 0 && settings.value.beepEnabled)
    }

    /** Нажатие кнопки управления весами. */
    fun onControlPressed() {
        val s = settings.value
        if (s.beepEnabled) beeper.beep(s.beepNote)
    }

    /** Пример звука при выборе ноты в настройках (выбор ноты доступен только при включённом звуке). */
    fun preview(note: BeepNote) {
        if (settings.value.beepEnabled) beeper.beep(note)
    }
}
