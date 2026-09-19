package dev.openscales.sound

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import dev.openscales.data.BeepNote

fun interface Beeper {
    fun beep(note: BeepNote)
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

    /** Заранее подготовить трек, чтобы первое нажатие не ждало его создания. */
    fun prepare(note: BeepNote) {
        if (trackNote != note) rebuild(note)
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

    fun release() {
        track?.release()
        track = null
        trackNote = null
    }

    private fun rebuild(note: BeepNote) {
        release()
        val pcm = BeepPcm.generate(note.hz)
        track = runCatching {
            AudioTrack.Builder()
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
                .setBufferSizeInBytes(pcm.size * 2)
                .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                .build()
                .also { it.write(pcm, 0, pcm.size) }
        }.onFailure { Log.w(TAG, "AudioTrack init failed", it) }.getOrNull()
        trackNote = note.takeIf { track != null }
    }

    private companion object {
        const val TAG = "Beeper"
    }
}
