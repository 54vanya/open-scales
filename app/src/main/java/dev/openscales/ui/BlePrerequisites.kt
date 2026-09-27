package dev.openscales.ui

import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import dev.openscales.OpenScalesApp
import dev.openscales.ui.components.BlePermissions
import dev.openscales.ui.scan.BlePrerequisite

data class BlePrerequisiteState(
    val value: BlePrerequisite,
    val requestPermission: () -> Unit,
    val enableBluetooth: () -> Unit,
)

/**
 * Разрешения и состояние Bluetooth. Перепроверяются при каждом возврате на экран (разрешения отзывают только
 * в системных настройках) и сразу при включении/выключении Bluetooth — в том числе из шторки, которая экран
 * на паузу не ставит.
 */
@Composable
fun rememberBlePrerequisite(): BlePrerequisiteState {
    val context = LocalContext.current
    val app = context.applicationContext as OpenScalesApp
    var tick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { tick++ }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                tick++
            }
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        onDispose { context.unregisterReceiver(receiver) }
    }
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { tick++ }
    val enable = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { tick++ }
    val value = remember(tick) {
        when {
            !BlePermissions.granted(context) -> BlePrerequisite.NO_PERMISSION
            !app.isBluetoothEnabled -> BlePrerequisite.BLUETOOTH_OFF
            else -> BlePrerequisite.OK
        }
    }
    return BlePrerequisiteState(
        value = value,
        requestPermission = { permissions.launch(BlePermissions.required) },
        enableBluetooth = { enable.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)) },
    )
}
