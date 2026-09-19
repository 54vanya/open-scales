package dev.openscales.sound

import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

/** PCM короткого «пика»: синус с линейными фронтами, чтобы не было щелчков. */
object BeepPcm {
    const val SAMPLE_RATE = 48_000
    const val DURATION_MS = 80
    const val FADE_MS = 5

    /** Амплитуда с запасом до клиппинга; громкость задаёт системный поток. */
    private const val AMPLITUDE = 0.6

    fun generate(hz: Int, durationMs: Int = DURATION_MS, fadeMs: Int = FADE_MS): ShortArray {
        val total = SAMPLE_RATE * durationMs / 1000
        val fade = SAMPLE_RATE * fadeMs / 1000
        return ShortArray(total) { i ->
            val envelope = when {
                i < fade -> i.toDouble() / fade
                i >= total - fade -> (total - 1 - i).toDouble() / fade
                else -> 1.0
            }
            val sample = sin(2 * PI * hz * i / SAMPLE_RATE) * envelope * AMPLITUDE
            (sample * Short.MAX_VALUE).roundToInt().toShort()
        }
    }
}
