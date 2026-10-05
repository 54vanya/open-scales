package dev.openscales.ui.settings

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ExposureZero
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.openscales.ui.components.screenContentPadding
import dev.openscales.R
import dev.openscales.data.AppLanguage
import dev.openscales.data.AppSettings
import dev.openscales.data.BeepNote
import dev.openscales.data.ThemeMode
import dev.openscales.recipe.StepWeightMode
import dev.openscales.ui.components.ConnectedChoice
import dev.openscales.ui.components.labelRes
import dev.openscales.ui.theme.OpenScalesTheme

/** Действия экрана настроек приложения; настройки весов — в карточке весов (`ScaleDetailsScreen`). */
data class SettingsActions(
    val onBack: () -> Unit,
    val onBeepEnabled: (Boolean) -> Unit = {},
    val onBeepNote: (BeepNote) -> Unit = {},
    val onTriggerOnPress: (Boolean) -> Unit = {},
    val onKeepScreenOn: (Boolean) -> Unit = {},
    val onSyncTimer: (Boolean) -> Unit = {},
    val onSlashedZero: (Boolean) -> Unit = {},
    val onStepWeightMode: (StepWeightMode) -> Unit = {},
    val onStepSignals: (Boolean) -> Unit = {},
    val onLanguage: (AppLanguage) -> Unit = {},
    val onOwnColors: (Boolean) -> Unit = {},
    val onThemeMode: (ThemeMode) -> Unit = {},
)

@Composable
fun SettingsScreen(
    appSettings: AppSettings,
    actions: SettingsActions,
    language: AppLanguage = AppLanguage.SYSTEM,
    ownColors: Boolean = true,
    themeMode: ThemeMode = ThemeMode.SYSTEM,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    // Цвета обоев есть только с Android 12; раньше приложение всегда в собственной палитре, строка не нужна.
    val ownColorsItem: GroupItem? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        { i, n ->
            SettingRow(
                i, n, Icons.Rounded.Palette, stringResource(R.string.setting_app_own_colors),
                stringResource(R.string.setting_app_own_colors_hint),
                trailing = { Switch(checked = ownColors, onCheckedChange = actions.onOwnColors) },
                onClick = { actions.onOwnColors(!ownColors) },
            )
        }
    } else {
        null
    }
    val noteLabels = BeepNote.entries.map { stringResource(it.labelRes()) }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MediumFlexibleTopAppBar(
                title = { Text(stringResource(R.string.settings_title_app)) },
                subtitle = { Text(stringResource(R.string.app_name)) },
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
            section(R.string.section_app)
            group(
                listOfNotNull(
                    { i, n ->
                        ChoiceRow(i, n, Icons.Rounded.Language, stringResource(R.string.setting_app_language)) {
                            ConnectedChoice(
                                options = listOf(
                                    AppLanguage.SYSTEM to stringResource(R.string.language_system),
                                    // Названия языков — всегда на самих этих языках.
                                    AppLanguage.RUSSIAN to stringResource(R.string.language_russian),
                                    AppLanguage.ENGLISH to stringResource(R.string.language_english),
                                ),
                                selected = language,
                                onSelect = actions.onLanguage,
                                // Три подписи на двух языках: узкие отступы, подпись уменьшается, а не обрезается.
                                compact = true,
                            )
                        }
                    },
                    { i, n ->
                        ChoiceRow(i, n, Icons.Rounded.Contrast, stringResource(R.string.setting_app_theme)) {
                            ConnectedChoice(
                                options = listOf(
                                    ThemeMode.SYSTEM to stringResource(R.string.theme_system),
                                    ThemeMode.LIGHT to stringResource(R.string.theme_light),
                                    ThemeMode.DARK to stringResource(R.string.theme_dark),
                                ),
                                selected = themeMode,
                                onSelect = actions.onThemeMode,
                                compact = true,
                            )
                        }
                    },
                    ownColorsItem,
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
                            i, n, Icons.Rounded.ExposureZero, stringResource(R.string.setting_app_slashed_zero), null,
                            trailing = {
                                Switch(checked = appSettings.slashedZero, onCheckedChange = actions.onSlashedZero)
                            },
                            onClick = { actions.onSlashedZero(!appSettings.slashedZero) },
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
                        ChoiceRow(i, n, Icons.Rounded.WaterDrop, stringResource(R.string.setting_step_weight)) {
                            ConnectedChoice(
                                options = listOf(
                                    StepWeightMode.REMAINING to stringResource(R.string.step_weight_remaining),
                                    StepWeightMode.POURED to stringResource(R.string.step_weight_poured),
                                ),
                                selected = appSettings.stepWeightMode,
                                onSelect = actions.onStepWeightMode,
                                compact = true,
                            )
                        }
                    },
                    { i, n ->
                        SettingRow(
                            i, n, Icons.Rounded.NotificationsActive, stringResource(R.string.setting_step_signals),
                            stringResource(R.string.setting_step_signals_hint),
                            trailing = { Switch(checked = appSettings.stepSignals, onCheckedChange = actions.onStepSignals) },
                            onClick = { actions.onStepSignals(!appSettings.stepSignals) },
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

        }
    }

}

@Preview(showBackground = true, heightDp = 1250)
@Composable
private fun SettingsPreview() {
    OpenScalesTheme(dynamicColor = false) {
        SettingsScreen(appSettings = AppSettings(beepNote = BeepNote.G6), actions = SettingsActions({}))
    }
}

@Preview(showBackground = true, widthDp = 320, heightDp = 1250, locale = "ru")
@Composable
private fun SettingsNarrowRussianPreview() {
    OpenScalesTheme(dynamicColor = false) {
        SettingsScreen(appSettings = AppSettings(), actions = SettingsActions({}), language = AppLanguage.RUSSIAN)
    }
}
