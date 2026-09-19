package dev.openscales.session

import dev.openscales.protocol.Precision
import dev.openscales.protocol.ScaleModel
import dev.openscales.protocol.Sensitivity
import dev.openscales.protocol.TimerState
import dev.openscales.protocol.WeightUnit

/** Фазы подключения — те же, что `ConnectionPhase` оригинала. */
enum class ConnectionPhase {
    DISCONNECTED, CONNECTING, BONDING, SUBSCRIBING, HANDSHAKING, READY, DISCONNECTING, FAILED;

    val isBusy: Boolean get() = this in setOf(CONNECTING, BONDING, SUBSCRIBING, HANDSHAKING, DISCONNECTING)
}

/** Почему сессия завершилась — от этого зависит авто-переподключение. */
enum class EndReason {
    /** Пользователь нажал «Отключить». */
    USER,

    /** Весы выключены, сброшены или забыты по команде из приложения. */
    DEVICE_COMMAND,

    /** Связь потеряна после READY. */
    LOST,

    /** Не удалось подключиться. */
    FAILED,
}

data class ScaleSettings(
    val sound: Boolean? = null,
    val standbyMinutes: Int? = null,
    val sensitivity: Sensitivity? = null,
    val precision: Precision? = null,
    val brightness: Int? = null,
    val firmware: String? = null,
    val serial: String? = null,
)

data class ScaleState(
    val phase: ConnectionPhase = ConnectionPhase.DISCONNECTED,
    val address: String? = null,
    val name: String? = null,
    val model: ScaleModel = ScaleModel.UNKNOWN,
    val error: String? = null,
    val weight: Float? = null,
    val flowRate: Float = 0f,
    val unit: WeightUnit = WeightUnit.GRAM,
    val timeSeconds: Int = 0,
    val timerState: TimerState = TimerState.RESET,
    val overload: Boolean = false,
    val batteryPercent: Int? = null,
    val settings: ScaleSettings = ScaleSettings(),
    /** Идёт авто-переподключение после потери связи. */
    val reconnecting: Boolean = false,
) {
    val isReady: Boolean get() = phase == ConnectionPhase.READY
}
