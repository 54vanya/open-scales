package dev.openscales.data

import android.app.UiModeManager
import android.content.res.Configuration
import org.junit.Assert.assertEquals
import org.junit.Test

class ThemeModeTest {

    private val lightNormal = Configuration.UI_MODE_TYPE_NORMAL or Configuration.UI_MODE_NIGHT_NO
    private val darkNormal = Configuration.UI_MODE_TYPE_NORMAL or Configuration.UI_MODE_NIGHT_YES

    @Test
    fun `night mode round trip`() {
        for (mode in ThemeMode.entries) assertEquals(mode, ThemeMode.fromNightMode(mode.toNightMode()))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromNightMode(UiModeManager.MODE_NIGHT_CUSTOM))
    }

    @Test
    fun `ui mode keeps the type and replaces only the night bits`() {
        assertEquals(darkNormal, ThemeMode.DARK.applyTo(lightNormal))
        assertEquals(lightNormal, ThemeMode.LIGHT.applyTo(darkNormal))
        assertEquals(darkNormal, ThemeMode.SYSTEM.applyTo(darkNormal))
        assertEquals(lightNormal, ThemeMode.SYSTEM.applyTo(lightNormal))
    }

    @Test
    fun `local store keeps the choice`() {
        var saved: String? = null
        val store = LocalThemeModeStore(read = { saved }, write = { saved = it })
        assertEquals(ThemeMode.SYSTEM, store.current())
        store.set(ThemeMode.DARK)
        assertEquals(ThemeMode.DARK, store.current())
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromName("garbage"))
    }
}
