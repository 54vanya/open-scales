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

fun formatWeight(value: Float?, unit: WeightUnit): String =
    if (value == null) "—" else String.format(Locale.US, "%.${unit.decimals}f", value)

/** Время таймера `M:SS`, не длиннее 5 символов: всё, что ≥ 100 минут, показывается как `99:59`. */
fun formatTime(seconds: Int): String {
    val s = seconds.coerceIn(0, MAX_TIMER_SECONDS)
    return "%d:%02d".format(s / 60, s % 60)
}

const val MAX_TIMER_SECONDS = 99 * 60 + 59

/** Самое широкое значение таймера — задаёт ширину области под таймер. */
const val TIMER_WIDTH_TEMPLATE = "00:00"

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
