package dev.openscales.ui.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Battery0Bar
import androidx.compose.material.icons.rounded.Battery1Bar
import androidx.compose.material.icons.rounded.Battery2Bar
import androidx.compose.material.icons.rounded.Battery3Bar
import androidx.compose.material.icons.rounded.Battery4Bar
import androidx.compose.material.icons.rounded.Battery5Bar
import androidx.compose.material.icons.rounded.Battery6Bar
import androidx.compose.material.icons.rounded.BatteryFull
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Coffee
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Scale
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.VerticalAlignBottom
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.WideNavigationRail
import androidx.compose.material3.WideNavigationRailItem
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.coerceAtMost
import kotlin.math.ceil
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.openscales.R
import dev.openscales.protocol.ScaleModel
import dev.openscales.protocol.TimerState
import dev.openscales.protocol.WeightUnit
import dev.openscales.session.ConnectionPhase
import dev.openscales.session.ScaleState
import dev.openscales.ui.components.BusyIndicator
import dev.openscales.ui.components.rememberPressAction
import dev.openscales.ui.scan.BlePrerequisite
import dev.openscales.ui.components.FLOW_PLACEHOLDER_DIGITS
import dev.openscales.ui.components.TIMER_WIDTH_TEMPLATE
import dev.openscales.ui.components.formatTime
import dev.openscales.ui.components.flowSymbolRes
import dev.openscales.ui.components.formatWeight
import dev.openscales.ui.components.symbolRes
import dev.openscales.ui.components.labelRes
import dev.openscales.ui.components.messageRes
import dev.openscales.ui.theme.DigitsTextStyle
import dev.openscales.ui.theme.UnitTextStyle
import dev.openscales.ui.theme.OpenScalesTheme
import dev.openscales.ui.theme.WeightTextStyle
import dev.openscales.ui.theme.withSlashedZero

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
    /** Нет разрешения или Bluetooth выключен — баннер показывает причину и действие вместо фазы подключения. */
    prerequisite: BlePrerequisite = BlePrerequisite.OK,
    onRequestPermission: () -> Unit = {},
    onEnableBluetooth: () -> Unit = {},
    /** Только debug-сборка: журнал BLE. */
    onOpenJournal: (() -> Unit)? = null,
    /** Срабатывать при касании (true) или при отпускании (false). */
    triggerOnPress: Boolean = true,
    /** Перечёркнутый ноль в цифрах показаний. */
    slashedZero: Boolean = true,
    selectedTab: MainTab = MainTab.SCALE,
    onSelectTab: (MainTab) -> Unit = {},
    /** Содержимое вкладки «Рецепты»; отступы — от верхней и нижней панелей. */
    recipesTab: @Composable (PaddingValues) -> Unit = {},
    /** Плавающая кнопка вкладки «Рецепты». */
    recipesFab: @Composable () -> Unit = {},
) {
    // Тот же критерий, что делит вкладку «Весы» на две колонки: окно шире своей высоты — рейка слева.
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth > maxHeight
        Row(Modifier.fillMaxSize()) {
            if (wide) MainNavigationRail(selectedTab, onSelectTab)
            Scaffold(
                // Боковой отступ под вырез и панель жестов рейка уже взяла себе.
                modifier = if (wide) Modifier.consumeWindowInsets(WindowInsets.safeDrawing.only(WindowInsetsSides.Start)) else Modifier,
                snackbarHost = { SnackbarHost(snackbarHostState) },
                bottomBar = { if (!wide) MainNavigationBar(selectedTab, onSelectTab) },
                floatingActionButton = { if (selectedTab == MainTab.RECIPES) recipesFab() },
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
                            BatteryLevel(state)
                            if (onOpenJournal != null) {
                                IconButton(onClick = onOpenJournal) { Icon(Icons.Rounded.BugReport, stringResource(R.string.journal_title)) }
                            }
                            IconButton(onClick = onOpenScan) {
                                Icon(Icons.Rounded.Scale, stringResource(R.string.action_scan))
                            }
                            IconButton(onClick = onOpenSettings) {
                                Icon(Icons.Rounded.Settings, stringResource(R.string.action_settings))
                            }
                        },
                    )
                },
            ) { padding ->
                when (selectedTab) {
                    MainTab.SCALE -> ScaleTab(
                        state, hasSavedDevice, padding, wide, onTare, onToggleTimer, onResetTimer, onConnect, onOpenScan,
                        prerequisite, onRequestPermission, onEnableBluetooth, triggerOnPress, slashedZero,
                    )
                    MainTab.RECIPES -> recipesTab(padding)
                }
            }
        }
    }
}

