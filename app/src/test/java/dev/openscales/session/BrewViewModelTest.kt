package dev.openscales.session

import dev.openscales.data.SavedDevice
import dev.openscales.data.SavedDeviceStore
import dev.openscales.protocol.Cmd
import dev.openscales.protocol.Frame
import dev.openscales.protocol.ScaleModel
import dev.openscales.protocol.TimerState
import dev.openscales.recipe.BuiltInRecipes
import dev.openscales.recipe.PourFocus
import dev.openscales.recipe.Recipe
import dev.openscales.recipe.RecipeTimeline
import dev.openscales.sound.StepSignal
import dev.openscales.ui.brew.BrewPhase
import dev.openscales.ui.brew.BrewViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.roundToInt

/** Варка по рецепту поверх настоящего репозитория и эмулятора весов. */
class BrewViewModelTest {

    private class MemoryStore : SavedDeviceStore {
        override val device: Flow<SavedDevice?> = MutableStateFlow(null)
        override suspend fun save(device: SavedDevice) = Unit
        override suspend fun clear() = Unit
    }

    private class Env(scope: TestScope, recipe: Recipe = BuiltInRecipes.hoffmannV60) {
        val transports = mutableListOf<FakeBleTransport>()
        val repository = ScaleRepository(
            scope = scope.backgroundScope,
            store = MemoryStore(),
            transportFactory = { address -> FakeBleTransport(address).also { transports += it } },
            nowMs = { scope.testScheduler.currentTime },
        )
        val signals = mutableListOf<Pair<Long, StepSignal>>()
        val brew = BrewViewModel(
            repository, recipe, scope.backgroundScope, scope.backgroundScope,
            signal = { signals += scope.testScheduler.currentTime to it },
            nowMs = { scope.testScheduler.currentTime },
        )
        val scale get() = transports.last()

        fun connect(scope: TestScope) {
            repository.connect("C8:47:8C:00:11:22", "Basic 3", ScaleModel.BASIC3)
            scope.settle()
            check(repository.state.value.isReady)
        }
    }

    /** Кадр веса в граммах; время весов не важно — секундомер ведёт приложение. */
    private fun FakeBleTransport.emitGrams(grams: Double) {
        val raw = (grams * 10).roundToInt()
        emitFrame(
            Frame.TYPE_READ, Cmd.WEIGHT,
            byteArrayOf((raw ushr 24).toByte(), (raw ushr 16).toByte(), (raw ushr 8).toByte(), raw.toByte(), 0, 0, 0, 0),
        )
    }

    private fun TestScope.weigh(env: Env, grams: Double) {
        env.scale.emitGrams(grams)
        runCurrent()
    }

    /** «Старт»: тара ушла на весы, и весы прислали ноль — дальше ждём пролив от нуля. */
    private fun TestScope.start(env: Env) {
        env.brew.startOrCancel()
        settle(TARE_ACK_MS)
        weigh(env, 0.0)
    }

    /** Доза 15 г зафиксирована, весы оттарированы с зерном — открыт шаг «Шаги». */
    private fun TestScope.readyToBrew(): Env {
        val env = Env(this)
        env.connect(this)
        weigh(env, 15.0)
        env.brew.fixDose()
        runCurrent()
        weigh(env, 0.0)
        assertEquals(BrewPhase.READY, env.brew.ui.value.phase)
        return env
    }

    private fun Env.holds() = brew.ui.value.holdWater

