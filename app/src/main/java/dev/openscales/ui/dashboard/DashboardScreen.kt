package dev.openscales.ui.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.BluetoothSearching
import androidx.compose.material.icons.rounded.Battery5Bar
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.VerticalAlignBottom
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import java.util.concurrent.atomic.AtomicBoolean
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.openscales.R
import dev.openscales.protocol.ScaleModel
import dev.openscales.protocol.TimerState
import dev.openscales.protocol.WeightUnit
import dev.openscales.session.ConnectionPhase
import dev.openscales.session.ScaleState
import dev.openscales.ui.components.TIMER_WIDTH_TEMPLATE
import dev.openscales.ui.components.formatTime
import dev.openscales.ui.components.formatWeight
import dev.openscales.ui.components.labelRes
import dev.openscales.ui.theme.DigitsTextStyle
import dev.openscales.ui.theme.UnitTextStyle
import dev.openscales.ui.theme.OpenScalesTheme
import dev.openscales.ui.theme.WeightTextStyle

@Composable
fun DashboardScreen(
    state: ScaleState,
    hasSavedDevice: Boolean,
    snackbarHostState: SnackbarHostState,
    onTare: () -> Unit,
    onToggleTimer: () -> Unit,
    onResetTimer: () -> Unit,
    onConnect: () -> Unit,
    onOpenScan: () -> Unit,
    onOpenSettings: () -> Unit,
    /** Только debug-сборка: журнал BLE. */
    onOpenJournal: (() -> Unit)? = null,
    /** Срабатывать при касании (true) или при отпускании (false). */
    triggerOnPress: Boolean = true,
) {
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        state.name ?: stringResource(R.string.no_scale),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                subtitle = { Text(state.model.takeIf { it.isKnown }?.displayName ?: stringResource(R.string.app_name)) },
                actions = {
                    if (onOpenJournal != null) {
                        IconButton(onClick = onOpenJournal) { Icon(Icons.Rounded.BugReport, "Журнал BLE") }
                    }
                    IconButton(onClick = onOpenScan) {
                        Icon(Icons.AutoMirrored.Rounded.BluetoothSearching, stringResource(R.string.action_scan))
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Rounded.Settings, stringResource(R.string.action_settings))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            BatteryChip(state)
            ScaleDisplay(state, modifier = Modifier.weight(1f))
            AnimatedVisibility(visible = !state.isReady) {
                ConnectBanner(state, hasSavedDevice, onConnect, onOpenScan)
            }
            ControlButtons(
                tareEnabled = state.isReady,
                triggerOnPress = triggerOnPress,
                timerRunning = state.timerState == TimerState.RUNNING,
                onTare = onTare,
                onToggleTimer = onToggleTimer,
                onResetTimer = onResetTimer,
            )
        }
    }
}

/** Единственная индикация состояния вверху — заряд весов; статус подключения показывает баннер снизу. */
@Composable
private fun BatteryChip(state: ScaleState) {
    val percent = state.batteryPercent?.takeIf { state.isReady }
    Row(modifier = Modifier.heightIn(min = 32.dp), verticalAlignment = Alignment.CenterVertically) {
        if (percent != null) {
            AssistChip(
                onClick = {},
                label = { Text(stringResource(R.string.battery, percent)) },
                leadingIcon = {
                    Icon(
                        Icons.Rounded.Battery5Bar,
                        contentDescription = null,
                        modifier = Modifier.size(AssistChipDefaults.IconSize),
                    )
                },
            )
        }
    }
}