/** Вкладка главного экрана; вкладки не создают записей в стеке «назад». */
enum class MainTab { SCALE, RECIPES }

@Composable
private fun MainNavigationBar(selected: MainTab, onSelect: (MainTab) -> Unit) {
    ShortNavigationBar {
        MainTab.entries.forEach { tab ->
            ShortNavigationBarItem(
                selected = tab == selected,
                onClick = { onSelect(tab) },
                icon = { Icon(tab.icon(), contentDescription = null) },
                label = { Text(stringResource(tab.labelRes())) },
            )
        }
    }
}

@Composable
private fun MainNavigationRail(selected: MainTab, onSelect: (MainTab) -> Unit) {
    WideNavigationRail {
        MainTab.entries.forEach { tab ->
            WideNavigationRailItem(
                selected = tab == selected,
                onClick = { onSelect(tab) },
                icon = { Icon(tab.icon(), contentDescription = null) },
                label = { Text(stringResource(tab.labelRes())) },
                railExpanded = false,
            )
        }
    }
}

private fun MainTab.icon(): ImageVector = when (this) {
    MainTab.SCALE -> Icons.Rounded.Scale
    MainTab.RECIPES -> Icons.Rounded.Coffee
}

private fun MainTab.labelRes(): Int = when (this) {
    MainTab.SCALE -> R.string.tab_scale
    MainTab.RECIPES -> R.string.tab_recipes
}

/** Вкладка «Весы»: карточка показаний, баннер подключения и кнопки управления. */
@Composable
private fun ScaleTab(
    state: ScaleState,
    hasSavedDevice: Boolean,
    padding: PaddingValues,
    wide: Boolean,
    onTare: () -> Unit,
    onToggleTimer: () -> Unit,
    onResetTimer: () -> Unit,
    onConnect: () -> Unit,
    onOpenScan: () -> Unit,
    prerequisite: BlePrerequisite,
    onRequestPermission: () -> Unit,
    onEnableBluetooth: () -> Unit,
    triggerOnPress: Boolean,
    slashedZero: Boolean,
) {
    val measures = rememberReadoutMeasures(slashedZero)
    val cardMinHeight = rememberScaleDisplayMinHeight(measures)
    val display = @Composable { modifier: Modifier -> ScaleDisplay(state, measures, modifier) }
    // Отступ до соседей — внутри анимации: сворачивается вместе с баннером. Снаружи он пропадал бы
    // одним кадром, когда баннер уходит из раскладки, и в конце исчезновения всё дёргалось бы.
    val banner = @Composable { gap: PaddingValues ->
        AnimatedVisibility(
            visible = !state.isReady,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Box(Modifier.padding(gap)) { ConnectBanner(state, hasSavedDevice, prerequisite, onConnect, onOpenScan, onRequestPermission, onEnableBluetooth) }
        }
    }
    val buttons = @Composable { vertical: Boolean, modifier: Modifier ->
        ControlButtons(
            vertical = vertical,
            modifier = modifier,
            tareEnabled = state.isReady,
            triggerOnPress = triggerOnPress,
            timerRunning = state.timerState == TimerState.RUNNING,
            onTare = onTare,
            onToggleTimer = onToggleTimer,
            onResetTimer = onResetTimer,
        )
    }
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        val viewport = maxHeight
        val sideWidth = maxOf(maxWidth * 0.4f, 280.dp).coerceAtMost(maxWidth / 2)
        if (wide) {
            // Альбомная ориентация: высоты на одну колонку не хватает — карточка слева, управление справа.
            val rowHeight = maxOf(viewport, cardMinHeight)
            Row(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).height(rowHeight),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                display(Modifier.weight(1f).fillMaxHeight())
                Column(
                    modifier = Modifier
                        .width(sideWidth)
                        .verticalScroll(rememberScrollState())
                        .heightIn(min = rowHeight),
                ) {
                    banner(PaddingValues(bottom = ScreenGap))
                    buttons(true, Modifier.weight(1f))
                }
            }
        } else {
            FillOrScrollColumn(
                viewport = viewport,
                fillerMinHeight = cardMinHeight,
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
            ) {
                display(Modifier)
                banner(PaddingValues(top = ScreenGap))
                buttons(false, Modifier.padding(top = ScreenGap))
            }
        }
    }
}

