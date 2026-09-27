package dev.openscales.protocol

import java.util.UUID

/** Разбор рекламы весов: модель по имени и данным производителя, фильтр своих устройств. */
object Advertisement {

    data class Identity(val model: ScaleModel, val protocolVersion: Int?)

    /**
     * Manufacturer data компании 0xFFFF: `[broadcastType (1|2), deviceCode (1 DOT, 2 Basic3, 3 ESPRO), protocol?]`.
     */
    fun parseManufacturerData(data: ByteArray?): Identity? {
        if (data == null || data.size < 2) return null
        val broadcastType = data.u8(0)
        if (broadcastType != 1 && broadcastType != 2) return null
        val model = when (data.u8(1)) {
            1 -> ScaleModel.DOT
            2 -> ScaleModel.BASIC3
            3 -> ScaleModel.ESPRO
            else -> return null
        }
        return Identity(model, data.getOrNull(2)?.let { it.toInt() and 0xFF })
    }

    fun isScale(identity: Identity?, serviceUuids: Collection<UUID>): Boolean =
        identity != null || serviceUuids.any { it in GattIds.SCAN_SERVICES }

    /** Модель по сервисам, если manufacturer data нет. */
    fun modelFromServices(serviceUuids: Collection<UUID>): ScaleModel =
        if (GattIds.SERVICE_LEGACY in serviceUuids) ScaleModel.OLD_DOUBLE else ScaleModel.UNKNOWN
}
