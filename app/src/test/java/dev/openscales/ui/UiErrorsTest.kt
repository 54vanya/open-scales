package dev.openscales.ui

import dev.openscales.R
import dev.openscales.ble.BleException
import dev.openscales.ble.BluetoothOffException
import dev.openscales.session.CommandException
import dev.openscales.session.ConnectionError
import dev.openscales.ui.components.messageRes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UiErrorsTest {

    @Test
    fun `every connection error has its own message`() {
        val errors = listOf(
            ConnectionError.NotTimemore, ConnectionError.PairingNotStarted, ConnectionError.PairingRejected,
            ConnectionError.PairingTimeout, ConnectionError.NoModel, ConnectionError.Lost,
            ConnectionError.DroppedWhileConnecting, ConnectionError.Failed("x"),
        )
        val messages = errors.map { it.messageRes() }
        assertEquals(errors.size, messages.toSet().size)
    }

    @Test
    fun `transport failure shows a generic message whatever the exception text`() {
        assertEquals(R.string.error_connect_failed, ConnectionError.Failed("Connect timeout 15000ms").messageRes())
        assertEquals(R.string.error_connect_failed, ConnectionError.Failed("status=133").messageRes())
    }

    @Test
    fun `command errors map by kind`() {
        fun res(kind: CommandException.Kind, ready: Boolean = true) = commandErrorRes(CommandException(kind, "tech"), ready)
        assertEquals(R.string.error_command_timeout, res(CommandException.Kind.TIMEOUT))
        assertEquals(R.string.error_command_rejected, res(CommandException.Kind.REJECTED))
        assertEquals(R.string.error_command_transport, res(CommandException.Kind.TRANSPORT))
        assertEquals(R.string.error_not_connected, res(CommandException.Kind.CANCELLED, ready = false))
        // Вытесненная команда при подключённых весах — не ошибка.
        assertNull(res(CommandException.Kind.CANCELLED, ready = true))
        assertEquals(R.string.error_command_failed, commandErrorRes(IllegalStateException("boom"), scaleReady = true))
    }

    @Test
    fun `scan errors`() {
        assertEquals(ScanError.BLUETOOTH_OFF, ScanError.of(BluetoothOffException()))
        assertEquals(ScanError.FAILED, ScanError.of(BleException("scan failed: 2")))
    }
}