/**
 * Колонка экрана: первый элемент (карточка показаний) забирает всю оставшуюся высоту [viewport], но не меньше
 * [fillerMinHeight]. Если столько места нет, колонка выше окна и прокручивается снаружи. Обычная `Column`
 * так не умеет: `weight` внутри прокрутки не работает, а `heightIn` не пересиливает `weight`.
 * Отступы между элементами — у самих элементов, чтобы сворачиваться вместе с ними.
 */
@Composable
private fun FillOrScrollColumn(
    viewport: Dp,
    fillerMinHeight: Dp,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Layout(content, modifier) { measurables, constraints ->
        val loose = constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity)
        val others = measurables.mapIndexed { i, m -> if (i == FILLER_INDEX) null else m.measure(loose) }
        val used = others.sumOf { it?.height ?: 0 }
        val fillerHeight = maxOf(fillerMinHeight.roundToPx(), viewport.roundToPx() - used)
        val filler = measurables[FILLER_INDEX].measure(loose.copy(minHeight = fillerHeight, maxHeight = fillerHeight))
        val placeables = others.toMutableList().also { it[FILLER_INDEX] = filler }.filterNotNull()
        val width = placeables.maxOf { it.width }.coerceIn(constraints.minWidth, constraints.maxWidth)
        layout(width, used + fillerHeight) {
            var y = 0
            placeables.forEach {
                it.placeRelative(0, y)
                y += it.height
            }
        }
    }
}

private const val FILLER_INDEX = 0

/** Отступ между карточкой, баннером и кнопками. */
private val ScreenGap = 16.dp

/** Заряд весов в верхней панели: иконка по уровню и проценты; статус подключения показывает баннер снизу. */
@Composable
private fun BatteryLevel(state: ScaleState) {
    val percent = state.batteryPercent?.takeIf { state.isReady } ?: return
    val description = stringResource(R.string.battery, percent)
    Row(
        modifier = Modifier
            .padding(horizontal = 8.dp)
            .clearAndSetSemantics { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(batteryIcon(percent), contentDescription = null, modifier = Modifier.size(20.dp))
        Text("$percent%", style = MaterialTheme.typography.labelLarge)
    }
}

private fun batteryIcon(percent: Int): ImageVector = when {
    percent >= 95 -> Icons.Rounded.BatteryFull
    else -> BATTERY_BARS[(percent * BATTERY_BARS.size / 100).coerceIn(0, BATTERY_BARS.lastIndex)]
}

private val BATTERY_BARS = listOf(
    Icons.Rounded.Battery0Bar, Icons.Rounded.Battery1Bar, Icons.Rounded.Battery2Bar, Icons.Rounded.Battery3Bar,
    Icons.Rounded.Battery4Bar, Icons.Rounded.Battery5Bar, Icons.Rounded.Battery6Bar,
)

/** «Экран весов»: крупные таймер и вес, поток внизу. */
@Composable
private fun ScaleDisplay(state: ScaleState, measures: DisplayMeasures, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Column(modifier = Modifier.padding(CardPadding)) {
            // Строка предупреждений: место зарезервировано, чтобы таймер не прыгал при перегрузе.
            Row(
                modifier = Modifier.fillMaxWidth().height(measures.warningRowHeight),
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
            // Таймер, вес и поток — одной группой по центру карточки, вокруг общей оси.
            ReadoutArea(Modifier.fillMaxWidth().weight(1f), measures) { r ->
                val numberColor = MaterialTheme.colorScheme.onSurface
                val unitColor = MaterialTheme.colorScheme.onSurfaceVariant
                AxisLabel(stringResource(R.string.timer), r)
                AxisRow(formatTime(state.timeSeconds), r.main, numberColor, r)
                Spacer(Modifier.height(ReadoutGap))
                AxisRow(
                    formatWeight(state.weight.takeIf { state.isReady }, state.unit), r.main, numberColor, r,
                    unit = stringResource(state.unit.symbolRes()), unitColor = unitColor,
                )
                AxisLabel(stringResource(R.string.flow_rate), r)
                AxisRow(
                    formatWeight(
                        state.flowRate.takeIf { state.isReady && !state.model.isLegacy() },
                        state.unit,
                        FLOW_PLACEHOLDER_DIGITS,
                    ),
                    r.flow, numberColor, r,
                    unit = stringResource(state.unit.flowSymbolRes()), unitColor = unitColor,
                )
            }
        }
    }
}

private val UnitGap = 8.dp
private val ReadoutGap = 8.dp

/** Шаблоны чисел крупным стилем: 4 цифры и разделитель. Пятая цифра веса выступает влево за ось. */
private val NUMBER_TEMPLATES = listOf("000.0", "00.00", TIMER_WIDTH_TEMPLATE)

/** Шаблоны числа потока: граммы и унции. */
private val FLOW_TEMPLATES = listOf("00.0", "0.00")

/**
 * Стили и колонки группы: [main] — таймер и вес, [flow] — число потока, [unit] — единицы веса и потока.
 * Каждая строка — поле [side], числовая колонка [numberWidth] и поле [side] под единицу. Строки центрируются,
 * поэтому по центру карточки стоит ровно числовая колонка, а её правый край — ось.
 */
private class ReadoutStyles(
    val main: TextStyle,
    val flow: TextStyle,
    val unit: TextStyle,
    val numberWidth: Dp,
    val side: Dp,
)

/** Строка на оси: число кончается на оси (лишние цифры выступают влево в поле), единица начинается за ней. */
@Composable
private fun AxisRow(
    number: String,
    style: TextStyle,
    color: Color,
    r: ReadoutStyles,
    unit: String? = null,
    unitColor: Color = Color.Unspecified,
) {
    Row {
        Spacer(Modifier.width(r.side))
        Text(
            number,
            style = style,
            color = color,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier
                .alignByBaseline()
                .width(r.numberWidth)
                .wrapContentWidth(Alignment.End, unbounded = true),
        )
        if (unit != null) {
            Text(
                unit,
                style = r.unit,
                color = unitColor,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.alignByBaseline().width(r.side).padding(start = UnitGap),
            )
        } else {
            Spacer(Modifier.width(r.side))
        }
    }
}

/** Подпись над числом, прижатая к оси справа. */
@Composable
private fun AxisLabel(text: String, r: ReadoutStyles) {
    Row {
        Spacer(Modifier.width(r.side))
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.width(r.numberWidth).wrapContentWidth(Alignment.End, unbounded = true),
        )
        Spacer(Modifier.width(r.side))
    }
}

