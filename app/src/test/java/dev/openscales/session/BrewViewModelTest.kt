package dev.openscales.session

import dev.openscales.data.SavedDevice
import dev.openscales.data.SavedDeviceStore
import dev.openscales.protocol.Cmd
import dev.openscales.protocol.Frame
import dev.openscales.protocol.ScaleModel
import dev.openscales.protocol.TimerState
import dev.openscales.recipe.BuiltInRecipes
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

    private class Env(scope: TestScope) {
        val transports = mutableListOf<FakeBleTransport>()
        val repository = ScaleRepository(
            scope = scope.backgroundScope,
            store = MemoryStore(),
            transportFactory = { address -> FakeBleTransport(address).also { transports += it } },
            nowMs = { scope.testScheduler.currentTime },
        )
        val signals = mutableListOf<Pair<Long, StepSignal>>()
        val brew = BrewViewModel(
            repository, BuiltInRecipes.hoffmannV60, scope.backgroundScope, scope.backgroundScope,
            signal = { signals += scope.testScheduler.currentTime to it },
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

    private companion object {
        /** Сколько ждать ответа весов на тару: команда идёт через очередь сессии. */
        const val TARE_ACK_MS = 500L
    }
}
