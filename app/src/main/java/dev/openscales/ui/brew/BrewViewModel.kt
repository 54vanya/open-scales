package dev.openscales.ui.brew

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.openscales.protocol.TimerState
import dev.openscales.recipe.PourDetector
import dev.openscales.recipe.PourFocus
import dev.openscales.recipe.PourStill
import dev.openscales.recipe.TargetState
import dev.openscales.recipe.Recipe
import dev.openscales.recipe.RecipeItem
import dev.openscales.recipe.RecipeTimeline
import dev.openscales.recipe.TareState
import dev.openscales.recipe.toGrams
import dev.openscales.session.ScaleRepository
import dev.openscales.session.ScaleState
import dev.openscales.sound.StepSignal
import dev.openscales.ui.commandErrorRes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Фаза варки. [PAUSED] не хранится отдельно: после начала пролива идёт ли время — решает общий таймер
 * приложения, поэтому пауза с корпуса весов (при синхронизации таймера) отражается сама.
 */
enum class BrewPhase { BEANS, READY, ARMED, RUNNING, PAUSED, FINISHED }

/** Всё, что рисует экран варки. Вес и доза — в граммах, в единицы весов переводит показ. */
data class BrewUi(
    val phase: BrewPhase,
    val scale: ScaleState,
    /** Доза, зафиксированная кнопкой «Далее». */
    val doseG: Double?,
    /** Текущий вес с весов; `null` — весы не готовы. */
    val weightG: Double?,
    /** Последний полученный вес: по нему распределение держится, пока нет связи. */
    val lastWeightG: Double,
    /** Время варки; до начала пролива — ноль, хотя общий таймер мог идти на вкладке «Весы». */
    val seconds: Int,
    /** Нули частей рецепта: налитое в каждую часть считается от своей тары. */
    val tare: TareState = TareState(),
    /** Часть, где идёт время, ждёт тары: на табло и в ряду кнопок — «Тара». */
    val awaitingTare: Boolean = false,
    /** Тару части нажали, ждём ответа весов и ноль. */
    val tareBusy: Boolean = false,
    /**
     * Вода шага воды ещё не улеглась (льётся или недолито 3 г и больше): шаг с крупным временем и конец рецепта
     * показывают на табло воду, а не отсчёт и итог.
     */
    val holdWater: Boolean = false,
) {
    val started: Boolean get() = phase == BrewPhase.RUNNING || phase == BrewPhase.PAUSED || phase == BrewPhase.FINISHED

    /** Время для хода шагов: до начала пролива текущего шага нет. */
    val timelineSeconds: Int? get() = if (started) seconds else null

    /** База для предпросмотра на «Зерне»: насыпанное на весы, а пока его нет — доза рецепта по умолчанию. */
    fun previewDoseG(defaultDoseG: Double): Double = weightG?.takeIf { it >= MIN_DOSE_G } ?: defaultDoseG

    /** Налитое в часть [part]: до «Старт» весы не оттарированы — ноль; без связи — по последнему весу. */
    fun pouredIn(part: Int): Double = if (phase == BrewPhase.READY) 0.0 else tare.pouredIn(part, weightG ?: lastWeightG)

    /** Кнопки «Тара» части можно нажать. */
    val canTarePart: Boolean get() = awaitingTare && !tareBusy && scale.isReady

    /** «Далее»: весы готовы и на них хоть что-то есть. */
    val canFixDose: Boolean get() = phase == BrewPhase.BEANS && weightG != null && weightG >= MIN_DOSE_G

    companion object {
        const val MIN_DOSE_G = 1.0
    }
}

/**
 * Состояние варки по рецепту. Своего таймера нет: время — общий таймер [ScaleRepository], как на вкладке «Весы»,
 * чтобы таймер весов не встал и они не ушли в авто-отключение посреди пролива.
 *
 * [longScope] — для команд, которые должны дойти после закрытия экрана («Завершить» ставит таймер на паузу
 * и сразу закрывает Activity). [beep] — сигнал кнопок управления, [signal] — сигнал хода рецепта
 * (звучать ли — решает настройка «Сигналы шагов»). [nowMs] — часы для остановки пролива, те же, что у репозитория.
 */
