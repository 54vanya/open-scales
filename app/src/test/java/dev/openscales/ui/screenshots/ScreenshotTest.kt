package dev.openscales.ui.screenshots

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import dev.openscales.ui.theme.OpenScalesTheme
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Основа скриншот-тестов одобренных экранов: Compose рисуется в JVM (Robolectric, настоящая графика) и
 * сравнивается с эталоном в `app/src/test/screenshots`. Эталоны пишет `recordRoborazziDebug`, сверяет
 * `verifyRoborazziDebug`. Телефон по умолчанию — 411×914 dp, русский, светлая тема, собственные цвета приложения.
 * Приложение — простой [Application]: `OpenScalesApp` поднимал бы Bluetooth и хранилища.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, qualifiers = ScreenshotTest.PHONE)
abstract class ScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    /** Снимок содержимого экрана в `<name>.png`. */
    protected fun snapshot(name: String, dark: Boolean = false, content: @Composable () -> Unit) {
        compose.setContent { OpenScalesTheme(darkTheme = dark, dynamicColor = false, content = content) }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("$DIR/$name.png")
    }

    /** Снимок всего экрана вместе с окнами поверх (диалоги) в `<name>.png`. */
    protected fun screen(name: String, dark: Boolean = false, content: @Composable () -> Unit) {
        compose.setContent { OpenScalesTheme(darkTheme = dark, dynamicColor = false, content = content) }
        compose.waitForIdle()
        captureScreenRoboImage("$DIR/$name.png")
    }

    companion object {
        const val DIR = "src/test/screenshots"
        const val PHONE = "ru-w411dp-h914dp-xxhdpi"
        const val NARROW = "ru-w320dp-h780dp-xxhdpi"
    }
}
