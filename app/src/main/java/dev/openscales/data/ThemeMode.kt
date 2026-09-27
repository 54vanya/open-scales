package dev.openscales.data

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import androidx.annotation.RequiresApi

/** Тема приложения: как в системе, светлая или тёмная. */
enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK;

    /** Режим ночи для `UiModeManager.setApplicationNightMode`. */
    fun toNightMode(): Int = when (this) {
        SYSTEM -> UiModeManager.MODE_NIGHT_AUTO
        LIGHT -> UiModeManager.MODE_NIGHT_NO
        DARK -> UiModeManager.MODE_NIGHT_YES
    }

    /** `uiMode` конфигурации с подменённым признаком ночи; для «как в системе» — без изменений. */
    fun applyTo(uiMode: Int): Int = when (this) {
        SYSTEM -> uiMode
        LIGHT -> (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or Configuration.UI_MODE_NIGHT_NO
        DARK -> (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or Configuration.UI_MODE_NIGHT_YES
    }

    companion object {
        fun fromNightMode(mode: Int): ThemeMode = when (mode) {
            UiModeManager.MODE_NIGHT_NO -> LIGHT
            UiModeManager.MODE_NIGHT_YES -> DARK
            else -> SYSTEM
        }

        fun fromName(name: String?): ThemeMode = entries.firstOrNull { it.name == name } ?: SYSTEM
    }
}

/** Где хранится выбор темы. */
interface ThemeModeStore {
    fun current(): ThemeMode
    fun set(mode: ThemeMode)
}

/**
 * Android 12+: система применяет ночной режим приложения сама — до первого кадра, к фону окна, заставке и значкам
 * системных панелей — и сама пересоздаёт экраны при смене. Прочитать выбранный режим публичного API нет,
 * а менять его может только приложение, поэтому выбор для показа в настройках хранится в [local].
 */
@RequiresApi(31)
class SystemThemeModeStore(context: Context, private val local: ThemeModeStore) : ThemeModeStore {
    private val manager = context.getSystemService(UiModeManager::class.java)

    override fun current(): ThemeMode = local.current()

    override fun set(mode: ThemeMode) {
        local.set(mode)
        manager.setApplicationNightMode(mode.toNightMode())
    }
}

/**
 * Android 8–11: системного ночного режима приложения нет. Выбор хранится у приложения и подставляется
 * в `uiMode` контекста каждого экрана — см. [withAppConfiguration].
 */
class LocalThemeModeStore(
    private val read: () -> String?,
    private val write: (String) -> Unit,
) : ThemeModeStore {
    override fun current(): ThemeMode = ThemeMode.fromName(read())

    override fun set(mode: ThemeMode) = write(mode.name)
}