class BrewViewModel(
    private val repository: ScaleRepository,
    recipe: Recipe,
    scope: CoroutineScope,
    private val longScope: CoroutineScope,
    private val beep: () -> Unit = {},
    private val signal: (StepSignal) -> Unit = {},
    private val nowMs: () -> Long = { System.nanoTime() / 1_000_000 },
) : ViewModel(scope) {

    private val _recipe = MutableStateFlow(recipe)

    /** Рецепт варки. На «Зерне» его может сменить исправленная версия ([updateRecipe]), дальше он не меняется. */
    val recipeState: StateFlow<Recipe> = _recipe.asStateFlow()
    val recipe: Recipe get() = _recipe.value

    var timeline = RecipeTimeline(recipe)
        private set

    /** Хранимая стадия; RUNNING/PAUSED из неё выводятся по таймеру. */
    private enum class Stage { BEANS, READY, ARMED, STARTED, FINISHED }

    private val stage = MutableStateFlow(Stage.BEANS)
    private val dose = MutableStateFlow<Double?>(null)
    private val lastWeight = MutableStateFlow(0.0)

    /** Нули частей рецепта и признак «тару части нажали, ждём ноль». */
    private data class PartTare(val state: TareState = TareState(), val busy: Boolean = false)

    private val partTare = MutableStateFlow(PartTare())

    /** Часть, чью тару весы приняли и ждут первого кадра около нуля; `null` — не ждём. */
    private var awaitingPartZero: Int? = null

    /** Нажатие «Тара» части, чей ответ ещё ждём: после новой варки прежний ответ не засчитывается. */
    private var tareAttempt = 0

    private var detector: PourDetector? = null

    /**
     * «Старт» оттарировал весы, и ждём первый кадр около нуля: только он станет опорным весом пролива.
     * Кадры, пришедшие до того, как тара сработала (воронка с зерном, 300 г), иначе засчитались бы как пролив.
     */
    private var awaitingZero = false

    /** Нажатие «Старт», чью тару ещё ждём; повторный «Старт» после отмены не путается с прежней тарой. */
    private var startAttempt = 0

    /**
     * Общий таймер после начала пролива уже сброшен (видели 0:00). До этого в состоянии ещё может быть прежний
     * отсчёт с вкладки «Весы»: конец рецепта сработал бы сразу (4:10 > 3:30), а сигнал смены шага — ложно (1:05).
     */
    private var freshTimer = false

    /** Текущий по времени шаг, о котором уже прозвучал сигнал; смена — новый сигнал. */
    private var signalledStep: Int? = null

    /** Шаги с целью, о наборе которых уже сигналили: при колебаниях веса у цели сигнал не повторяется. */
    private val reachedTargets = mutableSetOf<Int>()

    /** Остановка пролива по весу — когда вода может улечься. */
    private var still = PourStill()

    /** Шаг воды, чья вода улеглась: отпущен до начала следующего шага воды. */
    private val settledWater = MutableStateFlow<Int?>(null)

    /** Будильник «вес не рос 1,5 с»: при неизменном весе новых состояний весов нет. */
    private var settleWake: Job? = null

    private val _errors = Channel<Int>(Channel.BUFFERED)
    val errors: Flow<Int> = _errors.receiveAsFlow()

    val ui: StateFlow<BrewUi> = combine(
        stage, dose, lastWeight, repository.state, combine(partTare, settledWater, ::Pair),
    ) { stage, dose, last, s, (tare, settled) ->
        val seconds = if (stage == Stage.STARTED || stage == Stage.FINISHED) s.timeSeconds else 0
        BrewUi(
            phase = when (stage) {
                Stage.BEANS -> BrewPhase.BEANS
                Stage.READY -> BrewPhase.READY
                Stage.ARMED -> BrewPhase.ARMED
                Stage.STARTED -> if (s.timerState == TimerState.RUNNING) BrewPhase.RUNNING else BrewPhase.PAUSED
                Stage.FINISHED -> BrewPhase.FINISHED
            },
            scale = s,
            doseG = dose,
            weightG = s.weightG(),
            lastWeightG = last,
            seconds = seconds,
            tare = tare.state,
            // Часть ждёт тары, только пока идёт время рецепта; прежний отсчёт общего таймера до сброса не считаем.
            awaitingTare = stage == Stage.STARTED && freshTimer && !tare.state.isTared(timeline.partAt(seconds)),
            tareBusy = tare.busy,
            holdWater = holdsWater(stage, seconds, dose, s.weightG() ?: last, tare.state, settled),
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, BrewUi(BrewPhase.BEANS, repository.state.value, null, null, 0.0, 0))

    init {
        viewModelScope.launch { repository.state.collect(::onScaleState) }
    }

    /**
     * Другой рецепт вместо текущего: пользователь выбрал «Сменить рецепт» или поправил свой рецепт в редакторе.
     * До «Старт» (на «Зерне» и на «Шагах») подменяем на месте: стадия и зафиксированная доза остаются, цели
     * пересчитываются. После «Старт» рецепт не меняется до конца варки.
     */
    fun updateRecipe(new: Recipe) {
        if (stage.value != Stage.BEANS && stage.value != Stage.READY || new == recipe) return
        timeline = RecipeTimeline(new)
        _recipe.value = new
    }

    private fun onScaleState(s: ScaleState) {
        val weight = s.weightG()
        if (weight != null) lastWeight.value = weight
        when (stage.value) {
            Stage.ARMED -> when {
                weight == null -> Unit
                awaitingZero -> acceptZero(weight)
                // Показания не сглаживаем: первый же кадр выше порога — пролив начался.
                detector?.isPour(weight) == true -> startBrew()
            }
            Stage.STARTED -> {
                if (s.timeSeconds == 0) freshTimer = true
                if (freshTimer) {
                    // Время ушло в следующую часть — прошлые застывают на весе к началу шага «Тара».
                    val part = timeline.partAt(s.timeSeconds.coerceAtMost(timeline.total))
                    partTare.update { it.copy(state = it.state.freezeBefore(part, lastWeight.value)) }
                }
                if (weight != null) acceptPartZero(weight)
                if (freshTimer && s.timerState == TimerState.RUNNING && s.timeSeconds >= timeline.total) {
                    stage.value = Stage.FINISHED
                    launchReporting(viewModelScope) { repository.toggleTimer() }
                }
                if (freshTimer) checkSignals(s.timeSeconds.coerceAtMost(timeline.total))
            }
            Stage.FINISHED -> if (freshTimer) checkSignals(timeline.total)
            else -> Unit
        }
        settle()
    }

    /**
     * Табло держит воду: идёт шаг с крупным временем (или время рецепта вышло), а вода шага воды не отпущена.
     * Без шага воды (в части нет целей, часть ждёт тары) держать нечего.
     */
    private fun holdsWater(stage: Stage, seconds: Int, dose: Double?, weightG: Double, tare: TareState, settled: Int?): Boolean {
        if (dose == null || !freshTimer) return false
        when (stage) {
            Stage.STARTED -> {
                val step = timeline.stepAt(seconds) ?: return false
                if (!(recipe.items[step] as RecipeItem.Step).showTime) return false
            }
            Stage.FINISHED -> Unit
            else -> return false
        }
        val focus = PourFocus.of(timeline, dose, weightG, seconds.coerceAtMost(timeline.total), tare) ?: return false
        return focus.itemIndex != settled
    }

    /**
     * Вода улеглась — пролив остановился и до рубежа шага воды осталось меньше [PourFocus.SETTLE_LEFT_G]: шаг воды
     * отпускается до начала следующего. Пока пролив не остановился, ждём будильником: вес может больше не прийти.
     */
    private fun settle() {
        val current = stage.value
        if (!freshTimer || current != Stage.STARTED && current != Stage.FINISHED) return
        val dose = dose.value ?: return
        val now = nowMs()
        val weight = lastWeight.value
        still.onWeight(weight, now)
        val seconds = repository.state.value.timeSeconds.coerceAtMost(timeline.total)
        val focus = PourFocus.of(timeline, dose, weight, seconds, partTare.value.state)
        settleWake?.cancel()
        if (focus == null || focus.itemIndex == settledWater.value) return
        if (still.isStill(now)) {
            if (focus.leftG < PourFocus.SETTLE_LEFT_G) settledWater.value = focus.itemIndex
            return
        }
        val at = still.stillAtMs ?: return
        settleWake = viewModelScope.launch {
            delay(at - now)
            settle()
        }
    }

    /** Первый вес около нуля после тары «Старт» становится опорным весом пролива. */
    private fun acceptZero(weight: Double) {
        if (!awaitingZero || abs(weight) >= TARE_ZERO_G) return
        awaitingZero = false
        detector = PourDetector(weight)
    }

    /** Смена шага по времени (и конец рецепта) — одиночный сигнал; первый набор цели шага воды — двойной. */
    private fun checkSignals(seconds: Int) {
        val step = timeline.stepAt(seconds)
        if (step != signalledStep) {
            signalledStep = step
            signal(StepSignal.STEP)
        }
        val dose = dose.value ?: return
        val focus = PourFocus.of(timeline, dose, lastWeight.value, seconds, partTare.value.state) ?: return
        if (focus.state != TargetState.BELOW && reachedTargets.add(focus.itemIndex)) signal(StepSignal.TARGET)
    }

    private fun startBrew() {
        stage.value = Stage.STARTED
        detector = null
        freshTimer = false
        // Первый шаг начинается вместе с проливом — это не «смена шага», сигнал не нужен.
        signalledStep = timeline.stepAt(0)
        reachedTargets.clear()
        still = PourStill()
        settleWake?.cancel()
        settledWater.value = null
        partTare.value = PartTare()
        awaitingPartZero = null
        tareAttempt++
        // Как «Сброс» и «Старт» на вкладке «Весы»: общий таймер мог идти там.
        launchReporting(viewModelScope) {
            repository.resetTimer()
            repository.toggleTimer()
        }
    }

    /** Время варки в миллисекундах для идеального уровня на полосе налива; `null` — пролив не начался. */
    fun brewElapsedMs(): Long? = if (ui.value.started) repository.timerElapsedMs() else null

    fun tare() {
        beep()
        launchReporting(viewModelScope) { repository.withSession { tare() } }
    }

    /**
     * «Тара» на шаге «Тара»: оттарировать весы, и первый кадр около нуля станет нулём части, где идёт время.
     * Сами весы без нажатия не тарируются: в начале шага на них ещё может стоять аэропресс.
     */
    fun tarePart() {
        val current = ui.value
        if (!current.canTarePart) return
        beep()
        val part = timeline.partAt(current.seconds)
        val attempt = ++tareAttempt
        partTare.update { it.copy(busy = true) }
        viewModelScope.launch {
            try {
                repository.withSession { tare() }
                if (attempt == tareAttempt) {
                    awaitingPartZero = part
                    // Весы уже показывали ноль — одинаковое состояние повторно не придёт, берём его сейчас.
                    repository.state.value.weightG()?.let(::acceptPartZero)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (attempt == tareAttempt) partTare.update { it.copy(busy = false) }
                commandErrorRes(e, repository.state.value.isReady)?.let { _errors.trySend(it) }
            }
        }
    }

    /** Первый вес около нуля после тары части — её ноль. */
    private fun acceptPartZero(weight: Double) {
        val part = awaitingPartZero ?: return
        if (abs(weight) >= TARE_ZERO_G) return
        awaitingPartZero = null
        partTare.update { PartTare(it.state.tared(part, weight), busy = false) }
    }

    /** «Далее»: доза — показание весов в момент нажатия. */
    fun fixDose() {
        val current = ui.value
        if (!current.canFixDose) return
        dose.value = current.weightG
        stage.value = Stage.READY
    }

    /** Назад со «Шагов» до «Старт»: к «Зерну», доза остаётся ориентиром до следующего «Далее». */
    fun backToBeans() {
        if (stage.value == Stage.READY) stage.value = Stage.BEANS
    }

    /**
     * «Старт» — оттарировать весы и ждать пролив от нуля; повторное нажатие отменяет ожидание. Кнопка
     * оптимистична: ожидание показывается сразу, а если весы тару не приняли — возвращается «Старт».
     */
    fun startOrCancel() {
        when (stage.value) {
            Stage.READY -> {
                if (ui.value.weightG == null) return
                beep()
                detector = null
                awaitingZero = false
                stage.value = Stage.ARMED
                val attempt = ++startAttempt
                viewModelScope.launch {
                    try {
                        repository.withSession { tare() }
                        if (attempt == startAttempt && stage.value == Stage.ARMED) {
                            awaitingZero = true
                            // Весы уже показывали ноль — одинаковое состояние повторно не придёт, берём его сейчас.
                            repository.state.value.weightG()?.let(::acceptZero)
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        if (attempt == startAttempt && stage.value == Stage.ARMED) stage.value = Stage.READY
                        commandErrorRes(e, repository.state.value.isReady)?.let { _errors.trySend(it) }
                    }
                }
            }
            Stage.ARMED -> {
                beep()
                detector = null
                awaitingZero = false
                startAttempt++
                stage.value = Stage.READY
            }
            else -> Unit
        }
    }

    fun togglePause() {
        if (stage.value != Stage.STARTED) return
        beep()
        launchReporting(viewModelScope) { repository.toggleTimer() }
    }

    /**
     * «Завершить»: общий таймер встаёт на текущем значении и остаётся таким на вкладке «Весы».
     * В ожидании пролива варка таймер ещё не запускала — чужой отсчёт с вкладки «Весы» не трогаем.
     */
    fun finish() {
        if (stage.value == Stage.STARTED && repository.state.value.timerState == TimerState.RUNNING) {
            launchReporting(longScope) { repository.toggleTimer() }
        }
        // Экран закрывается: ни сигналов, ни конца рецепта больше не нужно.
        freshTimer = false
        stage.value = Stage.FINISHED
    }

    private fun ScaleState.weightG(): Double? = weight?.takeIf { isReady }?.let { toGrams(it.toDouble(), unit) }

    private fun launchReporting(scope: CoroutineScope, block: suspend () -> Unit) {
        scope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                commandErrorRes(e, repository.state.value.isReady)?.let { _errors.trySend(it) }
            }
        }
    }

    private companion object {
        /** Кадр после тары считается нулём, если он ближе 1 г к нулю. */
        const val TARE_ZERO_G = 1.0
    }
}
