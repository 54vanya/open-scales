package dev.openscales.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.openscales.ui.components.plusContent
import org.junit.Assert.assertEquals
import org.junit.Test

/** Отступы списка под `Scaffold`: бока — вырез экрана плюс свой отступ. */
class ScreenPaddingTest {

    @Test
    fun `without insets only the own padding remains`() {
        val p = PaddingValues(top = 64.dp, bottom = 24.dp).plusContent(LayoutDirection.Ltr, 16.dp, top = 8.dp, bottom = 24.dp)
        assertEquals(16.dp, p.calculateStartPadding(LayoutDirection.Ltr))
        assertEquals(16.dp, p.calculateEndPadding(LayoutDirection.Ltr))
        assertEquals(72.dp, p.calculateTopPadding())
        assertEquals(48.dp, p.calculateBottomPadding())
    }

    @Test
    fun `cutout on the left pushes the start edge`() {
        val p = PaddingValues(start = 48.dp).plusContent(LayoutDirection.Ltr, 16.dp, 0.dp, 0.dp)
        assertEquals(64.dp, p.calculateStartPadding(LayoutDirection.Ltr))
        assertEquals(16.dp, p.calculateEndPadding(LayoutDirection.Ltr))
    }

    @Test
    fun `cutout on the right pushes the end edge`() {
        val p = PaddingValues(end = 48.dp).plusContent(LayoutDirection.Ltr, 12.dp, 0.dp, 0.dp)
        assertEquals(12.dp, p.calculateStartPadding(LayoutDirection.Ltr))
        assertEquals(60.dp, p.calculateEndPadding(LayoutDirection.Ltr))
    }

    @Test
    fun `right-to-left keeps the cutout on its physical side`() {
        // Отступы Scaffold заданы физическими сторонами: вырез слева остаётся слева и при письме справа налево.
        val p = PaddingValues.Absolute(left = 48.dp).plusContent(LayoutDirection.Rtl, 16.dp, 0.dp, 0.dp)
        assertEquals(16.dp, p.calculateStartPadding(LayoutDirection.Rtl))
        assertEquals(64.dp, p.calculateEndPadding(LayoutDirection.Rtl))
    }
}
