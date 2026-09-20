package dev.openscales.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Label
import androidx.compose.material.icons.rounded.BluetoothDisabled
import androidx.compose.material.icons.rounded.Brightness6
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Numbers
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Scale
import androidx.compose.material.icons.rounded.Straighten
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.openscales.R
import dev.openscales.data.AppSettings
import dev.openscales.data.BeepNote
import dev.openscales.protocol.Precision
import dev.openscales.protocol.ScaleModel
import dev.openscales.protocol.Sensitivity
import dev.openscales.protocol.WeightUnit
import dev.openscales.session.ConnectionPhase
import dev.openscales.session.ScaleSession
import dev.openscales.session.ScaleSettings
import dev.openscales.session.ScaleState
import dev.openscales.ui.components.ConnectedChoice
import dev.openscales.ui.components.labelRes
import dev.openscales.ui.theme.OpenScalesTheme

data class SettingsActions(
    val onBack: () -> Unit,
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
    val onBeepEnabled: (Boolean) -> Unit = {},
    val onBeepNote: (BeepNote) -> Unit = {},
    val onTriggerOnPress: (Boolean) -> Unit = {},
    val onKeepScreenOn: (Boolean) -> Unit = {},
    val onSyncTimer: (Boolean) -> Unit = {},
)

private enum class Confirm { FACTORY_RESET, FORGET }

private val BrightnessPresets = listOf(30, 70, 100)

