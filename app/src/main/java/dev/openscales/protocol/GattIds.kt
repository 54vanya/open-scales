package dev.openscales.protocol

import java.util.UUID

private fun uuid16(short: String) = UUID.fromString("0000$short-0000-1000-8000-00805f9b34fb")

/** GATT-идентификаторы из `DeviceScanActivity`, `Ble2025DeviceUtils`, `a0`, `v` оригинала. */
object GattIds {
    val CCCD: UUID = uuid16("2902")

    // Протокол 2025 (TES015/016/017)
    val SERVICE_2025: UUID = uuid16("fff0")
    val NOTIFY_2025: UUID = uuid16("fff1")
    val WRITE_2025: UUID = uuid16("fff2")

    // Legacy TES08
    val SERVICE_LEGACY: UUID = uuid16("181d")
    val WEIGHT_LEGACY: UUID = uuid16("2a9d")
    val COMMAND_LEGACY: UUID = UUID.fromString("553f4e49-bf21-4468-9c6c-0e4fb5b17697")

    /** Используется оригиналом только как фильтр сканирования. */
    val SERVICE_BODY_COMPOSITION: UUID = uuid16("181a")

    val SCAN_SERVICES: List<UUID> = listOf(SERVICE_LEGACY, SERVICE_BODY_COMPOSITION, SERVICE_2025)

    /** Company ID manufacturer data, в которой весы рекламируют модель. */
    const val MANUFACTURER_ID = 0xFFFF
}
