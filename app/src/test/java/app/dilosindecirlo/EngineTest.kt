package app.dilosindecirlo

import app.dilosindecirlo.engine.CardResult
import app.dilosindecirlo.engine.EndReason
import app.dilosindecirlo.engine.GameConfig
import app.dilosindecirlo.engine.GameEngine
import app.dilosindecirlo.engine.GameEvent
import app.dilosindecirlo.engine.GameMode
import app.dilosindecirlo.engine.Phase
import app.dilosindecirlo.engine.ScoreRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EngineTest {
    private fun engine(config: GameConfig = GameConfig(seed = 1L), cards: Int = 60) =
        GameEngine(config, fakeCards(cards)).also { it.beginTurn() }

    @Test fun hitAddsOnePoint() {
        val e = engine()
        assertTrue(e.hit(e.cardSerial))
        assertEquals(listOf(1, 0), e.scores)
    }

    @Test fun violationSubtractsOnePoint() {
        val e = engine()
        assertTrue(e.violation(e.cardSerial))
        assertEquals(listOf(-1, 0), e.scores)
    }

    @Test fun passScoresNothingByDefault() {
        val e = engine()
        assertTrue(e.pass(e.cardSerial))
        assertEquals(listOf(0, 0), e.scores)
    }

    @Test fun passPenaltyIsConfigurable() {
        val e = engine(GameConfig(seed = 1L, rules = ScoreRules(passPenalty = 1)))
        e.pass(e.cardSerial)
        assertEquals(-1, e.scores[0])
    }

    @Test fun scoreCanBeKeptNonNegative() {
        val e = engine(GameConfig(seed = 1L, rules = ScoreRules(allowNegative = false)))
        e.violation(e.cardSerial)
        assertEquals(0, e.scores[0])
    }

    @Test fun doubleTapDoesNotScoreTwice() {
        val e = engine()
        val serial = e.cardSerial
        assertTrue(e.hit(serial))
        assertFalse(e.hit(serial))
        assertFalse(e.violation(serial))
        assertEquals(1, e.scores[0])
    }

    @Test fun actionsAreIgnoredWhenPaused() {
        val e = engine()
        e.pause()
        assertEquals(Phase.PAUSED, e.phase)
        assertFalse(e.hit(e.cardSerial))
        e.resume()
        assertEquals(Phase.PLAYING, e.phase)
    }

    @Test fun timerDoesNotRunWhilePaused() {
        val e = engine()
        e.tick(5_000)
        val left = e.timeLeftMs
        e.pause()
        e.tick(20_000)
        assertEquals(left, e.timeLeftMs)
    }

    @Test fun turnEndsWhenTimeRunsOut() {
        val e = engine()
        e.hit(e.cardSerial)
        e.tick(60_000)
        assertEquals(Phase.TURN_SUMMARY, e.phase)
        val summary = e.lastSummary!!
        assertEquals(EndReason.TIME_UP, summary.reason)
        assertEquals(1, summary.hits)
        assertEquals(1, summary.points)
    }

    @Test fun tenSecondWarningIsEmittedOnce() {
        val e = engine()
        e.drainEvents()
        e.tick(49_000)
        e.tick(2_000)
        e.tick(2_000)
        assertEquals(1, e.drainEvents().count { it == GameEvent.TEN_SECONDS })
    }

    @Test fun teamsRotateAndRoundsAdvance() {
        val e = engine(GameConfig(seed = 3L, rounds = 2))
        assertEquals(0, e.snapshot().currentTeam)
        e.endTurnNow(); e.nextTurn(); e.beginTurn()
        assertEquals(1, e.snapshot().currentTeam)
        e.endTurnNow(); e.nextTurn(); e.beginTurn()
        assertEquals(0, e.snapshot().currentTeam)
        assertEquals(2, e.snapshot().round)
    }

    @Test fun gameEndsAfterLastRound() {
        val e = engine(GameConfig(seed = 3L, rounds = 1))
        e.endTurnNow(); e.nextTurn(); e.beginTurn()
        e.endTurnNow(); e.nextTurn()
        assertEquals(Phase.GAME_OVER, e.phase)
    }

    @Test fun pointTargetEndsGameAtEndOfRound() {
        val e = engine(GameConfig(seed = 3L, rounds = 5, targetPoints = 1))
        e.hit(e.cardSerial)
        e.endTurnNow(); e.nextTurn()
        // El equipo 1 todavía juega su turno de la misma ronda.
        assertEquals(Phase.TURN_INTRO, e.phase)
        e.beginTurn(); e.endTurnNow(); e.nextTurn()
        assertEquals(Phase.GAME_OVER, e.phase)
        assertEquals(listOf(0), e.winners())
    }

    @Test fun cardsNeverRepeatWithinAGame() {
        val e = engine(GameConfig(seed = 9L, rounds = 1), cards = 40)
        val seen = mutableSetOf<String>()
        while (e.phase == Phase.PLAYING) {
            assertTrue(seen.add(e.currentCard!!.id))
            e.hit(e.cardSerial)
        }
    }

    @Test fun deckExhaustionEndsTurnWithoutCrashing() {
        val e = engine(GameConfig(seed = 9L, rounds = 1), cards = 5)
        repeat(5) { e.hit(e.cardSerial) }
        assertEquals(Phase.TURN_SUMMARY, e.phase)
        assertEquals(EndReason.DECK_EMPTY, e.lastSummary!!.reason)
        e.nextTurn()
        assertEquals(Phase.GAME_OVER, e.phase)
    }

    @Test fun cardsPerTurnLimitEndsTurn() {
        val e = engine(GameConfig(seed = 2L, cardsPerTurn = 3))
        repeat(3) { e.pass(e.cardSerial) }
        assertEquals(EndReason.CARD_LIMIT, e.lastSummary!!.reason)
    }

    @Test fun passCanBeDisabled() {
        val e = engine(GameConfig(seed = 2L, allowPass = false))
        assertFalse(e.pass(e.cardSerial))
        assertEquals(0, e.snapshot().turnPasses)
    }

    @Test fun survivalLosesLivesAndEnds() {
        val e = engine(GameConfig(mode = GameMode.SURVIVAL, teamNames = listOf("J1"), playersPerTeam = 1, lives = 3, rounds = 1, seed = 4L))
        repeat(3) { e.violation(e.cardSerial) }
        assertEquals(EndReason.NO_LIVES, e.lastSummary!!.reason)
    }

    @Test fun survivalHitsAddBonusTime() {
        val e = engine(GameConfig(mode = GameMode.SURVIVAL, teamNames = listOf("J1"), playersPerTeam = 1, turnSeconds = 30, bonusSeconds = 2, rounds = 1, seed = 4L))
        val before = e.timeLeftMs
        e.hit(e.cardSerial)
        assertEquals(before + 2_000, e.timeLeftMs)
    }

    @Test fun sameSeedGivesSameSequence() {
        fun sequence(): List<String> {
            val e = engine(GameConfig(seed = 77L, rounds = 1))
            val out = mutableListOf<String>()
            repeat(10) { out += e.currentCard!!.id; e.hit(e.cardSerial) }
            return out
        }
        assertEquals(sequence(), sequence())
    }

    @Test fun differentSeedsGiveDifferentOrder() {
        fun first(seed: Long) = engine(GameConfig(seed = seed)).currentCard!!.id
        val firsts = (1L..8L).map { first(it) }.toSet()
        assertTrue(firsts.size > 1)
    }

    @Test fun partyModeAnnouncesRuleBeforeTurn() {
        val e = GameEngine(GameConfig(mode = GameMode.PARTY, partyRules = true, seed = 5L), fakeCards())
        assertNotNull(e.activeRule)
    }

    @Test fun resultsAreRecordedPerCard() {
        val e = engine()
        e.hit(e.cardSerial); e.pass(e.cardSerial); e.violation(e.cardSerial)
        e.endTurnNow()
        assertEquals(listOf(CardResult.HIT, CardResult.PASS, CardResult.VIOLATION), e.lastSummary!!.cards.map { it.result })
    }
}
