package dev.openscales.ui.brew

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import dev.openscales.R
import dev.openscales.protocol.WeightUnit
import dev.openscales.recipe.BuiltInRecipes
import dev.openscales.recipe.ManualDose
import dev.openscales.recipe.Recipe
import dev.openscales.ui.components.symbolRes
import dev.openscales.ui.recipes.stepWeightWithUnit
import dev.openscales.ui.theme.OpenScalesTheme

/**
 * «Доза кофе»: зерно уже унесли с весов, а «Далее» не нажали. Число — в единицах весов с их точностью,
 * [initialG] — подстановка (устоявшийся вес или доза рецепта). Под полем всегда одна строка: вода от введённой
 * дозы или допустимый диапазон, — чтобы окно не меняло высоту. «Далее» — [onConfirm] с дозой в граммах.
 */
@Composable
fun ManualDoseDialog(
    recipe: Recipe,
    unit: WeightUnit,
    initialG: Double,
    scaleReady: Boolean,
    onConfirm: (Double) -> Unit,
    onDismiss: () -> Unit,
) {
    // Весы переключили единицы — число в поле было бы в прежних: подставляем заново.
    var field by rememberSaveable(unit, stateSaver = TextFieldValue.Saver) {
        val text = ManualDose.text(initialG, unit)
        mutableStateOf(TextFieldValue(text, TextRange(text.length)))
    }
    val grams = ManualDose.parseGrams(field.text, unit)
    val canConfirm = grams != null && scaleReady
    val confirm = { if (grams != null && scaleReady) onConfirm(grams) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.brew_manual_dose_title)) },
        text = {
            OutlinedTextField(
                value = field,
                onValueChange = { if (ManualDose.accepts(it.text, unit)) field = it },
                isError = grams == null,
                supportingText = {
                    Text(
                        if (grams != null) {
                            stringResource(R.string.brew_manual_dose_water, stepWeightWithUnit(recipe.totalWaterG(grams), unit))
                        } else {
                            val (min, max) = ManualDose.bounds(unit)
                            stringResource(
                                R.string.brew_manual_dose_range,
                                min,
                                stringResource(R.string.value_with_unit, max, stringResource(unit.symbolRes())),
                            )
                        },
                        maxLines = 1,
                    )
                },
                suffix = { Text(stringResource(unit.symbolRes())) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { confirm() }),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        },
        confirmButton = {
            TextButton(onClick = confirm, enabled = canConfirm) { Text(stringResource(R.string.brew_next)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Preview(locale = "ru")
@Composable
private fun ManualDoseGramsPreview() {
    OpenScalesTheme(dynamicColor = false) {
        ManualDoseDialog(BuiltInRecipes.hoffmannV60, WeightUnit.GRAM, 18.2, scaleReady = true, onConfirm = {}, onDismiss = {})
    }
}

@Preview(locale = "ru")
@Composable
private fun ManualDoseOuncesPreview() {
    OpenScalesTheme(dynamicColor = false) {
        ManualDoseDialog(BuiltInRecipes.hoffmannV60, WeightUnit.OUNCE, 18.2, scaleReady = true, onConfirm = {}, onDismiss = {})
    }
}

@Preview(locale = "ru")
@Composable
private fun ManualDoseOutOfRangePreview() {
    OpenScalesTheme(dynamicColor = false) {
        ManualDoseDialog(BuiltInRecipes.hoffmannV60, WeightUnit.GRAM, 150.0, scaleReady = true, onConfirm = {}, onDismiss = {})
    }
}
