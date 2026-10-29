package app.dilosindecirlo.engine

import app.dilosindecirlo.data.Card
import kotlin.random.Random

enum class Phase { TURN_INTRO, PLAYING, PAUSED, TURN_SUMMARY, GAME_OVER }

enum class CardResult { HIT, PASS, VIOLATION }

enum class EndReason { TIME_UP, CARD_LIMIT, NO_LIVES, DECK_EMPTY, MANUAL }

/** Eventos para sonido/vibración; la interfaz los consume con [GameEngine.drainEvents]. */
enum class GameEvent { TURN_STARTED, HIT, PASS, VIOLATION, TEN_SECONDS, TURN_ENDED, GAME_OVER }

data class PlayedCard(val card: Card, val result: CardResult, val teamIndex: Int)

data class TurnSummary(
    val teamIndex: Int,
    val describer: Int,
    val round: Int,
    val hits: Int,
    val passes: Int,
    val violations: Int,
    val points: Int,
    val timeLeftMs: Long,
    val cards: List<PlayedCard>,
    val reason: EndReason,
)

/** Foto inmutable del estado, pensada para la interfaz. */
data class GameSnapshot(
    val phase: Phase,
    val mode: GameMode,
    val teamNames: List<String>,
    val scores: List<Int>,
    val currentTeam: Int,
    val describer: Int,
    val playersPerTeam: Int,
    val round: Int,
    val totalRounds: Int,
    val card: Card?,
    val cardSerial: Long,
    val timeLeftMs: Long,
    val turnTotalMs: Long,
    val turnHits: Int,
    val turnPasses: Int,
    val turnViolations: Int,
    val livesLeft: Int,
    val maxLives: Int,
    val rule: PartyRule?,
    val canPass: Boolean,
    val summary: TurnSummary?,
    val deckRemaining: Int,
    val deckExhausted: Boolean,
    val targetPoints: Int,
)

/**
 * Motor del juego: máquina de estados pura (sin Android) y determinista dada una semilla.
 * El tiempo avanza solo cuando se llama a [tick], lo que permite probarlo sin relojes reales.
 */
class GameEngine(val config: GameConfig, pool: List<Card>) {
    private val random = Random(config.seed)
    private val deck = Deck(pool, random)
    private val events = mutableListOf<GameEvent>()

    val teamCount: Int = config.teamNames.size
    private val scoreArray = IntArray(teamCount)
    private val turnsPlayed = IntArray(teamCount)

    var phase: Phase = Phase.TURN_INTRO
        private set
    var round: Int = 1
        private set
    var currentTeam: Int = 0
        private set
    var currentCard: Card? = null
        private set
    /** Cambia con cada carta; las acciones del usuario deben enviar el valor visto para evitar dobles pulsaciones. */
    var cardSerial: Long = 0
        private set
    var timeLeftMs: Long = 0
        private set
    var turnTotalMs: Long = 0
        private set
    var livesLeft: Int = config.lives
        private set
    var activeRule: PartyRule? = null
        private set
    var deckExhausted: Boolean = false
        private set

    private var turnHits = 0
    private var turnPasses = 0
    private var turnViolations = 0
    private var turnScoreStart = 0
    private var tenSecondsSent = false
    private val turnCards = mutableListOf<PlayedCard>()

    val summaries = mutableListOf<TurnSummary>()
    val allPlayed = mutableListOf<PlayedCard>()
    var lastSummary: TurnSummary? = null
        private set

    val scores: List<Int> get() = scoreArray.toList()

    init {
        require(teamCount >= 1) { "Se necesita al menos un participante" }
        pickPartyRule()
    }

    // ---- Flujo de turnos -------------------------------------------------------------------

