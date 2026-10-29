package app.dilosindecirlo.stats

import app.dilosindecirlo.engine.CardResult
import app.dilosindecirlo.engine.GameMode
import app.dilosindecirlo.engine.PlayedCard
import app.dilosindecirlo.engine.TurnSummary
import org.json.JSONArray
import org.json.JSONObject

/** Resultado final de una partida, tal como lo necesitan las estadísticas. */
data class GameResult(
    val mode: GameMode,
    val teamNames: List<String>,
    val scores: List<Int>,
    val winners: List<Int>,
    val played: List<PlayedCard>,
    val summaries: List<TurnSummary>,
    val deckExhausted: Boolean,
) {
    val totalHits: Int get() = played.count { it.result == CardResult.HIT }
    val totalPasses: Int get() = played.count { it.result == CardResult.PASS }
    val totalViolations: Int get() = played.count { it.result == CardResult.VIOLATION }
}

data class TeamScore(val name: String, val score: Int)

data class GameRecord(
    val timeMillis: Long,
    val mode: String,
    val teams: List<TeamScore>,
    val winners: List<String>,
    val hits: Int,
    val violations: Int,
)

data class StatsData(
    val gamesPlayed: Int = 0,
    val totalHits: Int = 0,
    val totalPasses: Int = 0,
    val totalViolations: Int = 0,
    val bestTurnHits: Int = 0,
    val bestSurvival: Int = 0,
    val bestTimeAttack: Int = 0,
    val teamWins: Map<String, Int> = emptyMap(),
    val teamGames: Map<String, Int> = emptyMap(),
    val teamBestScore: Map<String, Int> = emptyMap(),
    val wordHits: Map<String, Int> = emptyMap(),
    val wordMisses: Map<String, Int> = emptyMap(),
    val categoryPlays: Map<String, Int> = emptyMap(),
    val history: List<GameRecord> = emptyList(),
    val unlocked: Map<String, Long> = emptyMap(),
    val challengeDays: Set<Long> = emptySet(),
    val modesPlayed: Set<String> = emptySet(),
) {
    val gamesWon: Int get() = teamWins.values.sum()
    fun favoriteCategories(limit: Int = 3): List<Pair<String, Int>> =
        categoryPlays.entries.sortedByDescending { it.value }.take(limit).map { it.key to it.value }
}

data class Achievement(
    val id: String,
    val emoji: String,
    val title: String,
    val description: String,
    val isUnlocked: (StatsData) -> Boolean,
)

object Achievements {
    const val CREATOR_ID = "creator"

    val all: List<Achievement> = listOf(
        Achievement("first_game", "🎬", "¡A jugar!", "Termina tu primera partida") { it.gamesPlayed >= 1 },
        Achievement("ten_games", "🎖️", "Veterano", "Juega 10 partidas") { it.gamesPlayed >= 10 },
        Achievement("fifty_games", "🏅", "Leyenda de la fiesta", "Juega 50 partidas") { it.gamesPlayed >= 50 },
        Achievement("hits_100", "💯", "Cien aciertos", "Acumula 100 palabras acertadas") { it.totalHits >= 100 },
        Achievement("hits_500", "🚀", "Medio millar", "Acumula 500 palabras acertadas") { it.totalHits >= 500 },
        Achievement("turn_10", "⚡", "Turno estelar", "Consigue 10 aciertos en un solo turno") { it.bestTurnHits >= 10 },
        Achievement("turn_15", "🌪️", "Imparable", "Consigue 15 aciertos en un solo turno") { it.bestTurnHits >= 15 },
        Achievement("survivor_15", "🛡️", "Superviviente", "Consigue 15 aciertos en una carrera de supervivencia") { it.bestSurvival >= 15 },
        Achievement("time_attack_12", "⏱️", "Contra el reloj", "Consigue 12 aciertos en un turno de contrarreloj") { it.bestTimeAttack >= 12 },
        Achievement("party", "🎉", "Alma de la fiesta", "Juega una partida en modo fiesta") { GameMode.PARTY.name in it.modesPlayed },
        Achievement("explorer", "🧭", "Explorador", "Juega cartas de 15 categorías distintas") { it.categoryPlays.size >= 15 },
        Achievement("challenger", "🎯", "Retador", "Completa 3 desafíos diarios") { it.challengeDays.size >= 3 },
        Achievement(CREATOR_ID, "✏️", "Creador", "Crea tu primera carta personalizada") { false },
    )

    fun byId(id: String): Achievement? = all.firstOrNull { it.id == id }
}

data class Challenge(
    val id: String,
    val emoji: String,
    val title: String,
    val description: String,
    val isMet: (GameResult) -> Boolean,
)

