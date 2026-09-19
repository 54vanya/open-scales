package dev.openscales.ui.scan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.BluetoothConnected
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Scale
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.openscales.R
import dev.openscales.ble.DiscoveredScale
import dev.openscales.data.SavedDevice
import dev.openscales.protocol.ScaleModel
import dev.openscales.session.ScaleState
import dev.openscales.ui.components.labelRes
import dev.openscales.ui.theme.OpenScalesTheme

enum class BlePrerequisite { OK, NO_PERMISSION, BLUETOOTH_OFF }

@Composable
fun ScanScreen(
    prerequisite: BlePrerequisite,
    scan: ScanUiState,
    scale: ScaleState,
    saved: SavedDevice?,
    onBack: () -> Unit,
    onRequestPermission: () -> Unit,
    onEnableBluetooth: () -> Unit,
    onStartScan: () -> Unit,
    onStopScan: () -> Unit,
    onSelect: (address: String, name: String, model: ScaleModel) -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.scan_title)) },
                subtitle = {
                    Text(
                        stringResource(if (scan.scanning) R.string.scan_subtitle_scanning else R.string.scan_subtitle_idle),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back))
                    }
                },
                actions = {
                    if (scan.scanning) LoadingIndicator(Modifier.padding(end = 12.dp).size(32.dp))
                },
                scrollBehavior = scrollBehavior,
            )
        },
        floatingActionButton = {
            if (prerequisite == BlePrerequisite.OK) {
                ExtendedFloatingActionButton(
                    onClick = if (scan.scanning) onStopScan else onStartScan,
                    icon = { Icon(if (scan.scanning) Icons.Rounded.Stop else Icons.Rounded.Refresh, null) },
                    text = { Text(stringResource(if (scan.scanning) R.string.scan_stop else R.string.scan_again)) },
                )
            }
        },
    ) { padding ->
        when (prerequisite) {
            BlePrerequisite.NO_PERMISSION -> Prerequisite(
                padding,
                R.string.permission_title,
                R.string.permission_text,
                R.string.permission_grant,
                onRequestPermission,
            )

            BlePrerequisite.BLUETOOTH_OFF -> Prerequisite(
                padding,
                R.string.bluetooth_off_title,
                R.string.bluetooth_off_text,
                R.string.bluetooth_enable,
                onEnableBluetooth,
            )

            BlePrerequisite.OK -> DeviceList(padding, scan, scale, saved, onSelect)
        }
    }
}

@Composable
private fun Prerequisite(padding: PaddingValues, title: Int, text: Int, action: Int, onAction: () -> Unit) {
    Column(Modifier.padding(padding).padding(16.dp)) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(title), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(text), style = MaterialTheme.typography.bodyMedium)
                Button(onClick = onAction) { Text(stringResource(action)) }
            }
        }
    }
}

@Composable
private fun DeviceList(
    padding: PaddingValues,
    scan: ScanUiState,
    scale: ScaleState,
    saved: SavedDevice?,
    onSelect: (String, String, ScaleModel) -> Unit,
) {
    val found = scan.devices.filterNot { it.address == saved?.address }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = padding.calculateTopPadding(),
            bottom = padding.calculateBottomPadding() + 96.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
    ) {
        if (saved != null) {
            item { SectionHeader(R.string.scan_saved) }
            item {
                val seen = scan.devices.firstOrNull { it.address == saved.address }
                DeviceItem(
                    title = saved.name,
                    model = saved.model,
                    rssi = seen?.rssi,
                    bonded = true,
                    scale = scale,
                    address = saved.address,
                    index = 0,
                    count = 1,
                    onClick = { onSelect(saved.address, saved.name, saved.model) },
                )
            }
        }
        item { SectionHeader(R.string.scan_found) }
        if (found.isEmpty()) {
            item {
                Text(
                    scan.error ?: stringResource(R.string.scan_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
        }
        itemsIndexed(found, key = { _, d -> d.address }) { index, device ->
            DeviceItem(
                title = device.name,
                model = device.model,
                rssi = device.rssi,
                bonded = device.bonded,
                scale = scale,
                address = device.address,
                index = index,
                count = found.size,
                onClick = { onSelect(device.address, device.name, device.model) },
            )
        }
    }
}

@Composable
private fun SectionHeader(text: Int) {
    Text(
        stringResource(text),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp, start = 4.dp),
    )
}

@Composable
private fun DeviceItem(
    title: String,
    model: ScaleModel,
    rssi: Int?,
    bonded: Boolean,
    scale: ScaleState,
    address: String,
    index: Int,
    count: Int,
    onClick: () -> Unit,
) {
    val isCurrent = scale.address.equals(address, ignoreCase = true)
    val modelText = if (model.isKnown) model.displayName else address
    SegmentedListItem(
        onClick = onClick,
        shapes = ListItemDefaults.segmentedShapes(index, count),
        leadingContent = {
            Icon(
                when {
                    isCurrent && scale.isReady -> Icons.Rounded.BluetoothConnected
                    model.isKnown -> Icons.Rounded.Scale
                    else -> Icons.Rounded.Bluetooth
                },
                contentDescription = null,
            )
        },
        supportingContent = {
            Text(
                when {
                    isCurrent && scale.phase.isBusy -> stringResource(scale.phase.labelRes())
                    isCurrent && scale.error != null -> scale.error
                    rssi != null -> stringResource(R.string.scan_rssi, modelText, rssi) +
                        if (bonded) " · " + stringResource(R.string.scan_bonded) else ""

                    else -> modelText
                },
            )
        },
        trailingContent = {
            when {
                isCurrent && scale.phase.isBusy -> LoadingIndicator(Modifier.size(32.dp))
                isCurrent && scale.isReady -> Icon(
                    Icons.Rounded.CheckCircle,
                    contentDescription = stringResource(R.string.phase_ready),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title)
    }
}

@Preview(showBackground = true)
@Composable
private fun ScanPreview() {
    OpenScalesTheme(dynamicColor = false) {
        ScanScreen(
            prerequisite = BlePrerequisite.OK,
            scan = ScanUiState(
                scanning = true,
                devices = listOf(
                    DiscoveredScale("AA:01", "Timemore ESPRO", ScaleModel.ESPRO, -48, false),
                    DiscoveredScale("AA:02", "TES016", ScaleModel.BASIC3, -71, true),
                ),
            ),
            scale = ScaleState(),
            saved = SavedDevice("AA:03", "Моя Basic 3", ScaleModel.BASIC3),
            onBack = {}, onRequestPermission = {}, onEnableBluetooth = {},
            onStartScan = {}, onStopScan = {}, onSelect = { _, _, _ -> },
        )
    }
}
