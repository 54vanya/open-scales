package dev.openscales.ui.scale

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Label
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.BluetoothConnected
import androidx.compose.material.icons.rounded.BluetoothDisabled
import androidx.compose.material.icons.rounded.Brightness6
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Numbers
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Scale
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Straighten
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.openscales.ui.components.screenContentPadding
import dev.openscales.R
import dev.openscales.data.SavedDevice
import dev.openscales.protocol.Precision
import dev.openscales.protocol.ScaleModel
import dev.openscales.protocol.Sensitivity
import dev.openscales.protocol.WeightUnit
import dev.openscales.session.ConnectionPhase
import dev.openscales.session.ScaleSession
import dev.openscales.session.ScaleSettings
import dev.openscales.session.ScaleState
import dev.openscales.ui.components.BusyIndicator
import dev.openscales.ui.components.ConnectedChoice
import dev.openscales.ui.components.labelRes
import dev.openscales.ui.settings.ChoiceRow
import dev.openscales.ui.settings.GroupItem
import dev.openscales.ui.settings.RenameDialog
import dev.openscales.ui.settings.SettingRow
import dev.openscales.ui.settings.StandbyRow
import dev.openscales.ui.settings.group
import dev.openscales.ui.settings.orDash
import dev.openscales.ui.settings.section
import dev.openscales.ui.theme.OpenScalesTheme

data class ScaleDetailsActions(
    val onBack: () -> Unit,
    val onConnect: () -> Unit,
    val onUnit: (WeightUnit) -> Unit,
    val onSound: (Boolean) -> Unit,
    val onSensitivity: (Sensitivity) -> Unit,
    val onPrecision: (Precision) -> Unit,
    val onStandby: (Int) -> Unit,
    val onBrightness: (Int) -> Unit,
    val onRename: (String) -> Unit,
    val onDisconnect: () -> Unit,
    val onPowerOff: () -> Unit,
    val onFactoryReset: () -> Unit,
    val onForget: () -> Unit,
)

private enum class Confirm { FACTORY_RESET, FORGET }

private val BrightnessPresets = listOf(30, 70, 100)

/** Состояние весов с этим адресом относительно текущей сессии. */
enum class ScaleLink { CONNECTED, CONNECTING, DISCONNECTED }

fun scaleLink(address: String, state: ScaleState): ScaleLink = when {
    !state.address.equals(address, ignoreCase = true) -> ScaleLink.DISCONNECTED
    state.isReady -> ScaleLink.CONNECTED
    state.phase.isBusy || state.reconnecting -> ScaleLink.CONNECTING
    else -> ScaleLink.DISCONNECTED
}

/**
 * Карточка весов: сведения, настройки (только пока эти весы подключены — настройки хранятся в самих весах)
 * и действия. Открывается шестерёнкой в строке весов на экране «Весы».
 */
