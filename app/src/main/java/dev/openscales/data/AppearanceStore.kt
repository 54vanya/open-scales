package dev.openscales.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Настройки оформления, которые нужны уже к первому кадру: читаются синхронно (`SharedPreferences`),
 * а не из DataStore — иначе запуск на миг показывал бы не ту палитру.
 */
class AppearanceStore(
    private val read: (key: String) -> Boolean?,
    private val write: (key: String, value: Boolean) -> Unit,
) {
    private val _ownColors = MutableStateFlow(read(KEY_OWN_COLORS) ?: true)

    /** Собственная палитра приложения (по умолчанию) или, на Android 12+, цвета обоев. */
    val ownColors: StateFlow<Boolean> = _ownColors.asStateFlow()

    fun setOwnColors(enabled: Boolean) {
        write(KEY_OWN_COLORS, enabled)
        _ownColors.value = enabled
    }

    private companion object {
        const val KEY_OWN_COLORS = "own_colors"
    }
}