    @Test
    fun `wait step holds the water until the pour stops near the target`() = runTest {
        val env = readyToBrew()
        start(env)
        weigh(env, 2.0)
        // 0:13,3 — «Взболтайте» 0:12–0:17, а цветение (30 г) ещё льётся.
        settle(13_300)
        weigh(env, 20.0)
        assertTrue(env.holds())
        weigh(env, 26.0)
        assertTrue(env.holds())

        // Встал на 28 г (осталось 2): отпускает ровно через 1,5 с — без новых кадров веса.
        weigh(env, 28.0)
        settle(1_499)
        assertTrue(env.holds())
        settle(1)
        assertFalse(env.holds())

        // Отпущенная вода держится до следующего шага воды: ранний пролив отсчёт не сбивает.
        settle(20_000)
        weigh(env, 40.0)
        assertFalse(env.holds())

        // 1:49 — «Подождите» 1:45–2:00 после «Налейте» до 250 г, а налито 200: держит, пока не дольют.
        settle(70_000)
        weigh(env, 200.0)
        settle(5_000)
        assertEquals(6, env.brew.timeline.stepAt(env.brew.ui.value.timelineSeconds))
        assertTrue(env.holds())
        weigh(env, 249.0)
        settle(1_500)
        assertFalse(env.holds())
    }

    @Test
    fun `underpour holds the water, overpour lets it go`() = runTest {
        val env = readyToBrew()
        start(env)
        weigh(env, 2.0)
        // 0:20 — «Подождите», в цветение налито 25 г из 30: стоит, но осталось 5 г.
        settle(20_000)
        weigh(env, 25.0)
        settle(5_000)
        assertTrue(env.holds())
        // Осталось ровно 3 г — ещё держит.
        weigh(env, 27.0)
        settle(3_000)
        assertTrue(env.holds())
        // Перелив на 6 г: «осталось» меньше нуля — отпускает.
        weigh(env, 36.0)
        settle(1_500)
        assertFalse(env.holds())
    }

    @Test
    fun `the end of the recipe holds the last pour`() = runTest {
        val recipe = Recipe(
            id = "user:one-pour",
            title = dev.openscales.recipe.Text.Plain("Налейте"),
            defaultDoseG = 15,
            description = null,
            items = listOf(dev.openscales.recipe.RecipeItem.Step(dev.openscales.recipe.Text.Plain("Налейте"), 105, targetG = 180)),
        )
        val env = Env(this, recipe)
        env.connect(this)
        weigh(env, 15.0)
        env.brew.fixDose()
        runCurrent()
        weigh(env, 0.0)
        start(env)
        weigh(env, 2.0)

        // Время вышло на 1:45, а на весах 170 г и ещё льётся: итога нет, табло держит воду.
        settle(104_000)
        weigh(env, 165.0)
        settle(1_000)
        weigh(env, 170.0)
        settle(1_000)
        assertEquals(BrewPhase.FINISHED, env.brew.ui.value.phase)
        assertTrue(env.holds())
        weigh(env, 179.0)
        settle(1_499)
        assertTrue(env.holds())
        settle(1)
        assertFalse(env.holds())
    }

    @Test
    fun `next is disabled on an empty scale and fixes the dose`() = runTest {
        val env = Env(this)
        env.connect(this)
        weigh(env, 0.3)
        assertFalse(env.brew.ui.value.canFixDose)
        env.brew.fixDose()
        runCurrent()
        assertEquals(BrewPhase.BEANS, env.brew.ui.value.phase)

        weigh(env, 18.0)
        env.brew.fixDose()
        runCurrent()
        assertEquals(BrewPhase.READY, env.brew.ui.value.phase)
        assertEquals(18.0, env.brew.ui.value.doseG!!, 1e-6)
    }

    @Test
    fun `recipe can be swapped before start, keeping the step and the dose`() = runTest {
        val env = Env(this)
        env.connect(this)
        val kasuya = BuiltInRecipes.kasuya46
        // «Зерно»: подмена на месте.
        env.brew.updateRecipe(kasuya)
        assertEquals(kasuya, env.brew.recipeState.value)
        assertEquals(RecipeTimeline(kasuya).total, env.brew.timeline.total)
        assertEquals(BrewPhase.BEANS, env.brew.ui.value.phase)

        // После «Далее»: стадия и доза остаются.
        weigh(env, 18.0)
        env.brew.fixDose()
        runCurrent()
        val kalita = BuiltInRecipes.kalitaWave
        env.brew.updateRecipe(kalita)
        runCurrent()
        assertEquals(kalita, env.brew.recipe)
        assertEquals(BrewPhase.READY, env.brew.ui.value.phase)
        assertEquals(18.0, env.brew.ui.value.doseG!!, 1e-6)
        assertEquals(RecipeTimeline(kalita).total, env.brew.timeline.total)
    }

