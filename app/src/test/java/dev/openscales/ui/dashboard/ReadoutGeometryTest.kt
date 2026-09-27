package dev.openscales.ui.dashboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadoutGeometryTest {

    // Размеры в dp (плотность 1) — порядок величин шрифта цифр 88 sp, единиц 28 sp и подписей 14 sp.
    private val measures = ReadoutMeasures(
        numberTemplateWidths = listOf(249f, 249f, 247f),
        digitWidth = 55.5f,
        bigLineHeight = 96f,
        bigBaseline = 76f,
        unitLineHeight = 36f,
        unitBaseline = 28f,
        flowNumberWidth = 104f,
        flowLineHeight = 40f,
        unitWidths = listOf(15f, 32f, 38f, 55f),
        unitGap = 8f,
        labelsHeight = 40f,
        rowGap = 8f,
    )

    /** Ширина карточки внутри: экран минус поля экрана (2×16) и карточки (2×24). */
    private fun cardWidth(screenDp: Float) = screenDp - 32f - 48f

    @Test
    fun `centered number column with units and overhang in the sides fits typical and narrow screens`() {
        for (screen in listOf(320f, 360f, 412f, 448f)) {
            val width = cardWidth(screen)
            val g = readoutGeometry(measures, width, maxHeight = 600f)
            val side = g.sideWidth(measures.unitGap)
            assertTrue("$screen dp", g.numberWidth + 2 * side <= width + 0.01f)
            assertTrue("$screen dp: units", side >= measures.unitGap + g.unitWidth)
            assertTrue("$screen dp: overhang", side >= g.overhang)
        }
    }

    @Test
    fun `number column holds every template`() {
        val g = readoutGeometry(measures, maxWidth = 1000f, maxHeight = 1000f)
        assertEquals(1f, g.scale)
        assertEquals(measures.numberTemplateWidths.max(), g.numberWidth)
        assertEquals(measures.unitWidths.max(), g.unitWidth)
        assertEquals(measures.digitWidth, g.overhang)
    }

    @Test
    fun `narrow card scales digits down but not below the minimum`() {
        val narrow = readoutGeometry(measures, cardWidth(320f), maxHeight = 600f)
        assertTrue(narrow.scale < 1f)
        assertTrue(narrow.scale >= MIN_READOUT_SCALE)
        val tiny = readoutGeometry(measures, maxWidth = 80f, maxHeight = 600f)
        assertEquals(MIN_READOUT_SCALE, tiny.scale)
        assertTrue(tiny.unitScale < 1f)
    }

    @Test
    fun `low card scales digits by height`() {
        val g = readoutGeometry(measures, maxWidth = 1000f, maxHeight = 200f)
        // Две крупные строки делят остаток высоты после подписей, потока и отступа.
        assertEquals((200f - 40f - 40f - 8f) / 2 / 96f, g.scale, 0.001f)
    }

    @Test
    fun `units do not shrink while there is room`() {
        val g = readoutGeometry(measures, cardWidth(412f), maxHeight = 600f)
        assertEquals(1f, g.unitScale)
        assertEquals(1f, g.flowScale)
    }

    @Test
    fun `flow number shrinks only when it would not fit the number column`() {
        val g = readoutGeometry(measures, maxWidth = 80f, maxHeight = 600f)
        assertTrue(g.flowScale < 1f)
        assertTrue(measures.flowNumberWidth * g.flowScale <= g.numberWidth + 0.01f)
    }

    @Test
    fun `min height is where the height limit reaches the minimum scale`() {
        val min = readoutMinHeight(measures)
        val atMin = readoutGeometry(measures, maxWidth = 1000f, maxHeight = min)
        assertEquals(MIN_READOUT_SCALE, atMin.scale, 0.001f)
        val above = readoutGeometry(measures, maxWidth = 1000f, maxHeight = min + 20f)
        assertTrue(above.scale > MIN_READOUT_SCALE)
        // Ниже минимума масштаб держится на пределе, и группа выше доступной высоты — поэтому экран прокручивается.
        val below = readoutGeometry(measures, maxWidth = 1000f, maxHeight = min - 20f)
        assertEquals(MIN_READOUT_SCALE, below.scale)
    }

    @Test
    fun `weight row is as tall as its unit when digits are scaled below it`() {
        // Крупный шрифт в системе: единица выше уменьшенных цифр, и строка веса выше строки таймера.
        val tallUnits = ReadoutMeasures(
            numberTemplateWidths = listOf(249f, 249f, 247f), digitWidth = 55.5f,
            bigLineHeight = 96f, bigBaseline = 76f, unitLineHeight = 80f, unitBaseline = 62f,
            flowNumberWidth = 104f, flowLineHeight = 80f, unitWidths = listOf(15f, 32f, 38f, 55f),
            unitGap = 8f, labelsHeight = 80f, rowGap = 8f,
        )
        val min = readoutMinHeight(tallUnits)
        // Таймер 96 × 0.3, вес — по единице: 62 над базовой линией и 18 под ней.
        assertEquals(96f * MIN_READOUT_SCALE + 80f + 80f + 80f + 8f, min, 0.01f)
        val g = readoutGeometry(tallUnits, maxWidth = 1000f, maxHeight = min + 48f)
        // Лишние 48 px: вес всё ещё по единице, растёт только таймер.
        assertEquals((96f * MIN_READOUT_SCALE + 48f) / 96f, g.scale, 0.001f)
    }
}
