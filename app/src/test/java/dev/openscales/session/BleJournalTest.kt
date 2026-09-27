package dev.openscales.session

import dev.openscales.ble.BleJournal
import dev.openscales.ble.LoggingBleTransport
import dev.openscales.protocol.Cmd
import dev.openscales.protocol.GattIds
import dev.openscales.protocol.ScaleModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BleJournalTest {

    @Test
    fun `ring buffer keeps the last 2000 entries`() {
        val journal = BleJournal()
        repeat(2500) { journal.add(BleJournal.Kind.INFO, "e$it") }
        val entries = journal.entries.value
        assertEquals(2000, entries.size)
        assertEquals("e500", entries.first().text)
        assertEquals("e2499", entries.last().text)
    }

    @Test
    fun `frames are described with command names`() {
        val read = byteArrayOf(0xA5.toByte(), 0x5A, 0x02, 0x05, 0x00, 0x00, 0x5A, 0x51)
        assertTrue(BleJournal.describe(GattIds.WRITE_2025, read).endsWith("[read BATTERY]"))
        assertEquals("TARE", BleJournal.commandName(Cmd.TARE))
        assertEquals("0x7E", BleJournal.commandName(0x7E))
    }

    @Test
    fun `whole handshake is visible in the journal`() = runTest {
        val journal = BleJournal()
        val fake = FakeBleTransport()
        val s = ScaleSession(
            LoggingBleTransport(fake, journal), backgroundScope.coroutineContext, ScaleModel.UNKNOWN,
            log = { if (it.startsWith("phase=")) journal.add(BleJournal.Kind.PHASE, it) },
        )
        s.start()
        settle()
        val lines = journal.entries.value.map { "${it.kind} ${it.text}" }
        listOf("CONNECTING", "BONDING", "SUBSCRIBING", "HANDSHAKING", "READY")
            .forEach { phase -> assertTrue(phase, lines.any { it == "PHASE phase=$phase" }) }
        assertTrue(lines.any { it.startsWith("MTU request MTU 247 ok") })
        assertTrue(lines.any { it.startsWith("NOTIFY notify") })
        listOf("BATTERY", "MODEL", "WEIGHT_UNIT", "MODE_STAGE", "TIMER", "DEVICE_NAME").forEach { cmd ->
            assertTrue("TX $cmd", lines.any { it.startsWith("TX") && it.endsWith("[read $cmd]") })
            assertTrue("RX $cmd", lines.any { it.startsWith("RX") && it.contains("read $cmd") })
        }
    }

    @Test
    fun `notable events survive a flood of frames`() {
        val journal = BleJournal()
        journal.add(BleJournal.Kind.STALL, "main thread busy 2000ms")
        journal.add(BleJournal.Kind.TIMER, "late tick", notable = true)
        repeat(5000) { journal.add(BleJournal.Kind.RX, "frame $it") }
        repeat(100) { journal.add(BleJournal.Kind.TIMER, "tick $it") }
        assertTrue(journal.entries.value.none { it.kind == BleJournal.Kind.STALL })
        assertEquals(listOf("main thread busy 2000ms", "late tick"), journal.notable.value.map { it.text })
    }

    @Test
    fun `notable buffer keeps the last 500 entries and clear empties both`() {
        val journal = BleJournal()
        repeat(600) { journal.add(BleJournal.Kind.INFO, "e$it") }
        assertEquals(500, journal.notable.value.size)
        assertEquals("e100", journal.notable.value.first().text)
        journal.clear()
        assertTrue(journal.entries.value.isEmpty())
        assertTrue(journal.notable.value.isEmpty())
    }

    @Test
    fun `export lists notable events before the full log`() {
        val journal = BleJournal()
        journal.add(BleJournal.Kind.RX, "frame")
        journal.add(BleJournal.Kind.GAP, "no frames for 800ms")
        val text = journal.export("header")
        val notable = text.indexOf("== Notable events ==")
        val full = text.indexOf("== Full log ==")
        assertTrue(text.startsWith("header"))
        assertTrue(notable in 0 until full)
        assertTrue(text.indexOf("no frames for 800ms") in notable until full)
        assertTrue(text.indexOf("frame\n", full) > full)
    }

    @Test
    fun `pause between protocol frames is recorded as a gap`() = runTest {
        val journal = BleJournal()
        var now = 0L
        val fake = FakeBleTransport()
        val logging = LoggingBleTransport(fake, journal, nowMs = { now })
        val gaps = { journal.notable.value.filter { it.kind == BleJournal.Kind.GAP }.map { it.text } }
        backgroundScope.launch { logging.events.collect {} }
        settle()
        // Время кадра берётся при доставке в коллектор, поэтому после каждого кадра — settle().
        suspend fun frameAfter(ms: Long) {
            now += ms
            fake.emitWeight(0)
            settle()
        }
        frameAfter(0)
        frameAfter(120)
        frameAfter(800)
        assertEquals(listOf("no frames for 800ms"), gaps())

        fake.disconnectFromDevice()
        settle()
        frameAfter(5_000)
        assertEquals("a reconnect is not a gap", 1, gaps().size)
    }
}
