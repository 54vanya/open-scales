package dev.openscales.ui.dashboard

/**
 * Размеры шаблонов показаний в px при базовых стилях (без масштаба). Значения на экране сюда не попадают —
 * только шаблоны, поэтому ось не зависит ни от показываемых чисел, ни от текущей единицы.
 */
internal class ReadoutMeasures(
    /** Шаблоны чисел крупным стилем: `000.0`, `00.00`, `00:00` — 4 цифры и разделитель. */
    val numberTemplateWidths: List<Float>,
    /** Одна цифра крупным стилем: выступ пятой цифры веса или минуса влево за ось. */
    val digitWidth: Float,
    /** Высота строки крупного стиля. */
    val bigLineHeight: Float,
    /** Базовая линия крупного стиля от верха строки. */
    val bigBaseline: Float,
    /** Высота строки единиц: единица не масштабируется вместе с цифрами и в мелком масштабе выше них. */
    val unitLineHeight: Float,
    /** Базовая линия единиц от верха строки. */
    val unitBaseline: Float,
    /** Самый широкий шаблон числа потока его стилем. */
    val flowNumberWidth: Float,
    /** Высота строки потока: число и единица, выровненные по базовой линии. */
    val flowLineHeight: Float,
    /** Все единицы веса и потока стилем единиц (`g`, `oz`, `g/s`, `oz/s`). */
    val unitWidths: List<Float>,
    /** Отступ единицы от оси. */
    val unitGap: Float,
    /** Высота обеих подписей вместе. */
    val labelsHeight: Float,
    /** Отступ между таймером и весом. */
    val rowGap: Float,
)

/**
 * Геометрия группы показаний, px. По центру карточки стоит только числовая колонка [numberWidth];
 * ось — её правый край. Единицы справа и выступ пятой цифры слева живут в полях по бокам.
 */
internal class ReadoutGeometry(
    /** Масштаб крупного стиля (таймер и вес). */
    val scale: Float,
    /** Масштаб стиля потока. */
    val flowScale: Float,
    /** Масштаб единиц — меньше 1, только если колонка единиц не влезает даже при минимальном [scale]. */
    val unitScale: Float,
    /** Ширина числовой колонки: слева от оси. */
    val numberWidth: Float,
    /** Ширина колонки единиц без отступа от оси. */
    val unitWidth: Float,
    /** Резерв слева от числовой колонки под пятую цифру или минус. */
    val overhang: Float,
) {
    /** Поле с каждой стороны числовой колонки: вмещает и колонку единиц с отступом, и выступ. */
    fun sideWidth(unitGap: Float) = maxOf(unitGap + unitWidth, overhang)
}

internal const val MIN_READOUT_SCALE = 0.3f

/**
 * Крупный стиль уменьшается ровно настолько, чтобы по ширине влезла центрированная числовая колонка
 * с одинаковыми полями по бокам — каждое не уже колонки единиц с отступом и не уже выступа пятой цифры,
 * а по высоте — две крупные строки, подписи, поток и отступ между строками.
 */
internal fun readoutGeometry(m: ReadoutMeasures, maxWidth: Float, maxHeight: Float): ReadoutGeometry {
    val number = m.numberTemplateWidths.max()
    val unit = m.unitWidths.max()
    val byWidth = minOf(
        (maxWidth - 2 * (m.unitGap + unit)) / number,
        maxWidth / (number + 2 * m.digitWidth),
    )
    val byHeight = scaleForHeight(m, maxHeight - m.labelsHeight - m.flowLineHeight - m.rowGap)
    val scale = minOf(1f, byWidth, byHeight).coerceAtLeast(MIN_READOUT_SCALE)
    val numberWidth = number * scale
    val overhang = m.digitWidth * scale
    val unitScale = (((maxWidth - numberWidth) / 2 - m.unitGap) / unit).coerceIn(MIN_READOUT_SCALE, 1f)
    // Число потока мельче крупного и обычно влезает в числовую колонку; ужимаем, только если нет.
    val flowScale = minOf(1f, numberWidth / m.flowNumberWidth)
    return ReadoutGeometry(scale, flowScale, unitScale, numberWidth, unit * unitScale, overhang)
}

/**
 * Наименьшая высота группы показаний, px: две крупные строки при [MIN_READOUT_SCALE], подписи, поток и отступ.
 * Ниже неё [readoutGeometry] упирается в нижний предел масштаба, и содержимое вылезло бы за карточку.
 */
internal fun readoutMinHeight(m: ReadoutMeasures): Float =
    bigRowsHeight(m, MIN_READOUT_SCALE) + m.labelsHeight + m.flowLineHeight + m.rowGap

/**
 * Высота строк таймера и веса при масштабе [scale]. Строка веса выровнена по базовой линии с единицей, а единица
 * не масштабируется, поэтому в мелком масштабе строка веса выше строки таймера.
 */
private fun bigRowsHeight(m: ReadoutMeasures, scale: Float): Float {
    val timer = m.bigLineHeight * scale
    val weight = maxOf(m.bigBaseline * scale, m.unitBaseline) +
        maxOf((m.bigLineHeight - m.bigBaseline) * scale, m.unitLineHeight - m.unitBaseline)
    return timer + weight
}

/** Наибольший масштаб не больше 1, при котором строки таймера и веса помещаются в [height]. */
private fun scaleForHeight(m: ReadoutMeasures, height: Float): Float {
    if (bigRowsHeight(m, 1f) <= height) return 1f
    // Высота растёт с масштабом монотонно, но кусочно (из-за единицы) — ищем делением пополам.
    var low = 0f
    var high = 1f
    repeat(24) {
        val mid = (low + high) / 2
        if (bigRowsHeight(m, mid) <= height) low = mid else high = mid
    }
    return low
}
