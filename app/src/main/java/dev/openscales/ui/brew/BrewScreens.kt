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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
) {
    val unit = ui.scale.unit
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = { BrewTopBar(recipe.title.resolve(), stringResource(R.string.brew_beans_title), onBack, onEdit) },
        bottomBar = {
            val tare = rememberPressAction(ui.scale.isReady && triggerOnPress, onTare)
            Row(
                modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilledTonalButton(
                    onClick = tare.onClick,
                    enabled = ui.scale.isReady,
                    modifier = Modifier.weight(1f).heightIn(min = ButtonDefaults.MediumContainerHeight).then(tare.modifier),
                ) { Text(stringResource(R.string.tare), style = MaterialTheme.typography.titleMedium) }
                Button(
                    onClick = onNext,
                    enabled = ui.canFixDose,
                    modifier = Modifier.weight(1f).heightIn(min = ButtonDefaults.MediumContainerHeight),
                ) { Text(stringResource(R.string.brew_next), style = MaterialTheme.typography.titleMedium) }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
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
            Text(
                stringResource(
                    R.string.brew_dose_water,
                    stepWeightWithUnit(recipe.defaultDoseG.toDouble(), unit),
                    stepWeightWithUnit(recipe.totalWaterG(base), unit),
                ),
                style = MaterialTheme.typography.titleMedium,
            )
            recipe.description?.let {
                Text(it.resolve(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            HorizontalDivider()
            RecipePreview(recipe, base, unit)
        }
    }
}

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
    // Распределение — по текущему весу, а без связи — по последнему полученному. До «Старт» весы ещё не
    // оттарированы (на них воронка с зерном) — налитым считается ноль, шаги показывают полные цели.
    val poured = if (ui.phase == BrewPhase.READY) 0.0 else ui.weightG ?: ui.lastWeightG
    val water = PourDistribution.values(recipe.targetsG(dose), poured, mode)
    val waterIndex = recipe.items.runningFold(-1) { n, item -> if ((item as? RecipeItem.Step)?.targetG != null) n + 1 else n }
        .drop(1)
    val listState = rememberLazyListState()
    val descriptionItems = if (recipe.description != null) 1 else 0
    LaunchedEffect(current) {
        // Плавно к верхнему краю; у конца списка LazyList упирается сам.
        // Над шагами может стоять описание рецепта — первый элемент списка, номера шагов сдвинуты на него.
        if (current != null) listState.animateScrollToItem(current + descriptionItems)
    }
    val board = @Composable { modifier: Modifier ->
        PourBoard(recipe, timeline, ui, dose, mode, slashedZero, modifier)
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
        bottomBar = { StepsControls(ui, triggerOnPress, onTare, onStart, onPause, onStop, onTimeDrawn) },
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
                            water = item.targetG?.let { water[waterIndex[index]] },
                            unit = unit,
                            modifier = faded,
                        )
                        is RecipeItem.Hint -> HintRow(item.text.resolve(), faded)
                    }
                }
            }
        }
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
            if (maxWidth > maxHeight) {
                // Окно шире своей высоты: табло слева на всю высоту, список справа.
                Row(Modifier.fillMaxSize()) {
                    Column(Modifier.weight(1f).fillMaxHeight().padding(start = 16.dp, top = 8.dp, bottom = 8.dp)) {
                        ReconnectBanner(ui.scale)
                        board(Modifier.fillMaxWidth().weight(1f))
                    }
                    list(Modifier.weight(1f).fillMaxHeight())
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

/**
 * Табло пролива: текущий по времени шаг и остаток времени в нём; крупно, шрифтом показаний, — значение шага воды
 * (цвет: лей / хватит / перелив); мелко — цель, общий вес и поток. Без связи — по последнему полученному весу.
 */
@Composable
private fun PourBoard(
    recipe: Recipe,
    timeline: RecipeTimeline,
    ui: BrewUi,
    doseG: Double,
    mode: StepWeightMode,
    slashedZero: Boolean,
    modifier: Modifier = Modifier,
) {
    val unit = ui.scale.unit
    val seconds = ui.timelineSeconds
    val current = timeline.stepAt(seconds)
    val weightG = ui.weightG ?: ui.lastWeightG
    val focus = PourFocus.of(timeline, doseG, weightG, seconds)
    val firstStep = recipe.items.indexOfFirst { it is RecipeItem.Step }
    // Шаг, у которого крупно время до конца, а не вода: текущий по времени, а в ожидании пролива — первый.
    val timeStep = when {
        current != null && seconds != null -> current
        ui.phase == BrewPhase.ARMED -> firstStep
        else -> null
    }?.takeIf { (recipe.items[it] as RecipeItem.Step).showTime }
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
                if (ui.phase == BrewPhase.FINISHED) {
                    // Время рецепта вышло: итог варки — вес на весах сейчас и общее время.
                    BigReading(
                        text = formatReadingWeight(weightG, unit),
                        unitSymbol = stringResource(unit.symbolRes()),
                        color = colors.onSurface,
                        slashedZero = slashedZero,
                    )
                    BoardCaption(stringResource(R.string.brew_on_scale))
                    BoardCaption(stringResource(R.string.brew_total_time, formatTime(ui.seconds)))
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
            }
        }
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
) {
    TopAppBar(
        title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        subtitle = { Text(subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back)) }
        },
        actions = {
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
    onTimeDrawn: ((Int) -> Unit)? = null,
) {
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

    Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!ui.started) {
                FilledTonalButton(
                    onClick = tare.onClick,
                    enabled = tareEnabled,
                    modifier = height.weight(1f).then(tare.modifier),
                ) { label(stringResource(R.string.tare)) }
                Button(
                    onClick = start.onClick,
                    enabled = startEnabled,
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    modifier = height.weight(1.3f).then(start.modifier),
                ) { label(stringResource(if (ui.phase == BrewPhase.ARMED) R.string.brew_waiting else R.string.start)) }
            } else {
                Button(
                    onClick = pause.onClick,
                    enabled = pauseEnabled,
                    modifier = height.weight(1.3f).then(pause.modifier),
                ) { label(stringResource(if (ui.phase == BrewPhase.PAUSED) R.string.brew_resume else R.string.pause)) }
            }
            OutlinedButton(onClick = onStop, modifier = height.weight(1f)) { label(stringResource(R.string.brew_stop)) }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp, start = 4.dp, end = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            val small = MaterialTheme.typography.bodyMedium
            val color = MaterialTheme.colorScheme.onSurfaceVariant
            Text(
                stringResource(
                    R.string.value_with_unit,
                    formatWeight(ui.weightG?.let { ui.scale.weight }, ui.scale.unit),
                    stringResource(ui.scale.unit.symbolRes()),
                ),
                style = small, color = color,
            )
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
