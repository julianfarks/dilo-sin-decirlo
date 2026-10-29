package app.dilosindecirlo.engine

import app.dilosindecirlo.data.Card
import app.dilosindecirlo.data.CardText

/**
 * Detección (aproximada) de palabras prohibidas en una transcripción de voz.
 *
 * Criterios, pensados para evitar falsas penalizaciones:
 *  - Se ignoran mayúsculas y acentos.
 *  - Una palabra coincide si es igual a la prohibida o su plural/singular simple ("s"/"es").
 *  - NO se aceptan coincidencias parciales dentro de otra palabra ("carne" no coincide con "carnero").
 *  - Las prohibidas de varias palabras ("Torre Eiffel") deben aparecer completas y seguidas.
 *  - Decir la propia palabra secreta también cuenta como infracción.
 *
 * Es una ayuda, no un árbitro: el reconocimiento de voz se equivoca, y por eso la app pide confirmación.
 */
object ForbiddenMatcher {

    private const val MIN_FORM_LENGTH = 3

    private fun forms(word: String): Set<String> {
        val out = mutableSetOf(word)
        if (word.endsWith("es") && word.length > 4) out += word.dropLast(2)
        if (word.endsWith("s") && word.length > 3) out += word.dropLast(1)
        return out
    }

    private fun sameWord(a: String, b: String): Boolean =
        forms(a).intersect(forms(b)).any { it.length >= MIN_FORM_LENGTH }

    /** Devuelve las palabras prohibidas (tal como se muestran en la carta) que aparecen en [transcript]. */
    fun matches(transcript: String, card: Card): List<String> {
        val spoken = CardText.normalize(transcript).split(' ').filter { it.isNotEmpty() }
        if (spoken.isEmpty()) return emptyList()
        val found = mutableListOf<String>()
        for (target in card.forbidden + card.word) {
            val parts = CardText.normalize(target).split(' ').filter { it.isNotEmpty() }
            if (parts.isEmpty()) continue
            if (containsSequence(spoken, parts)) found += target
        }
        return found
    }

    private fun containsSequence(spoken: List<String>, parts: List<String>): Boolean {
        if (parts.size > spoken.size) return false
        for (start in 0..spoken.size - parts.size) {
            var ok = true
            for (i in parts.indices) {
                if (!sameWord(spoken[start + i], parts[i])) {
                    ok = false
                    break
                }
            }
            if (ok) return true
        }
        return false
    }
}

/**
 * Seguimiento de la transcripción acumulada del reconocedor: las transcripciones parciales crecen
 * mientras se habla, así que al cambiar de carta se descarta lo ya dicho para no penalizar la carta nueva.
 */
class VoiceTracker {
    private var baseline = ""
    private var last = ""

    fun onCardChanged() {
        baseline = last
    }

    fun onUtteranceEnd() {
        baseline = ""
        last = ""
    }

    /** Devuelve solo el texto nuevo desde que apareció la carta actual. */
    fun fresh(transcript: String): String {
        val full = CardText.normalize(transcript)
        last = full
        if (baseline.isNotEmpty() && !full.startsWith(baseline)) baseline = ""
        return if (baseline.isEmpty()) full else full.removePrefix(baseline).trim()
    }
}
