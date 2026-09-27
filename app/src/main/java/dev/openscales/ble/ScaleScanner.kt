package dev.openscales.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import dev.openscales.BuildConfig
import dev.openscales.protocol.Advertisement
import dev.openscales.protocol.GattIds
import dev.openscales.protocol.ScaleModel
import dev.openscales.protocol.toHex
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

data class DiscoveredScale(
    val address: String,
    val name: String,
    val model: ScaleModel,
    val rssi: Int,
    val bonded: Boolean,
)

/**
 * Сканирование весов. Оригинал фильтрует по сервисам и по manufacturer data 0xFFFF,
 * но системный фильтр по сервисам отсекает весы, которые кладут в рекламу только manufacturer data,
 * поэтому сканируем без фильтра и отбираем результаты сами.
 */
@SuppressLint("MissingPermission")
class ScaleScanner(private val adapter: BluetoothAdapter) {

    fun scan(): Flow<DiscoveredScale> = callbackFlow {
        val scanner = adapter.bluetoothLeScanner ?: throw BluetoothOffException()
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                toScale(result)?.let { trySend(it) }
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                results.forEach { r -> toScale(r)?.let { trySend(it) } }
            }

            override fun onScanFailed(errorCode: Int) {
                close(BleException("scan failed: $errorCode"))
            }
        }
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        scanner.startScan(null, settings, callback)
        awaitClose { runCatching { scanner.stopScan(callback) } }
    }

    private fun toScale(result: ScanResult): DiscoveredScale? {
        val record = result.scanRecord
        if (BuildConfig.DEBUG && record?.deviceName != null) {
            android.util.Log.d(
                "ScaleScanner",
                "seen ${result.device.address} name=${record.deviceName} rssi=${result.rssi} " +
                    "services=${record.serviceUuids} raw=${record.bytes?.toHex()}",
            )
        }
        val services = record?.serviceUuids.orEmpty().map { it.uuid }
        val identity = Advertisement.parseManufacturerData(
            record?.getManufacturerSpecificData(GattIds.MANUFACTURER_ID),
        )
        if (!Advertisement.isScale(identity, services)) return null
        val name = record?.deviceName ?: result.device.name ?: return null
        return DiscoveredScale(
            address = result.device.address,
            name = name,
            model = identity?.model ?: Advertisement.modelFromServices(services),
            rssi = result.rssi,
            bonded = result.device.bondState == android.bluetooth.BluetoothDevice.BOND_BONDED,
        )
    }
}
