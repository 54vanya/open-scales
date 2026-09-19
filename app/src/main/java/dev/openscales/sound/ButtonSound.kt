package dev.openscales.sound

import dev.openscales.data.AppSettings
import dev.openscales.data.AppSettingsStore
import dev.openscales.data.BeepNote
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/** Решает, звучать ли кнопкам, и на какой ноте — по настройкам приложения. */
class ButtonSound(
    scope: CoroutineScope,
    store: AppSettingsStore,
    private val beeper: Beeper,
) {
    val settings: StateFlow<AppSettings> = store.settings.stateIn(scope, SharingStarted.Eagerly, AppSettings())

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