object Challenges {
    val all: List<Challenge> = listOf(
        Challenge("turn8", "⚡", "Turno estelar", "Consigue 8 aciertos en un solo turno") { r -> r.summaries.any { it.hits >= 8 } },
        Challenge("clean", "🧼", "Manos limpias", "Termina una partida sin ninguna infracción") { r ->
            r.summaries.isNotEmpty() && r.totalViolations == 0
        },
        Challenge("hits25", "🔢", "Racha de 25", "Acierta 25 palabras en una misma partida") { r -> r.totalHits >= 25 },
        Challenge("nopass", "🚫", "Sin pasar", "Acierta 10 palabras sin pasar ninguna en la partida") { r ->
            r.totalHits >= 10 && r.totalPasses == 0
        },
        Challenge("hard5", "🔥", "Reto difícil", "Acierta 5 palabras de dificultad alta") { r ->
            r.played.count { it.result == CardResult.HIT && it.card.difficulty >= 3 } >= 5
        },
        Challenge("comeback", "💪", "Sin rendirse", "Gana una partida con 3 o más infracciones en total") { r ->
            r.totalViolations >= 3 && r.winners.size == 1
        },
    )

    fun forDay(epochDay: Long): Challenge = all[Math.floorMod(epochDay, all.size.toLong()).toInt()]
}

/** Aviso de algo que se acaba de conseguir al terminar una partida. */
data class GameOutcome(
    val stats: StatsData,
    val newAchievements: List<Achievement>,
    val recordsBroken: List<String>,
    val challengeCompleted: Challenge?,
)

object StatsLogic {
    private const val MAX_HISTORY = 25

    fun apply(stats: StatsData, result: GameResult, nowMillis: Long, epochDay: Long): GameOutcome {
        val records = mutableListOf<String>()

        val teamGames = stats.teamGames.toMutableMap()
        val teamWins = stats.teamWins.toMutableMap()
        val teamBest = stats.teamBestScore.toMutableMap()
        result.teamNames.forEachIndexed { i, name ->
            teamGames[name] = (teamGames[name] ?: 0) + 1
            if (i in result.winners && result.teamNames.size > 1) teamWins[name] = (teamWins[name] ?: 0) + 1
            val score = result.scores.getOrElse(i) { 0 }
            if (score > (teamBest[name] ?: Int.MIN_VALUE)) {
                if (teamBest.containsKey(name) && score > 0) records += "Récord de $name: $score puntos"
                teamBest[name] = score
            }
        }

        val wordHits = stats.wordHits.toMutableMap()
        val wordMisses = stats.wordMisses.toMutableMap()
        val categories = stats.categoryPlays.toMutableMap()
        for (p in result.played) {
            if (p.result == CardResult.HIT) wordHits[p.card.id] = (wordHits[p.card.id] ?: 0) + 1
            else wordMisses[p.card.id] = (wordMisses[p.card.id] ?: 0) + 1
            categories[p.card.categoryId] = (categories[p.card.categoryId] ?: 0) + 1
        }

        val bestTurn = result.summaries.maxOfOrNull { it.hits } ?: 0
        var bestTurnHits = stats.bestTurnHits
        if (bestTurn > bestTurnHits) {
            if (bestTurnHits > 0) records += "Récord de aciertos en un turno: $bestTurn"
            bestTurnHits = bestTurn
        }
        var bestSurvival = stats.bestSurvival
        if (result.mode == GameMode.SURVIVAL && bestTurn > bestSurvival) {
            if (bestSurvival > 0) records += "Nuevo récord de supervivencia: $bestTurn"
            bestSurvival = bestTurn
        }
        var bestTimeAttack = stats.bestTimeAttack
        if (result.mode == GameMode.TIME_ATTACK && bestTurn > bestTimeAttack) {
            if (bestTimeAttack > 0) records += "Nuevo récord de contrarreloj: $bestTurn"
            bestTimeAttack = bestTurn
        }

        val record = GameRecord(
            timeMillis = nowMillis,
            mode = result.mode.name,
            teams = result.teamNames.mapIndexed { i, n -> TeamScore(n, result.scores.getOrElse(i) { 0 }) },
            winners = result.winners.mapNotNull { result.teamNames.getOrNull(it) },
            hits = result.totalHits,
            violations = result.totalViolations,
        )

        var updated = stats.copy(
            gamesPlayed = stats.gamesPlayed + 1,
            totalHits = stats.totalHits + result.totalHits,
            totalPasses = stats.totalPasses + result.totalPasses,
            totalViolations = stats.totalViolations + result.totalViolations,
            bestTurnHits = bestTurnHits,
            bestSurvival = bestSurvival,
            bestTimeAttack = bestTimeAttack,
            teamWins = teamWins,
            teamGames = teamGames,
            teamBestScore = teamBest,
            wordHits = wordHits,
            wordMisses = wordMisses,
            categoryPlays = categories,
            history = (listOf(record) + stats.history).take(MAX_HISTORY),
            modesPlayed = stats.modesPlayed + result.mode.name,
        )

        val challenge = Challenges.forDay(epochDay)
        var completed: Challenge? = null
        if (epochDay !in updated.challengeDays && challenge.isMet(result)) {
            updated = updated.copy(challengeDays = updated.challengeDays + epochDay)
            completed = challenge
        }

        val unlocked = unlockNew(updated, nowMillis)
        return GameOutcome(unlocked.first, unlocked.second, records, completed)
    }

