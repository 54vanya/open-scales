package dev.openscales.sound

import dev.openscales.data.AppSettings
import dev.openscales.data.BeepNote
import dev.openscales.data.InMemoryAppSettingsStore
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class ButtonSoundTest {

    private class RecordingBeeper : Beeper {
        val played = mutableListOf<BeepNote>()
        override fun beep(note: BeepNote) { played += note }
    }

    @Test
    fun `every press beeps on the selected note`() = runTest {
        val store = InMemoryAppSettingsStore(AppSettings(beepNote = BeepNote.D6))
        val beeper = RecordingBeeper()
        val sound = ButtonSound(backgroundScope, store, beeper)
        runCurrent()
        repeat(3) { sound.onControlPressed() }
        assertEquals(List(3) { BeepNote.D6 }, beeper.played)

        store.setBeepNote(BeepNote.G6)
        runCurrent()
        sound.onControlPressed()
        assertEquals(BeepNote.G6, beeper.played.last())
    }

    @Test
    fun `disabled sound stays silent`() = runTest {
        val store = InMemoryAppSettingsStore(AppSettings(beepEnabled = false))
        val beeper = RecordingBeeper()
        val sound = ButtonSound(backgroundScope, store, beeper)
        runCurrent()
        sound.onControlPressed()
        sound.preview(BeepNote.C6)
        assertTrue(beeper.played.isEmpty())
    }

    @Test
    fun `pcm is 80 ms, starts and ends at zero and never clips`() {
        val pcm = BeepPcm.generate(BeepNote.A6.hz)
        assertEquals(BeepPcm.SAMPLE_RATE * 80 / 1000, pcm.size)
        assertEquals(0, pcm.first().toInt())
        assertEquals(0, pcm.last().toInt())
        assertTrue(pcm.all { abs(it.toInt()) < Short.MAX_VALUE })
        // Плавный фронт: в первую миллисекунду амплитуда мала.
        assertTrue(pcm.take(48).all { abs(it.toInt()) < Short.MAX_VALUE / 5 })
    }
}
