package app.dilosindecirlo

import app.dilosindecirlo.data.CardCodec
import app.dilosindecirlo.data.CardText
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogTest {
    private val catalog by lazy {
        val file = listOf("src/main/assets/cards.json", "app/src/main/assets/cards.json").map(::File).first { it.exists() }
        CardCodec.parseCatalog(file.readText(Charsets.UTF_8))
    }

    @Test fun hasAtLeast500Cards() = assertTrue("cartas: ${catalog.cards.size}", catalog.cards.size >= 500)

    @Test fun everyCardHasBetween5And10Forbidden() {
        catalog.cards.forEach { assertTrue(it.word, it.forbidden.size in 5..10) }
    }

    @Test fun noDuplicateWords() {
        val words = catalog.cards.map { CardText.normalize(it.word) }
        assertEquals(words.size, words.toSet().size)
    }

    @Test fun noDuplicateIds() {
        assertEquals(catalog.cards.size, catalog.cards.map { it.id }.toSet().size)
    }

    @Test fun forbiddenWordsAreNotTheSecretWord() {
        catalog.cards.forEach { c ->
            val n = CardText.normalize(c.word)
            c.forbidden.forEach { assertFalse("${c.word} / $it", CardText.normalize(it) == n) }
        }
    }

    @Test fun noRepeatedForbiddenWithinCard() {
        catalog.cards.forEach { c ->
            val n = c.forbidden.map { CardText.normalize(it) }
            assertEquals(c.word, n.size, n.toSet().size)
        }
    }

    @Test fun categoriesAreValidAndPopulated() {
        val ids = catalog.categories.map { it.id }.toSet()
        assertEquals(25, ids.size)
        catalog.cards.forEach { assertTrue("${it.word}: ${it.categoryId}", it.categoryId in ids) }
        ids.forEach { id -> assertTrue(id, catalog.cards.count { it.categoryId == id } >= 20) }
    }

    @Test fun difficultiesAreInRange() {
        catalog.cards.forEach { assertTrue(it.difficulty in 1..3) }
    }

    @Test fun customCardsRoundTrip() {
        val cards = catalog.cards.take(3).map { it.copy(custom = true) }
        val back = CardCodec.parseCustom(CardCodec.exportJson(cards)) { "x" }
        assertEquals(cards.map { it.word }, back.map { it.word })
        assertEquals(cards.map { it.forbidden }, back.map { it.forbidden })
    }
}
