package dev.openscales.session

import dev.openscales.data.SavedDevice
import dev.openscales.data.SavedDeviceStore
import dev.openscales.protocol.ScaleModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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
        val repository = ScaleRepository(
            scope = scope.backgroundScope,
            store = store,
            transportFactory = { address ->
                FakeBleTransport(address).also { configure(it); transports += it }
            },
        )
    }

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
}
