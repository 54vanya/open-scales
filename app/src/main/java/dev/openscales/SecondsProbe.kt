package dev.openscales

/**
 * Проба пропуска секунд (только debug-журнал): следит за последовательностью показанных значений секундомера.
 * Пропуск — значение выросло больше чем на 1 или, при [maxGapMs], между сменами значения идущего времени прошло
 * дольше порога («замерло и догнало»). Уменьшение значения — сброс или новый запуск, не пропуск.
 */
class SecondsProbe(private val maxGapMs: Long? = null) {
    private var last: Int? = null
    private var lastAt = 0L

    /** Время остановлено: следующая смена значения не сравнивается по паузе с тем, что было до остановки. */
    fun pause() {
        last = null
    }

    /** Новое значение; `null`, если всё ровно, иначе описание пропуска. Повтор того же значения игнорируется. */
    fun onValue(value: Int, nowMs: Long): String? {
        val prev = last
        if (prev == value) return null
        val gap = nowMs - lastAt
        last = value
        lastAt = nowMs
        if (prev == null || value < prev) return null
        val jumped = value > prev + 1
        val stalled = maxGapMs != null && gap > maxGapMs
        return if (jumped || stalled) "$prev→$value after ${gap}ms" else null
    }
}
