package dev.openscales.ui

import android.content.Context
import android.os.Build
import androidx.activity.ComponentActivity
import dev.openscales.OpenScalesApp
import dev.openscales.data.withAppConfiguration

/**
 * Базовый экран приложения. Язык приложения система применяет сама с Android 13, тему — с Android 12;
 * на более старых версиях выбранные язык и тема подставляются в контекст каждого экрана здесь.
 */
open class OpenScalesActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        val app = newBase.applicationContext as OpenScalesApp
        val language = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) app.languageStore.current() else null
        val theme = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) app.themeStore.current() else null
        super.attachBaseContext(newBase.withAppConfiguration(language, theme))
    }
}