@Composable
fun SettingsScreen(
    state: ScaleState,
    appSettings: AppSettings,
    snackbarHostState: SnackbarHostState,
    actions: SettingsActions,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var confirm by rememberSaveable { mutableStateOf<Confirm?>(null) }
    var renaming by rememberSaveable { mutableStateOf(false) }
    val ready = state.isReady
    val legacy = state.model == ScaleModel.OLD_DOUBLE
    val s = state.settings
    val hasScale = state.address != null
    val noteLabels = BeepNote.entries.map { stringResource(it.labelRes()) }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            MediumFlexibleTopAppBar(
                title = { Text(stringResource(if (hasScale) R.string.settings_title else R.string.settings_title_app)) },
                subtitle = { Text(state.name ?: if (hasScale) state.model.displayName else stringResource(R.string.app_name)) },
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
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
        ) {
            section(R.string.section_app)
            group(
                listOf(
                    { i, n ->
                        ChoiceRow(i, n, Icons.Rounded.TouchApp, stringResource(R.string.setting_app_trigger)) {
                            ConnectedChoice(
                                options = listOf(
                                    true to stringResource(R.string.trigger_on_press),
                                    false to stringResource(R.string.trigger_on_release),
                                ),
                                selected = appSettings.triggerOnPress,
                                onSelect = actions.onTriggerOnPress,
                            )
                        }
                    },
                    { i, n ->
                        SettingRow(
                            i, n, Icons.Rounded.LightMode, stringResource(R.string.setting_app_keep_screen_on), null,
                            trailing = {
                                Switch(checked = appSettings.keepScreenOn, onCheckedChange = actions.onKeepScreenOn)
                            },
                            onClick = { actions.onKeepScreenOn(!appSettings.keepScreenOn) },
                        )
                    },
                    { i, n ->
                        SettingRow(
                            i, n, Icons.Rounded.Timer, stringResource(R.string.setting_app_sync_timer),
                            stringResource(R.string.setting_app_sync_timer_hint),
                            trailing = {
                                Switch(
                                    checked = appSettings.syncTimerWithScale,
                                    onCheckedChange = actions.onSyncTimer,
                                )
                            },
                            onClick = { actions.onSyncTimer(!appSettings.syncTimerWithScale) },
                        )
                    },
                    { i, n ->
                        SettingRow(
                            i, n, Icons.AutoMirrored.Rounded.VolumeUp, stringResource(R.string.setting_app_beep), null,
                            trailing = {
                                Switch(checked = appSettings.beepEnabled, onCheckedChange = actions.onBeepEnabled)
                            },
                            onClick = { actions.onBeepEnabled(!appSettings.beepEnabled) },
                        )
                    },
                    { i, n ->
                        ChoiceRow(i, n, Icons.Rounded.MusicNote, stringResource(R.string.setting_app_beep_note)) {
                            ConnectedChoice(
                                options = BeepNote.entries.zip(noteLabels),
                                selected = appSettings.beepNote,
                                onSelect = actions.onBeepNote,
                                enabled = appSettings.beepEnabled,
                                compact = true,
                            )
                        }
                    },
                ),
            )

            if (!hasScale) return@LazyColumn

            if (!ready) {
                item {
                    Text(
                        stringResource(R.string.settings_not_connected),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
            }

            section(R.string.section_device)
            group(
                buildList {
                    add { i, n ->
                        SettingRow(i, n, Icons.AutoMirrored.Rounded.Label, stringResource(R.string.setting_name), state.name.orDash(),
                            enabled = ready, onClick = { renaming = true })
                    }
                    add { i, n -> SettingRow(i, n, Icons.Rounded.Scale, stringResource(R.string.setting_model), state.model.displayName) }
                    add { i, n -> SettingRow(i, n, Icons.Rounded.Bluetooth, stringResource(R.string.setting_address), state.address.orDash()) }
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
                    if (state.model.hasSoundSwitch) {
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
                    if (state.model.hasBrightness) {
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
                }) { Text(stringResource(R.string.confirm)) }
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

private typealias GroupItem = @Composable (index: Int, count: Int) -> Unit

private fun LazyListScope.section(title: Int) {
    item {
        Text(
            stringResource(title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 20.dp, bottom = 8.dp, start = 4.dp),
        )
    }
}

private fun LazyListScope.group(items: List<GroupItem>) {
    items.forEachIndexed { index, content -> item { content(index, items.size) } }
}

@Composable
private fun SettingRow(
    index: Int,
    count: Int,
    icon: ImageVector,
    title: String,
    value: String?,
    enabled: Boolean = true,
    destructive: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val tint = if (destructive) MaterialTheme.colorScheme.error else Color.Unspecified
    val shapes = ListItemDefaults.segmentedShapes(index, count)
    val leading: @Composable () -> Unit = { Icon(icon, null, tint = tint) }
    val supporting: (@Composable () -> Unit)? = value?.let { { Text(it) } }
    val content: @Composable () -> Unit = {
        Text(title, color = if (destructive) MaterialTheme.colorScheme.error else Color.Unspecified)
    }
    if (onClick != null) {
        SegmentedListItem(
            onClick = onClick,
            shapes = shapes,
            enabled = enabled,
            leadingContent = leading,
            supportingContent = supporting,
            trailingContent = trailing,
            content = content,
        )
    } else {
        SegmentedListItem(
            shapes = shapes,
            leadingContent = leading,
            supportingContent = supporting,
            trailingContent = trailing,
            content = content,
        )
    }
}

@Composable
private fun ChoiceRow(index: Int, count: Int, icon: ImageVector, title: String, choice: @Composable () -> Unit) {
    SegmentedListItem(
        shapes = ListItemDefaults.segmentedShapes(index, count),
        leadingContent = { Icon(icon, null) },
        supportingContent = { Column(Modifier.padding(top = 8.dp)) { choice() } },
    ) {
        Text(title)
    }
}

@Composable
private fun StandbyRow(index: Int, count: Int, s: ScaleSettings, enabled: Boolean, onChange: (Int) -> Unit) {
    val minutes = s.standbyMinutes
    SegmentedListItem(
        shapes = ListItemDefaults.segmentedShapes(index, count),
        leadingContent = { Icon(Icons.Rounded.Timer, null) },
        supportingContent = {
            Text(minutes?.let { stringResource(R.string.standby_minutes, it) } ?: stringResource(R.string.unknown_value))
        },
        trailingContent = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                FilledTonalIconButton(
                    onClick = { minutes?.let { onChange(it - 1) } },
                    enabled = enabled && minutes != null && minutes > ScaleSession.STANDBY_RANGE.first,
                ) { Icon(Icons.Rounded.Remove, stringResource(R.string.decrease)) }
                FilledTonalIconButton(
                    onClick = { minutes?.let { onChange(it + 1) } },
                    enabled = enabled && minutes != null && minutes < ScaleSession.STANDBY_RANGE.last,
                ) { Icon(Icons.Rounded.Add, stringResource(R.string.increase)) }
            }
        },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(R.string.setting_standby))
    }
}

@Composable
private fun RenameDialog(initial: String, maxBytes: Int, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf(initial) }
    val bytes = text.trim().toByteArray(Charsets.UTF_8).size
    val valid = bytes in 1..maxBytes
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.rename_title)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                isError = !valid,
                supportingText = { Text("$bytes / $maxBytes · " + stringResource(R.string.rename_limit, maxBytes, maxBytes)) },
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(text.trim()) }, enabled = valid) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun String?.orDash(): String = this?.takeIf { it.isNotBlank() } ?: stringResource(R.string.unknown_value)

@Preview(showBackground = true, heightDp = 1400)
@Composable
private fun SettingsPreview() {
    OpenScalesTheme(dynamicColor = false) {
        SettingsScreen(
            state = ScaleState(
                phase = ConnectionPhase.READY,
                name = "Black Mirror ESPRO",
                address = "C8:47:8C:00:11:22",
                model = ScaleModel.ESPRO,
                settings = ScaleSettings(
                    sound = true, standbyMinutes = 5, sensitivity = Sensitivity.MEDIUM,
                    precision = Precision.HIGH, brightness = 70, firmware = "V1.2.3", serial = "TM2025000123",
                ),
            ),
            appSettings = AppSettings(beepNote = BeepNote.G6),
            snackbarHostState = SnackbarHostState(),
            actions = SettingsActions({}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}),
        )
    }
}

@Preview(showBackground = true, widthDp = 320)
@Composable
private fun SettingsNoScaleNarrowPreview() {
    OpenScalesTheme(dynamicColor = false) {
        SettingsScreen(
            state = ScaleState(),
            appSettings = AppSettings(),
            snackbarHostState = SnackbarHostState(),
            actions = SettingsActions({}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}),
        )
    }
}
