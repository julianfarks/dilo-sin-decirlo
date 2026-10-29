package app.dilosindecirlo.engine

import app.dilosindecirlo.data.Card

enum class GameMode(val title: String, val tagline: String, val emoji: String) {
    CLASSIC("Por equipos", "Los equipos alternan turnos", "👥"),
    FFA("Todos contra todos", "Cada jugador por su cuenta", "🥊"),
    TIME_ATTACK("Contrarreloj", "Poco tiempo, máximos aciertos", "⏱️"),
    SURVIVAL("Supervivencia", "Aguanta hasta perder tus vidas", "❤️"),
    CATEGORIES("Por categorías", "Elige de qué quieres hablar", "🗂️"),
    RANDOM("Aleatorio", "Todas las categorías mezcladas", "🎲"),
    HARD("Difícil", "Conceptos abstractos y palabras raras", "🔥"),
    PARTY("Fiesta", "Reglas locas en cada turno", "🎉"),
}

enum class Difficulty(val label: String) {
    EASY("Fácil"),
    NORMAL("Normal"),
    HARD("Difícil"),
}

/** Reglas de puntuación (todas configurables antes de jugar). */
data class ScoreRules(
    val hitPoints: Int = 1,
    val violationPenalty: Int = 1,
    val passPenalty: Int = 0,
    /** Si es false, la puntuación de un equipo nunca baja de cero. */
    val allowNegative: Boolean = true,
)

/** Regla especial del modo fiesta; se anuncia antes de cada turno. */
data class PartyRule(
    val id: String,
    val title: String,
    val description: String,
    val timeFactor: Double = 1.0,
    val pointsMultiplier: Int = 1,
    val allowPass: Boolean? = null,
    val extraPenalty: Int = 0,
    /** true si la regla se juega "de palabra de honor" y la detección por voz no aplica. */
    val honorOnly: Boolean = false,
)

object PartyRules {
    val all: List<PartyRule> = listOf(
        PartyRule("relampago", "Ronda relámpago", "El turno dura la mitad. ¡A toda velocidad!", timeFactor = 0.5),
        PartyRule("doble", "Puntos dobles", "Cada acierto vale el doble.", pointsMultiplier = 2),
        PartyRule("sin_pasar", "Sin pasar", "En este turno no se puede pasar de carta.", allowPass = false),
        PartyRule("castigo", "Castigo extra", "Cada infracción resta un punto más.", extraPenalty = 1),
        PartyRule("mimica", "Solo mímica", "Describe solo con gestos, ¡sin hablar!", honorOnly = true),
        PartyRule("susurro", "Susurros", "Describe susurrando. Si gritas, el equipo rival te puede marcar una infracción.", honorOnly = true),
        PartyRule("acento", "Acento loco", "Describe con un acento inventado. ¡Sin reírse!", honorOnly = true),
        PartyRule("tiempo_extra", "Tiempo extra", "Este turno tienes un 50 % más de tiempo.", timeFactor = 1.5),
    )
}

data class GameConfig(
    val mode: GameMode = GameMode.CLASSIC,
    val teamNames: List<String> = listOf("Equipo Azul", "Equipo Rojo"),
    val playersPerTeam: Int = 2,
    val turnSeconds: Int = 60,
    /** 0 = sin objetivo de puntos (solo cuentan las rondas). */
    val targetPoints: Int = 0,
    val rounds: Int = 3,
    /** 0 = sin límite de cartas por turno. */
    val cardsPerTurn: Int = 0,
    /** Vacío = todas las categorías visibles. */
    val categoryIds: Set<String> = emptySet(),
    val difficulty: Difficulty = Difficulty.NORMAL,
    val rules: ScoreRules = ScoreRules(),
    val allowPass: Boolean = true,
    val lives: Int = 3,
    val bonusSeconds: Int = 2,
    val partyRules: Boolean = false,
    val seed: Long = 0L,
) {
    val teamCount: Int get() = teamNames.size
    val isSurvival: Boolean get() = mode == GameMode.SURVIVAL
    /** Contrarreloj y supervivencia son una sola vuelta por participante. */
    val singleRound: Boolean get() = mode == GameMode.TIME_ATTACK || mode == GameMode.SURVIVAL
}

/** Configuraciones iniciales para cada modo y partidas rápidas. */
object Presets {
    fun forMode(mode: GameMode, seed: Long = System.nanoTime()): GameConfig = when (mode) {
        GameMode.CLASSIC -> GameConfig(mode = mode, targetPoints = 0, rounds = 3, seed = seed)
        GameMode.FFA -> GameConfig(
            mode = mode,
            teamNames = listOf("Jugador 1", "Jugador 2", "Jugador 3"),
            playersPerTeam = 1,
            rounds = 3,
            seed = seed,
        )
        GameMode.TIME_ATTACK -> GameConfig(mode = mode, turnSeconds = 45, rounds = 1, seed = seed)
        GameMode.SURVIVAL -> GameConfig(
            mode = mode,
            teamNames = listOf("Jugador 1"),
            playersPerTeam = 1,
            turnSeconds = 30,
            rounds = 1,
            lives = 3,
            bonusSeconds = 2,
            allowPass = true,
            seed = seed,
        )
        GameMode.CATEGORIES -> GameConfig(mode = mode, rounds = 3, seed = seed)
        GameMode.RANDOM -> GameConfig(mode = mode, rounds = 3, seed = seed)
        GameMode.HARD -> GameConfig(mode = mode, rounds = 3, difficulty = Difficulty.HARD, seed = seed)
        GameMode.PARTY -> GameConfig(mode = mode, rounds = 4, turnSeconds = 45, partyRules = true, seed = seed)
    }

    fun quick(seed: Long = System.nanoTime()): GameConfig =
        GameConfig(mode = GameMode.CLASSIC, targetPoints = 15, rounds = 2, turnSeconds = 60, seed = seed)
}

data class PoolResult(val cards: List<Card>, val relaxedDifficulty: Boolean)

object PoolBuilder {
    const val MIN_POOL = 12

    /**
     * Construye el conjunto de cartas elegibles para una partida.
     * Si el filtro de dificultad deja muy pocas cartas, se relaja (y se avisa con [PoolResult.relaxedDifficulty]).
     */
    fun build(all: List<Card>, config: GameConfig, hiddenCategories: Set<String>): PoolResult {
        var cards = all.filter { it.categoryId !in hiddenCategories }
        if (config.categoryIds.isNotEmpty()) cards = cards.filter { it.categoryId in config.categoryIds }
        val byCategory = cards
        val filtered = when (config.difficulty) {
            Difficulty.EASY -> byCategory.filter { it.difficulty <= 1 }
            Difficulty.NORMAL -> byCategory
            Difficulty.HARD -> byCategory.filter { it.difficulty >= 2 }
        }
        val relaxed = filtered.size < MIN_POOL && byCategory.size > filtered.size
        val result = if (relaxed) byCategory else filtered
        return PoolResult(result.distinctBy { it.id }, relaxed)
    }
}