    /** Comienza el turno del participante actual (desde la pantalla de preparación). */
    fun beginTurn() {
        if (phase != Phase.TURN_INTRO) return
        val first = drawCard()
        if (first == null) {
            deckExhausted = true
            finishGame()
            return
        }
        val seconds = effectiveTurnSeconds()
        turnTotalMs = seconds * 1000L
        timeLeftMs = turnTotalMs
        livesLeft = config.lives
        turnHits = 0
        turnPasses = 0
        turnViolations = 0
        turnCards.clear()
        turnScoreStart = scoreArray[currentTeam]
        tenSecondsSent = false
        currentCard = first
        cardSerial++
        phase = Phase.PLAYING
        events += GameEvent.TURN_STARTED
    }

    /** Avanza el reloj. Solo tiene efecto mientras se juega (no en pausa). */
    fun tick(deltaMs: Long) {
        if (phase != Phase.PLAYING || deltaMs <= 0) return
        val before = timeLeftMs
        timeLeftMs -= deltaMs
        if (!tenSecondsSent && turnTotalMs > 10_000 && before > 10_000 && timeLeftMs <= 10_000) {
            tenSecondsSent = true
            events += GameEvent.TEN_SECONDS
        }
        if (timeLeftMs <= 0) {
            timeLeftMs = 0
            endTurn(EndReason.TIME_UP)
        }
    }

    fun hit(serial: Long): Boolean = resolve(serial, CardResult.HIT)

    fun pass(serial: Long): Boolean = resolve(serial, CardResult.PASS)

    fun violation(serial: Long): Boolean = resolve(serial, CardResult.VIOLATION)

    fun pause() {
        if (phase == Phase.PLAYING) phase = Phase.PAUSED
    }

    fun resume() {
        if (phase == Phase.PAUSED) phase = Phase.PLAYING
    }

    /** Termina el turno antes de tiempo (p. ej. desde el menú de pausa). */
    fun endTurnNow() {
        if (phase == Phase.PLAYING || phase == Phase.PAUSED) endTurn(EndReason.MANUAL)
    }

    /** Desde el resumen del turno: pasa al siguiente participante o termina la partida. */
    fun nextTurn() {
        if (phase != Phase.TURN_SUMMARY) return
        turnsPlayed[currentTeam]++
        if (currentTeam == teamCount - 1) {
            if (shouldEndAfterRound()) {
                finishGame()
                return
            }
            round++
            currentTeam = 0
        } else {
            currentTeam++
        }
        if (deck.isEmpty()) {
            deckExhausted = true
            finishGame()
            return
        }
        pickPartyRule()
        phase = Phase.TURN_INTRO
    }

    // ---- Consultas -------------------------------------------------------------------------

    fun canPass(): Boolean = config.allowPass && (activeRule?.allowPass ?: true)

    fun describerIndex(team: Int = currentTeam): Int =
        if (config.playersPerTeam <= 1) 0 else turnsPlayed[team] % config.playersPerTeam

    /** Equipos ordenados de mayor a menor puntuación (índice, puntos). */
    fun ranking(): List<Pair<Int, Int>> =
        scoreArray.withIndex().map { it.index to it.value }.sortedByDescending { it.second }

    fun winners(): List<Int> {
        val best = scoreArray.maxOrNull() ?: return emptyList()
        return scoreArray.withIndex().filter { it.value == best }.map { it.index }
    }

    fun drainEvents(): List<GameEvent> {
        if (events.isEmpty()) return emptyList()
        val copy = events.toList()
        events.clear()
        return copy
    }

    fun snapshot(): GameSnapshot = GameSnapshot(
        phase = phase,
        mode = config.mode,
        teamNames = config.teamNames,
        scores = scores,
        currentTeam = currentTeam,
        describer = describerIndex(),
        playersPerTeam = config.playersPerTeam,
        round = round,
        totalRounds = if (config.singleRound) 1 else config.rounds,
        card = currentCard,
        cardSerial = cardSerial,
        timeLeftMs = timeLeftMs,
        turnTotalMs = turnTotalMs,
        turnHits = turnHits,
        turnPasses = turnPasses,
        turnViolations = turnViolations,
        livesLeft = livesLeft,
        maxLives = config.lives,
        rule = activeRule,
        canPass = canPass(),
        summary = lastSummary,
        deckRemaining = deck.size,
        deckExhausted = deckExhausted,
        targetPoints = config.targetPoints,
    )

