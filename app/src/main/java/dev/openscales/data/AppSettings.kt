package dev.openscales.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** Ноты сигнала кнопок — шестая октава, равномерная темперация (A4 = 440 Гц). */
enum class BeepNote(val hz: Int) {
    C6(1047), D6(1175), E6(1319), F6(1397), G6(1568), A6(1760), B6(1976);

    companion object {
        val DEFAULT = A6

        fun fromName(name: String?): BeepNote = entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}

/** Настройки самого приложения (не весов). */
data class AppSettings(
    val beepEnabled: Boolean = true,
    val beepNote: BeepNote = BeepNote.DEFAULT,
    /** Кнопки управления срабатывают при касании (как физические кнопки весов), иначе — при отпускании. */
    val triggerOnPress: Boolean = true,
    /** Не гасить экран, пока открыт главный экран. */
    val keepScreenOn: Boolean = true,
    /** Показывать таймер весов вместо собственного секундомера приложения. */
    val syncTimerWithScale: Boolean = false,
)

interface AppSettingsStore {
    val settings: Flow<AppSettings>
    suspend fun setBeepEnabled(enabled: Boolean)
    suspend fun setBeepNote(note: BeepNote)
    suspend fun setTriggerOnPress(onPress: Boolean)
    suspend fun setKeepScreenOn(keep: Boolean)
    suspend fun setSyncTimerWithScale(sync: Boolean)
}

class DataStoreAppSettingsStore(context: Context) : AppSettingsStore {
    private val store = context.applicationContext.dataStore

    override val settings: Flow<AppSettings> = store.data.map { p ->
        AppSettings(
            beepEnabled = p[BEEP_ENABLED] ?: true,
            beepNote = BeepNote.fromName(p[BEEP_NOTE]),
            triggerOnPress = p[TRIGGER_ON_PRESS] ?: true,
            keepScreenOn = p[KEEP_SCREEN_ON] ?: true,
            syncTimerWithScale = p[SYNC_TIMER] ?: false,
        )
    }

    override suspend fun setBeepEnabled(enabled: Boolean) {
        store.edit { it[BEEP_ENABLED] = enabled }
    }

    override suspend fun setBeepNote(note: BeepNote) {
        store.edit { it[BEEP_NOTE] = note.name }
    }

    override suspend fun setTriggerOnPress(onPress: Boolean) {
        store.edit { it[TRIGGER_ON_PRESS] = onPress }
    }

    override suspend fun setKeepScreenOn(keep: Boolean) {
        store.edit { it[KEEP_SCREEN_ON] = keep }
    }

    override suspend fun setSyncTimerWithScale(sync: Boolean) {
        store.edit { it[SYNC_TIMER] = sync }
    }

    private companion object {
        val BEEP_ENABLED = booleanPreferencesKey("beep_enabled")
        val BEEP_NOTE = stringPreferencesKey("beep_note")
        val TRIGGER_ON_PRESS = booleanPreferencesKey("trigger_on_press")
        val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        val SYNC_TIMER = booleanPreferencesKey("timer_sync")
    }
}

/** Для тестов и превью. */
class InMemoryAppSettingsStore(initial: AppSettings = AppSettings()) : AppSettingsStore {
    private val state = MutableStateFlow(initial)
    override val settings: Flow<AppSettings> = state

    override suspend fun setBeepEnabled(enabled: Boolean) {
        state.value = state.value.copy(beepEnabled = enabled)
    }

    override suspend fun setBeepNote(note: BeepNote) {
        state.value = state.value.copy(beepNote = note)
    }

    override suspend fun setTriggerOnPress(onPress: Boolean) {
        state.value = state.value.copy(triggerOnPress = onPress)
    }

    override suspend fun setKeepScreenOn(keep: Boolean) {
        state.value = state.value.copy(keepScreenOn = keep)
    }

    override suspend fun setSyncTimerWithScale(sync: Boolean) {
        state.value = state.value.copy(syncTimerWithScale = sync)
    }
}
