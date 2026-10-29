package app.dilosindecirlo.ui

import android.app.Application
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.dilosindecirlo.data.AppSettings
import app.dilosindecirlo.data.Card
import app.dilosindecirlo.data.CardCodec
import app.dilosindecirlo.data.CardRepository
import app.dilosindecirlo.data.CardValidator
import app.dilosindecirlo.data.Catalog
import app.dilosindecirlo.data.SettingsRepository
import app.dilosindecirlo.data.StatsRepository
import app.dilosindecirlo.engine.CardResult
import app.dilosindecirlo.engine.ForbiddenMatcher
import app.dilosindecirlo.engine.GameConfig
import app.dilosindecirlo.engine.GameEngine
import app.dilosindecirlo.engine.GameEvent
import app.dilosindecirlo.engine.GameMode
import app.dilosindecirlo.engine.GameSnapshot
import app.dilosindecirlo.engine.Phase
import app.dilosindecirlo.engine.PoolBuilder
import app.dilosindecirlo.engine.Presets
import app.dilosindecirlo.engine.VoiceTracker
import app.dilosindecirlo.feedback.Haptics
import app.dilosindecirlo.feedback.SoundManager
import app.dilosindecirlo.feedback.Sfx
import app.dilosindecirlo.stats.Achievement
import app.dilosindecirlo.stats.Achievements
import app.dilosindecirlo.stats.Challenge
import app.dilosindecirlo.stats.Challenges
import app.dilosindecirlo.stats.GameOutcome
import app.dilosindecirlo.stats.GameResult
import app.dilosindecirlo.stats.StatsData
import app.dilosindecirlo.stats.StatsLogic
import app.dilosindecirlo.voice.VoiceAssist
import app.dilosindecirlo.voice.VoiceState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.TimeZone
import java.util.UUID

sealed interface Screen {
    data object Home : Screen
    data class Setup(val mode: GameMode) : Screen
    data object Playing : Screen
    data object Categories : Screen
    data object Stats : Screen
    data object Settings : Screen
    data object HowTo : Screen
    data object CustomCards : Screen
    data class CardEditor(val cardId: String?) : Screen
}

enum class ResultKind { VICTORY, DRAW, DEFEAT }

data class Flash(val result: CardResult, val token: Long)

data class VoiceSuggestion(val word: String, val serial: Long)

