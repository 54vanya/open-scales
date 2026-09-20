package dev.openscales.sound

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import dev.openscales.data.BeepNote

fun interface Beeper {
    fun beep(note: BeepNote)

    /** Держать аудиовыход готовым к воспроизведению (или отпустить его). */
    fun warm(on: Boolean) {}
}

/**
 * «Пик» через [AudioTrack] в режиме `MODE_STATIC`: буфер загружен заранее, поэтому звук стартует
 * почти мгновенно. Повторное нажатие перезапускает сигнал, а не накладывает его.
 * `USAGE_ASSISTANCE_SONIFICATION`: беззвучный режим и громкость системных звуков соблюдаются
 * системой; аудиофокус не запрашивается, музыка не прерывается.
 */
class AudioTrackBeeper : Beeper {
    private var track: AudioTrack? = null
    private var trackNote: BeepNote? = null
    private var silence: AudioTrack? = null

    /** Заранее подготовить трек, чтобы первое нажатие не ждало его создания. */
    fun prepare(note: BeepNote) {
        if (trackNote != note) rebuild(note)
    }

    /**
     * Аудиовыход уходит в standby через три секунды тишины, а между нажатиями кнопок пауза всегда больше,
     * поэтому иначе каждый «пик» платит за холодный старт тракта. Держим его беззвучным зацикленным треком.
     */
    override fun warm(on: Boolean) {
        if (!on) return stopWarmUp()
        if (silence != null) return
        val frames = BeepPcm.SAMPLE_RATE * WARM_UP_MS / 1000
        silence = runCatching {
            newTrack(frames).also {
                it.write(ShortArray(frames), 0, frames)
                it.setVolume(0f)
                it.setLoopPoints(0, frames, -1)
                it.play()
            }
        }.onFailure { Log.w(TAG, "warm-up failed", it) }.getOrNull()
    }

    private fun stopWarmUp() {
        silence?.let {
            runCatching { it.stop() }
            it.release()
        }
        silence = null
    }

    override fun beep(note: BeepNote) {
        prepare(note)
        val t = track ?: return
        runCatching {
            if (t.playState == AudioTrack.PLAYSTATE_PLAYING) t.stop()
            t.reloadStaticData()
            t.play()
        }.onFailure { Log.w(TAG, "beep failed", it) }
    }

    /** Оба трека освобождаются здесь: и сигнал, и удержание тракта. */
    fun release() {
        stopWarmUp()
        track?.release()
        track = null
        trackNote = null
    }

    private fun rebuild(note: BeepNote) {
        val warm = silence != null
        release()
        val pcm = BeepPcm.generate(note.hz)
        track = runCatching {
            newTrack(pcm.size).also { it.write(pcm, 0, pcm.size) }
        }.onFailure { Log.w(TAG, "AudioTrack init failed", it) }.getOrNull()
        trackNote = note.takeIf { track != null }
        if (warm) warm(true)
    }

    private fun newTrack(frames: Int): AudioTrack = AudioTrack.Builder()
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .setAudioFormat(
            AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(BeepPcm.SAMPLE_RATE)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build(),
        )
        .setTransferMode(AudioTrack.MODE_STATIC)
        .setBufferSizeInBytes(frames * 2)
        .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
        .build()

    private companion object {
        const val TAG = "Beeper"

        /** Длина буфера тишины: кратна 10 мс — периоду вывода у типичного HAL. */
        const val WARM_UP_MS = 100
    }
}
