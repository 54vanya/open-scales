package dev.openscales.session

import dev.openscales.data.SavedDevice
import dev.openscales.data.SavedDeviceStore
import dev.openscales.protocol.Cmd
import dev.openscales.protocol.Frame
import dev.openscales.protocol.GattIds
import dev.openscales.protocol.ScaleModel
import dev.openscales.protocol.TimerState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScaleRepositoryTest {

    private class MemoryStore(initial: SavedDevice? = null) : SavedDeviceStore {
        val flow = MutableStateFlow(initial)
        override val device: Flow<SavedDevice?> = flow
        override suspend fun save(device: SavedDevice) { flow.value = device }
        override suspend fun clear() { flow.value = null }
    }

    private class Env(scope: TestScope, saved: SavedDevice? = null) {
        val store = MemoryStore(saved)
        val transports = mutableListOf<FakeBleTransport>()
        var configure: (FakeBleTransport) -> Unit = {}
        val syncTimer = MutableStateFlow(false)
        val repository = ScaleRepository(
            scope = scope.backgroundScope,
            store = store,
            transportFactory = { address ->
                FakeBleTransport(address).also { configure(it); transports += it }
            },
            syncTimer = syncTimer,
            nowMs = { scope.testScheduler.currentTime },
        )
    }

    /** Кадр веса 18.3 г, поток 2.4 г/с, время 75 с. */
    private fun FakeBleTransport.emitWeightAt75s() =
        emitFrame(Frame.TYPE_READ, Cmd.WEIGHT, byteArrayOf(0, 0, 0, 0xB7.toByte(), 0, 0x18, 0, 75))

    private val basic3 = SavedDevice("C8:47:8C:00:11:22", "Basic 3", ScaleModel.BASIC3)

    @Test
    fun `manual connect saves device once ready`() = runTest {
        val env = Env(this)
        env.repository.connect(basic3.address, "TES016", ScaleModel.BASIC3)
        settle()
        assertTrue(env.repository.state.value.isReady)
        assertEquals(basic3, env.store.flow.value)
    }

    @Test
    fun `auto connect uses saved device`() = runTest {
        val env = Env(this, basic3)
        runCurrent()
        env.repository.autoConnect()
        settle()
        assertTrue(env.repository.state.value.isReady)
        assertEquals(basic3.address, env.transports.single().address)
    }

    @Test
    fun `link loss triggers reconnect every 5 seconds`() = runTest {
        val env = Env(this, basic3)
        runCurrent()
        env.repository.autoConnect()
        settle()

        env.configure = { it.connectError = "device not found" }
        env.transports.last().disconnectFromDevice()
        runCurrent()
        assertTrue(env.repository.state.value.reconnecting)

        // Пауза отсчитывается от конца неудачной попытки (fixed delay, как ReconnectTask оригинала).
        val step = ScaleRepository.RECONNECT_INTERVAL_MS + FakeBleTransport.CONNECT_LATENCY_MS + 100
        advanceTimeBy(step)
        assertEquals(2, env.transports.size)
        advanceTimeBy(step)
        assertEquals(3, env.transports.size)

        env.configure = {}
        advanceTimeBy(ScaleRepository.RECONNECT_INTERVAL_MS + 5_000)
        assertTrue(env.repository.state.value.isReady)
        assertFalse(env.repository.state.value.reconnecting)
    }

    @Test
    fun `reconnecting stays set across attempts until the scale is ready again`() = runTest {
        val env = Env(this, basic3)
        runCurrent()
        env.repository.autoConnect()
        settle()
        val seen = mutableListOf<Pair<ConnectionPhase, Boolean>>()
        backgroundScope.launch {
            env.repository.state.collect { seen += it.phase to it.reconnecting }
        }
        runCurrent()

        // Весы выключены: две неудачные попытки, потом весы включились.
        env.configure = { it.connectError = "device not found" }
        env.transports.last().disconnectFromDevice()
        advanceTimeBy(2 * (ScaleRepository.RECONNECT_INTERVAL_MS + FakeBleTransport.CONNECT_LATENCY_MS + 100))
        env.configure = {}
        settle()
        assertTrue(env.repository.state.value.isReady)

        // Между потерей связи и READY баннер не должен мигать «Не подключено» без индикатора.
        val lost = seen.indexOfFirst { it.first != ConnectionPhase.READY }
        val ready = seen.indexOfLast { it.first != ConnectionPhase.READY } + 1
        val between = seen.subList(lost, ready)
        assertTrue("states $between", between.all { (phase, reconnecting) -> reconnecting || phase.isBusy })
        assertFalse(env.repository.state.value.reconnecting)
    }

    @Test
    fun `in background reconnect stops and resumes when the screen is shown again`() = runTest {
        val env = Env(this, basic3)
        runCurrent()
        env.repository.autoConnect()
        settle()
        assertTrue(env.repository.state.value.isReady)

        // Приложение свернули, весы выключились.
        env.repository.setAppVisible(false)
        env.configure = { it.connectError = "device not found" }
        env.transports.last().disconnectFromDevice()
        settle()
        assertEquals(1, env.transports.size)
        assertFalse(env.repository.state.value.reconnecting)

        // Вернулись на экран — новая попытка, а не результат старого таймаута.
        env.configure = {}
        env.repository.setAppVisible(true)
        env.repository.autoConnect()
        settle()
        assertEquals(2, env.transports.size)
        assertTrue(env.repository.state.value.isReady)
    }

    @Test
    fun `showing the screen while connected does not start a second connection`() = runTest {
        val env = Env(this, basic3)
        runCurrent()
        env.repository.autoConnect()
        settle()
        repeat(3) { env.repository.autoConnect() }
        settle()
        assertEquals(1, env.transports.size)
    }

    @Test
    fun `user disconnect does not reconnect`() = runTest {
        val env = Env(this, basic3)
        runCurrent()
        env.repository.autoConnect()
        settle()
        env.repository.disconnect()
        advanceTimeBy(60_000)
        assertEquals(1, env.transports.size)
        assertEquals(ConnectionPhase.DISCONNECTED, env.repository.state.value.phase)
    }

    @Test
    fun `failed manual connect is reported and not retried`() = runTest {
        val env = Env(this)
        env.configure = { it.connectError = "timeout" }
        env.repository.connect(basic3.address, basic3.name, basic3.model)
        advanceTimeBy(60_000)
        assertEquals(1, env.transports.size)
        assertEquals(ConnectionPhase.FAILED, env.repository.state.value.phase)
        assertNull(env.store.flow.value)
    }

    @Test
    fun `forget clears saved device and bond`() = runTest {
        val env = Env(this, basic3)
        runCurrent()
        env.repository.autoConnect()
        settle()
        env.repository.forget()
        settle()
        assertNull(env.store.flow.value)
        assertEquals(ConnectionPhase.DISCONNECTED, env.repository.state.value.phase)
        assertTrue(env.transports.first().writesOf(dev.openscales.protocol.Cmd.FORGET_DEVICE).isNotEmpty())
        advanceTimeBy(60_000)
        assertEquals(2, env.transports.size) // вторая — только для removeBond
    }

    @Test
    fun `early wake-up retries instead of waiting for the fallback`() = runTest {
        // Часы меток и часы пробуждений округляют миллисекунды независимо (elapsedRealtime против uptimeMillis):
        // ровно на границе секунды метка ещё на 1 мс в прошлой секунде.
        val ticks = mutableListOf<Triple<Int, Long, Boolean>>()
        val repository = ScaleRepository(
            scope = backgroundScope,
            store = MemoryStore(),
            transportFactory = { FakeBleTransport(it) },
            nowMs = { testScheduler.currentTime.let { if (it > 0 && it % 1_000 == 0L) it - 1 else it } },
            tickLog = { seconds, late, fallback -> ticks += Triple(seconds, late, fallback) },
        )
        runCurrent()
        repository.toggleTimer()
        runCurrent()
        advanceTimeBy(5_010)
        runCurrent()
        assertEquals((1..5).toList(), ticks.map { it.first })
        assertTrue(ticks.toString(), ticks.none { it.third })
        assertTrue(ticks.toString(), ticks.all { it.second <= 1 })
    }

    @Test
    fun `stopwatch runs without a scale and ticks once a second`() = runTest {
        val env = Env(this)
        val seconds = mutableListOf<Int>()
        backgroundScope.launch {
            env.repository.state.collect { if (seconds.lastOrNull() != it.timeSeconds) seconds += it.timeSeconds }
        }
        runCurrent()

        env.repository.toggleTimer()
        runCurrent()
        assertEquals(TimerState.RUNNING, env.repository.state.value.timerState)

        advanceTimeBy(75_000)
        runCurrent()
        assertEquals(75, env.repository.state.value.timeSeconds)
        assertEquals((0..75).toList(), seconds)

        // После паузы тик останавливается: новых значений не появляется.
        env.repository.toggleTimer()
        advanceTimeBy(10_000)
        runCurrent()
        assertEquals((0..75).toList(), seconds)
    }

    @Test
    fun `sync setting switches the source without reconnecting`() = runTest {
        val env = Env(this, basic3)
        runCurrent()
        env.repository.autoConnect()
        settle()
        env.transports.last().emitWeightAt75s()
        runCurrent()
        assertEquals(0, env.repository.state.value.timeSeconds)

        env.syncTimer.value = true
        runCurrent()
        assertEquals(75, env.repository.state.value.timeSeconds)

        env.syncTimer.value = false
        runCurrent()
        assertEquals(75, env.repository.state.value.timeSeconds) // часы приложения подхватили время весов
        assertEquals(1, env.transports.size) // переподключения не было
    }

    @Test
    fun `pause keeps elapsed time and reset zeroes it`() = runTest {
        val env = Env(this)
        env.repository.toggleTimer()
        advanceTimeBy(30_000)
        env.repository.toggleTimer()
        advanceTimeBy(10_000)
        runCurrent()
        assertEquals(TimerState.PAUSED, env.repository.state.value.timerState)
        assertEquals(30, env.repository.state.value.timeSeconds)

        env.repository.toggleTimer()
        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(35, env.repository.state.value.timeSeconds)

        env.repository.resetTimer()
        runCurrent()
        assertEquals(TimerState.RESET, env.repository.state.value.timerState)
        assertEquals(0, env.repository.state.value.timeSeconds)
    }

    @Test
    fun `scale time is ignored while sync is off`() = runTest {
        val env = Env(this, basic3)
        runCurrent()
        env.repository.autoConnect()
        settle()

        env.transports.last().emitWeightAt75s()
        runCurrent()
        assertEquals(0, env.repository.state.value.timeSeconds)
        assertEquals(18.3f, env.repository.state.value.weight!!, 0.001f)
    }

    @Test
    fun `sync shows time from the scale`() = runTest {
        val env = Env(this, basic3)
        env.syncTimer.value = true
        runCurrent()
        env.repository.autoConnect()
        settle()

        env.transports.last().emitWeightAt75s()
        runCurrent()
        assertEquals(75, env.repository.state.value.timeSeconds)
    }

    @Test
    fun `counting continues locally when the link is lost`() = runTest {
        val env = Env(this, basic3)
        env.syncTimer.value = true
        runCurrent()
        env.repository.autoConnect()
        settle()
        val transport = env.transports.last()
        transport.emitFrame(Frame.TYPE_READ, Cmd.TIMER, byteArrayOf(1))
        transport.emitWeightAt75s()
        runCurrent()
        assertEquals(75, env.repository.state.value.timeSeconds)

        env.configure = { it.connectError = "device not found" }
        transport.disconnectFromDevice()
        runCurrent()
        advanceTimeBy(10_000)
        runCurrent()
        assertEquals(TimerState.RUNNING, env.repository.state.value.timerState)
        assertEquals(85, env.repository.state.value.timeSeconds)
    }

    @Test
    fun `reset zeroes the app timer without touching the scale`() = runTest {
        val env = Env(this, basic3)
        runCurrent()
        env.repository.autoConnect()
        settle()
        env.repository.toggleTimer()
        advanceTimeBy(5_000)
        runCurrent()

        env.repository.resetTimer()
        settle()
        assertEquals(TimerState.RESET, env.repository.state.value.timerState)
        assertEquals(0, env.repository.state.value.timeSeconds)
        // Весы своего времени не присылали, сбрасывать им нечего — команда сброса не ушла.
        val resets = env.transports.last().frames.count {
            it.cmd == Cmd.TIMER && it.type == Frame.TYPE_WRITE && it.payload[0].toInt() == 3
        }
        assertEquals(0, resets)
    }

    @Test
    fun `rejected timer command does not stop the stopwatch`() = runTest {
        val env = Env(this, basic3)
        env.configure = { it.rejectedWrites += Cmd.TIMER }
        runCurrent()
        env.repository.autoConnect()
        settle()

        env.repository.toggleTimer()
        settle()
        assertEquals(TimerState.RUNNING, env.repository.state.value.timerState)
    }

    // region подхват таймера весов при подключении

    /**
     * Весы шлют кадры веса 10 раз в секунду, таймер в кадре — от [startSeconds] и идёт, если [running].
     * Секунды меняются на границе секунды виртуального времени, как у настоящих весов.
     */
    private fun TestScope.scaleTimer(env: Env, startSeconds: Int, running: Boolean) {
        val t0 = testScheduler.currentTime
        backgroundScope.launch {
            while (true) {
                val seconds = startSeconds + if (running) ((testScheduler.currentTime - t0) / 1_000).toInt() else 0
                env.transports.lastOrNull()?.emitWeight(seconds)
                delay(100)
            }
        }
    }

    private fun Env.scaleTimerState(state: TimerState) {
        configure = { it.readResponses[Cmd.TIMER] = byteArrayOf(state.code.toByte()) }
    }

    @Test
    fun `running scale timer is adopted on connect while sync is off`() = runTest {
        val env = Env(this, basic3)
        env.scaleTimerState(TimerState.RUNNING)
        runCurrent()
        scaleTimer(env, startSeconds = 133, running = true)
        env.repository.autoConnect()
        settle(3_000)
        val state = env.repository.state.value
        assertTrue(state.isReady)
        assertEquals(TimerState.RUNNING, state.timerState)
        // Подхват в момент смены секунды: дальше секунды приложения совпадают с весами.
        val scaleSeconds = { 133 + (testScheduler.currentTime / 1_000).toInt() }
        assertEquals(scaleSeconds(), state.timeSeconds)
        settle(10_000)
        assertEquals(scaleSeconds(), env.repository.state.value.timeSeconds)
    }

    @Test
    fun `paused scale timer is adopted and start continues from it`() = runTest {
        val env = Env(this, basic3)
        env.scaleTimerState(TimerState.PAUSED)
        runCurrent()
        scaleTimer(env, startSeconds = 45, running = false)
        env.repository.autoConnect()
        settle(3_000)
        assertEquals(TimerState.PAUSED, env.repository.state.value.timerState)
        assertEquals(45, env.repository.state.value.timeSeconds)

        env.repository.toggleTimer()
        settle(5_000)
        assertEquals(TimerState.RUNNING, env.repository.state.value.timerState)
        assertEquals(50, env.repository.state.value.timeSeconds)
    }

    @Test
    fun `reset scale timer leaves the app timer at zero`() = runTest {
        val env = Env(this, basic3)
        env.scaleTimerState(TimerState.RESET)
        runCurrent()
        scaleTimer(env, startSeconds = 0, running = false)
        env.repository.autoConnect()
        settle(5_000)
        assertEquals(TimerState.RESET, env.repository.state.value.timerState)
        assertEquals(0, env.repository.state.value.timeSeconds)
    }

    @Test
    fun `running app timer is kept when the scale reports its own time`() = runTest {
        val env = Env(this, basic3)
        env.scaleTimerState(TimerState.RUNNING)
        env.repository.toggleTimer()
        advanceTimeBy(80_000)
        runCurrent()
        scaleTimer(env, startSeconds = 10, running = true)
        env.repository.autoConnect()
        settle(3_000)
        assertEquals(TimerState.RUNNING, env.repository.state.value.timerState)
        assertEquals((testScheduler.currentTime / 1_000).toInt(), env.repository.state.value.timeSeconds)
    }

    @Test
    fun `reconnect does not adopt the scale timer again`() = runTest {
        val env = Env(this, basic3)
        env.scaleTimerState(TimerState.RUNNING)
        runCurrent()
        scaleTimer(env, startSeconds = 100, running = true)
        env.repository.autoConnect()
        settle(3_000)
        val adoptedAt = testScheduler.currentTime
        val adopted = env.repository.state.value.timeSeconds
        assertEquals(103, adopted)

        // Весы перезапустили таймер, связь оборвалась и восстановилась.
        env.transports.last().disconnectFromDevice()
        scaleTimer(env, startSeconds = 0, running = true)
        settle(20_000)
        assertTrue(env.repository.state.value.isReady)
        assertEquals(2, env.transports.size)
        val expected = adopted + ((testScheduler.currentTime - adoptedAt) / 1_000).toInt()
        assertEquals(expected, env.repository.state.value.timeSeconds)
    }

    @Test
    fun `TES08 does not touch the app timer`() = runTest {
        val env = Env(this, basic3.copy(model = ScaleModel.OLD_DOUBLE))
        env.configure = { it.services = setOf(GattIds.SERVICE_LEGACY) }
        runCurrent()
        scaleTimer(env, startSeconds = 30, running = true)
        env.repository.autoConnect()
        settle(5_000)
        assertTrue(env.repository.state.value.isReady)
        assertEquals(TimerState.RESET, env.repository.state.value.timerState)
        assertEquals(0, env.repository.state.value.timeSeconds)
    }

    // endregion

    @Test
    fun `stopwatch shows every second once and on time while weight frames stream`() = runTest {
        val env = Env(this, basic3)
        runCurrent()
        scaleTimer(env, startSeconds = 0, running = false)
        env.repository.autoConnect()
        settle(3_000)
        val changes = mutableListOf<Pair<Long, Int>>()
        backgroundScope.launch {
            env.repository.state.collect {
                if (changes.lastOrNull()?.second != it.timeSeconds) changes += testScheduler.currentTime to it.timeSeconds
            }
        }
        runCurrent()
        env.repository.toggleTimer()
        advanceTimeBy(60_000)
        runCurrent()

        assertEquals((0..60).toList(), changes.map { it.second })
        val gaps = changes.zipWithNext { a, b -> b.first - a.first }
        assertTrue("gaps $gaps", gaps.all { it <= 1_200 })
    }
}