/** «Экран весов»: крупные таймер и вес, поток внизу. */
@Composable
private fun ScaleDisplay(state: ScaleState, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            // Строка предупреждений: место зарезервировано, чтобы таймер не прыгал при перегрузе.
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = 24.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (state.overload) {
                    Icon(Icons.Rounded.Warning, null, tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(4.dp))
                    Text(
                        stringResource(R.string.overload),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
            // Таймер, вес и поток — одной группой по центру карточки.
            ReadoutArea(Modifier.fillMaxWidth().weight(1f)) { styles ->
                TimerReadout(state.timeSeconds, styles.main)
                Spacer(Modifier.height(ReadoutGap))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        formatWeight(state.weight.takeIf { state.isReady }, state.unit),
                        style = styles.main,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                    )
                    Text(
                        state.unit.symbol,
                        style = UnitTextStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = UnitGap, bottom = UnitBaselineGap),
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        stringResource(R.string.flow_rate),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            formatWeight(
                                state.flowRate.takeIf { state.isReady && !state.model.isLegacy() },
                                state.unit,
                            ),
                            style = styles.flowDigits,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                        )
                        Text(
                            stringResource(R.string.flow_rate_unit, state.unit.symbol),
                            style = styles.flowUnit,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = UnitGap),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Таймер размером с вес. Ширину задаёт невидимый шаблон `00:00`, текст прижат к правому краю:
 * при переходе `9:59 → 10:00` двоеточие и секунды остаются на месте, а блок — по центру.
 */
@Composable
private fun TimerReadout(seconds: Int, style: TextStyle) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            stringResource(R.string.timer),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Box(contentAlignment = Alignment.CenterEnd) {
            Text(TIMER_WIDTH_TEMPLATE, style = style, maxLines = 1, modifier = Modifier.alpha(0f))
            Text(formatTime(seconds), style = style, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
        }
    }
}

/** Самый широкий вес, под который считаем масштаб (DOT/Basic 3 — до 2 кг). */
private const val WEIGHT_WIDTH_TEMPLATE = "0000.0"
private val UnitGap = 8.dp
private val ReadoutGap = 8.dp

/** Отступ единицы веса от низа: цифры выше, единица прижата к их базовой линии. */
private val UnitBaselineGap = 16.dp

/** Стили группы: [main] — таймер и вес, [flowDigits] и [flowUnit] — строка потока. */
private class ReadoutStyles(val main: TextStyle, val flowDigits: TextStyle, val flowUnit: TextStyle)

/**
 * Группа таймера, веса и потока по центру. Таймер и вес показаны одним стилем [WeightTextStyle],
 * уменьшенным ровно настолько, чтобы обе строки, подписи и строка потока поместились по высоте,
 * а самые широкие значения (`00:00`, `0000.0 oz`) — по ширине.
 *
 * Все резервы меряются по их же стилям, а не задаются в dp: подписи и поток заданы в sp и растут
 * вместе с системным масштабом шрифта, и по фиксированному резерву их выдавливало бы за край карточки.
 */
@Composable
private fun ReadoutArea(modifier: Modifier, content: @Composable ColumnScope.(ReadoutStyles) -> Unit) {
    BoxWithConstraints(modifier) {
        val measurer = rememberTextMeasurer()
        val density = LocalDensity.current
        val labelStyle = MaterialTheme.typography.labelLarge
        // Меряем по самой широкой единице, чтобы размер не прыгал при смене г/унций.
        val widestUnit = WeightUnit.entries.maxBy { it.symbol.length }.symbol
        val flowUnitText = stringResource(R.string.flow_rate_unit, widestUnit)
        val styles = remember(constraints, density, labelStyle, flowUnitText) {
            val base = WeightTextStyle
            val unitGap = with(density) { UnitGap.toPx() }
            val timer = measurer.measure(TIMER_WIDTH_TEMPLATE, base).size
            val unit = measurer.measure(widestUnit, UnitTextStyle).size
            val weightWidth = measurer.measure(WEIGHT_WIDTH_TEMPLATE, base).size.width + unitGap + unit.width
            val widest = maxOf(timer.width.toFloat(), weightWidth)

            // Поток живёт в своём размере и ужимается, только если не влезает по ширине.
            val flowDigits = measurer.measure(WEIGHT_WIDTH_TEMPLATE, DigitsTextStyle).size
            val flowWidth = flowDigits.width + unitGap + measurer.measure(flowUnitText, UnitTextStyle).size.width
            val flowScale = minOf(1f, constraints.maxWidth / flowWidth)

            // Остаток высоты (без двух подписей, отступа и потока) делится между таймером и весом;
            // строка веса не ниже единицы с её отступом от базовой линии.
            val labels = measurer.measure("0", labelStyle).size.height * 2
            val flowHeight = maxOf(flowDigits.height, unit.height) * flowScale
            val free = constraints.maxHeight - labels - flowHeight - with(density) { ReadoutGap.toPx() }
            val unitRow = unit.height + with(density) { UnitBaselineGap.toPx() }
            val rowHeight = if (free / 2f >= unitRow) free / 2f else free - unitRow
            val scale = minOf(1f, constraints.maxWidth / widest, rowHeight / timer.height).coerceAtLeast(0.3f)
            ReadoutStyles(
                main = base.scaledBy(scale),
                flowDigits = DigitsTextStyle.scaledBy(flowScale),
                flowUnit = UnitTextStyle.scaledBy(flowScale),
            )
        }
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            content(styles)
        }
    }
}