    // ---- Internos --------------------------------------------------------------------------

    private fun resolve(serial: Long, result: CardResult): Boolean {
        if (phase != Phase.PLAYING) return false
        val card = currentCard ?: return false
        if (serial != cardSerial) return false
        if (result == CardResult.PASS && !canPass()) return false

        when (result) {
            CardResult.HIT -> {
                turnHits++
                addPoints(config.rules.hitPoints * (activeRule?.pointsMultiplier ?: 1))
                if (config.isSurvival) timeLeftMs += config.bonusSeconds * 1000L
                events += GameEvent.HIT
            }
            CardResult.PASS -> {
                turnPasses++
                addPoints(-config.rules.passPenalty)
                events += GameEvent.PASS
            }
            CardResult.VIOLATION -> {
                turnViolations++
                val base = config.rules.violationPenalty
                val extra = if (base > 0) activeRule?.extraPenalty ?: 0 else 0
                addPoints(-(base + extra))
                if (config.isSurvival) livesLeft--
                events += GameEvent.VIOLATION
            }
        }
        turnCards += PlayedCard(card, result, currentTeam)

        if (config.isSurvival && livesLeft <= 0) {
            endTurn(EndReason.NO_LIVES)
            return true
        }
        if (config.cardsPerTurn > 0 && turnCards.size >= config.cardsPerTurn) {
            endTurn(EndReason.CARD_LIMIT)
            return true
        }
        val next = drawCard()
        if (next == null) {
            deckExhausted = true
            endTurn(EndReason.DECK_EMPTY)
            return true
        }
        currentCard = next
        cardSerial++
        return true
    }

    private fun addPoints(delta: Int) {
        val updated = scoreArray[currentTeam] + delta
        scoreArray[currentTeam] = if (config.rules.allowNegative) updated else maxOf(0, updated)
    }

    private fun endTurn(reason: EndReason) {
        val summary = TurnSummary(
            teamIndex = currentTeam,
            describer = describerIndex(),
            round = round,
            hits = turnHits,
            passes = turnPasses,
            violations = turnViolations,
            points = scoreArray[currentTeam] - turnScoreStart,
            timeLeftMs = timeLeftMs.coerceAtLeast(0),
            cards = turnCards.toList(),
            reason = reason,
        )
        summaries += summary
        lastSummary = summary
        allPlayed += turnCards
        currentCard = null
        cardSerial++
        phase = Phase.TURN_SUMMARY
        events += GameEvent.TURN_ENDED
    }

    private fun finishGame() {
        currentCard = null
        cardSerial++
        phase = Phase.GAME_OVER
        events += GameEvent.GAME_OVER
    }

    private fun shouldEndAfterRound(): Boolean {
        if (config.singleRound || round >= config.rounds) return true
        return config.targetPoints > 0 && scoreArray.any { it >= config.targetPoints }
    }

    private fun effectiveTurnSeconds(): Int {
        val factor = activeRule?.timeFactor ?: 1.0
        return maxOf(10, (config.turnSeconds * factor).toInt())
    }

    private fun drawCard(): Card? = deck.draw(maxDifficulty())

    /** Dificultad progresiva: en supervivencia las cartas se vuelven más difíciles con los aciertos. */
    private fun maxDifficulty(): Int {
        if (!config.isSurvival) return 3
        return when {
            turnHits < 6 -> 1
            turnHits < 14 -> 2
            else -> 3
        }
    }

    private fun pickPartyRule() {
        if (!config.partyRules) {
            activeRule = null
            return
        }
        val options = PartyRules.all.filter { it.id != activeRule?.id }
        activeRule = options[random.nextInt(options.size)]
    }
}
