package dev.openscales.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/**
 * Отступы содержимого списка под `Scaffold`: к его отступам [scaffold] (заголовок, системные панели, вырез экрана)
 * прибавляются свои — [horizontal] с боков, [top] и [bottom]. Бока обязательно берутся из `Scaffold`: в альбомной
 * ориентации там вырез экрана и панель навигации, и список с боками-константой уходит под них.
 */
@Composable
fun screenContentPadding(
    scaffold: PaddingValues,
    horizontal: Dp = 16.dp,
    top: Dp = 0.dp,
    bottom: Dp = 0.dp,
): PaddingValues = scaffold.plusContent(LocalLayoutDirection.current, horizontal, top, bottom)

internal fun PaddingValues.plusContent(direction: LayoutDirection, horizontal: Dp, top: Dp, bottom: Dp) = PaddingValues(
    start = calculateStartPadding(direction) + horizontal,
    top = calculateTopPadding() + top,
    end = calculateEndPadding(direction) + horizontal,
    bottom = calculateBottomPadding() + bottom,
)
