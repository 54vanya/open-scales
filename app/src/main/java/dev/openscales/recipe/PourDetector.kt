package dev.openscales.recipe

/**
 * Начало пролива: первый кадр веса не меньше опорного плюс порог. Без окна и сглаживания — показания не буферизуем.
 * Сравнение с допуском: вес приходит `Float`, и 1.3 − 0.3 в нём чуть меньше 1.
 */
class PourDetector(private val baselineG: Double, private val thresholdG: Double = POUR_THRESHOLD_G) {
    fun isPour(weightG: Double): Boolean = weightG >= baselineG + thresholdG - EPSILON_G

    companion object {
        const val POUR_THRESHOLD_G = 1.0
        private const val EPSILON_G = 1e-3
    }
}