    @Test
    fun `recipe does not change after start`() = runTest {
        val env = readyToBrew()
        start(env)
        env.brew.updateRecipe(BuiltInRecipes.kasuya46)
        assertEquals(BuiltInRecipes.hoffmannV60, env.brew.recipe)
    }

    @Test
    fun `next is disabled without a scale`() = runTest {
        val env = Env(this)
        runCurrent()
        assertFalse(env.brew.ui.value.canFixDose)
    }

    @Test
    fun `water starts the time`() = runTest {
        val env = readyToBrew()
        start(env)
        assertEquals(BrewPhase.ARMED, env.brew.ui.value.phase)

        for (grams in listOf(0.4, 0.7)) {
            weigh(env, grams)
            settle(1_000)
            assertEquals(BrewPhase.ARMED, env.brew.ui.value.phase)
            assertEquals(0, env.brew.ui.value.seconds)
            assertEquals(TimerState.RESET, env.repository.state.value.timerState)
        }

        weigh(env, 1.3)
        runCurrent()
        assertEquals(BrewPhase.RUNNING, env.brew.ui.value.phase)
        settle(20_000)
        assertEquals(20, env.brew.ui.value.seconds)
        assertEquals(3, env.brew.timeline.stepAt(env.brew.ui.value.timelineSeconds))
    }

    @Test
    fun `start tares the scale and counts the pour from zero`() = runTest {
        val env = readyToBrew()
        // Воронка с зерном не оттарирована: 312 г.
        weigh(env, 312.0)
        env.brew.startOrCancel()
        runCurrent()
        assertEquals(BrewPhase.ARMED, env.brew.ui.value.phase)
        assertEquals("tare sent", 1, env.scale.writesOf(Cmd.TARE).size)
        // Кадр, пришедший до того, как тара сработала, — не пролив.
        weigh(env, 312.0)
        settle(TARE_ACK_MS)
        weigh(env, 312.0)
        assertEquals(BrewPhase.ARMED, env.brew.ui.value.phase)
        weigh(env, 0.0)
        weigh(env, 0.9)
        assertEquals(BrewPhase.ARMED, env.brew.ui.value.phase)
        weigh(env, 1.0)
        assertEquals(BrewPhase.RUNNING, env.brew.ui.value.phase)
    }

    @Test
    fun `rejected tare returns the start button`() = runTest {
        val env = readyToBrew()
        env.scale.rejectedWrites += Cmd.TARE
        env.brew.startOrCancel()
        settle(5_000)
        assertEquals(BrewPhase.READY, env.brew.ui.value.phase)
        weigh(env, 5.0)
        assertEquals(BrewPhase.READY, env.brew.ui.value.phase)
    }

    @Test
    fun `pressing again cancels waiting`() = runTest {
        val env = readyToBrew()
        env.brew.startOrCancel()
        runCurrent()
        env.brew.startOrCancel()
        runCurrent()
        assertEquals(BrewPhase.READY, env.brew.ui.value.phase)
        weigh(env, 5.0)
        settle(1_000)
        assertEquals(BrewPhase.READY, env.brew.ui.value.phase)
        assertEquals(0, env.brew.ui.value.seconds)
        assertNull(env.brew.ui.value.timelineSeconds)
    }

    @Test
    fun `previous stopwatch on the scale tab restarts from zero`() = runTest {
        val env = readyToBrew()
        env.repository.toggleTimer()
        settle(250_000)
        assertEquals(250, env.repository.state.value.timeSeconds)
        // До начала пролива время варки — ноль, хотя общий таймер идёт.
        assertEquals(0, env.brew.ui.value.seconds)

        start(env)
        weigh(env, 2.0)
        runCurrent()
        assertEquals(BrewPhase.RUNNING, env.brew.ui.value.phase)
        assertEquals(0, env.brew.ui.value.seconds)
        assertEquals(0, env.repository.state.value.timeSeconds)
        settle(5_000)
        assertEquals(5, env.brew.ui.value.seconds)
        assertEquals(BrewPhase.RUNNING, env.brew.ui.value.phase)
    }

