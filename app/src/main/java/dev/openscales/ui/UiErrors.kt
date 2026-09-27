package dev.openscales.ui

import androidx.annotation.StringRes
import dev.openscales.R
import dev.openscales.ble.BluetoothOffException
import dev.openscales.session.CommandException

/**
 * Ошибка команды → текст на языке интерфейса. `null` — не показывать: вытесненная команда таймера
 * или повторная тара при подключённых весах не ошибка для пользователя. Сообщение исключения к пользователю
 * не попадает — оно техническое и на английском.
 */
@StringRes
fun commandErrorRes(error: Throwable, scaleReady: Boolean): Int? = when (error) {
    is CommandException -> when (error.kind) {
        CommandException.Kind.TIMEOUT -> R.string.error_command_timeout
        CommandException.Kind.REJECTED -> R.string.error_command_rejected
        CommandException.Kind.TRANSPORT -> R.string.error_command_transport
        CommandException.Kind.CANCELLED -> if (scaleReady) null else R.string.error_not_connected
    }
    else -> R.string.error_command_failed
}

/** Причина, по которой поиск весов не удался. */
enum class ScanError(@StringRes val messageRes: Int) {
    BLUETOOTH_OFF(R.string.error_bluetooth_off),
    FAILED(R.string.error_scan_failed);

    companion object {
        fun of(error: Throwable) = if (error is BluetoothOffException) BLUETOOTH_OFF else FAILED
    }
}
