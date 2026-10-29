package app.dilosindecirlo.feedback

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import kotlin.math.PI
import kotlin.math.sin

enum class Sfx { START, HIT, PASS, VIOLATION, WARNING, TURN_END, VICTORY, DEFEAT }

/**
 * Efectos de sonido sintetizados en el propio teléfono (ondas generadas por código):
 * son originales, no usan archivos de audio ni música con derechos de autor.
 */
class SoundManager {
    private val handler = Handler(Looper.getMainLooper())
    private val sampleRate = 22_050
    private val cache = HashMap<Sfx, ShortArray>()

    private data class Note(val freq: Double, val ms: Int, val volume: Double = 0.6, val square: Boolean = false)

    private fun notes(sfx: Sfx): List<Note> = when (sfx) {
        Sfx.START -> listOf(Note(523.25, 90), Note(659.25, 90), Note(783.99, 160))
        Sfx.HIT -> listOf(Note(880.0, 70), Note(1318.5, 130))
        Sfx.PASS -> listOf(Note(392.0, 70, 0.4), Note(330.0, 90, 0.4))
        Sfx.VIOLATION -> listOf(Note(155.0, 170, 0.55, true), Note(110.0, 260, 0.55, true))
        Sfx.WARNING -> listOf(Note(1000.0, 70), Note(0.0, 60), Note(1000.0, 70))
        Sfx.TURN_END -> listOf(Note(784.0, 110), Note(659.0, 110), Note(523.0, 220))
        Sfx.VICTORY -> listOf(
            Note(523.25, 110), Note(659.25, 110), Note(783.99, 110), Note(1046.5, 220),
            Note(783.99, 90), Note(1046.5, 340),
        )
        Sfx.DEFEAT -> listOf(Note(392.0, 160, 0.5), Note(349.0, 160, 0.5), Note(311.0, 160, 0.5), Note(261.6, 380, 0.5))
    }

    private fun render(list: List<Note>): ShortArray {
        val total = list.sumOf { it.ms * sampleRate / 1000 }
        val out = ShortArray(total)
        var pos = 0
        for (n in list) {
            val len = n.ms * sampleRate / 1000
            for (i in 0 until len) {
                val t = i.toDouble() / sampleRate
                val envelope = minOf(1.0, i / (sampleRate * 0.005)) * minOf(1.0, (len - i) / (sampleRate * 0.03))
                val phase = sin(2.0 * PI * n.freq * t)
                val wave = if (n.freq == 0.0) 0.0 else if (n.square) (if (phase >= 0) 0.6 else -0.6) else phase
                out[pos + i] = (wave * envelope * n.volume * Short.MAX_VALUE).toInt().toShort()
            }
            pos += len
        }
        return out
    }

    fun play(sfx: Sfx) {
        try {
            val data = cache.getOrPut(sfx) { render(notes(sfx)) }
            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build(),
                )
                .setBufferSizeInBytes(data.size * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
            track.write(data, 0, data.size)
            track.play()
            val durationMs = data.size * 1000L / sampleRate
            handler.postDelayed({ runCatching { track.release() } }, durationMs + 150)
        } catch (e: Exception) {
            // Sin audio disponible: el juego sigue funcionando.
        }
    }
}

class Haptics(context: Context) {
    private val vibrator: Vibrator? = context.getSystemService(Vibrator::class.java)

    fun tick() = vibrate(longArrayOf(0, 25))
    fun hit() = vibrate(longArrayOf(0, 35))
    fun violation() = vibrate(longArrayOf(0, 90, 60, 90))
    fun turnEnd() = vibrate(longArrayOf(0, 220))
    fun victory() = vibrate(longArrayOf(0, 60, 50, 60, 50, 200))

    private fun vibrate(pattern: LongArray) {
        try {
            val v = vibrator ?: return
            if (!v.hasVibrator()) return
            v.vibrate(VibrationEffect.createWaveform(pattern, -1))
        } catch (e: Exception) {
            // Ignorar: la vibración es opcional.
        }
    }
}
