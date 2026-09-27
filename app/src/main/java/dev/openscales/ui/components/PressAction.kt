package dev.openscales.ui.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import java.util.concurrent.atomic.AtomicBoolean

/** Кнопка, срабатывающая по касанию: [modifier] ловит касание, [onClick] остаётся для TalkBack и клавиатуры. */
class PressAction(val onClick: () -> Unit, val modifier: Modifier)

/**
 * Действие по касанию выполняется прямо в обработке касания, на проходе [PointerEventPass.Initial]:
 * так оно заведомо раньше `onClick`, и короткое касание не отправляет команду дважды. Событие не
 * поглощается, поэтому рябь и обычный `onClick` работают как прежде.
 *
 * [PressAction.onClick] нужен для активации без касания (TalkBack, клавиатура): если действие уже
 * выполнено касанием, он его не повторяет. Жест, закончившийся не отпусканием над кнопкой, снимает
 * признак — иначе он «съел» бы следующую активацию.
 */
@Composable
fun rememberPressAction(enabled: Boolean, action: () -> Unit): PressAction {
    val currentAction by rememberUpdatedState(action)
    val handledByPress = remember { AtomicBoolean(false) }
    val modifier = if (!enabled) {
        Modifier
    } else {
        Modifier.pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                handledByPress.set(true)
                currentAction()
                // Смотрим сырые события: кнопка поглощает отпускание, и готовые помощники
                // (`waitForUpOrCancellation`) приняли бы это за отмену жеста.
                var last = down
                while (last.pressed) {
                    val event = awaitPointerEvent(PointerEventPass.Final)
                    last = event.changes.firstOrNull { it.id == down.id } ?: break
                }
                val inside = last.position.x in 0f..size.width.toFloat() &&
                    last.position.y in 0f..size.height.toFloat()
                // Палец ушёл с кнопки — `onClick` не придёт, и признак не должен съесть следующее нажатие.
                if (!inside) handledByPress.set(false)
            }
        }
    }
    val onClick = remember { { if (!handledByPress.getAndSet(false)) currentAction() } }
    return PressAction(onClick, modifier)
}
