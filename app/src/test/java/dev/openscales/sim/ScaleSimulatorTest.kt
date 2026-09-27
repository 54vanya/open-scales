package dev.openscales.sim

import dev.openscales.data.SavedDevice
import dev.openscales.data.SavedDeviceStore
import dev.openscales.protocol.ScaleModel
import dev.openscales.protocol.WeightUnit
import dev.openscales.session.ScaleRepository
import dev.openscales.session.settle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Виртуальные весы под настоящим репозиторием и сессией. */
class ScaleSimulatorTest {

    private class MemoryStore : SavedDeviceStore {
        val flow = MutableStateFlow<SavedDevice?>(null)
        override val device: Flow<SavedDevice?> = flow
        override suspend fun save(device: SavedDevice) { flow.value = device }
        override suspend fun clear() { flow.value = null }
    }

    private class Env(scope: TestScope) {
        val simulator = ScaleSimulator(scope.backgroundScope) { scope.testScheduler.currentTime }
        val store = MemoryStore()
        val repository = ScaleRepository(
            scope = scope.backgroundScope,
            store = store,
            transportFactory = { simulator.transport() },
            nowMs = { scope.testScheduler.currentTime },
        )

        fun connect() = repository.connect(ScaleSimulator.ADDRESS, "Virtual DOT", ScaleModel.DOT)
    }

    @Test
    fun `connects to ready and streams weight frames`() = runTest {
        val env = Env(this)
        env.connect()
        settle(3_000)
        val state = env.repository.state.value
        assertTrue(state.isReady)
        assertEquals(ScaleModel.DOT, state.model)
        assertEquals("Virtual DOT", state.name)
        assertEquals(80, state.batteryPercent)
        assertEquals(SavedDevice(ScaleSimulator.ADDRESS, "Virtual DOT", ScaleModel.DOT), env.store.flow.value)

        assertEquals("ok", env.simulator.execute("pour 250 30"))
        settle(15_000)
        assertEquals(125f, env.repository.state.value.weight!!, 1f)
        assertEquals(8.3f, env.repository.state.value.flowRate, 0.2f)
        settle(20_000)
        assertEquals(250f, env.repository.state.value.weight!!, 0.05f)
        assertEquals(0f, env.repository.state.value.flowRate, 0.05f)
    }

    @Test
    fun `tare through the session zeroes the weight`() = runTest {
        val env = Env(this)
        env.connect()
        settle(3_000)
        env.simulator.execute("weight 312")
        settle(500)
        env.repository.withSession { tare() }
        settle(500)
        assertEquals(0f, env.repository.state.value.weight!!, 0.05f)
    }

    @Test
    fun `drop keeps reconnect failing until back`() = runTest {
        val env = Env(this)
        env.connect()
        settle(3_000)
        assertEquals("ok", env.simulator.execute("drop"))
        settle(20_000)
        assertFalse(env.repository.state.value.isReady)
        assertTrue(env.repository.state.value.reconnecting)
        assertTrue(env.simulator.execute("status"), "link=dropped" in env.simulator.execute("status"))

        env.simulator.execute("back")
        settle(ScaleRepository.RECONNECT_INTERVAL_MS + 3_000)
        assertTrue(env.repository.state.value.isReady)
        assertTrue("link=connected" in env.simulator.execute("status"))
    }

    @Test
    fun `unit switch reaches the app`() = runTest {
        val env = Env(this)
        env.connect()
        settle(3_000)
        env.simulator.execute("weight 250")
        env.simulator.execute("unit oz")
        settle(500)
        assertEquals(WeightUnit.OUNCE, env.repository.state.value.unit)
        assertEquals(8.82f, env.repository.state.value.weight!!, 0.005f)
        env.simulator.execute("unit g")
        settle(500)
        assertEquals(250f, env.repository.state.value.weight!!, 0.05f)
    }

    @Test
    fun `rejected tare fails the command`() = runTest {
        val env = Env(this)
        env.connect()
        settle(3_000)
        env.simulator.execute("weight 50")
        env.simulator.execute("reject tare")
        val result = runCatching { env.repository.withSession { tare() } }
        assertTrue(result.isFailure)
        settle(500)
        assertEquals(50f, env.repository.state.value.weight!!, 0.05f)
    }

    @Test
    fun `status and parsing`() = runTest {
        val env = Env(this)
        val sim = env.simulator
        assertEquals(
            "weight=0.0g tare=0.0g shown=0.0g unit=g timer=RESET 0s battery=80% link=idle reject=none noise=0.0g",
            sim.execute("status"),
        )
        assertEquals("ok", sim.execute("battery 20"))
        assertEquals("ok", sim.execute("reject timer"))
        assertEquals("ok", sim.execute("noise 0.1"))
        assertTrue(sim.execute("status").contains("battery=20% link=idle reject=timer noise=0.1g"))
        assertEquals("ok", sim.execute("reject all"))
        assertTrue(sim.execute("status").contains("reject=all"))

        val before = sim.execute("status")
        for (bad in listOf("weight abc", "weight", "pour 10", "pour 10 0", "unit kg", "battery 101", "reject nothing", "fly", "")) {
            assertTrue(bad, sim.execute(bad).startsWith("error:"))
        }
        assertEquals(before, sim.execute("status"))

        assertEquals("ok", sim.execute("reset"))
        assertTrue(sim.execute("status").contains("battery=80% link=idle reject=none noise=0.0g"))
    }
}
