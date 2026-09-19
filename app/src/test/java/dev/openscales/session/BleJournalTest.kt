package dev.openscales.session

import dev.openscales.ble.BleJournal
import dev.openscales.ble.LoggingBleTransport
import dev.openscales.protocol.Cmd
import dev.openscales.protocol.GattIds
import dev.openscales.protocol.ScaleModel
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
}