    @Test
    fun `timer stops at the end of the recipe`() = runTest {
        val env = readyToBrew()
        start(env)
        weigh(env, 2.0)
        settle(215_000)
        val ui = env.brew.ui.value
        assertEquals(BrewPhase.FINISHED, ui.phase)
        assertEquals(210, ui.seconds)
        assertEquals(TimerState.PAUSED, env.repository.state.value.timerState)
        assertNull(env.brew.timeline.stepAt(ui.timelineSeconds))
        // Вес продолжает обновляться.
        weigh(env, 251.0)
        assertEquals(251.0, env.brew.ui.value.weightG!!, 1e-6)
    }

    @Test
    fun `pause keeps the time and derives from the shared timer`() = runTest {
        val env = readyToBrew()
        start(env)
        weigh(env, 2.0)
        settle(62_000)
        env.brew.togglePause()
        runCurrent()
        assertEquals(BrewPhase.PAUSED, env.brew.ui.value.phase)
        settle(10_000)
        assertEquals(62, env.brew.ui.value.seconds)
        env.brew.togglePause()
        runCurrent()
        assertEquals(BrewPhase.RUNNING, env.brew.ui.value.phase)
    }

    @Test
    fun `precise brew time runs between seconds and stops on pause`() = runTest {
        val env = readyToBrew()
        assertNull(env.brew.brewElapsedMs())
        start(env)
        weigh(env, 2.0)
        settle(1_500)
        assertEquals(1_500.0, env.brew.brewElapsedMs()!!.toDouble(), 100.0)
        assertEquals(1, env.brew.ui.value.seconds)
        env.brew.togglePause()
        runCurrent()
        val paused = env.brew.brewElapsedMs()!!
        settle(2_000)
        assertEquals(paused, env.brew.brewElapsedMs())
    }

    @Test
    fun `finish pauses the shared timer at the current time`() = runTest {
        val env = readyToBrew()
        start(env)
        weigh(env, 2.0)
        settle(160_000)
        env.brew.finish()
        runCurrent()
        settle(10_000)
        assertEquals(TimerState.PAUSED, env.repository.state.value.timerState)
        assertEquals(160, env.repository.state.value.timeSeconds)
    }

    @Test
    fun `link loss keeps the time and the last weight`() = runTest {
        val env = readyToBrew()
        start(env)
        weigh(env, 70.0)
        settle(10_000)
        env.scale.disconnectFromDevice()
        runCurrent()
        val ui = env.brew.ui.value
        assertNull(ui.weightG)
        assertEquals(70.0, ui.lastWeightG, 1e-6)
        assertTrue(ui.scale.reconnecting)
        settle(3_000)
        assertEquals(13, env.brew.ui.value.seconds)
        assertEquals(BrewPhase.RUNNING, env.brew.ui.value.phase)
    }

    @Test
    fun `signals step changes, targets once and the end`() = runTest {
        val env = readyToBrew()
        start(env)
        weigh(env, 2.0)
        runCurrent()
        val start = testScheduler.currentTime
        assertTrue("no signal at the pour start", env.signals.isEmpty())

        // Цветение набрано на 0:05 — двойной, колебания у цели не повторяют.
        settle(5_000)
        weigh(env, 29.4)
        weigh(env, 30.3)
        weigh(env, 29.2)
        weigh(env, 30.1)
        assertEquals(listOf(StepSignal.TARGET), env.signals.map { it.second })

        // 0:12 — «Покачайте», 0:17 — «Подождите».
        settle(13_000)
        assertEquals(listOf(StepSignal.TARGET, StepSignal.STEP, StepSignal.STEP), env.signals.map { it.second })
        assertEquals(12_000.0, (env.signals[1].first - start).toDouble(), 1_000.0)

        // Конец рецепта — ещё один одиночный.
        settle(200_000)
        val kinds = env.signals.map { it.second }
        assertEquals(StepSignal.STEP, kinds.last())
        assertEquals(8, kinds.count { it == StepSignal.STEP }) // 7 смен шага + конец
        assertEquals(BrewPhase.FINISHED, env.brew.ui.value.phase)
    }

