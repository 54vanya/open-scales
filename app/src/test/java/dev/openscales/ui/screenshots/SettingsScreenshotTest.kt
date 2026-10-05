package dev.openscales.ui.screenshots

import dev.openscales.data.AppSettings
import dev.openscales.ui.settings.SettingsActions
import dev.openscales.ui.settings.SettingsScreen
import org.junit.Test

/** Настройки приложения в тёмной теме: значки всех строк видны на тёмном фоне. */
class SettingsScreenshotTest : ScreenshotTest() {

    @Test
    fun settingsDark() = snapshot("settings_dark", dark = true) {
        SettingsScreen(appSettings = AppSettings(), actions = SettingsActions({}))
    }
}
