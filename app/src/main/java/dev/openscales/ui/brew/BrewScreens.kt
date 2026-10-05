package dev.openscales.ui.brew

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.getValue
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.foundation.Canvas
import kotlinx.coroutines.delay
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Dialpad
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.openscales.R
import dev.openscales.protocol.TimerState
import dev.openscales.protocol.WeightUnit
import dev.openscales.recipe.BuiltInRecipes
import dev.openscales.recipe.PourDistribution
import dev.openscales.recipe.Recipe
import dev.openscales.recipe.RecipeItem
import dev.openscales.recipe.RecipeTimeline
import dev.openscales.recipe.StepWater
import dev.openscales.recipe.StepWeightMode
import dev.openscales.recipe.PourFocus
import dev.openscales.recipe.TargetState
import dev.openscales.recipe.fromGrams
import dev.openscales.recipe.formatStepWeight
import dev.openscales.recipe.formatReadingWeight
import dev.openscales.session.ConnectionPhase
import dev.openscales.session.ScaleState
import dev.openscales.ui.components.BusyIndicator
import dev.openscales.ui.components.formatTime
import dev.openscales.ui.components.FLOW_PLACEHOLDER_DIGITS
import dev.openscales.ui.components.flowSymbolRes
import dev.openscales.ui.components.formatWeight
import dev.openscales.ui.components.labelRes
import dev.openscales.ui.components.rememberPressAction
import dev.openscales.ui.components.symbolRes
import dev.openscales.ui.recipes.HintRow
import dev.openscales.ui.recipes.StepCard
import dev.openscales.ui.recipes.resolve
import dev.openscales.ui.recipes.stepWeightWithUnit
import dev.openscales.ui.theme.OpenScalesTheme
import dev.openscales.ui.theme.UnitTextStyle
import dev.openscales.ui.theme.DigitsTextStyle
import dev.openscales.ui.theme.WeightTextStyle
import dev.openscales.ui.theme.withSlashedZero

/** Шаг «Зерно»: пользователь насыпает зерно и фиксирует дозу кнопкой «Далее». */
@Composable
fun BeansScreen(
    recipe: Recipe,
    ui: BrewUi,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onTare: () -> Unit,
    onNext: () -> Unit,
    triggerOnPress: Boolean = true,
    slashedZero: Boolean = true,
    /** Свой рецепт: кнопка «Изменить» в заголовке; у встроенного — `null`, кнопки нет. */
    onEdit: (() -> Unit)? = null,
    /** Ручной ввод дозы: зерно уже унесли с весов, а «Далее» не нажали. */
    onManualDose: () -> Unit = {},
) {
    val unit = ui.scale.unit
    // Окно шире своей высоты: кнопки столбцом справа, а не рядом внизу — высоты и так мало.
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth > maxHeight
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                BrewTopBar(
                    recipe.title.resolve(),
                    stringResource(R.string.brew_beans_title),
                    onBack,
                    onEdit,
                    onManualDose = onManualDose,
                    manualDoseEnabled = ui.scale.isReady,
                )
            },
            bottomBar = { if (!wide) BeansControls(ui, triggerOnPress, onTare, onNext, vertical = false) },
        ) { padding ->
            Row(Modifier.fillMaxSize().padding(padding)) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    ReconnectBanner(ui.scale)
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.extraLarge,
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 16.dp, horizontal = 16.dp),
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            // Тот же формат, что на вкладке «Весы»; на узком экране с крупным шрифтом число уменьшается.
                            BasicText(
                                formatWeight(ui.weightG?.let { ui.scale.weight }, unit),
                                style = WeightTextStyle.withSlashedZero(slashedZero).copy(color = MaterialTheme.colorScheme.onSurface),
                                maxLines = 1,
                                softWrap = false,
                                autoSize = TextAutoSize.StepBased(minFontSize = 24.sp, maxFontSize = WeightTextStyle.fontSize),
                                modifier = Modifier.alignByBaseline().weight(1f, fill = false),
                            )
                            Text(
                                stringResource(unit.symbolRes()),
                                style = UnitTextStyle,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.alignByBaseline().padding(start = 8.dp),
                            )
                        }
                    }
                    // Цели и вода — от насыпанного, а пока на весах пусто или весов нет — от дозы рецепта.
                    val base = ui.previewDoseG(recipe.defaultDoseG.toDouble())
                    // Рецепт как есть — от весов не зависит; ниже, если на весах уже есть зерно, — пересчёт на него.
                    val defaultDose = recipe.defaultDoseG.toDouble()
                    // Одна строка, чтобы экран не дёргался: пока на весах есть зерно — пересчёт на него, иначе рецепт как есть.
                    // Доза рецепта и вода от насыпанного в одной строке не смешиваются.
                    Text(
                        if (ui.weightG != null && ui.weightG >= BrewUi.MIN_DOSE_G) {
                            stringResource(
                                R.string.brew_scaled_water,
                                stepWeightWithUnit(ui.weightG, unit),
                                stepWeightWithUnit(recipe.totalWaterG(ui.weightG), unit),
                            )
                        } else {
                            stringResource(
                                R.string.brew_dose_water,
                                stepWeightWithUnit(defaultDose, unit),
                                stepWeightWithUnit(recipe.totalWaterG(defaultDose), unit),
                            )
                        },
                        style = MaterialTheme.typography.titleMedium,
                    )
                    recipe.description?.let {
                        Text(it.resolve(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    HorizontalDivider()
                    RecipePreview(recipe, base, unit)
                }
                if (wide) BeansControls(ui, triggerOnPress, onTare, onNext, vertical = true)
            }
        }
    }
}