@Composable
fun ScaleDetailsScreen(
    address: String,
    state: ScaleState,
    saved: SavedDevice?,
    snackbarHostState: SnackbarHostState,
    actions: ScaleDetailsActions,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var confirm by rememberSaveable { mutableStateOf<Confirm?>(null) }
    var renaming by rememberSaveable { mutableStateOf(false) }
    val link = scaleLink(address, state)
    val ready = link == ScaleLink.CONNECTED
    val name = (if (ready) state.name else saved?.name)?.takeIf { it.isNotBlank() } ?: saved?.name
    val model = if (ready) state.model else saved?.model ?: ScaleModel.UNKNOWN
    val legacy = model == ScaleModel.OLD_DOUBLE
    val s = state.settings

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            MediumFlexibleTopAppBar(
                title = { Text(name.orDash()) },
                subtitle = { Text(model.displayName) },
                navigationIcon = {
                    IconButton(onClick = actions.onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back))
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = screenContentPadding(padding, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
        ) {
            if (!ready) {
                // Не подключены: только то, что известно без весов, и как до них добраться.
                section(R.string.section_device)
                group(
                    listOf<GroupItem>(
                        { i, n -> SettingRow(i, n, Icons.AutoMirrored.Rounded.Label, stringResource(R.string.setting_name), name.orDash()) },
                        { i, n -> SettingRow(i, n, Icons.Rounded.Scale, stringResource(R.string.setting_model), model.displayName) },
                        { i, n -> SettingRow(i, n, Icons.Rounded.Bluetooth, stringResource(R.string.setting_address), address) },
                    ),
                )
                section(R.string.section_actions)
                group(
                    listOf<GroupItem>(
                        { i, n ->
                            if (link == ScaleLink.CONNECTING) {
                                SettingRow(
                                    i, n, Icons.Rounded.BluetoothConnected,
                                    stringResource(if (state.reconnecting && !state.phase.isBusy) R.string.reconnecting else state.phase.labelRes()),
                                    null,
                                    trailing = { BusyIndicator() },
                                )
                            } else {
                                SettingRow(i, n, Icons.Rounded.BluetoothConnected, stringResource(R.string.action_connect), null,
                                    onClick = actions.onConnect)
                            }
                        },
                        { i, n ->
                            SettingRow(i, n, Icons.Rounded.DeleteForever, stringResource(R.string.action_forget), null,
                                destructive = true, onClick = { confirm = Confirm.FORGET })
                        },
                    ),
                )
                return@LazyColumn
            }

            section(R.string.section_device)
            group(
                buildList {
                    add { i, n ->
                        SettingRow(i, n, Icons.AutoMirrored.Rounded.Label, stringResource(R.string.setting_name), name.orDash(),
                            enabled = ready, onClick = { renaming = true })
                    }
                    add { i, n -> SettingRow(i, n, Icons.Rounded.Scale, stringResource(R.string.setting_model), model.displayName) }
                    add { i, n -> SettingRow(i, n, Icons.Rounded.Bluetooth, stringResource(R.string.setting_address), address) }
                    if (!legacy) {
                        add { i, n -> SettingRow(i, n, Icons.Rounded.Memory, stringResource(R.string.setting_firmware), s.firmware.orDash()) }
                        add { i, n -> SettingRow(i, n, Icons.Rounded.Numbers, stringResource(R.string.setting_serial), s.serial.orDash()) }
                    }
                },
            )

            section(R.string.section_weighing)
            group(
                buildList {
                    if (!legacy) {
                        add { i, n ->
                            ChoiceRow(i, n, Icons.Rounded.Straighten, stringResource(R.string.setting_unit)) {
                                ConnectedChoice(
                                    options = listOf(
                                        WeightUnit.GRAM to stringResource(R.string.unit_gram),
                                        WeightUnit.OUNCE to stringResource(R.string.unit_ounce),
                                    ),
                                    selected = state.unit,
                                    onSelect = actions.onUnit,
                                    enabled = ready,
                                )
                            }
                        }
                        add { i, n ->
                            ChoiceRow(i, n, Icons.Rounded.Tune, stringResource(R.string.setting_sensitivity)) {
                                ConnectedChoice(
                                    options = listOf(
                                        Sensitivity.HIGH to stringResource(R.string.level_high),
                                        Sensitivity.MEDIUM to stringResource(R.string.level_medium),
                                        Sensitivity.LOW to stringResource(R.string.level_low),
                                    ),
                                    selected = s.sensitivity,
                                    onSelect = actions.onSensitivity,
                                    enabled = ready,
                                )
                            }
                        }
                        add { i, n ->
                            ChoiceRow(i, n, Icons.Rounded.Speed, stringResource(R.string.setting_precision)) {
                                ConnectedChoice(
                                    options = listOf(
                                        Precision.HIGH to stringResource(R.string.level_high),
                                        Precision.LOW to stringResource(R.string.level_low),
                                    ),
                                    selected = s.precision,
                                    onSelect = actions.onPrecision,
                                    enabled = ready,
                                )
                            }
                        }
                        add { i, n -> StandbyRow(i, n, s, ready, actions.onStandby) }
                    }
                    if (model.hasSoundSwitch) {
                        add { i, n ->
                            SettingRow(
                                i, n, Icons.AutoMirrored.Rounded.VolumeUp, stringResource(R.string.setting_sound), null,
                                enabled = ready,
                                trailing = {
                                    Switch(checked = s.sound == true, onCheckedChange = actions.onSound, enabled = ready)
                                },
                                onClick = { actions.onSound(s.sound != true) },
                            )
                        }
                    }
                    if (model.hasBrightness) {
                        add { i, n ->
                            ChoiceRow(i, n, Icons.Rounded.Brightness6, stringResource(R.string.setting_brightness)) {
                                ConnectedChoice(
                                    options = BrightnessPresets.zip(
                                        listOf(
                                            stringResource(R.string.brightness_dark),
                                            stringResource(R.string.brightness_medium),
                                            stringResource(R.string.brightness_bright),
                                        ),
                                    ),
                                    selected = s.brightness?.let { b -> BrightnessPresets.minBy { kotlin.math.abs(it - b) } },
                                    onSelect = actions.onBrightness,
                                    enabled = ready,
                                )
                            }
                        }
                    }
                },
            )

            section(R.string.section_actions)
            group(
                buildList {
                    add { i, n ->
                        SettingRow(i, n, Icons.Rounded.BluetoothDisabled, stringResource(R.string.action_disconnect), null,
                            enabled = state.phase != ConnectionPhase.DISCONNECTED, onClick = actions.onDisconnect)
                    }
                    if (!legacy) {
                        add { i, n ->
                            SettingRow(i, n, Icons.Rounded.PowerSettingsNew, stringResource(R.string.action_power_off), null,
                                enabled = ready, onClick = actions.onPowerOff)
                        }
                        add { i, n ->
                            SettingRow(i, n, Icons.Rounded.RestartAlt, stringResource(R.string.action_factory_reset), null,
                                enabled = ready, destructive = true, onClick = { confirm = Confirm.FACTORY_RESET })
                        }
                    }
                    add { i, n ->
                        SettingRow(i, n, Icons.Rounded.DeleteForever, stringResource(R.string.action_forget), null,
                            destructive = true, onClick = { confirm = Confirm.FORGET })
                    }
                },
            )
        }
    }

    confirm?.let { c ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(stringResource(if (c == Confirm.FORGET) R.string.action_forget else R.string.action_factory_reset)) },
            text = { Text(stringResource(if (c == Confirm.FORGET) R.string.forget_confirm else R.string.factory_reset_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    confirm = null
                    if (c == Confirm.FORGET) actions.onForget() else actions.onFactoryReset()
                }) { Text(stringResource(if (c == Confirm.FORGET) R.string.confirm_forget else R.string.confirm_factory_reset)) }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    if (renaming) {
        RenameDialog(
            initial = state.name.orEmpty(),
            maxBytes = if (legacy) ScaleSession.LEGACY_NAME_MAX_BYTES else ScaleSession.NAME_MAX_BYTES,
            onDismiss = { renaming = false },
            onSave = {
                renaming = false
                actions.onRename(it)
            },
        )
    }
}

private val PreviewSaved = SavedDevice("C8:47:8C:00:11:22", "TIMEMORE_Dot", ScaleModel.DOT)

private val PreviewActions = ScaleDetailsActions({}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {})

@Preview(showBackground = true, heightDp = 1300)
@Composable
private fun ScaleDetailsConnectedPreview() {
    OpenScalesTheme(dynamicColor = false) {
        ScaleDetailsScreen(
            address = PreviewSaved.address,
            state = ScaleState(
                phase = ConnectionPhase.READY, name = "TIMEMORE_Dot", address = PreviewSaved.address, model = ScaleModel.DOT,
                settings = ScaleSettings(standbyMinutes = 5, sensitivity = Sensitivity.MEDIUM, precision = Precision.HIGH, firmware = "v1.0.4"),
            ),
            saved = PreviewSaved,
            snackbarHostState = SnackbarHostState(),
            actions = PreviewActions,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ScaleDetailsDisconnectedPreview() {
    OpenScalesTheme(dynamicColor = false) {
        ScaleDetailsScreen(
            address = PreviewSaved.address,
            state = ScaleState(),
            saved = PreviewSaved,
            snackbarHostState = SnackbarHostState(),
            actions = PreviewActions,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ScaleDetailsConnectingPreview() {
    OpenScalesTheme(dynamicColor = false) {
        ScaleDetailsScreen(
            address = PreviewSaved.address,
            state = ScaleState(phase = ConnectionPhase.HANDSHAKING, address = PreviewSaved.address, model = ScaleModel.DOT),
            saved = PreviewSaved,
            snackbarHostState = SnackbarHostState(),
            actions = PreviewActions,
        )
    }
}
