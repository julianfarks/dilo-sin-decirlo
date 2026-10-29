package app.dilosindecirlo.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

enum class VoiceState { OFF, LISTENING, UNAVAILABLE }

/**
 * Envoltorio del reconocimiento de voz del sistema (opcional).
 *
 * Limitaciones (también se explican a la persona en Ajustes):
 *  - Depende del servicio de reconocimiento instalado en el teléfono; puede requerir Internet según el dispositivo
 *    (se pide modo sin conexión cuando está disponible, pero no está garantizado).
 *  - El sistema puede emitir un pitido al reiniciar la escucha y puede perder palabras o confundirlas.
 *  - Por eso solo se usa como ayuda: por defecto pide confirmación antes de aplicar una penalización.
 *
 * Debe usarse siempre desde el hilo principal.
 */
class VoiceAssist(
    private val context: Context,
    private val onText: (text: String, isFinal: Boolean) -> Unit,
    private val onState: (VoiceState) -> Unit,
) {
    private val handler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var active = false
    private var consecutiveErrors = 0

    companion object {
        private const val MAX_CONSECUTIVE_ERRORS = 8

        fun isAvailable(context: Context): Boolean = SpeechRecognizer.isRecognitionAvailable(context)
    }

    fun start() {
        if (active) return
        if (!isAvailable(context)) {
            onState(VoiceState.UNAVAILABLE)
            return
        }
        active = true
        consecutiveErrors = 0
        try {
            recognizer = SpeechRecognizer.createSpeechRecognizer(context).also { it.setRecognitionListener(listener) }
            onState(VoiceState.LISTENING)
            listen()
        } catch (e: Exception) {
            active = false
            onState(VoiceState.UNAVAILABLE)
        }
    }

    fun stop() {
        active = false
        handler.removeCallbacksAndMessages(null)
        try {
            recognizer?.stopListening()
            recognizer?.cancel()
            recognizer?.destroy()
        } catch (e: Exception) {
            // nada que hacer
        }
        recognizer = null
        onState(VoiceState.OFF)
    }

    private fun listen() {
        if (!active) return
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-ES")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        }
        try {
            recognizer?.startListening(intent)
        } catch (e: Exception) {
            scheduleRestart(600)
        }
    }

    private fun scheduleRestart(delayMs: Long) {
        if (!active) return
        handler.postDelayed({ listen() }, delayMs)
    }

    private fun firstResult(bundle: Bundle?): String? =
        bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onEvent(eventType: Int, params: Bundle?) {}

        override fun onPartialResults(partialResults: Bundle?) {
            firstResult(partialResults)?.let { consecutiveErrors = 0; onText(it, false) }
        }

        override fun onResults(results: Bundle?) {
            consecutiveErrors = 0
            firstResult(results)?.let { onText(it, true) }
            scheduleRestart(150)
        }

        override fun onError(error: Int) {
            when (error) {
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                    active = false
                    onState(VoiceState.UNAVAILABLE)
                }
                SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                    // Silencio: es normal, simplemente se vuelve a escuchar.
                    scheduleRestart(200)
                }
                else -> {
                    consecutiveErrors++
                    if (consecutiveErrors >= MAX_CONSECUTIVE_ERRORS) {
                        active = false
                        onState(VoiceState.UNAVAILABLE)
                    } else {
                        // Reiniciar el reconocedor ante errores de red/ocupado.
                        try {
                            recognizer?.cancel()
                        } catch (e: Exception) {
                            // ignorar
                        }
                        scheduleRestart(700)
                    }
                }
            }
        }
    }
}
