package dev.openscales.ble

import kotlinx.coroutines.flow.Flow
import java.util.UUID

enum class BondState { NONE, BONDING, BONDED }

sealed interface TransportEvent {
    data class Notification(val characteristic: UUID, val value: ByteArray) : TransportEvent
    data class BondChanged(val state: BondState, val previous: BondState) : TransportEvent
    data class Disconnected(val status: Int) : TransportEvent
}

class BleException(message: String) : Exception(message)

/**
 * GATT-соединение с одним устройством. Все suspend-операции сериализованы реализацией
 * и бросают [BleException] при ошибке или таймауте.
 */
interface BleTransport {
    val address: String
    val events: Flow<TransportEvent>

    suspend fun connect()

    /** Обнаруживает сервисы и возвращает их UUID. */
    suspend fun discoverServices(): Set<UUID>

    fun bondState(): BondState

    /** Запускает системное сопряжение. false — система отказалась его начинать. */
    fun createBond(): Boolean

    fun removeBond(): Boolean

    /** Возвращает согласованный MTU. */
    suspend fun requestMtu(mtu: Int): Int

    /** Просит у системы короткий интервал соединения. Ошибка не фатальна. */
    fun requestHighPriority() {}

    suspend fun enableNotifications(service: UUID, characteristic: UUID, indication: Boolean)

    suspend fun write(service: UUID, characteristic: UUID, value: ByteArray, withResponse: Boolean = true)

    /** Закрывает GATT. Повторный вызов безопасен. */
    fun close()
}
