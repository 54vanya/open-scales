package dev.openscales.ui

import android.bluetooth.BluetoothAdapter
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import dev.openscales.OpenScalesApp
import dev.openscales.ui.components.BlePermissions
import dev.openscales.ui.scan.BlePrerequisite

class BlePrerequisiteState(
    val value: BlePrerequisite,
    val requestPermission: () -> Unit,
    val enableBluetooth: () -> Unit,
)

/** Разрешения и состояние Bluetooth; перепроверяются при каждом возврате на экран. */
@Composable
fun rememberBlePrerequisite(): BlePrerequisiteState {
    val context = LocalContext.current
    val app = context.applicationContext as OpenScalesApp
    var tick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { tick++ }
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