/**
 * Размеры шаблонов показаний базовыми стилями — общие для геометрии группы и минимальной высоты карточки.
 *
 * Все резервы меряются по их же стилям, а не задаются в dp: подписи и поток заданы в sp и растут
 * вместе с системным масштабом шрифта, и по фиксированному резерву их выдавливало бы за край карточки.
 */
private class DisplayMeasures(
    val readout: ReadoutMeasures,
    val main: TextStyle,
    val flow: TextStyle,
    /** Строка перегруза: иконка 24 dp или подпись, если она выше. */
    val warningRowHeight: Dp,
)

@Composable
private fun rememberReadoutMeasures(slashedZero: Boolean): DisplayMeasures {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val labelStyle = MaterialTheme.typography.labelLarge
    // Все единицы, чтобы ось не двигалась при смене г/унций.
    val units = WeightUnit.entries.flatMap { listOf(stringResource(it.symbolRes()), stringResource(it.flowSymbolRes())) }
    return remember(density, labelStyle, units, slashedZero) {
        val main = WeightTextStyle.withSlashedZero(slashedZero)
        val flow = DigitsTextStyle.withSlashedZero(slashedZero)
        fun width(text: String, style: TextStyle) = measurer.measure(text, style).size.width.toFloat()
        fun height(text: String, style: TextStyle) = measurer.measure(text, style).size.height
        val labelHeight = height("0", labelStyle)
        val big = measurer.measure("0", main)
        val unit = measurer.measure("g", UnitTextStyle)
        val flowDigits = measurer.measure("0", flow)
        // Число и единица в строке выровнены по базовой линии: высота — наибольший подъём плюс наибольший спуск.
        val flowRow = maxOf(flowDigits.firstBaseline, unit.firstBaseline) +
            maxOf(flowDigits.size.height - flowDigits.firstBaseline, unit.size.height - unit.firstBaseline)
        with(density) {
            DisplayMeasures(
                readout = ReadoutMeasures(
                    numberTemplateWidths = NUMBER_TEMPLATES.map { width(it, main) },
                    digitWidth = width("0", main),
                    bigLineHeight = big.size.height.toFloat(),
                    bigBaseline = big.firstBaseline,
                    unitLineHeight = unit.size.height.toFloat(),
                    unitBaseline = unit.firstBaseline,
                    flowNumberWidth = FLOW_TEMPLATES.maxOf { width(it, flow) },
                    flowLineHeight = flowRow,
                    unitWidths = units.map { width(it, UnitTextStyle) },
                    unitGap = UnitGap.toPx(),
                    labelsHeight = labelHeight * 2f,
                    rowGap = ReadoutGap.toPx(),
                ),
                main = main,
                flow = flow,
                warningRowHeight = maxOf(24.dp, labelHeight.toDp()),
            )
        }
    }
}