private fun TextStyle.scaledBy(factor: Float) =
    if (factor >= 1f) this else copy(fontSize = fontSize * factor, lineHeight = lineHeight * factor)

private fun ScaleModel.isLegacy() = this == ScaleModel.OLD_DOUBLE

@Composable
private fun ConnectBanner(
    state: ScaleState,
    hasSavedDevice: Boolean,
    onConnect: () -> Unit,
    onOpenScan: () -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val busy = state.phase.isBusy || state.reconnecting
            if (busy) LoadingIndicator(Modifier.size(28.dp))
            Text(
                text = when {
                    state.reconnecting && !state.phase.isBusy -> stringResource(R.string.reconnecting)
                    state.phase.isBusy -> stringResource(state.phase.labelRes())
                    else -> state.error ?: stringResource(state.phase.labelRes())
                },
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge,
            )
            if (hasSavedDevice) {
                Button(onClick = onConnect, enabled = !busy) { Text(stringResource(R.string.action_connect)) }
            } else {
                Button(onClick = onOpenScan) { Text(stringResource(R.string.action_find_scale)) }
            }
        }
    }
}

/** Кнопки как на корпусе весов: Тара / Старт-Пауза / Сброс — Material 3 Expressive ButtonGroup. */
@Composable
private fun ControlButtons(
    /** Тара невозможна без весов; таймер ведёт приложение, поэтому его кнопки активны всегда. */
    tareEnabled: Boolean,
    triggerOnPress: Boolean,
    timerRunning: Boolean,
    onTare: () -> Unit,
    onToggleTimer: () -> Unit,
    onResetTimer: () -> Unit,
) {
    val height = ButtonDefaults.LargeContainerHeight
    val tareInteraction = remember { MutableInteractionSource() }
    val timerInteraction = remember { MutableInteractionSource() }
    val resetInteraction = remember { MutableInteractionSource() }
    // По умолчанию как физические кнопки весов: срабатывают в момент касания (настраивается).
    val tare = rememberPressAction(tareEnabled && triggerOnPress, onTare)
    val toggleTimer = rememberPressAction(triggerOnPress, onToggleTimer)
    val resetTimer = rememberPressAction(triggerOnPress, onResetTimer)
    ButtonGroup(
        overflowIndicator = { ButtonGroupDefaults.OverflowIndicator(it) },
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        customItem(
            buttonGroupContent = {
                FilledTonalButton(
                    onClick = tare.onClick,
                    enabled = tareEnabled,
                    shapes = ButtonDefaults.shapesFor(height),
                    contentPadding = ControlButtonPadding,
                    interactionSource = tareInteraction,
                    modifier = Modifier.weight(1f).heightIn(min = height).animateWidth(tareInteraction).then(tare.modifier),
                ) {
                    ButtonLabel(Icons.Rounded.VerticalAlignBottom, stringResource(R.string.tare), height)
                }
            },
            menuContent = {},
        )
        customItem(
            buttonGroupContent = {
                Button(
                    onClick = toggleTimer.onClick,
                    shapes = ButtonDefaults.shapesFor(height),
                    contentPadding = ControlButtonPadding,
                    interactionSource = timerInteraction,
                    modifier = Modifier.weight(1.3f).heightIn(min = height).animateWidth(timerInteraction)
                        .then(toggleTimer.modifier),
                ) {
                    // Без переходной анимации: плавная смена вида читается как задержка кнопки.
                    ButtonLabel(
                        if (timerRunning) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        stringResource(if (timerRunning) R.string.pause else R.string.start),
                        height,
                    )
                }
            },
            menuContent = {},
        )
        customItem(
            buttonGroupContent = {
                OutlinedButton(
                    onClick = resetTimer.onClick,
                    shapes = ButtonDefaults.shapesFor(height),
                    contentPadding = ControlButtonPadding,
                    interactionSource = resetInteraction,
                    modifier = Modifier.weight(1f).heightIn(min = height).animateWidth(resetInteraction).then(resetTimer.modifier),
                ) {
                    ButtonLabel(Icons.Rounded.RestartAlt, stringResource(R.string.reset), height)
                }
            },
            menuContent = {},
        )
    }
}

