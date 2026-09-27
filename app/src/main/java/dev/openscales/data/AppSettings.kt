package dev.openscales.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.openscales.recipe.StepWeightMode
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
    /** Перечёркнутый ноль в цифрах показаний. */
    val slashedZero: Boolean = true,
    /** Как показывать воду в шагах рецепта. */
    val stepWeightMode: StepWeightMode = StepWeightMode.DEFAULT,
    /** Звуковые сигналы смены шага и набора цели на экране варки. */
    val stepSignals: Boolean = true,
)

interface AppSettingsStore {
    val settings: Flow<AppSettings>
    suspend fun setBeepEnabled(enabled: Boolean)
    suspend fun setBeepNote(note: BeepNote)
    suspend fun setTriggerOnPress(onPress: Boolean)
    suspend fun setKeepScreenOn(keep: Boolean)
    suspend fun setSyncTimerWithScale(sync: Boolean)
    suspend fun setSlashedZero(slashed: Boolean)
    suspend fun setStepWeightMode(mode: StepWeightMode)
    suspend fun setStepSignals(enabled: Boolean)
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
            slashedZero = p[SLASHED_ZERO] ?: true,
            stepWeightMode = StepWeightMode.fromName(p[STEP_WEIGHT_MODE]),
            stepSignals = p[STEP_SIGNALS] ?: true,
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

    override suspend fun setSlashedZero(slashed: Boolean) {
        store.edit { it[SLASHED_ZERO] = slashed }
    }

    override suspend fun setStepWeightMode(mode: StepWeightMode) {
        store.edit { it[STEP_WEIGHT_MODE] = mode.name }
    }

    override suspend fun setStepSignals(enabled: Boolean) {
        store.edit { it[STEP_SIGNALS] = enabled }
    }

    private companion object {
        val BEEP_ENABLED = booleanPreferencesKey("beep_enabled")
        val BEEP_NOTE = stringPreferencesKey("beep_note")
        val TRIGGER_ON_PRESS = booleanPreferencesKey("trigger_on_press")
        val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        val SYNC_TIMER = booleanPreferencesKey("timer_sync")
        val SLASHED_ZERO = booleanPreferencesKey("slashed_zero")
        val STEP_WEIGHT_MODE = stringPreferencesKey("step_weight_mode")
        val STEP_SIGNALS = booleanPreferencesKey("step_signals")
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

    override suspend fun setSlashedZero(slashed: Boolean) {
        state.value = state.value.copy(slashedZero = slashed)
    }

    override suspend fun setStepWeightMode(mode: StepWeightMode) {
        state.value = state.value.copy(stepWeightMode = mode)
    }

    override suspend fun setStepSignals(enabled: Boolean) {
        state.value = state.value.copy(stepSignals = enabled)
    }
}
