package app.dilosindecirlo

import app.dilosindecirlo.data.Card
import app.dilosindecirlo.data.CardText
import app.dilosindecirlo.data.CardValidator
import app.dilosindecirlo.engine.ForbiddenMatcher
import app.dilosindecirlo.engine.VoiceTracker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MatcherAndValidatorTest {
    private val pizza = Card("t:pizza", "pizza", listOf("queso", "masa", "italiana", "horno", "porción"), "comida", 1, false)

    @Test fun normalizeRemovesAccentsAndCase() {
        assertEquals("porcion de cafe", CardText.normalize("  PORCIÓN   de Café! "))
    }

    @Test fun matcherIgnoresAccentsAndCase() {
        assertEquals(listOf("porción"), ForbiddenMatcher.matches("una PORCION grande", pizza))
    }

    @Test fun matcherAcceptsSimplePlurals() {
        assertEquals(listOf("queso"), ForbiddenMatcher.matches("con muchos quesos", pizza))
    }

    @Test fun matcherDoesNotMatchInsideOtherWords() {
        assertTrue(ForbiddenMatcher.matches("el masajista y el hornero", pizza).isEmpty())
    }

    @Test fun matcherCountsTheSecretWord() {
        assertEquals(listOf("pizza"), ForbiddenMatcher.matches("es una pizza", pizza))
    }

    @Test fun matcherHandlesMultiWordForbidden() {
        val card = Card("t:x", "mar", listOf("agua salada", "playa", "olas", "pez", "barco"), "naturaleza", 1, false)
        assertEquals(listOf("agua salada"), ForbiddenMatcher.matches("es agua salada", card))
        assertTrue(ForbiddenMatcher.matches("agua dulce y salada", card).isEmpty())
    }

    @Test fun trackerIgnoresTextSpokenBeforeCardChange() {
        val t = VoiceTracker()
        assertEquals("el queso", t.fresh("el queso"))
        t.onCardChanged()
        assertEquals("y", t.fresh("el queso y"))
    }

    private val ok = listOf("queso", "masa", "italiana", "horno", "porción")

    @Test fun validCardPasses() {
        assertTrue(CardValidator.validate("Pizza", ok, emptyList()).isEmpty())
    }

    @Test fun tooFewForbiddenWordsFails() {
        assertFalse(CardValidator.validate("Pizza", ok.take(4), emptyList()).isEmpty())
    }

    @Test fun tooManyForbiddenWordsFails() {
        assertFalse(CardValidator.validate("Pizza", (1..11).map { "palabra$it" }, emptyList()).isEmpty())
    }

    @Test fun duplicateForbiddenFails() {
        assertFalse(CardValidator.validate("Pizza", listOf("queso", "Queso", "masa", "horno", "italiana"), emptyList()).isEmpty())
    }

    @Test fun secretWordAsForbiddenFails() {
        assertFalse(CardValidator.validate("Pizza", listOf("pizza", "masa", "horno", "italiana", "queso"), emptyList()).isEmpty())
    }

    @Test fun duplicateCardFails() {
        assertFalse(CardValidator.validate("pizzas", ok, listOf("Pizza")).isEmpty())
    }

    @Test fun emptyWordFails() {
        assertFalse(CardValidator.validate("  ", ok, emptyList()).isEmpty())
    }
}
