package dev.openscales.session

import dev.openscales.ble.BondState
import dev.openscales.protocol.Cmd
import dev.openscales.protocol.Frame
import dev.openscales.protocol.GattIds
import dev.openscales.protocol.LegacyCmd
import dev.openscales.protocol.ScaleModel
import dev.openscales.protocol.TimerState
import dev.openscales.protocol.WeightUnit
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScaleSessionTest {

    private fun TestScope.session(
        transport: FakeBleTransport,
        model: ScaleModel = ScaleModel.UNKNOWN,
    ): Pair<ScaleSession, MutableList<ConnectionPhase>> {
        val s = ScaleSession(transport, backgroundScope.coroutineContext, model, log = {})
        val phases = mutableListOf<ConnectionPhase>()
        backgroundScope.launch { s.state.collect { if (phases.lastOrNull() != it.phase) phases += it.phase } }
        return s to phases
    }

    @Test
    fun `first connection bonds, subscribes, handshakes and becomes ready`() = runTest {
        val t = FakeBleTransport(bond = BondState.NONE)
        t.onCreateBond = {
            t.bond = BondState.BONDING
            backgroundScope.launch { delay(3_000); t.bond = BondState.BONDED }
            true
        }
        val (s, phases) = session(t)
        s.start()
        settle()

        assertEquals(
            listOf(
                ConnectionPhase.DISCONNECTED, ConnectionPhase.CONNECTING, ConnectionPhase.BONDING,
                ConnectionPhase.SUBSCRIBING, ConnectionPhase.HANDSHAKING, ConnectionPhase.READY,
            ),
            phases,
        )
        assertEquals(listOf(GattIds.NOTIFY_2025), t.notificationsEnabled)
        assertEquals(
            listOf(Cmd.BATTERY, Cmd.MODEL, Cmd.WEIGHT_UNIT, Cmd.MODE_STAGE, Cmd.TIMER, Cmd.DEVICE_NAME),
            t.frames.map { it.cmd },
        )
        val state = s.state.value
        assertEquals(ScaleModel.BASIC3, state.model)
        assertEquals(80, state.batteryPercent)
        assertEquals("Basic 3", state.name)
    }

    @Test
    fun `rejected pairing fails the session`() = runTest {
        val t = FakeBleTransport(bond = BondState.NONE)
        t.onCreateBond = {
            t.bond = BondState.BONDING
            backgroundScope.launch { delay(2_000); t.bond = BondState.NONE }
            true
        }
        val (s, _) = session(t)
        s.start()
        settle()
        assertEquals(EndReason.FAILED, s.awaitEnd())
        assertEquals(ConnectionPhase.FAILED, s.state.value.phase)
        assertTrue(s.state.value.error!!.contains("сопряжение"))
        assertTrue(t.closeCount > 0)
    }

    @Test
    fun `pairing that never starts times out after 10 seconds`() = runTest {
        val t = FakeBleTransport(bond = BondState.NONE)
        t.onCreateBond = { true } // система «согласилась», но так и не начала
        val (s, _) = session(t)
        s.start()
        advanceTimeBy(ScaleSession.BOND_START_TIMEOUT_MS - 1_000)
        assertEquals(ConnectionPhase.BONDING, s.state.value.phase)
        settle()
        assertEquals(ConnectionPhase.FAILED, s.state.value.phase)
    }

    @Test
    fun `cccd failure with protocol data already seen is accepted`() = runTest {
        val t = FakeBleTransport()
        t.cccdError = "descriptor timeout"
        val (s, _) = session(t, ScaleModel.BASIC3)
        s.start()
        runCurrent()
        // Весы уже шлют вес до колбэка CCCD.
        advanceTimeBy(ScaleSession.MTU_DELAY_MS + 100)
        t.emitFrame(Frame.TYPE_READ, Cmd.WEIGHT, byteArrayOf(0, 0, 0, 10, 0, 0, 0, 0))
        settle()
        assertEquals(ConnectionPhase.READY, s.state.value.phase)
        assertEquals(1, t.connectCount)
    }

    @Test
    fun `cccd failure without data rebuilds gatt and finally fails`() = runTest {
        val t = FakeBleTransport()
        t.cccdError = "descriptor timeout"
        val (s, _) = session(t, ScaleModel.BASIC3)
        s.start()
        settle()
        assertEquals(1 + ScaleSession.MAX_GATT_REBUILDS, t.connectCount)
        assertEquals(ConnectionPhase.FAILED, s.state.value.phase)
    }

    @Test
    fun `legacy TES08 skips bonding and handshake`() = runTest {
        val t = FakeBleTransport(services = setOf(GattIds.SERVICE_LEGACY), bond = BondState.NONE)
        val (s, phases) = session(t)
        s.start()
        settle()
        assertEquals(ConnectionPhase.READY, s.state.value.phase)
        assertTrue(ConnectionPhase.BONDING !in phases)
        assertEquals(listOf(GattIds.WEIGHT_LEGACY), t.notificationsEnabled)
        assertEquals(ScaleModel.OLD_DOUBLE, s.state.value.model)

        s.tare()
        assertEquals(LegacyCmd.TARE, t.legacyWrites.single()[0].toInt())
    }

    @Test
    fun `weight notifications update state`() = runTest {
        val t = FakeBleTransport()
        val (s, _) = session(t, ScaleModel.BASIC3)
        s.start()
        settle()
        t.emitFrame(Frame.TYPE_READ, Cmd.WEIGHT, byteArrayOf(0, 0, 0, 0xB7.toByte(), 0, 0x18, 0, 75))
        runCurrent()
        val state = s.state.value
        assertEquals(18.3f, state.weight!!, 0.001f)
        assertEquals(2.4f, state.flowRate, 0.001f)
        assertEquals(75, state.timeSeconds)
    }

    @Test
    fun `timer toggle sends start then pause`() = runTest {
        val t = FakeBleTransport()
        val (s, _) = session(t, ScaleModel.BASIC3)
        s.start()
        settle()
        t.readResponses[Cmd.TIMER] = byteArrayOf(1)
        s.toggleTimer()
        assertEquals(TimerState.RUNNING, s.state.value.timerState)
        t.readResponses[Cmd.TIMER] = byteArrayOf(2)
        s.toggleTimer()
        assertEquals(TimerState.PAUSED, s.state.value.timerState)
        assertEquals(listOf(1, 2), t.frames.filter { it.cmd == Cmd.TIMER && it.type == Frame.TYPE_WRITE }.map { it.payload[0].toInt() })
    }

    @Test
    fun `timer state changes instantly and reverts when scale rejects`() = runTest {
        val t = FakeBleTransport()
        val (s, _) = session(t, ScaleModel.BASIC3)
        s.start()
        settle()
        t.silentCommands += Cmd.TIMER
        val job = async { runCatching { s.startTimer() } }
        runCurrent()
        // Ещё нет ответа весов, а кнопка уже в состоянии «идёт».
        assertEquals(TimerState.RUNNING, s.state.value.timerState)
        t.silentCommands -= Cmd.TIMER
        t.rejectedWrites += Cmd.TIMER
        settle()
        assertTrue(job.await().isFailure) // таймаут ответа
        assertEquals(TimerState.RESET, s.state.value.timerState)

        assertTrue(runCatching { s.startTimer() }.isFailure) // отказ весов
        assertEquals(TimerState.RESET, s.state.value.timerState)
    }

    @Test
    fun `settings writes use payloads of the original app`() = runTest {
        val t = FakeBleTransport()
        val (s, _) = session(t, ScaleModel.ESPRO)
        s.start()
        settle()
        s.setUnit(WeightUnit.OUNCE)
        s.setSensitivity(dev.openscales.protocol.Sensitivity.LOW)
        s.setPrecision(dev.openscales.protocol.Precision.LOW)
        s.setStandbyMinutes(5)
        s.setBrightness(70)
        s.tare()
        fun payloadOf(cmd: Int) = t.frames.single { it.cmd == cmd && it.type == Frame.TYPE_WRITE }.payload.map { it.toInt() and 0xFF }
        assertEquals(listOf(1), payloadOf(Cmd.WEIGHT_UNIT))
        assertEquals(listOf(5, 3), payloadOf(Cmd.SENSITIVITY))
        assertEquals(listOf(10), payloadOf(Cmd.PRECISION))
        assertEquals(listOf(0x01, 0x2C, 0, 0), payloadOf(Cmd.STANDBY_TIME))
        assertEquals(listOf(70), payloadOf(Cmd.BRIGHTNESS))
        assertEquals(listOf(0, 0), payloadOf(Cmd.TARE))
    }

    @Test
    fun `link loss after ready ends with LOST`() = runTest {
        val t = FakeBleTransport()
        val (s, _) = session(t, ScaleModel.BASIC3)
        s.start()
        settle()
        t.disconnectFromDevice()
        settle()
        assertEquals(EndReason.LOST, s.awaitEnd())
        assertEquals(ConnectionPhase.DISCONNECTED, s.state.value.phase)
    }

    @Test
    fun `user disconnect sends 0x1C and closes after grace period`() = runTest {
        val t = FakeBleTransport()
        val (s, _) = session(t, ScaleModel.BASIC3)
        s.start()
        settle()
        val job = async { s.disconnect() }
        settle()
        job.await()
        assertEquals(1, t.writesOf(Cmd.DISCONNECT).size)
        assertEquals(EndReason.USER, s.awaitEnd())
        assertTrue(t.closeCount > 0)
    }

    @Test
    fun `factory reset ends session without reconnect reason`() = runTest {
        val t = FakeBleTransport()
        val (s, _) = session(t, ScaleModel.BASIC3)
        s.start()
        settle()
        s.factoryReset()
        t.disconnectFromDevice()
        settle()
        assertEquals(EndReason.DEVICE_COMMAND, s.awaitEnd())
    }
}