    @Test
    fun `previous short stopwatch gives no false step signal`() = runTest {
        val env = readyToBrew()
        env.repository.toggleTimer()
        settle(65_000)
        start(env)
        weigh(env, 2.0)
        settle(3_000)
        assertTrue(env.signals.toString(), env.signals.isEmpty())
    }

    @Test
    fun `finish is silent`() = runTest {
        val env = readyToBrew()
        start(env)
        weigh(env, 2.0)
        settle(3_000)
        env.brew.finish()
        settle(3_000)
        assertTrue(env.signals.toString(), env.signals.isEmpty())
    }

    @Test
    fun `preview uses the weighed beans or the default dose`() = runTest {
        val env = Env(this)
        runCurrent()
        assertEquals(15.0, env.brew.ui.value.previewDoseG(15.0), 0.0) // весов нет
        env.connect(this)
        weigh(env, 0.3)
        assertEquals(15.0, env.brew.ui.value.previewDoseG(15.0), 0.0) // на весах почти пусто
        weigh(env, 12.0)
        assertEquals(12.0, env.brew.ui.value.previewDoseG(15.0), 1e-6)
        assertEquals(200.0, env.brew.recipe.totalWaterG(env.brew.ui.value.previewDoseG(15.0)), 1e-4)
    }

    // region шаг «Тара»

    /** Ван Бюнник, доза 30 г, пролив начался: «Тара» 1:15–1:25, «Разбавьте» 1:25–1:40 до 100 г от тары. */
    private fun TestScope.brewingVanBunnik(): Env {
        val env = Env(this, BuiltInRecipes.vanBunnikAeropress)
        env.connect(this)
        weigh(env, 30.0)
        env.brew.fixDose()
        runCurrent()
        start(env)
        weigh(env, 2.0)
        runCurrent()
        assertEquals(BrewPhase.RUNNING, env.brew.ui.value.phase)
        return env
    }

    private fun Env.focus() = ui().let { PourFocus.of(brew.timeline, 30.0, it.weightG!!, it.timelineSeconds, it.tare) }

    private fun Env.ui() = brew.ui.value

    @Test
    fun `tare step waits for the button and never tares by itself`() = runTest {
        val env = brewingVanBunnik()
        weigh(env, 100.0)
        settle(60_000)
        assertFalse(env.ui().awaitingTare)
        val taresBefore = env.scale.writesOf(Cmd.TARE).size
        // 1:16 — шаг «Тара»: концентрат 70 г в чашке, аэропресс снят.
        settle(16_000)
        weigh(env, 70.0)
        assertTrue(env.ui().awaitingTare)
        assertTrue(env.ui().canTarePart)
        assertNull(env.focus())
        // Первая часть застыла на весе к началу шага «Тара».
        assertEquals(100.0, env.ui().pouredIn(0), 1e-6)
        // Время дошло до «Разбавьте», тару не нажимали — весы не тарировались, вода части не считается.
        settle(10_000)
        weigh(env, 90.0)
        assertTrue(env.ui().awaitingTare)
        assertEquals(taresBefore, env.scale.writesOf(Cmd.TARE).size)
        assertEquals(0.0, env.ui().pouredIn(1), 1e-6)
    }

