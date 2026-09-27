package dev.openscales.ui.recipes

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.openscales.R
import dev.openscales.protocol.WeightUnit
import dev.openscales.recipe.StepWater
import dev.openscales.recipe.StepWeightMode
import dev.openscales.recipe.formatStepWeight
import dev.openscales.ui.components.formatTime
import dev.openscales.ui.components.symbolRes

/**
 * Карточка шага рецепта — на экране «Шаги» и свёрнутым элементом в редакторе. Справа — вода шага при варке
 * ([water]) или рубеж ([target], в редакторе). [onClick] — карточка нажимается (редактор).
 */
@Composable
fun StepCard(
    title: String,
    note: String?,
    range: String,
    /** Остаток в текущем шаге; у остальных шагов `null`. */
    remaining: Int?,
    water: StepWater?,
    unit: WeightUnit,
    modifier: Modifier = Modifier,
    target: String? = null,
    onClick: (() -> Unit)? = null,
    /** У шага стоит «крупно — время до конца шага»: в редакторе рядом со временем иконка таймера. */
    showsTime: Boolean = false,
) {
    val active = remaining != null
    // Выделение переходит с шага на шаг плавно.
    val container by animateColorAsState(
        if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        label = "highlight",
    )
    val content by animateColorAsState(
        if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
        label = "highlightContent",
    )
    val colors = CardDefaults.cardColors(containerColor = container, contentColor = content)
    val body = @Composable {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                note?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    val muted = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                    Text(
                        if (remaining != null) stringResource(R.string.brew_step_left, formatTime(remaining)) else range,
                        style = MaterialTheme.typography.labelLarge,
                        color = muted,
                    )
                    if (showsTime) {
                        Icon(
                            Icons.Rounded.Timer,
                            contentDescription = stringResource(R.string.editor_show_time),
                            tint = muted,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
            if (water != null) {
                // Крупно — значение режима, мелко под ним — цель шага с подписью, что значит крупное число.
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        stringResource(
                            R.string.value_with_unit,
                            formatStepWeight(water.grams, unit),
                            stringResource(unit.symbolRes()),
                        ),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Text(
                        stringResource(
                            if (water.mode == StepWeightMode.REMAINING) R.string.brew_left_of else R.string.brew_poured_of,
                            stepWeightWithUnit(water.targetG, unit),
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.End,
                    )
                }
            } else if (target != null) {
                Text(target, style = MaterialTheme.typography.headlineSmall)
            }
        }
    }
    if (onClick != null) {
        Card(onClick = onClick, modifier = modifier.fillMaxWidth(), colors = colors) { body() }
    } else {
        Card(modifier = modifier.fillMaxWidth(), colors = colors) { body() }
    }
}

/** Подпись рецепта: иконка и текст мелко, без карточки. */
@Composable
fun HintRow(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(Icons.Rounded.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
