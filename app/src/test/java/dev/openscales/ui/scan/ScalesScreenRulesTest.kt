package dev.openscales.ui.scan

import dev.openscales.protocol.ScaleModel
import dev.openscales.session.ConnectionPhase
import dev.openscales.session.ScaleState
import dev.openscales.ui.scale.ScaleLink
import dev.openscales.ui.scale.scaleLink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScalesScreenRulesTest {

    private val address = "C8:47:8C:00:11:22"

    @Test
    fun `link of the saved scale follows the session`() {
        assertEquals(ScaleLink.DISCONNECTED, scaleLink(address, ScaleState()))
        assertEquals(ScaleLink.CONNECTED, scaleLink(address, ScaleState(phase = ConnectionPhase.READY, address = address.lowercase())))
        assertEquals(ScaleLink.CONNECTING, scaleLink(address, ScaleState(phase = ConnectionPhase.HANDSHAKING, address = address)))
        assertEquals(ScaleLink.CONNECTING, scaleLink(address, ScaleState(reconnecting = true, address = address)))
        // Подключены другие весы — эти не подключены.
        assertEquals(ScaleLink.DISCONNECTED, scaleLink(address, ScaleState(phase = ConnectionPhase.READY, address = "AA:BB", model = ScaleModel.DOT)))
    }

    @Test
    fun `tapping the row connects only a disconnected scale`() {
        assertEquals(SavedRowAction.CONNECT, savedRowAction(ScaleLink.DISCONNECTED))
        assertEquals(SavedRowAction.OPEN_DETAILS, savedRowAction(ScaleLink.CONNECTED))
        assertEquals(SavedRowAction.OPEN_DETAILS, savedRowAction(ScaleLink.CONNECTING))
    }

    @Test
    fun `search starts by itself only without a connected scale`() {
        assertTrue(shouldAutoScan(scaleReady = false))
        assertFalse(shouldAutoScan(scaleReady = true))
    }
}