data class ImportReport(val added: Int, val skipped: List<String>)

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val cardRepo = CardRepository(app)
    private val statsRepo = StatsRepository(app)
    private val settingsRepo = SettingsRepository(app)
    private val sound = SoundManager()
    private val haptics = Haptics(app)

    // ---- Datos -----------------------------------------------------------------------------
    var loadError by mutableStateOf<String?>(null)
        private set
    var catalog by mutableStateOf(Catalog(emptyList(), emptyList()))
        private set
    var customCards by mutableStateOf<List<Card>>(emptyList())
        private set
    var favorites by mutableStateOf<Set<String>>(emptySet())
        private set
    var settings by mutableStateOf(AppSettings())
        private set
    var stats by mutableStateOf(StatsData())
        private set

    // ---- Navegación ------------------------------------------------------------------------
    var screen by mutableStateOf<Screen>(Screen.Home)
        private set
    private val backStack = ArrayDeque<Screen>()

    // ---- Configuración y partida -----------------------------------------------------------
    var setup by mutableStateOf(Presets.forMode(GameMode.CLASSIC))
        private set
    var seedText by mutableStateOf("")
    var game by mutableStateOf<GameSnapshot?>(null)
        private set
    var outcome by mutableStateOf<GameOutcome?>(null)
        private set
    var resultKind by mutableStateOf(ResultKind.VICTORY)
        private set
    var lastSeed by mutableStateOf(0L)
        private set
    var poolNotice by mutableStateOf<String?>(null)
        private set
    var toast by mutableStateOf<String?>(null)
    var flash by mutableStateOf<Flash?>(null)
        private set
    var voiceState by mutableStateOf(VoiceState.OFF)
        private set
    var voiceSuggestion by mutableStateOf<VoiceSuggestion?>(null)
        private set

    private var engine: GameEngine? = null
    private var lastConfig: GameConfig? = null
    private var ticker: Job? = null
    private var recorded = false
    private var lastCardId: String? = null
    private var flashCounter = 0L
    private val voiceTracker = VoiceTracker()
    private var voice: VoiceAssist? = null

    init {
        try {
            catalog = cardRepo.loadCatalog()
        } catch (e: Exception) {
            loadError = "No se pudo cargar el catálogo de cartas: ${e.message}"
        }
        customCards = cardRepo.loadCustom()
        favorites = cardRepo.loadFavorites()
        settings = settingsRepo.load()
        stats = statsRepo.load()
        val saved = settingsRepo.loadTeamNames()
        if (saved.size >= 2) setup = setup.copy(teamNames = saved)
    }

    val allCards: List<Card> get() = catalog.cards + customCards

    fun categoryName(id: String): String = catalog.category(id)?.let { "${it.emoji} ${it.name}" } ?: id

    // ---- Desafío diario --------------------------------------------------------------------
    private fun epochDayNow(): Long {
        val now = System.currentTimeMillis()
        return StatsLogic.epochDay(now, TimeZone.getDefault().getOffset(now))
    }

    val todayChallenge: Challenge get() = Challenges.forDay(epochDayNow())
    val todayChallengeDone: Boolean get() = epochDayNow() in stats.challengeDays

    // ---- Navegación ------------------------------------------------------------------------
    fun navigate(target: Screen) {
        backStack.addLast(screen)
        screen = target
    }

    fun goHome() {
        backStack.clear()
        screen = Screen.Home
    }

    /** @return true si la pulsación de "atrás" se consumió dentro de la app. */
    fun back(): Boolean {
        if (screen == Screen.Playing) return false // la pantalla de juego pide confirmación
        val previous = backStack.removeLastOrNull() ?: return false
        screen = previous
        return true
    }

    fun openSetup(mode: GameMode, categories: Set<String> = emptySet()) {
        val saved = setup.teamNames
        var base = Presets.forMode(mode)
        if (mode != GameMode.FFA && mode != GameMode.SURVIVAL && saved.size >= 2) base = base.copy(teamNames = saved)
        if (categories.isNotEmpty()) base = base.copy(categoryIds = categories)
        setup = base
        seedText = ""
        navigate(Screen.Setup(mode))
    }

    fun updateSetup(transform: (GameConfig) -> GameConfig) {
        setup = transform(setup)
    }

    fun showMessage(message: String) {
        toast = message
    }

    // ---- Partida ---------------------------------------------------------------------------
    fun startQuickGame() = startGame(Presets.quick())

    fun startGame(config: GameConfig) {
        val seed = seedText.trim().toLongOrNull() ?: config.seed.takeIf { it != 0L } ?: System.nanoTime()
        val finalConfig = config.copy(seed = seed)
        val pool = PoolBuilder.build(allCards, finalConfig, settings.hiddenCategories)
        if (pool.cards.size < 8) {
            toast = "Hay muy pocas cartas con esos filtros (${pool.cards.size}). Elige más categorías o cambia la dificultad."
            return
        }
        stopTicker()
        voiceStop()
        engine = GameEngine(finalConfig, pool.cards)
        lastConfig = finalConfig
        lastSeed = seed
        recorded = false
        outcome = null
        lastCardId = null
        voiceSuggestion = null
        poolNotice = if (pool.relaxedDifficulty) "Había pocas cartas con esa dificultad; se incluyeron cartas de otros niveles." else null
        if (finalConfig.mode != GameMode.FFA && finalConfig.mode != GameMode.SURVIVAL) settingsRepo.saveTeamNames(finalConfig.teamNames)
        refresh()
        if (screen != Screen.Playing) navigate(Screen.Playing)
    }

    fun playAgain() {
        val config = lastConfig ?: return
        seedText = ""
        startGame(config.copy(seed = System.nanoTime()))
    }

    fun beginTurn() {
        engine?.beginTurn()
        refresh()
    }

    fun onHit(serial: Long) {
        if (engine?.hit(serial) == true) refresh()
    }

    fun onPass(serial: Long) {
        if (engine?.pass(serial) == true) refresh()
    }

    fun onViolation(serial: Long) {
        if (engine?.violation(serial) == true) refresh()
    }

    fun pauseGame() {
        engine?.pause()
        refresh()
    }

    fun resumeGame() {
        engine?.resume()
        refresh()
    }

    fun endTurnNow() {
        engine?.endTurnNow()
        refresh()
    }

    fun nextTurn() {
        engine?.nextTurn()
        refresh()
    }

    fun quitGame() {
        stopTicker()
        voiceStop()
        engine = null
        game = null
        goHome()
    }

    /** Se llama cuando la app pasa a segundo plano: el reloj no debe seguir corriendo. */
    fun onAppBackground() {
        if (engine?.phase == Phase.PLAYING) pauseGame()
    }

    private fun refresh() {
        val e = engine ?: return
        for (event in e.drainEvents()) handleEvent(event, e)
        game = e.snapshot()

        val cardId = e.currentCard?.id
        if (cardId != lastCardId) {
            lastCardId = cardId
            voiceTracker.onCardChanged()
            voiceSuggestion = null
        }
        if (e.phase == Phase.PLAYING) startTicker() else stopTicker()
        if (e.phase == Phase.GAME_OVER && !recorded) finalizeGame(e)
    }

    private fun handleEvent(event: GameEvent, e: GameEngine) {
        when (event) {
            GameEvent.TURN_STARTED -> play(Sfx.START)
            GameEvent.HIT -> {
                play(Sfx.HIT)
                if (settings.vibration) haptics.hit()
                flash = Flash(CardResult.HIT, ++flashCounter)
            }
            GameEvent.PASS -> play(Sfx.PASS)
            GameEvent.VIOLATION -> {
                play(Sfx.VIOLATION)
                if (settings.vibration) haptics.violation()
                flash = Flash(CardResult.VIOLATION, ++flashCounter)
            }
            GameEvent.TEN_SECONDS -> {
                play(Sfx.WARNING)
                if (settings.vibration) haptics.tick()
            }
            GameEvent.TURN_ENDED -> {
                play(Sfx.TURN_END)
                if (settings.vibration) haptics.turnEnd()
            }
            GameEvent.GAME_OVER -> Unit // se gestiona en finalizeGame
        }
    }

    private fun play(sfx: Sfx) {
        if (settings.sound) sound.play(sfx)
    }

    private fun startTicker() {
        if (ticker?.isActive == true) return
        ticker = viewModelScope.launch {
            var last = SystemClock.elapsedRealtime()
            while (isActive) {
                delay(100)
                val now = SystemClock.elapsedRealtime()
                val delta = (now - last).coerceIn(0, 1000)
                last = now
                val e = engine ?: break
                if (e.phase != Phase.PLAYING) break
                e.tick(delta)
                refresh()
                if (e.phase != Phase.PLAYING) break
            }
        }
    }

    private fun stopTicker() {
        ticker?.cancel()
        ticker = null
    }

    private fun finalizeGame(e: GameEngine) {
        recorded = true
        val result = GameResult(
            mode = e.config.mode,
            teamNames = e.config.teamNames,
            scores = e.scores,
            winners = e.winners(),
            played = e.allPlayed.toList(),
            summaries = e.summaries.toList(),
            deckExhausted = e.deckExhausted,
        )
        val previous = stats
        val now = System.currentTimeMillis()
        val result2 = StatsLogic.apply(previous, result, now, epochDayNow())
        stats = result2.stats
        statsRepo.save(stats)
        outcome = result2

        val bestTurn = result.summaries.maxOfOrNull { it.hits } ?: 0
        resultKind = when {
            (result.scores.maxOrNull() ?: 0) <= 0 -> ResultKind.DEFEAT
            result.teamNames.size == 1 -> when (result.mode) {
                GameMode.SURVIVAL -> if (bestTurn >= previous.bestSurvival) ResultKind.VICTORY else ResultKind.DEFEAT
                GameMode.TIME_ATTACK -> if (bestTurn >= previous.bestTimeAttack) ResultKind.VICTORY else ResultKind.DEFEAT
                else -> ResultKind.VICTORY
            }
            result.winners.size > 1 -> ResultKind.DRAW
            else -> ResultKind.VICTORY
        }
        if (resultKind == ResultKind.DEFEAT) play(Sfx.DEFEAT) else {
            play(Sfx.VICTORY)
            if (settings.vibration) haptics.victory()
        }
    }

    // ---- Voz (opcional) --------------------------------------------------------------------
    fun voiceStart() {
        if (voice == null) {
            voice = VoiceAssist(
                context = getApplication<Application>(),
                onText = { text, isFinal -> onVoiceText(text, isFinal) },
                onState = { voiceState = it },
            )
        }
        voice?.start()
    }

    fun voiceStop() {
        voice?.stop()
        voiceState = VoiceState.OFF
    }

    private fun onVoiceText(text: String, isFinal: Boolean) {
        val e = engine ?: return
        val card = e.currentCard ?: return
        if (e.phase != Phase.PLAYING) return
        val fresh = voiceTracker.fresh(text)
        if (isFinal) voiceTracker.onUtteranceEnd()
        val matches = ForbiddenMatcher.matches(fresh, card)
        if (matches.isEmpty()) return
        if (settings.voiceAutoApply) {
            toast = "Voz: se dijo «${matches.first()}»"
            onViolation(e.cardSerial)
        } else {
            voiceSuggestion = VoiceSuggestion(matches.first(), e.cardSerial)
        }
    }

    fun confirmVoiceSuggestion() {
        val s = voiceSuggestion ?: return
        voiceSuggestion = null
        onViolation(s.serial)
    }

    fun dismissVoiceSuggestion() {
        voiceSuggestion = null
    }

    // ---- Ajustes ---------------------------------------------------------------------------
    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        settings = transform(settings)
        settingsRepo.save(settings)
    }

    fun toggleHiddenCategory(id: String) {
        updateSettings {
            it.copy(hiddenCategories = if (id in it.hiddenCategories) it.hiddenCategories - id else it.hiddenCategories + id)
        }
    }

    fun resetStats() {
        statsRepo.reset()
        stats = StatsData()
    }

    // ---- Favoritas -------------------------------------------------------------------------
    fun toggleFavorite(cardId: String) {
        favorites = if (cardId in favorites) favorites - cardId else favorites + cardId
        cardRepo.saveFavorites(favorites)
    }

    fun cardById(id: String): Card? = allCards.firstOrNull { it.id == id }

    // ---- Cartas personalizadas -------------------------------------------------------------
    /** @return lista de errores (vacía si se guardó correctamente). */
    fun saveCustomCard(editId: String?, word: String, forbidden: List<String>, categoryId: String, difficulty: Int): List<String> {
        val others = allCards.filter { it.id != editId }.map { it.word }
        val errors = CardValidator.validate(word, forbidden, others)
        if (errors.isNotEmpty()) return errors
        val card = Card(
            id = editId ?: ("c:" + UUID.randomUUID()),
            word = word.trim(),
            forbidden = forbidden.map { it.trim() }.filter { it.isNotEmpty() },
            categoryId = categoryId,
            difficulty = difficulty.coerceIn(1, 3),
            custom = true,
        )
        customCards = if (editId == null) customCards + card else customCards.map { if (it.id == editId) card else it }
        cardRepo.saveCustom(customCards)
        val (updated, achievement) = StatsLogic.unlockManual(stats, Achievements.CREATOR_ID, System.currentTimeMillis())
        if (achievement != null) {
            stats = updated
            statsRepo.save(stats)
            toast = "Logro desbloqueado: ${achievement.title}"
        }
        return emptyList()
    }

    fun deleteCustomCard(id: String) {
        customCards = customCards.filterNot { it.id == id }
        cardRepo.saveCustom(customCards)
        if (id in favorites) {
            favorites = favorites - id
            cardRepo.saveFavorites(favorites)
        }
    }

    fun exportCustomJson(): String = CardCodec.exportJson(customCards)

    fun importCustomJson(json: String): ImportReport {
        val parsed = try {
            CardCodec.parseCustom(json) { UUID.randomUUID().toString() }
        } catch (e: Exception) {
            return ImportReport(0, listOf("El archivo no tiene un formato válido."))
        }
        val skipped = mutableListOf<String>()
        val knownCategories = catalog.categories.map { it.id }.toSet()
        var added = 0
        val working = customCards.toMutableList()
        for (card in parsed) {
            val existing = catalog.cards.map { it.word } + working.map { it.word }
            val errors = CardValidator.validate(card.word, card.forbidden, existing)
            if (errors.isNotEmpty()) {
                skipped += "${card.word.ifBlank { "(sin palabra)" }}: ${errors.first()}"
                continue
            }
            val category = if (card.categoryId in knownCategories) card.categoryId else "cultura"
            working += card.copy(id = "c:" + UUID.randomUUID(), categoryId = category)
            added++
        }
        if (added > 0) {
            customCards = working
            cardRepo.saveCustom(customCards)
        }
        return ImportReport(added, skipped)
    }

    override fun onCleared() {
        stopTicker()
        voice?.stop()
        super.onCleared()
    }

    fun achievementList(): List<Pair<Achievement, Boolean>> = Achievements.all.map { it to (it.id in stats.unlocked) }
}