    @Test
    fun `tare button zeroes the part and water counts from it`() = runTest {
        val env = brewingVanBunnik()
        weigh(env, 100.0)
        settle(76_000)
        weigh(env, 70.0)
        val taresBefore = env.scale.writesOf(Cmd.TARE).size
        env.brew.tarePart()
        runCurrent()
        assertTrue(env.ui().tareBusy)
        assertFalse(env.ui().canTarePart)
        env.brew.tarePart()
        settle(TARE_ACK_MS)
        assertEquals("one tare", taresBefore + 1, env.scale.writesOf(Cmd.TARE).size)
        // Кадр до срабатывания тары — не ноль части.
        weigh(env, 70.0)
        assertTrue(env.ui().awaitingTare)
        weigh(env, 0.2)
        assertFalse(env.ui().awaitingTare)
        assertFalse(env.ui().tareBusy)
        weigh(env, 40.2)
        val focus = env.focus()!!
        assertEquals(6, focus.itemIndex)
        assertEquals(60.0, focus.leftG, 1e-6)
        assertEquals(100.0, env.ui().pouredIn(0), 1e-6)
    }

    @Test
    fun `rejected part tare keeps the buttons`() = runTest {
        val env = brewingVanBunnik()
        settle(76_000)
        weigh(env, 70.0)
        env.scale.rejectedWrites += Cmd.TARE
        env.brew.tarePart()
        settle(5_000)
        assertTrue(env.ui().awaitingTare)
        assertFalse(env.ui().tareBusy)
        assertTrue(env.ui().canTarePart)
    }

    @Test
    fun `no tare button after the end or in recipes without tare`() = runTest {
        val env = brewingVanBunnik()
        settle(105_000)
        weigh(env, 70.0)
        assertEquals(BrewPhase.FINISHED, env.ui().phase)
        assertFalse(env.ui().awaitingTare)

        val hoffmann = readyToBrew()
        start(hoffmann)
        weigh(hoffmann, 2.0)
        settle(100_000)
        weigh(hoffmann, 100.0)
        assertFalse(hoffmann.ui().awaitingTare)
    }

    // endregion

    // region Ручная доза

    @Test
    fun `beans already taken away - manual dose prefilled with the weighed beans`() = runTest {
        val env = Env(this)
        env.connect(this)
        weigh(env, 18.2)
        settle(2_000)
        // Чашку снимают: промежуточный кадр и почти ноль.
        weigh(env, 9.6)
        weigh(env, 0.1)
        settle(3_000)
        assertFalse(env.brew.ui.value.canFixDose)
        val prefill = env.brew.manualDosePrefillG()
        assertEquals(18.2, prefill, 1e-3)

        env.brew.fixManualDose(prefill)
        runCurrent()
        val ui = env.brew.ui.value
        assertEquals(BrewPhase.READY, ui.phase)
        assertEquals(listOf(36, 182, 303), env.brew.recipe.targetsG(ui.doseG!!).map { it.roundToInt() })
    }

    @Test
    fun `nothing weighed - manual dose prefilled with the recipe dose`() = runTest {
        val env = Env(this)
        env.connect(this)
        weigh(env, 0.0)
        settle(5_000)
        assertEquals(15.0, env.brew.manualDosePrefillG(), 1e-9)
    }

    @Test
    fun `manual dose out of range is refused`() = runTest {
        val env = Env(this)
        env.connect(this)
        env.brew.fixManualDose(0.9)
        env.brew.fixManualDose(100.1)
        runCurrent()
        assertEquals(BrewPhase.BEANS, env.brew.ui.value.phase)
        assertNull(env.brew.ui.value.doseG)
        env.brew.fixManualDose(100.0)
        runCurrent()
        assertEquals(BrewPhase.READY, env.brew.ui.value.phase)
    }

    @Test
    fun `manual dose needs a ready scale`() = runTest {
        val env = Env(this)
        env.brew.fixManualDose(18.0)
        runCurrent()
        assertEquals(BrewPhase.BEANS, env.brew.ui.value.phase)
        assertNull(env.brew.ui.value.doseG)
    }

    // endregion

    private companion object {
        /** Сколько ждать ответа весов на тару: команда идёт через очередь сессии. */
        const val TARE_ACK_MS = 500L
    }
}