/** Наименьшая высота карточки: поля, строка перегруза и группа показаний при минимальном масштабе. */
@Composable
private fun rememberScaleDisplayMinHeight(measures: DisplayMeasures): Dp {
    val density = LocalDensity.current
    return remember(measures, density) {
        with(density) { CardPadding * 2 + measures.warningRowHeight + ceil(readoutMinHeight(measures.readout)).toDp() }
    }
}

private val CardPadding = 24.dp

/**
 * Группа таймера, веса и потока по центру. Ширины колонок и масштаб считает [readoutGeometry]
 * по шаблонам, а не по значениям — поэтому ось не двигается.
 */
@Composable
private fun ReadoutArea(
    modifier: Modifier,
    measures: DisplayMeasures,
    content: @Composable ColumnScope.(ReadoutStyles) -> Unit,
) {
    BoxWithConstraints(modifier) {
        val density = LocalDensity.current
        val styles = remember(constraints, density, measures) {
            val geometry = readoutGeometry(
                measures.readout,
                maxWidth = constraints.maxWidth.toFloat(),
                maxHeight = constraints.maxHeight.toFloat(),
            )
            with(density) {
                ReadoutStyles(
                    main = scaled(measures.main, geometry.scale),
                    flow = scaled(measures.flow, geometry.flowScale),
                    unit = scaled(UnitTextStyle, geometry.unitScale),
                    numberWidth = geometry.numberWidth.toDp(),
                    side = geometry.sideWidth(UnitGap.toPx()).toDp(),
                )
            }
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

/**
 * Уменьшает стиль так, чтобы на экране он стал ровно в [factor] раз меньше измеренного. Умножать sp нельзя:
 * с Android 14 крупный шрифт масштабируется нелинейно, и уменьшенный размер система увеличила бы сильнее
 * исходного — текст вылез бы за рассчитанные колонки. Поэтому масштабируем в dp и переводим обратно в sp.
 */
private fun Density.scaled(style: TextStyle, factor: Float) =
    if (factor >= 1f) {
        style
    } else {
        style.copy(
            fontSize = (style.fontSize.toDp() * factor).toSp(),
            lineHeight = (style.lineHeight.toDp() * factor).toSp(),
            letterSpacing = if (style.letterSpacing.isSp) (style.letterSpacing.toDp() * factor).toSp() else style.letterSpacing,
        )
    }

private fun ScaleModel.isLegacy() = this == ScaleModel.OLD_DOUBLE

@Composable
internal fun ConnectBanner(
    state: ScaleState,
    hasSavedDevice: Boolean,
    prerequisite: BlePrerequisite,
    onConnect: () -> Unit,
    onOpenScan: () -> Unit,
    onRequestPermission: () -> Unit,
    onEnableBluetooth: () -> Unit,
    /** Своя фраза вместо фазы подключения, пока приложение само не подключается (вкладка «Рецепты»). */
    message: String? = null,
) {
    // Высота баннера меняется со статусом («Подключение…» в одну строку, «Чтение параметров…» — в две,
    // индикатор появляется и пропадает). Без анимации соседи — в альбомной ориентации кнопки — прыгали бы.
    Card(
        modifier = Modifier.animateContentSize(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        // Кнопка переносится под текст, если не помещается рядом, — текст не рвётся посреди слова.
        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            // Без разрешения или с выключенным Bluetooth подключиться нельзя: показываем причину, а не фазу попытки.
            val blocked = prerequisite != BlePrerequisite.OK
            val busy = !blocked && (state.phase.isBusy || state.reconnecting)
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (busy) BusyIndicator()
                Text(
                    text = when {
                        prerequisite == BlePrerequisite.NO_PERMISSION -> stringResource(R.string.permission_title)
                        prerequisite == BlePrerequisite.BLUETOOTH_OFF -> stringResource(R.string.bluetooth_off_title)
                        state.reconnecting && !state.phase.isBusy -> stringResource(R.string.reconnecting)
                        state.phase.isBusy -> stringResource(state.phase.labelRes())
                        message != null -> message
                        else -> stringResource(state.error?.messageRes() ?: state.phase.labelRes())
                    },
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            // «Подключить» — только когда приложение само не подключается: заблокированная кнопка на всё
            // время попыток лишь занимала место. Статус и индикатор в это время показывает сам баннер.
            // Весы готовы — баннер уже исчезает с надписью «Подключено», кнопке в нём делать нечего.
            when {
                state.isReady && !blocked -> Unit
                prerequisite == BlePrerequisite.NO_PERMISSION ->
                    Button(onClick = onRequestPermission) { Text(stringResource(R.string.permission_grant)) }
                prerequisite == BlePrerequisite.BLUETOOTH_OFF ->
                    Button(onClick = onEnableBluetooth) { Text(stringResource(R.string.action_enable_bluetooth)) }
                hasSavedDevice -> if (!busy) Button(onClick = onConnect) { Text(stringResource(R.string.action_connect)) }
                else -> Button(onClick = onOpenScan) { Text(stringResource(R.string.action_find_scale)) }
            }
        }
    }
}

/**
 * Кнопки как на корпусе весов: Тара / Старт-Пауза / Сброс. В ряд — Material 3 Expressive ButtonGroup;
 * [vertical] — колонкой на всю высоту [modifier] (альбомная ориентация), иконка слева от подписи.
 */
@Composable
private fun ControlButtons(
    vertical: Boolean,
    /** Тара невозможна без весов; таймер ведёт приложение, поэтому его кнопки активны всегда. */
    tareEnabled: Boolean,
    triggerOnPress: Boolean,
    timerRunning: Boolean,
    onTare: () -> Unit,
    onToggleTimer: () -> Unit,
    onResetTimer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // По умолчанию как физические кнопки весов: срабатывают в момент касания (настраивается).
    val tare = rememberPressAction(tareEnabled && triggerOnPress, onTare)
    val toggleTimer = rememberPressAction(triggerOnPress, onToggleTimer)
    val resetTimer = rememberPressAction(triggerOnPress, onResetTimer)
    val tareLabel = stringResource(R.string.tare)
    // Без переходной анимации: плавная смена вида читается как задержка кнопки.
    val timerIcon = if (timerRunning) Icons.Rounded.Pause else Icons.Rounded.PlayArrow
    val timerLabel = stringResource(if (timerRunning) R.string.pause else R.string.start)
    val resetLabel = stringResource(R.string.reset)

    if (vertical) {
        val shapes = ButtonDefaults.shapesFor(ButtonDefaults.MediumContainerHeight)
        val item = Modifier.fillMaxWidth().heightIn(min = ButtonDefaults.MediumContainerHeight)
        Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(
                onClick = tare.onClick,
                enabled = tareEnabled,
                shapes = shapes,
                modifier = item.weight(1f).then(tare.modifier),
            ) { ButtonLabelInRow(Icons.Rounded.VerticalAlignBottom, tareLabel) }
            Button(
                onClick = toggleTimer.onClick,
                shapes = shapes,
                modifier = item.weight(1.3f).then(toggleTimer.modifier),
            ) { ButtonLabelInRow(timerIcon, timerLabel) }
            OutlinedButton(
                onClick = resetTimer.onClick,
                shapes = shapes,
                modifier = item.weight(1f).then(resetTimer.modifier),
            ) { ButtonLabelInRow(Icons.Rounded.RestartAlt, resetLabel) }
        }
        return
    }

    val height = ButtonDefaults.LargeContainerHeight
    val tareInteraction = remember { MutableInteractionSource() }
    val timerInteraction = remember { MutableInteractionSource() }
    val resetInteraction = remember { MutableInteractionSource() }
    ButtonGroup(
        overflowIndicator = { ButtonGroupDefaults.OverflowIndicator(it) },
        modifier = modifier.fillMaxWidth().padding(bottom = 8.dp),
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
                    ButtonLabel(Icons.Rounded.VerticalAlignBottom, tareLabel)
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
                    ButtonLabel(timerIcon, timerLabel)
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
                    ButtonLabel(Icons.Rounded.RestartAlt, resetLabel)
                }
            },
            menuContent = {},
        )
    }
}