/** Кнопки «Зерна»: «Тара» и «Далее» — рядом внизу или, в широком окне ([vertical]), столбцом у правого края. */
@Composable
private fun BeansControls(ui: BrewUi, triggerOnPress: Boolean, onTare: () -> Unit, onNext: () -> Unit, vertical: Boolean) {
    val tare = rememberPressAction(ui.scale.isReady && triggerOnPress, onTare)
    val height = Modifier.heightIn(min = ButtonDefaults.MediumContainerHeight)
    val buttons = @Composable { slot: Modifier ->
        FilledTonalButton(
            onClick = tare.onClick,
            enabled = ui.scale.isReady,
            modifier = slot.then(height).then(tare.modifier),
        ) { Text(stringResource(R.string.tare), style = MaterialTheme.typography.titleMedium) }
        Button(
            onClick = onNext,
            enabled = ui.canFixDose,
            modifier = slot.then(height),
        ) { Text(stringResource(R.string.brew_next), style = MaterialTheme.typography.titleMedium) }
    }
    if (vertical) {
        SideControls { buttons(Modifier.fillMaxWidth()) }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) { buttons(Modifier.weight(1f)) }
    }
}

/**
 * Столбец кнопок у правого края широкого окна: на всю высоту под заголовком, кнопки по центру; если кнопки не
 * помещаются по высоте (крупный шрифт) — прокручивается. Вырез экрана и системные панели уже учтены отступами
 * `Scaffold`, внутри которого стоит столбец.
 */
@Composable
private fun SideControls(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .width(SIDE_CONTROLS_WIDTH)
            .fillMaxHeight()
            .verticalScroll(rememberScrollState())
            .padding(start = 8.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
        content = content,
    )
}

/** Ширина столбца кнопок: «Лейте, когда готовы» помещается при обычном шрифте. */
private val SIDE_CONTROLS_WIDTH = 200.dp

/**
 * Компактный предпросмотр рецепта на «Зерне»: одна строка на шаг — время начала, название, цель от [baseG];
 * подсказки мелко под своим шагом; без карточек и комментариев (они — на экране «Шаги»).
 */
@Composable
private fun RecipePreview(recipe: Recipe, baseG: Double, unit: WeightUnit) {
    val timeline = remember(recipe) { RecipeTimeline(recipe) }
    val lastStep = recipe.items.indexOfLast { it is RecipeItem.Step }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(bottom = 8.dp)) {
        recipe.items.forEachIndexed { index, item ->
            when (item) {
                is RecipeItem.Step -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        formatTime(timeline.startOf(index)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = muted,
                        // С крупным шрифтом время шире колонки — отодвигает название, но не слипается с ним.
                        modifier = Modifier.widthIn(min = PREVIEW_TIME_WIDTH).padding(end = PREVIEW_TIME_GAP),
                    )
                    Text(item.title.resolve(), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    val right = when {
                        item.targetG != null -> stepWeightWithUnit(recipe.scaled(item.targetG, baseG), unit)
                        index == lastStep -> stringResource(R.string.brew_until, formatTime(timeline.total))
                        else -> null
                    }
                    if (right != null) {
                        Text(
                            right,
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (item.targetG != null) MaterialTheme.colorScheme.onSurface else muted,
                        )
                    }
                }
                is RecipeItem.Hint -> Text(
                    item.text.resolve(),
                    style = MaterialTheme.typography.labelMedium,
                    color = muted,
                    modifier = Modifier.padding(start = PREVIEW_TIME_WIDTH + PREVIEW_TIME_GAP),
                )
            }
        }
    }
}

private val PREVIEW_TIME_WIDTH = 44.dp

/** Полоса налива внизу табло. */
private val POUR_BAR_HEIGHT = 24.dp

