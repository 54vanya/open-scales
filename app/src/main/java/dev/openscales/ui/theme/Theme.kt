package dev.openscales.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import dev.openscales.OpenScalesApp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import dev.openscales.R
import androidx.compose.ui.unit.sp

// Запасная палитра для Android < 12: тёплый «кофейный» акцент.
private val LightColors = lightColorScheme(
    primary = Color(0xFF8B4F24),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDBC8),
    onPrimaryContainer = Color(0xFF331200),
    secondary = Color(0xFF765848),
    secondaryContainer = Color(0xFFFFDBC8),
    tertiary = Color(0xFF636032),
    tertiaryContainer = Color(0xFFEAE4AA),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFFB68B),
    onPrimary = Color(0xFF522300),
    primaryContainer = Color(0xFF6E380F),
    onPrimaryContainer = Color(0xFFFFDBC8),
    secondary = Color(0xFFE6BEAB),
    secondaryContainer = Color(0xFF5C4032),
    tertiary = Color(0xFFCDC890),
    tertiaryContainer = Color(0xFF4B4800),
)

@Composable
fun OpenScalesTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colors = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialExpressiveTheme(
        colorScheme = colors,
        motionScheme = MotionScheme.expressive(),
        content = content,
    )
}

/** Тема экранов приложения: собственная палитра или цвета обоев — по настройке «Собственные цвета приложения». */
@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val app = LocalContext.current.applicationContext as OpenScalesApp
    val ownColors by app.appearance.ownColors.collectAsState()
    OpenScalesTheme(dynamicColor = !ownColors, content = content)
}

/**
 * Шрифт цифр показаний — урезанный Google Sans Flex (`tools/make-digits-font.sh`), одинаковый на всех телефонах:
 * в системном Roboto нет перечёркнутого нуля. Оптический размер Compose сам не выставляет — задаём по номиналу.
 */
private fun digitsFamily(opticalSize: Float) = FontFamily(
    Font(
        R.font.readout_digits,
        FontWeight.Medium,
        variationSettings = FontVariation.Settings(
            FontVariation.weight(FontWeight.Medium.weight),
            FontVariation.Setting("opsz", opticalSize),
        ),
    ),
)

/** Цифры таймера и веса: табличные, чтобы значение не «прыгало» при смене цифр. */
val WeightTextStyle = TextStyle(
    fontSize = 88.sp,
    lineHeight = 96.sp,
    fontFamily = digitsFamily(88f),
    fontWeight = FontWeight.Medium,
    fontFeatureSettings = digitFeatures(slashedZero = true),
    letterSpacing = (-1).sp,
)

/** Единицы измерения рядом со значениями: «g» у веса и «g/s» у потока — одним начертанием. */
val UnitTextStyle = TextStyle(
    fontSize = 28.sp,
    lineHeight = 32.sp,
    fontWeight = FontWeight.Normal,
)

/** Цифры потока — мельче таймера и веса, тот же шрифт. */
val DigitsTextStyle = TextStyle(
    fontSize = 32.sp,
    lineHeight = 40.sp,
    fontFamily = digitsFamily(32f),
    fontWeight = FontWeight.Medium,
    fontFeatureSettings = digitFeatures(slashedZero = true),
)

/** OpenType-фичи цифр: табличные всегда, перечёркнутый ноль — по настройке (ширина глифа та же). */
fun digitFeatures(slashedZero: Boolean) = if (slashedZero) "tnum, zero" else "tnum"

/** Стиль цифр с нулём по настройке «Перечёркнутый ноль». */
fun TextStyle.withSlashedZero(slashedZero: Boolean) = copy(fontFeatureSettings = digitFeatures(slashedZero))
