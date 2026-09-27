package dev.openscales.ui.components

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.ui.unit.sp
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.unit.dp
import dev.openscales.data.BeepNote
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.core.content.ContextCompat
import dev.openscales.R
import dev.openscales.protocol.WeightUnit
import dev.openscales.session.ConnectionError
import dev.openscales.session.ConnectionPhase
import java.util.Locale

object BlePermissions {
    val required: Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

    fun granted(context: Context): Boolean = required.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }
}

/**
 * Вес или поток с числом знаков по единице. Нет значения — заглушка из черт: [placeholderDigits] до точки
 * и по одной на каждый знак после (`−−−.−`); знак минуса в табличных цифрах шрифта показаний шириной ровно с цифру.
 */
fun formatWeight(value: Float?, unit: WeightUnit, placeholderDigits: Int = WEIGHT_PLACEHOLDER_DIGITS): String =
    if (value == null) {
        NO_DIGIT.repeat(placeholderDigits) + "." + NO_DIGIT.repeat(unit.decimals)
    } else {
        String.format(Locale.US, "%.${unit.decimals}f", value)
    }

private const val NO_DIGIT = "\u2212"

/** Черт до точки в заглушке веса. */
const val WEIGHT_PLACEHOLDER_DIGITS = 3

/** Черт до точки в заглушке потока. */
const val FLOW_PLACEHOLDER_DIGITS = 2

/** Время таймера `M:SS`, не длиннее 5 символов: всё, что ≥ 100 минут, показывается как `99:59`. */
fun formatTime(seconds: Int): String {
    val s = seconds.coerceIn(0, MAX_TIMER_SECONDS)
    return "%d:%02d".format(s / 60, s % 60)
}

const val MAX_TIMER_SECONDS = 99 * 60 + 59

/** Самое широкое значение таймера — один из шаблонов числовой колонки главного экрана. */
const val TIMER_WIDTH_TEMPLATE = "00:00"

/** Текст причины неудачи подключения. У [ConnectionError.Failed] подробность не показываем: она в журнале. */
@StringRes
fun ConnectionError.messageRes(): Int = when (this) {
    ConnectionError.NotTimemore -> R.string.error_not_timemore
    ConnectionError.PairingNotStarted -> R.string.error_pairing_not_started
    ConnectionError.PairingRejected -> R.string.error_pairing_rejected
    ConnectionError.PairingTimeout -> R.string.error_pairing_timeout
    ConnectionError.NoModel -> R.string.error_no_model
    ConnectionError.Lost -> R.string.error_link_lost
    ConnectionError.DroppedWhileConnecting -> R.string.error_dropped_while_connecting
    is ConnectionError.Failed -> R.string.error_connect_failed
}

/** Обозначение единицы веса на языке интерфейса (`g`/`г`); `WeightUnit.symbol` — техническое, для логов. */
@StringRes
fun WeightUnit.symbolRes(): Int = when (this) {
    WeightUnit.GRAM -> R.string.unit_gram_symbol
    WeightUnit.OUNCE -> R.string.unit_ounce_symbol
}

/** Обозначение единицы потока: собирать из единицы веса и «/s» нельзя — по-русски это «г/с». */
@StringRes
fun WeightUnit.flowSymbolRes(): Int = when (this) {
    WeightUnit.GRAM -> R.string.flow_unit_gram
    WeightUnit.OUNCE -> R.string.flow_unit_ounce
}

/**
 * Индикатор занятости — классический круговой спиннер Material 3 (выразительный `LoadingIndicator` с меняющейся
 * фигурой пользователю не понравился). Один размер во всём приложении.
 */
@Composable
fun BusyIndicator(modifier: Modifier = Modifier, color: Color = ProgressIndicatorDefaults.circularColor) {
    CircularProgressIndicator(modifier.size(BusyIndicatorSize), color = color, strokeWidth = 3.dp)
}

private val BusyIndicatorSize = 24.dp

@StringRes
fun ConnectionPhase.labelRes(): Int = when (this) {
    ConnectionPhase.DISCONNECTED -> R.string.phase_disconnected
    ConnectionPhase.CONNECTING -> R.string.phase_connecting
    ConnectionPhase.BONDING -> R.string.phase_bonding
    ConnectionPhase.SUBSCRIBING -> R.string.phase_subscribing
    ConnectionPhase.HANDSHAKING -> R.string.phase_handshaking
    ConnectionPhase.READY -> R.string.phase_ready
    ConnectionPhase.DISCONNECTING -> R.string.phase_disconnecting
    ConnectionPhase.FAILED -> R.string.phase_failed
}

/**
 * Выбор одного значения из нескольких — connected button group Material 3 Expressive.
 */
@Composable
fun <T> ConnectedChoice(
    options: List<Pair<T, String>>,
    selected: T?,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    /** Узкие кнопки для длинных рядов (например, 7 нот). */
    compact: Boolean = false,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        options.forEachIndexed { index, (value, label) ->
            ToggleButton(
                checked = value == selected,
                onCheckedChange = { if (it) onSelect(value) },
                enabled = enabled,
                modifier = Modifier
                    .weight(1f)
                    .semantics { role = Role.RadioButton },
                shapes = when (index) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
                contentPadding = if (compact) CompactChoicePadding else ToggleButtonDefaults.contentPaddingFor(ToggleButtonDefaults.size.height),
            ) {
                if (compact) {
                    // В длинном ряду («Соль» среди 7 нот) подпись уменьшается, а не обрезается.
                    BasicText(
                        label,
                        maxLines = 1,
                        style = LocalTextStyle.current.copy(color = LocalContentColor.current),
                        autoSize = TextAutoSize.StepBased(minFontSize = 9.sp, maxFontSize = 14.sp),
                    )
                } else {
                    Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

private val CompactChoicePadding = PaddingValues(horizontal = 2.dp)

@StringRes
fun BeepNote.labelRes(): Int = when (this) {
    BeepNote.C6 -> R.string.note_c
    BeepNote.D6 -> R.string.note_d
    BeepNote.E6 -> R.string.note_e
    BeepNote.F6 -> R.string.note_f
    BeepNote.G6 -> R.string.note_g
    BeepNote.A6 -> R.string.note_a
    BeepNote.B6 -> R.string.note_b
}