/** Отметка идеального уровня: узкая и чуть выше полосы, чтобы читалась и поверх заполнения. */
private val POUR_MARK_WIDTH = 4.dp
private val POUR_MARK_OVERHANG = 3.dp

/** Как часто обновлять идеальный уровень на полосе налива. */
private const val PACE_TICK_MS = 100L

/** Кнопка «Тара» на табло — крупная, как число, чтобы попасть, не глядя. */
private val TARE_BUTTON_HEIGHT = 96.dp
private val PREVIEW_TIME_GAP = 8.dp

/**
 * Шаг «Шаги»: табло пролива, под ним список шагов, внизу закреплённые кнопки и мелкая строка веса и времени.
 * Табло читается краем глаза, пока смотришь на чайник; список вторичен и при смене шага встаёт к нему сразу.
 */
@Composable
fun StepsScreen(
    recipe: Recipe,
    timeline: RecipeTimeline,
    ui: BrewUi,
    mode: StepWeightMode,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onTare: () -> Unit,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onStop: () -> Unit,
    /** «Тара» на шаге «Тара» — с табло и из ряда кнопок. */
    onTarePart: () -> Unit = {},
    /** Время варки в миллисекундах для идеального уровня на полосе; `null` — считать по целым секундам. */
    elapsedMs: () -> Long? = { null },
    triggerOnPress: Boolean = true,
    slashedZero: Boolean = true,
    /** Только debug: время варки действительно отрисовано (проба «замерло и догнало»). */
    onTimeDrawn: ((Int) -> Unit)? = null,
    /** «Сменить рецепт» в заголовке — только до «Старт»; `null` — иконки нет. */
    onSwap: (() -> Unit)? = null,
) {
    val unit = ui.scale.unit
    val dose = ui.doseG ?: recipe.defaultDoseG.toDouble()
    val seconds = ui.timelineSeconds
    val current = timeline.stepAt(seconds)
    // Распределение — по текущему весу, а без связи — по последнему полученному, у каждой части рецепта — от своей
    // тары. До «Старт» весы ещё не оттарированы (на них воронка с зерном) — налитым считается ноль.
    val water = PourDistribution.values(recipe, dose, ui::pouredIn, mode)
    val listState = rememberLazyListState()
    val descriptionItems = if (recipe.description != null) 1 else 0
    LaunchedEffect(current) {
        // Плавно к верхнему краю; у конца списка LazyList упирается сам.
        // Над шагами может стоять описание рецепта — первый элемент списка, номера шагов сдвинуты на него.
        if (current != null) listState.animateScrollToItem(current + descriptionItems)
    }
    val board = @Composable { modifier: Modifier ->
        PourBoard(recipe, timeline, ui, dose, mode, slashedZero, triggerOnPress, onTarePart, elapsedMs, modifier)
    }

    // Окно шире своей высоты: табло слева на всю высоту, список по центру, кнопки столбцом справа.
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth > maxHeight
        val controls = @Composable { vertical: Boolean ->
            StepsControls(ui, triggerOnPress, onTare, onStart, onPause, onStop, onTarePart, onTimeDrawn, vertical)
        }
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                BrewTopBar(
                    recipe.title.resolve(),
                    stringResource(R.string.brew_dose, stepWeightWithUnit(dose, unit)),
                    onBack,
                    onSwap = onSwap,
                )
            },
            bottomBar = { if (!wide) controls(false) },
        ) { padding ->
            val list = @Composable { modifier: Modifier ->
                LazyColumn(
                    state = listState,
                    modifier = modifier,
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // Описание рецепта (помол, температура) — как на «Зерне», чтобы не держать его в голове во время варки.
                    recipe.description?.let { description ->
                        item(key = "description") {
                            Text(
                                description.resolve(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                            )
                        }
                    }
                    itemsIndexed(recipe.items) { index, item ->
                        // Пройденные шаги гаснут плавно; числа при этом не анимируются.
                        val alpha by animateFloatAsState(if (timeline.isFaded(index, seconds)) FADED_ALPHA else 1f, label = "fade")
                        val faded = Modifier.alpha(alpha)
                        when (item) {
                            is RecipeItem.Step -> StepCard(
                                title = item.title.resolve(),
                                note = item.note?.resolve(),
                                range = formatTime(timeline.startOf(index)) + "–" + formatTime(timeline.endOf(index)),
                                remaining = if (index == current && seconds != null) timeline.remainingIn(index, seconds) else null,
                                water = water[index],
                                unit = unit,
                                modifier = faded,
                                isTare = item.tare,
                            )
                            is RecipeItem.Hint -> HintRow(item.text.resolve(), faded)
                        }
                    }
                }
            }
            Box(Modifier.fillMaxSize().padding(padding)) {
                if (wide) {
                    Row(Modifier.fillMaxSize()) {
                        Column(Modifier.weight(1f).fillMaxHeight().padding(start = 16.dp, top = 8.dp, bottom = 8.dp)) {
                            ReconnectBanner(ui.scale)
                            board(Modifier.fillMaxWidth().weight(1f))
                        }
                        list(Modifier.weight(1f).fillMaxHeight())
                        controls(true)
                    }
                } else {
                    Column(Modifier.fillMaxSize()) {
                        Column(Modifier.padding(horizontal = 16.dp)) {
                            ReconnectBanner(ui.scale)
                            board(Modifier.fillMaxWidth())
                        }
                        list(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

/**
 * Табло пролива: текущий по времени шаг и остаток времени в нём; крупно, шрифтом показаний, — значение шага воды
 * (цвет: лей / хватит / перелив); мелко — цель, общий вес и поток. Без связи — по последнему полученному весу.
 * Отсчёт шага без воды и итог варки — только когда вода шага воды улеглась ([BrewUi.holdWater]), до того — вода.
 */
@Composable
private fun PourBoard(
    recipe: Recipe,
    timeline: RecipeTimeline,
    ui: BrewUi,
    doseG: Double,
    mode: StepWeightMode,
    slashedZero: Boolean,
    triggerOnPress: Boolean,
    onTarePart: () -> Unit,
    elapsedMs: () -> Long?,
    modifier: Modifier = Modifier,
) {
    val unit = ui.scale.unit
    val seconds = ui.timelineSeconds
    val current = timeline.stepAt(seconds)
    val weightG = ui.weightG ?: ui.lastWeightG
    val focus = PourFocus.of(timeline, doseG, weightG, seconds, ui.tare)
    val firstStep = recipe.items.indexOfFirst { it is RecipeItem.Step }
    // Шаг, у которого крупно время до конца, а не вода: текущий по времени, а в ожидании пролива — первый.
    // Пока вода не улеглась (ещё льётся или недолита), крупно остаётся вода, а остаток шага — в углу.
    val timeStep = when {
        current != null && seconds != null -> current
        ui.phase == BrewPhase.ARMED -> firstStep
        else -> null
    }?.takeIf { (recipe.items[it] as RecipeItem.Step).showTime && !ui.holdWater }
    // До пролива — первый шаг целиком, после конца времени — «Готово» и общая длительность.
    val (stepTitle, stepTime) = when {
        current != null && seconds != null ->
            (recipe.items[current] as RecipeItem.Step).title.resolve() to timeline.remainingIn(current, seconds)
        ui.started -> stringResource(R.string.brew_done) to timeline.total
        else -> (recipe.items[firstStep] as RecipeItem.Step).title.resolve() to timeline.endOf(firstStep)
    }
    val colors = MaterialTheme.colorScheme
    // До «Старт» табло пустое, с подсказкой нажать «Старт». Содержимое при этом раскладывается невидимым —
    // высота табло не меняется и список под ним не прыгает, когда варка начинается.
    val beforeStart = ui.phase == BrewPhase.READY
    Surface(modifier = modifier, shape = MaterialTheme.shapes.extraLarge, color = colors.surfaceContainerHighest) {
        // Как Surface: табло занимает всю отведённую высоту (левая колонка в альбомной раскладке).
        Box(propagateMinConstraints = true) {
            if (beforeStart) {
                Box(Modifier.matchParentSize(), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(R.string.brew_press_start),
                        style = MaterialTheme.typography.headlineSmall,
                        color = colors.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(20.dp),
                    )
                }
            }
            Column(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp).alpha(if (beforeStart) 0f else 1f),
                verticalArrangement = Arrangement.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stepTitle,
                        style = MaterialTheme.typography.headlineSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    // Крупно уже остаток шага — в углу итог последнего шага с водой (по настройке «Вес в шагах
                    // рецепта»): видно, попал ли прошлый пролив в рубеж, пока ждёшь следующего.
                    val corner = if (timeStep != null) focus else null
                    Text(
                        if (corner != null) {
                            stringResource(
                                R.string.value_with_unit,
                                formatReadingWeight(if (mode == StepWeightMode.REMAINING) corner.leftG else weightG, unit),
                                stringResource(unit.symbolRes()),
                            )
                        } else if (timeStep != null) {
                            ""
                        } else {
                            formatTime(stepTime)
                        },
                        style = DigitsTextStyle.withSlashedZero(slashedZero),
                        color = if (corner != null) corner.state.color() else colors.onSurface,
                    )
                }
                if (ui.phase == BrewPhase.FINISHED && !ui.holdWater) {
                    // Время рецепта вышло и вода улеглась: итог варки — вес на весах сейчас, общее время и цель рецепта.
                    BigReading(
                        text = formatReadingWeight(weightG, unit),
                        unitSymbol = stringResource(unit.symbolRes()),
                        color = colors.onSurface,
                        slashedZero = slashedZero,
                    )
                    BoardCaption(stringResource(R.string.brew_on_scale_time, formatTime(ui.seconds)))
                    // Попал ли в рецепт: цель последней части. Без цели строка пустая — высота табло та же.
                    BoardCaption(
                        recipe.finalTargetG(doseG)
                            ?.let { stringResource(R.string.brew_of_recipe, stepWeightWithUnit(it, unit)) }
                            .orEmpty(),
                    )
                } else if (ui.awaitingTare) {
                    // Часть ждёт тары: вместо воды — крупная кнопка. Весы тарирует только нажатие.
                    val part = timeline.partAt(seconds)
                    val tareStep = recipe.items.indices.first { recipe.partOf[it] == part && (recipe.items[it] as? RecipeItem.Step)?.tare == true }
                    val press = rememberPressAction(ui.canTarePart && triggerOnPress, onTarePart)
                    FilledTonalButton(
                        onClick = press.onClick,
                        enabled = ui.canTarePart,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp).heightIn(min = TARE_BUTTON_HEIGHT).then(press.modifier),
                    ) {
                        Text(stringResource(R.string.tare), style = MaterialTheme.typography.displaySmall)
                    }
                    (recipe.items[tareStep] as RecipeItem.Step).note?.let { BoardCaption(it.resolve()) }
                    BoardCaption(totalAndFlow(ui, weightG))
                } else if (timeStep != null) {
                    val left = if (seconds != null) timeline.remainingIn(timeStep, seconds) else timeline.endOf(timeStep) - timeline.startOf(timeStep)
                    BigReading(
                        text = formatTime(left),
                        unitSymbol = null,
                        color = if (seconds != null && left <= FINAL_SECONDS) colors.primary else colors.onSurface,
                        slashedZero = slashedZero,
                    )
                    val next = (timeStep + 1..recipe.items.lastIndex).firstOrNull { recipe.items[it] is RecipeItem.Step }
                    BoardCaption(
                        if (next != null) {
                            stringResource(R.string.brew_until_step, (recipe.items[next] as RecipeItem.Step).title.resolve())
                        } else {
                            stringResource(R.string.brew_until_end)
                        },
                    )
                    BoardCaption(totalAndFlow(ui, weightG))
                } else if (focus != null) {
                    val valueG = if (mode == StepWeightMode.REMAINING) focus.leftG else weightG
                    BigReading(
                        text = formatReadingWeight(valueG, unit),
                        unitSymbol = stringResource(unit.symbolRes()),
                        color = focus.state.color(),
                        slashedZero = slashedZero,
                    )
                    val target = stringResource(
                        if (mode == StepWeightMode.REMAINING) R.string.brew_left_of else R.string.brew_poured_of,
                        stepWeightWithUnit(focus.targetG, unit),
                    )
                    // Вода идёт не в текущий по времени шаг (запоздалый пролив, пауза) — назвать её шаг.
                    val waterStep = (recipe.items[focus.itemIndex] as RecipeItem.Step).title.resolve()
                    BoardCaption(if (focus.itemIndex != current) "$waterStep · $target" else target)
                    BoardCaption(totalAndFlow(ui, weightG))
                }
                // Полоса налива — только когда табло показывает воду; в остальных видах её место занято,
                // чтобы табло не меняло высоту при смене шага. Без анимации: сразу текущий вес.
                val bar = focus.takeIf { (ui.phase != BrewPhase.FINISHED || ui.holdWater) && !ui.awaitingTare && timeStep == null }
                // Идеальный уровень — раз в 0,1 с по точному времени, пока идёт пролив; читается только полосой,
                // поэтому табло не перекомпонуется десять раз в секунду.
                val running = bar != null && ui.phase == BrewPhase.RUNNING
                val preciseSeconds = remember { mutableDoubleStateOf(seconds?.toDouble() ?: 0.0) }
                LaunchedEffect(running) {
                    while (running) {
                        elapsedMs()?.let { preciseSeconds.doubleValue = it / 1_000.0 }
                        delay(PACE_TICK_MS)
                    }
                }
                PourBar(
                    fraction = bar?.fraction ?: 0f,
                    pace = {
                        val at = if (running && elapsedMs() != null) preciseSeconds.doubleValue else seconds?.toDouble()
                        bar?.paceFraction(timeline, at) ?: 0f
                    },
                    over = bar?.state == TargetState.OVER,
                    modifier = Modifier.alpha(if (bar != null) 1f else 0f),
                )
            }
        }
    }
}

/**
 * Полоса налива: налитое в шаг воды ([fraction]) и поверх — отметка идеального уровня ([pace] — сколько было бы
 * налито к этой секунде при ровном проливе). Отстаёшь — край налитого не дошёл до отметки; опережаешь — налитое ушло
 * за неё. Без анимации: значения показываются сразу.
 */
@Composable
private fun PourBar(fraction: Float, pace: () -> Float, over: Boolean, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val track = colors.outlineVariant
    val fill = if (over) colors.error else colors.primary
    val markColor = colors.onSurface
    // Одна полоса, скруглённая только снаружи: дорожка и налитое режутся по общему контуру, поэтому граница налитого
    // прямая, без выемки (у системного индикатора скруглённые концы оставляли разрыв).
    // Идеальный уровень читается в фазе рисования — десять обновлений в секунду не перекомпонуют табло.
    Canvas(modifier.fillMaxWidth().padding(top = 10.dp).height(POUR_BAR_HEIGHT + POUR_MARK_OVERHANG * 2)) {
        val barHeight = POUR_BAR_HEIGHT.toPx()
        val top = (size.height - barHeight) / 2
        val radius = CornerRadius(barHeight / 2)
        val outline = Path().apply {
            addRoundRect(RoundRect(0f, top, size.width, top + barHeight, radius))
        }
        val paceX = size.width * pace()
        clipPath(outline) {
            drawRect(track, topLeft = Offset(0f, top), size = Size(size.width, barHeight))
            drawRect(fill, topLeft = Offset(0f, top), size = Size(size.width * fraction, barHeight))
        }
        val markWidth = POUR_MARK_WIDTH.toPx()
        val markX = (paceX - markWidth / 2).coerceIn(0f, size.width - markWidth)
        drawRoundRect(
            markColor,
            topLeft = Offset(markX, 0f),
            size = Size(markWidth, size.height),
            cornerRadius = CornerRadius(markWidth / 2),
        )
    }
}

/**
 * Крупное число табло: вес или время. Забирает оставшуюся высоту, но не больше нужного: в невысокой левой
 * колонке альбомной раскладки оно ужимается, а подписи под ним остаются видны.
 */
@Composable
private fun ColumnScope.BigReading(text: String, unitSymbol: String?, color: Color, slashedZero: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().weight(1f, fill = false).padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        // Крупно, но без обрезки на узком экране и в левой колонке альбомной раскладки.
        BasicText(
            text,
            style = WeightTextStyle.withSlashedZero(slashedZero).copy(color = color),
            maxLines = 1,
            softWrap = false,
            autoSize = TextAutoSize.StepBased(minFontSize = 32.sp, maxFontSize = WeightTextStyle.fontSize),
            modifier = Modifier.alignByBaseline().weight(1f, fill = false),
        )
        if (unitSymbol != null) {
            Text(
                unitSymbol,
                style = UnitTextStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.alignByBaseline().padding(start = 8.dp),
            )
        }
    }
}

@Composable
private fun BoardCaption(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** «всего 200.0 г · 0.0 г/с»: общий вес на весах и поток. */
@Composable
private fun totalAndFlow(ui: BrewUi, weightG: Double): String {
    val unit = ui.scale.unit
    val total = stringResource(
        R.string.brew_total,
        stringResource(
            R.string.value_with_unit,
            formatWeight(fromGrams(weightG, unit).toFloat(), unit),
            stringResource(unit.symbolRes()),
        ),
    )
    return "$total · ${flowText(ui)}"
}

/** «0.0 г/с»: поток; без связи — прочерк. */
@Composable
private fun flowText(ui: BrewUi): String = stringResource(
    R.string.value_with_unit,
    formatWeight(ui.scale.flowRate.takeIf { ui.scale.isReady }, ui.scale.unit, FLOW_PLACEHOLDER_DIGITS),
    stringResource(ui.scale.unit.flowSymbolRes()),
)

/** Цвет значения воды: обычный, пока до цели далеко; акцентный у цели; цвет ошибки при переливе. */
@Composable
private fun TargetState.color(): Color = when (this) {
    TargetState.BELOW -> MaterialTheme.colorScheme.onSurface
    TargetState.AT -> MaterialTheme.colorScheme.primary
    TargetState.OVER -> MaterialTheme.colorScheme.error
}

/** Последние секунды шага с крупным временем — число акцентного цвета. */
private const val FINAL_SECONDS = 5


private const val FADED_ALPHA = 0.45f

@Composable
private fun BrewTopBar(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    onEdit: (() -> Unit)? = null,
    onSwap: (() -> Unit)? = null,
    /** Ручной ввод дозы — только на «Зерне»; активен, только пока весы готовы. */
    onManualDose: (() -> Unit)? = null,
    manualDoseEnabled: Boolean = true,
) {
    TopAppBar(
        title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        subtitle = { Text(subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back)) }
        },
        actions = {
            if (onManualDose != null) {
                IconButton(onClick = onManualDose, enabled = manualDoseEnabled) {
                    Icon(Icons.Rounded.Dialpad, stringResource(R.string.brew_manual_dose))
                }
            }
            if (onSwap != null) {
                IconButton(onClick = onSwap) { Icon(Icons.Rounded.SwapHoriz, stringResource(R.string.brew_swap_title)) }
            }
            if (onEdit != null) {
                IconButton(onClick = onEdit) { Icon(Icons.Rounded.Edit, stringResource(R.string.recipe_action_edit)) }
            }
        },
    )
}