@Composable
private fun ButtonLabel(icon: ImageVector, text: String) {
    // Три крупные кнопки в ряд: иконка над подписью, чтобы текст помещался на узких экранах.
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(32.dp))
        Text(text, style = MaterialTheme.typography.titleMedium, maxLines = 1)
    }
}

/** Кнопка в колонке: места по ширине много, поэтому иконка слева от подписи. */
@Composable
private fun ButtonLabelInRow(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(32.dp))
        Text(text, style = MaterialTheme.typography.titleLarge, maxLines = 1)
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

/** Унции и обычный ноль: ось та же, что у граммов. */
@Preview(showBackground = true, heightDp = 780)
@Composable
private fun DashboardOuncesPreview() {
    OpenScalesTheme(dynamicColor = false) {
        DashboardScreen(
            state = ScaleState(
                phase = ConnectionPhase.READY, name = "TIMEMORE_Dot", model = ScaleModel.DOT,
                weight = 70.55f, flowRate = 0.08f, unit = WeightUnit.OUNCE, timeSeconds = 0,
                batteryPercent = 89, address = "AA",
            ),
            hasSavedDevice = true,
            snackbarHostState = SnackbarHostState(),
            onTare = {}, onToggleTimer = {}, onResetTimer = {}, onConnect = {}, onOpenScan = {}, onOpenSettings = {},
            slashedZero = false,
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

/** Самый тесный случай без весов: баннер переносит кнопку под текст, экран прокручивается. */
@Preview(showBackground = true, widthDp = 320, heightDp = 640, fontScale = 2f)
@Composable
private fun DashboardDisconnectedLargeFontPreview() {
    OpenScalesTheme(dynamicColor = false) {
        DashboardScreen(
            state = ScaleState(),
            hasSavedDevice = false,
            snackbarHostState = SnackbarHostState(),
            onTare = {}, onToggleTimer = {}, onResetTimer = {}, onConnect = {}, onOpenScan = {}, onOpenSettings = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 780, heightDp = 360)
@Composable
private fun DashboardLandscapePreview() {
    OpenScalesTheme(dynamicColor = false) {
        DashboardScreen(
            state = ScaleState(
                phase = ConnectionPhase.READY, name = "TIMEMORE_Dot", model = ScaleModel.DOT,
                weight = 18.3f, flowRate = 2.4f, timeSeconds = 75, timerState = TimerState.RUNNING,
                batteryPercent = 89, address = "AA",
            ),
            hasSavedDevice = true,
            snackbarHostState = SnackbarHostState(),
            onTare = {}, onToggleTimer = {}, onResetTimer = {}, onConnect = {}, onOpenScan = {}, onOpenSettings = {},
        )
    }
}

@Preview(showBackground = true, heightDp = 780)
@Composable
private fun DashboardBluetoothOffPreview() {
    OpenScalesTheme(dynamicColor = false) {
        DashboardScreen(
            state = ScaleState(name = "TIMEMORE_Dot", model = ScaleModel.DOT, address = "AA"),
            hasSavedDevice = true,
            snackbarHostState = SnackbarHostState(),
            onTare = {}, onToggleTimer = {}, onResetTimer = {}, onConnect = {}, onOpenScan = {}, onOpenSettings = {},
            prerequisite = BlePrerequisite.BLUETOOTH_OFF,
        )
    }
}

@Preview(showBackground = true, widthDp = 780, heightDp = 360)
@Composable
private fun DashboardNoPermissionLandscapePreview() {
    OpenScalesTheme(dynamicColor = false) {
        DashboardScreen(
            state = ScaleState(),
            hasSavedDevice = false,
            snackbarHostState = SnackbarHostState(),
            onTare = {}, onToggleTimer = {}, onResetTimer = {}, onConnect = {}, onOpenScan = {}, onOpenSettings = {},
            prerequisite = BlePrerequisite.NO_PERMISSION,
        )
    }
}

@Preview(showBackground = true, widthDp = 780, heightDp = 360)
@Composable
private fun DashboardLandscapeDisconnectedPreview() {
    OpenScalesTheme(dynamicColor = false) {
        DashboardScreen(
            state = ScaleState(),
            hasSavedDevice = true,
            snackbarHostState = SnackbarHostState(),
            onTare = {}, onToggleTimer = {}, onResetTimer = {}, onConnect = {}, onOpenScan = {}, onOpenSettings = {},
        )
    }
}
