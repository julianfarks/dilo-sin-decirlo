package app.dilosindecirlo

import app.dilosindecirlo.stats.Achievements
import app.dilosindecirlo.stats.StatsCodec
import app.dilosindecirlo.stats.StatsData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StatsTest {
    @Test fun emptyStatsRoundTrip() {
        val back = StatsCodec.fromJson(StatsCodec.toJson(StatsData()))
        assertEquals(StatsData(), back)
    }

    @Test fun populatedStatsRoundTrip() {
        val data = StatsData(
            gamesPlayed = 4, totalHits = 30, totalPasses = 5, totalViolations = 2,
            bestTurnHits = 11, bestSurvival = 9, bestTimeAttack = 7,
            teamWins = mapOf("Azul" to 2), teamGames = mapOf("Azul" to 4), teamBestScore = mapOf("Azul" to 12),
            wordHits = mapOf("b:pizza" to 3), wordMisses = mapOf("b:sol" to 1), categoryPlays = mapOf("comida" to 8),
            unlocked = mapOf("first_game" to 123L), challengeDays = setOf(20000L), modesPlayed = setOf("CLASSIC"),
        )
        assertEquals(data, StatsCodec.fromJson(StatsCodec.toJson(data)))
    }

    @Test fun corruptJsonFallsBackOrThrowsCleanly() {
        val result = runCatching { StatsCodec.fromJson("{ esto no es json") }
        assertTrue(result.isFailure || result.getOrNull() == StatsData())
    }

    @Test fun achievementIdsAreUnique() {
        assertEquals(Achievements.all.size, Achievements.all.map { it.id }.toSet().size)
    }
}