/**
 * Кнопки варки: до старта «Тара» и «Старт» (в ожидании пролива — «Начните пролив»), после — «Пауза»/«Продолжить»;
 * «Стоп» всегда. Ниже мелко вес и время варки.
 */
@Composable
private fun StepsControls(
    ui: BrewUi,
    triggerOnPress: Boolean,
    onTare: () -> Unit,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onStop: () -> Unit,
    onTarePart: () -> Unit,
    onTimeDrawn: ((Int) -> Unit)? = null,
    /** Широкое окно: кнопки столбцом у правого края, вес и время — под ними. */
    vertical: Boolean = false,
) {
    val tarePart = rememberPressAction(ui.canTarePart && triggerOnPress, onTarePart)
    val tareEnabled = ui.scale.isReady && ui.phase == BrewPhase.READY
    val startEnabled = ui.phase == BrewPhase.ARMED || ui.phase == BrewPhase.READY && ui.weightG != null
    val pauseEnabled = ui.phase == BrewPhase.RUNNING || ui.phase == BrewPhase.PAUSED
    val tare = rememberPressAction(tareEnabled && triggerOnPress, onTare)
    val start = rememberPressAction(startEnabled && triggerOnPress, onStart)
    val pause = rememberPressAction(pauseEnabled && triggerOnPress, onPause)
    val height = Modifier.heightIn(min = ButtonDefaults.MediumContainerHeight)
    // Длинная подпись на узком экране или с крупным шрифтом уменьшается, а не обрезается.
    val label = @Composable { text: String ->
        val style = MaterialTheme.typography.titleMedium
        BasicText(
            text,
            maxLines = 1,
            softWrap = false,
            style = style.copy(color = LocalContentColor.current),
            autoSize = TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = style.fontSize),
        )
    }

    // Кнопки одни и те же в обеих раскладках: в ряду делят ширину по весам, в столбце — на всю его ширину.
    val buttons = @Composable { slot: (Float) -> Modifier ->
        if (!ui.started) {
            FilledTonalButton(
                onClick = tare.onClick,
                enabled = tareEnabled,
                modifier = height.then(slot(1f)).then(tare.modifier),
            ) { label(stringResource(R.string.tare)) }
            Button(
                onClick = start.onClick,
                enabled = startEnabled,
                contentPadding = PaddingValues(horizontal = 12.dp),
                modifier = height.then(slot(1.3f)).then(start.modifier),
            ) { label(stringResource(if (ui.phase == BrewPhase.ARMED) R.string.brew_waiting else R.string.start)) }
        } else {
            if (ui.awaitingTare) {
                // То же, что кнопка на табло: пока часть рецепта ждёт тары.
                FilledTonalButton(
                    onClick = tarePart.onClick,
                    enabled = ui.canTarePart,
                    modifier = height.then(slot(1f)).then(tarePart.modifier),
                ) { label(stringResource(R.string.tare)) }
            }
            Button(
                onClick = pause.onClick,
                enabled = pauseEnabled,
                modifier = height.then(slot(1.3f)).then(pause.modifier),
            ) { label(stringResource(if (ui.phase == BrewPhase.PAUSED) R.string.brew_resume else R.string.pause)) }
        }
        OutlinedButton(onClick = onStop, modifier = height.then(slot(1f))) { label(stringResource(R.string.brew_stop)) }
    }
    val small = MaterialTheme.typography.bodyMedium
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    val weight = @Composable {
        Text(
            stringResource(
                R.string.value_with_unit,
                formatWeight(ui.weightG?.let { ui.scale.weight }, ui.scale.unit),
                stringResource(ui.scale.unit.symbolRes()),
            ),
            style = small, color = color,
        )
    }
    val time = @Composable {
        val shown = ui.seconds
        Text(
            formatTime(shown),
            style = small,
            color = color,
            modifier = if (onTimeDrawn == null) {
                Modifier
            } else {
                Modifier.drawWithContent {
                    drawContent()
                    onTimeDrawn(shown)
                }
            },
        )
    }

    if (vertical) {
        SideControls {
            buttons { Modifier.fillMaxWidth() }
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                weight()
                time()
            }
        }
    } else {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { buttons { Modifier.weight(it) } }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp, start = 4.dp, end = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                weight()
                time()
            }
        }
    }
}