    /** Desbloquea los logros cuya condición se cumple y que aún no estaban desbloqueados. */
    fun unlockNew(stats: StatsData, nowMillis: Long): Pair<StatsData, List<Achievement>> {
        val fresh = Achievements.all.filter { it.id !in stats.unlocked && it.isUnlocked(stats) }
        if (fresh.isEmpty()) return stats to emptyList()
        return stats.copy(unlocked = stats.unlocked + fresh.map { it.id to nowMillis }) to fresh
    }

    fun unlockManual(stats: StatsData, id: String, nowMillis: Long): Pair<StatsData, Achievement?> {
        val achievement = Achievements.byId(id) ?: return stats to null
        if (id in stats.unlocked) return stats to null
        return stats.copy(unlocked = stats.unlocked + (id to nowMillis)) to achievement
    }

    fun epochDay(nowMillis: Long, offsetMillis: Int): Long = Math.floorDiv(nowMillis + offsetMillis, 86_400_000L)
}

/** Serialización de las estadísticas a JSON (se guardan en el almacenamiento local del teléfono). */
object StatsCodec {
    fun toJson(s: StatsData): String {
        val o = JSONObject()
        o.put("version", 1)
        o.put("gamesPlayed", s.gamesPlayed)
        o.put("totalHits", s.totalHits)
        o.put("totalPasses", s.totalPasses)
        o.put("totalViolations", s.totalViolations)
        o.put("bestTurnHits", s.bestTurnHits)
        o.put("bestSurvival", s.bestSurvival)
        o.put("bestTimeAttack", s.bestTimeAttack)
        o.put("teamWins", intMap(s.teamWins))
        o.put("teamGames", intMap(s.teamGames))
        o.put("teamBestScore", intMap(s.teamBestScore))
        o.put("wordHits", intMap(s.wordHits))
        o.put("wordMisses", intMap(s.wordMisses))
        o.put("categoryPlays", intMap(s.categoryPlays))
        o.put("unlocked", JSONObject().also { j -> s.unlocked.forEach { (k, v) -> j.put(k, v) } })
        o.put("challengeDays", JSONArray(s.challengeDays.toList()))
        o.put("modesPlayed", JSONArray(s.modesPlayed.toList()))
        val hist = JSONArray()
        for (h in s.history) {
            hist.put(
                JSONObject()
                    .put("t", h.timeMillis)
                    .put("mode", h.mode)
                    .put("hits", h.hits)
                    .put("violations", h.violations)
                    .put("winners", JSONArray(h.winners))
                    .put("teams", JSONArray().also { a ->
                        h.teams.forEach { t -> a.put(JSONObject().put("n", t.name).put("s", t.score)) }
                    }),
            )
        }
        o.put("history", hist)
        return o.toString()
    }

    fun fromJson(json: String): StatsData {
        val o = JSONObject(json)
        val hist = o.optJSONArray("history") ?: JSONArray()
        return StatsData(
            gamesPlayed = o.optInt("gamesPlayed"),
            totalHits = o.optInt("totalHits"),
            totalPasses = o.optInt("totalPasses"),
            totalViolations = o.optInt("totalViolations"),
            bestTurnHits = o.optInt("bestTurnHits"),
            bestSurvival = o.optInt("bestSurvival"),
            bestTimeAttack = o.optInt("bestTimeAttack"),
            teamWins = readIntMap(o.optJSONObject("teamWins")),
            teamGames = readIntMap(o.optJSONObject("teamGames")),
            teamBestScore = readIntMap(o.optJSONObject("teamBestScore")),
            wordHits = readIntMap(o.optJSONObject("wordHits")),
            wordMisses = readIntMap(o.optJSONObject("wordMisses")),
            categoryPlays = readIntMap(o.optJSONObject("categoryPlays")),
            unlocked = o.optJSONObject("unlocked")?.let { j -> j.keys().asSequence().associateWith { j.getLong(it) } } ?: emptyMap(),
            challengeDays = (o.optJSONArray("challengeDays") ?: JSONArray()).let { a -> (0 until a.length()).map { a.getLong(it) }.toSet() },
            modesPlayed = (o.optJSONArray("modesPlayed") ?: JSONArray()).let { a -> (0 until a.length()).map { a.getString(it) }.toSet() },
            history = (0 until hist.length()).map { i ->
                val h = hist.getJSONObject(i)
                val teams = h.getJSONArray("teams")
                val winners = h.getJSONArray("winners")
                GameRecord(
                    timeMillis = h.getLong("t"),
                    mode = h.getString("mode"),
                    hits = h.optInt("hits"),
                    violations = h.optInt("violations"),
                    winners = (0 until winners.length()).map { winners.getString(it) },
                    teams = (0 until teams.length()).map { j ->
                        val t = teams.getJSONObject(j)
                        TeamScore(t.getString("n"), t.getInt("s"))
                    },
                )
            },
        )
    }

    private fun intMap(map: Map<String, Int>): JSONObject = JSONObject().also { j -> map.forEach { (k, v) -> j.put(k, v) } }

    private fun readIntMap(o: JSONObject?): Map<String, Int> =
        if (o == null) emptyMap() else o.keys().asSequence().associateWith { o.getInt(it) }
}
