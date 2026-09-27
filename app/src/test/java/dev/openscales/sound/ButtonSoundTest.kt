package dev.openscales.sound

import dev.openscales.data.AppSettings
import dev.openscales.data.BeepNote
import dev.openscales.data.InMemoryAppSettingsStore
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class ButtonSoundTest {

    private class RecordingBeeper : Beeper {
        val played = mutableListOf<BeepNote>()
        var warm = false
        override fun beep(note: BeepNote) { played += note }
        override fun warm(on: Boolean) { warm = on }
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
    fun `warm-up follows the dashboard and the sound setting`() = runTest {
        // Сигналы шагов тоже держат тракт готовым — здесь проверяем только звук кнопок.
        val store = InMemoryAppSettingsStore(AppSettings(stepSignals = false))
        val beeper = RecordingBeeper()
        val sound = ButtonSound(backgroundScope, store, beeper)
        runCurrent()
        assertFalse(beeper.warm) // главный экран ещё не открыт

        sound.setDashboardVisible(true)
        assertTrue(beeper.warm)

        store.setBeepEnabled(false)
        runCurrent()
        assertFalse(beeper.warm) // звук выключен — держать тракт незачем

        store.setBeepEnabled(true)
        runCurrent()
        assertTrue(beeper.warm)

        sound.setDashboardVisible(false)
        assertFalse(beeper.warm)
    }

    @Test
    fun `warm-up survives screen recreation`() = runTest {
        val beeper = RecordingBeeper()
        val sound = ButtonSound(backgroundScope, InMemoryAppSettingsStore(), beeper)
        runCurrent()
        sound.setDashboardVisible(true)

        // Пересоздание экрана: новый экземпляр стартовал, старый только потом остановился.
        sound.setDashboardVisible(true)
        sound.setDashboardVisible(false)
        assertTrue(beeper.warm)

        sound.setDashboardVisible(false)
        assertFalse(beeper.warm)
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

    @Test
    fun `step signals follow their own setting`() = runTest {
        val store = InMemoryAppSettingsStore(AppSettings(beepEnabled = false, beepNote = BeepNote.E6, stepSignals = false))
        val beeper = RecordingBeeper()
        val sound = ButtonSound(backgroundScope, store, beeper)
        runCurrent()
        sound.onStepSignal(StepSignal.STEP)
        assertTrue(beeper.played.isEmpty()) // сигналы выключены

        store.setStepSignals(true)
        runCurrent()
        sound.onStepSignal(StepSignal.STEP)
        assertEquals(listOf(BeepNote.E6), beeper.played) // звук кнопок выключен, а сигнал звучит

        sound.onStepSignal(StepSignal.TARGET)
        advanceTimeBy(200)
        runCurrent()
        assertEquals(3, beeper.played.size) // двойной
    }

    @Test
    fun `warm-up also serves step signals`() = runTest {
        val store = InMemoryAppSettingsStore(AppSettings(beepEnabled = false, stepSignals = false))
        val beeper = RecordingBeeper()
        val sound = ButtonSound(backgroundScope, store, beeper)
        runCurrent()
        sound.setDashboardVisible(true)
        assertFalse(beeper.warm)
        store.setStepSignals(true)
        runCurrent()
        assertTrue(beeper.warm)
    }
}