/** Весы не готовы: переподключение идёт или связи нет. */
@Composable
private fun ReconnectBanner(state: ScaleState) {
    AnimatedVisibility(
        visible = !state.isReady,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
    ) {
        Card(
            modifier = Modifier.padding(bottom = 8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (state.reconnecting || state.phase.isBusy) BusyIndicator()
                Text(
                    stringResource(
                        when {
                            state.reconnecting && !state.phase.isBusy -> R.string.reconnecting
                            else -> state.phase.labelRes()
                        },
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    }
}

// region превью

private val previewScale = ScaleState(phase = ConnectionPhase.READY, name = "TIMEMORE_Dot", weight = 70f)

private fun previewUi(phase: BrewPhase, weight: Float = 70f, seconds: Int = 20) = BrewUi(
    phase = phase,
    scale = previewScale.copy(
        weight = weight,
        timerState = if (phase == BrewPhase.RUNNING) TimerState.RUNNING else TimerState.PAUSED,
        timeSeconds = seconds,
    ),
    doseG = 15.0,
    weightG = weight.toDouble(),
    lastWeightG = weight.toDouble(),
    seconds = if (phase == BrewPhase.READY || phase == BrewPhase.ARMED) 0 else seconds,
)

@Composable
private fun StepsPreview(
    ui: BrewUi,
    mode: StepWeightMode = StepWeightMode.REMAINING,
    recipe: Recipe = BuiltInRecipes.hoffmannV60,
) {
    OpenScalesTheme(dynamicColor = false) {
        StepsScreen(
            recipe = recipe, timeline = RecipeTimeline(recipe), ui = ui, mode = mode,
            snackbarHostState = SnackbarHostState(),
            onBack = {}, onTare = {}, onStart = {}, onPause = {}, onStop = {},
        )
    }
}

@Preview(showBackground = true, heightDp = 780, locale = "ru")
@Composable
private fun StepsReadyPreview() = StepsPreview(previewUi(BrewPhase.READY, weight = 0f))

@Preview(showBackground = true, heightDp = 780)
@Composable
private fun StepsArmedPreview() = StepsPreview(previewUi(BrewPhase.ARMED, weight = 0f))

@Preview(showBackground = true, heightDp = 780, locale = "ru")
@Composable
private fun StepsRunningRemainingPreview() = StepsPreview(previewUi(BrewPhase.RUNNING))

@Preview(showBackground = true, heightDp = 780, locale = "ru")
@Composable
private fun StepsRunningPouredPreview() = StepsPreview(previewUi(BrewPhase.RUNNING), StepWeightMode.POURED)

/** Аэропресс на 1:20: идёт «Подождите» с признаком времени — крупно «0:40» до «Взболтайте». */
@Preview(showBackground = true, heightDp = 780, locale = "ru")
@Composable
private fun StepsAeropressWaitPreview() =
    StepsPreview(previewUi(BrewPhase.RUNNING, weight = 200f, seconds = 80), recipe = BuiltInRecipes.hoffmannAeropress)

/** Последние секунды шага — акцентный цвет. */
@Preview(showBackground = true, heightDp = 780, locale = "ru")
@Composable
private fun StepsAeropressFinalSecondsPreview() =
    StepsPreview(previewUi(BrewPhase.RUNNING, weight = 200f, seconds = 116), recipe = BuiltInRecipes.hoffmannAeropress)

@Preview(showBackground = true, heightDp = 780)
@Composable
private fun StepsFinishedPreview() = StepsPreview(previewUi(BrewPhase.FINISHED, weight = 252f, seconds = 210))

@Preview(showBackground = true, heightDp = 780, locale = "ru")
@Composable
private fun BeansPreview() {
    OpenScalesTheme(dynamicColor = false) {
        BeansScreen(
            recipe = BuiltInRecipes.hoffmannV60,
            ui = previewUi(BrewPhase.BEANS, weight = 12f),
            snackbarHostState = SnackbarHostState(),
            onBack = {}, onTare = {}, onNext = {},
        )
    }
}

@Preview(showBackground = true, heightDp = 780)
@Composable
private fun BeansNoScalePreview() {
    OpenScalesTheme(dynamicColor = false) {
        BeansScreen(
            recipe = BuiltInRecipes.hoffmannV60,
            ui = BrewUi(BrewPhase.BEANS, ScaleState(), null, null, 0.0, 0),
            snackbarHostState = SnackbarHostState(),
            onBack = {}, onTare = {}, onNext = {},
        )
    }
}

// endregion
