package app.dilosindecirlo.engine

import app.dilosindecirlo.data.Card
import kotlin.random.Random

/**
 * Mazo barajado sin repetición: una carta que sale no vuelve a aparecer en la misma partida.
 * Cuando se agota, [draw] devuelve null (el motor lo gestiona sin errores).
 */
class Deck(cards: List<Card>, random: Random) {
    private val remaining: MutableList<Card> = cards.distinctBy { it.id }.shuffled(random).toMutableList()

    val size: Int get() = remaining.size

    fun isEmpty(): Boolean = remaining.isEmpty()

    /**
     * Saca una carta con dificultad <= [maxDifficulty]. Si no queda ninguna así, saca cualquiera.
     * Al estar barajado, la primera que cumple el filtro es aleatoria.
     */
    fun draw(maxDifficulty: Int = 3): Card? {
        if (remaining.isEmpty()) return null
        val index = remaining.indexOfFirst { it.difficulty <= maxDifficulty }
        return remaining.removeAt(if (index >= 0) index else 0)
    }
}
