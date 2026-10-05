package dev.openscales.ui.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.openscales.R
import dev.openscales.session.ScaleSession
import dev.openscales.session.ScaleSettings

/** Общие элементы экранов настроек приложения и карточки весов. */

internal typealias GroupItem = @Composable (index: Int, count: Int) -> Unit

internal fun LazyListScope.section(title: Int) {
    item {
        Text(
            stringResource(title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 20.dp, bottom = 8.dp, start = 4.dp),
        )
    }
}

internal fun LazyListScope.group(items: List<GroupItem>) {
    items.forEachIndexed { index, content -> item { content(index, items.size) } }
}

@Composable
internal fun SettingRow(
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
    // Не `Color.Unspecified`: без окраски значок рисуется своим чёрным и в тёмной теме не виден.
    val tint = if (destructive) MaterialTheme.colorScheme.error else LocalContentColor.current
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
internal fun ChoiceRow(index: Int, count: Int, icon: ImageVector, title: String, choice: @Composable () -> Unit) {
    SegmentedListItem(
        shapes = ListItemDefaults.segmentedShapes(index, count),
        leadingContent = { Icon(icon, null) },
        supportingContent = { Column(Modifier.padding(top = 8.dp)) { choice() } },
    ) {
        Text(title)
    }
}

@Composable
internal fun StandbyRow(index: Int, count: Int, s: ScaleSettings, enabled: Boolean, onChange: (Int) -> Unit) {
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
internal fun RenameDialog(initial: String, maxBytes: Int, onDismiss: () -> Unit, onSave: (String) -> Unit) {
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
                supportingText = { Text("$bytes / $maxBytes · " + pluralStringResource(R.plurals.rename_limit, maxBytes, maxBytes)) },
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(text.trim()) }, enabled = valid) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
internal fun String?.orDash(): String = this?.takeIf { it.isNotBlank() } ?: stringResource(R.string.unknown_value)