/** Кнопка, срабатывающая по касанию: [modifier] ловит касание, [onClick] остаётся для TalkBack и клавиатуры. */
private class PressAction(val onClick: () -> Unit, val modifier: Modifier)

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
private fun rememberPressAction(enabled: Boolean, action: () -> Unit): PressAction {
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

@Composable
private fun ButtonLabel(icon: ImageVector, text: String, height: androidx.compose.ui.unit.Dp) {
    // Три крупные кнопки в ряд: иконка над подписью, чтобы текст помещался на узких экранах.
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(32.dp))
        Text(text, style = MaterialTheme.typography.titleMedium, maxLines = 1)
    }
}

private val ControlButtonPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 8.dp)

@Preview(showBackground = true, heightDp = 780)
@Composable
private fun DashboardReadyPreview() {
    OpenScalesTheme(dynamicColor = false) {
        DashboardScreen(
            state = ScaleState(
                phase = ConnectionPhase.READY,
                name = "Black Mirror Basic 3",
                model = ScaleModel.BASIC3,
                weight = 18.3f,
                flowRate = 2.4f,
                timeSeconds = 615,
                timerState = TimerState.RUNNING,
                batteryPercent = 64,
                address = "AA:BB",
            ),
            hasSavedDevice = true,
            snackbarHostState = SnackbarHostState(),
            onTare = {}, onToggleTimer = {}, onResetTimer = {}, onConnect = {}, onOpenScan = {}, onOpenSettings = {},
        )
    }
}

/** Самый тесный случай: узкий экран и системный шрифт «Самый крупный». */
@Preview(showBackground = true, widthDp = 320, heightDp = 640, fontScale = 2f)
@Composable
private fun DashboardLargeFontPreview() {
    OpenScalesTheme(dynamicColor = false) {
        DashboardScreen(
            state = ScaleState(
                phase = ConnectionPhase.READY, name = "TIMEMORE_Dot", model = ScaleModel.DOT,
                weight = 1999.9f, flowRate = 2.4f, timeSeconds = 600, timerState = TimerState.RUNNING,
                batteryPercent = 89, address = "AA",
            ),
            hasSavedDevice = true,
            snackbarHostState = SnackbarHostState(),
            onTare = {}, onToggleTimer = {}, onResetTimer = {}, onConnect = {}, onOpenScan = {}, onOpenSettings = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun DashboardSmallScreenPreview() {
    OpenScalesTheme(dynamicColor = false) {
        DashboardScreen(
            state = ScaleState(
                phase = ConnectionPhase.READY, name = "TIMEMORE_Dot", model = ScaleModel.DOT,
                weight = 1999.9f, timeSeconds = 600, timerState = TimerState.RUNNING, batteryPercent = 89, address = "AA",
            ),
            hasSavedDevice = true,
            snackbarHostState = SnackbarHostState(),
            onTare = {}, onToggleTimer = {}, onResetTimer = {}, onConnect = {}, onOpenScan = {}, onOpenSettings = {},
        )
    }
}

@Preview(showBackground = true, heightDp = 780)
@Composable
private fun DashboardDisconnectedPreview() {
    OpenScalesTheme(dynamicColor = false) {
        DashboardScreen(
            state = ScaleState(),
            hasSavedDevice = false,
            snackbarHostState = SnackbarHostState(),
            onTare = {}, onToggleTimer = {}, onResetTimer = {}, onConnect = {}, onOpenScan = {}, onOpenSettings = {},
        )
    }
}

