package app.dilosindecirlo.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import app.dilosindecirlo.data.Card
import app.dilosindecirlo.engine.CardResult
import app.dilosindecirlo.engine.EndReason
import app.dilosindecirlo.engine.GameMode
import app.dilosindecirlo.engine.GameSnapshot
import app.dilosindecirlo.engine.Phase
import app.dilosindecirlo.ui.AppViewModel
import app.dilosindecirlo.ui.ResultKind
import app.dilosindecirlo.ui.Screen
import app.dilosindecirlo.ui.components.AutoSizeText
import app.dilosindecirlo.ui.components.BrutalButton
import app.dilosindecirlo.ui.components.BrutalCard
import app.dilosindecirlo.ui.components.Chip
import app.dilosindecirlo.ui.components.Confetti
import app.dilosindecirlo.ui.theme.Dsd
import app.dilosindecirlo.voice.VoiceState
import kotlinx.coroutines.delay

@Composable
fun GameRoot(vm: AppViewModel) {
    val g = vm.game
    val view = LocalView.current
    DisposableEffect(vm.settings.keepScreenOn) {
        view.keepScreenOn = vm.settings.keepScreenOn
        onDispose { view.keepScreenOn = false }
    }
    if (g == null) {
        LaunchedEffect(Unit) { vm.goHome() }
        return
    }
    var confirmQuit by remember { mutableStateOf(false) }
    val askQuit = {
        if (g.phase == Phase.PLAYING) vm.pauseGame()
        confirmQuit = true
    }
    BackHandler {
        if (g.phase == Phase.GAME_OVER) vm.quitGame() else askQuit()
    }

    when (g.phase) {
        Phase.TURN_INTRO -> TurnIntro(vm, g, askQuit)
        Phase.PLAYING, Phase.PAUSED -> PlayScreen(vm, g)
        Phase.TURN_SUMMARY -> TurnSummaryScreen(vm, g, askQuit)
        Phase.GAME_OVER -> GameOverScreen(vm, g)
    }

    if (confirmQuit) {
        AlertDialog(
            onDismissRequest = { confirmQuit = false },
            containerColor = Dsd.Paper,
            title = { Text("¿Salir de la partida?", fontWeight = FontWeight.Black) },
            text = { Text("Se perderá el progreso de esta partida.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmQuit = false
                    vm.quitGame()
                }) { Text("Salir", color = Dsd.Red, fontWeight = FontWeight.Black) }
            },
            dismissButton = {
                TextButton(onClick = { confirmQuit = false }) { Text("Seguir jugando", color = Dsd.Blue, fontWeight = FontWeight.Black) }
            },
        )
    }
}

private fun roundLabel(g: GameSnapshot): String =
    if (g.totalRounds > 1) "RONDA ${g.round} DE ${g.totalRounds}" else g.mode.title.uppercase()

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ScoreStrip(g: GameSnapshot, highlightCurrent: Boolean = true) {
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        g.teamNames.forEachIndexed { i, name ->
            val current = highlightCurrent && i == g.currentTeam
            val shape = RoundedCornerShape(14.dp)
            Row(
                Modifier
                    .clip(shape)
                    .background(if (current) Dsd.Yellow else Dsd.Paper)
                    .border(2.5.dp, Dsd.Ink, shape)
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(name, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Dsd.Ink, maxLines = 1)
                Spacer(Modifier.width(8.dp))
                Text("${g.scores[i]}", fontWeight = FontWeight.Black, fontSize = 18.sp, color = Dsd.Blue)
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Preparación del turno
// ---------------------------------------------------------------------------------------------

@Composable
private fun TurnIntro(vm: AppViewModel, g: GameSnapshot, onQuit: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Dsd.Cream)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(roundLabel(g), fontWeight = FontWeight.Black, color = Dsd.Blue, fontSize = 15.sp, letterSpacing = 1.5.sp, modifier = Modifier.weight(1f))
            Chip("Salir", false, onQuit)
        }
        Spacer(Modifier.height(10.dp))
        ScoreStrip(g)
        vm.poolNotice?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, fontSize = 12.sp, color = Dsd.Grey, textAlign = TextAlign.Center)
        }

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text("TURNO DE", fontWeight = FontWeight.Black, fontSize = 15.sp, letterSpacing = 3.sp, color = Dsd.Grey)
            Spacer(Modifier.height(8.dp))
            BrutalCard(Modifier.fillMaxWidth(), color = Dsd.Blue) {
                Text(
                    g.teamNames[g.currentTeam],
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = 34.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 22.dp),
                )
            }
            if (g.playersPerTeam > 1) {
                Spacer(Modifier.height(14.dp))
                Text("Describe: Jugador ${g.describer + 1}", fontWeight = FontWeight.Black, fontSize = 22.sp, color = Dsd.Ink)
            }
            g.rule?.let { rule ->
                Spacer(Modifier.height(16.dp))
                BrutalCard(Modifier.fillMaxWidth(), color = Dsd.Yellow) {
                    Column(Modifier.padding(14.dp)) {
                        Text("🎉 REGLA DE ESTE TURNO", fontWeight = FontWeight.Black, fontSize = 12.sp, letterSpacing = 1.sp, color = Dsd.Ink)
                        Text(rule.title, fontWeight = FontWeight.Black, fontSize = 22.sp, color = Dsd.Ink)
                        Text(rule.description, fontSize = 15.sp, color = Dsd.Ink)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(modeSummary(g), fontSize = 14.sp, color = Dsd.Grey, textAlign = TextAlign.Center)
        }

        Text(
            "Pasa el teléfono a quien va a describir. ¡Que el resto no vea la pantalla!",
            fontSize = 14.sp,
            color = Dsd.Ink,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 12.dp),
        )
        BrutalButton("¡EMPEZAR TURNO!", { vm.beginTurn() }, Modifier.fillMaxWidth(), color = Dsd.Red, fontSize = 26.sp, height = 74.dp)
    }
}

private fun modeSummary(g: GameSnapshot): String {
    return when (g.mode) {
        GameMode.SURVIVAL -> "Tienes ${g.maxLives} vidas. Cada acierto suma tiempo y cada infracción te quita una vida."
        GameMode.TIME_ATTACK -> "Consigue todos los aciertos que puedas antes de que se acabe el tiempo."
        else -> if (g.targetPoints > 0) "Primero en llegar a ${g.targetPoints} puntos (al terminar la ronda)." else "Quedan ${g.deckRemaining} cartas en el mazo."
    }
}

// ---------------------------------------------------------------------------------------------
// Pantalla de juego
// ---------------------------------------------------------------------------------------------

@Composable
private fun PlayScreen(vm: AppViewModel, g: GameSnapshot) {
    val paused = g.phase == Phase.PAUSED
    val card = g.card
    val animations = vm.settings.animations
    val serial = g.cardSerial

    // --- Asistente de voz (opcional) ---
    val context = LocalContext.current
    var hasMic by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
    }
    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasMic = granted
        if (!granted) vm.showMessage("Sin permiso de micrófono: el asistente de voz no se puede usar.")
    }
    val voiceAllowedThisTurn = vm.settings.voiceAssist && g.rule?.honorOnly != true
    LaunchedEffect(Unit) {
        if (vm.settings.voiceAssist && !hasMic) micLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }
    val wantVoice = voiceAllowedThisTurn && !paused && hasMic
    LaunchedEffect(wantVoice) {
        if (wantVoice) vm.voiceStart() else vm.voiceStop()
    }
    DisposableEffect(Unit) { onDispose { vm.voiceStop() } }

    // --- Destello y aviso al acertar o cometer una infracción ---
    val flash = vm.flash
    val flashAlpha = remember { Animatable(0f) }
    var banner by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(flash?.token) {
        if (flash != null) {
            banner = if (flash.result == CardResult.VIOLATION) "¡INFRACCIÓN!" else null
            if (animations) {
                flashAlpha.snapTo(0.42f)
                flashAlpha.animateTo(0f, tween(380))
            }
            if (banner != null) {
                delay(750)
                banner = null
            }
        }
    }

    Box(Modifier.fillMaxSize().background(Dsd.Cream)) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 14.dp, vertical = 8.dp),
        ) {
            GameHeader(vm, g, paused)
            Spacer(Modifier.height(6.dp))
            TimerBar(g)
            Spacer(Modifier.height(8.dp))

            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {
                    paused -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("⏸️", fontSize = 64.sp)
                    }
                    card != null -> CardArea(vm, card, animations)
                }
            }

            vm.voiceSuggestion?.let { suggestion ->
                LaunchedEffect(suggestion) {
                    delay(5000)
                    vm.dismissVoiceSuggestion()
                }
                Spacer(Modifier.height(8.dp))
                BrutalCard(Modifier.fillMaxWidth(), color = Dsd.Yellow, cornerRadius = 18.dp, shadow = 4.dp) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "🎤 ¿Dijo «${suggestion.word}»?",
                            fontWeight = FontWeight.Black,
                            fontSize = 15.sp,
                            color = Dsd.Ink,
                            modifier = Modifier.weight(1f),
                        )
                        Chip("Ignorar", false, { vm.dismissVoiceSuggestion() })
                        Spacer(Modifier.width(6.dp))
                        Chip("Infracción", true, { vm.confirmVoiceSuggestion() }, selectedColor = Dsd.Red)
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            BrutalButton(
                "✔ ACERTADA",
                { vm.onHit(serial) },
                Modifier.fillMaxWidth(),
                color = Dsd.Green,
                fontSize = 30.sp,
                height = 86.dp,
                enabled = !paused && card != null,
                debounceMs = 300,
            )
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BrutalButton(
                    "PASAR",
                    { vm.onPass(serial) },
                    Modifier.weight(1f),
                    color = Dsd.Blue,
                    fontSize = 20.sp,
                    height = 68.dp,
                    enabled = !paused && card != null && g.canPass,
                    debounceMs = 300,
                )
                BrutalButton(
                    "PALABRA PROHIBIDA",
                    { vm.onViolation(serial) },
                    Modifier.weight(1f),
                    color = Dsd.Red,
                    fontSize = 16.sp,
                    height = 68.dp,
                    enabled = !paused && card != null,
                    debounceMs = 300,
                )
            }
        }

        // Destello de color (no intercepta toques)
        if (flash != null && flashAlpha.value > 0f) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background((if (flash.result == CardResult.HIT) Dsd.Green else Dsd.Red).copy(alpha = flashAlpha.value)),
            )
        }
        banner?.let { text ->
            Box(Modifier.fillMaxSize().padding(top = 120.dp), contentAlignment = Alignment.TopCenter) {
                BrutalCard(color = Dsd.Red, cornerRadius = 18.dp) {
                    Text(
                        text,
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        fontSize = 28.sp,
                        modifier = Modifier.padding(horizontal = 22.dp, vertical = 12.dp),
                    )
                }
            }
        }
        if (paused) PauseOverlay(vm)
    }
}

@Composable
private fun GameHeader(vm: AppViewModel, g: GameSnapshot, paused: Boolean) {
    val animations = vm.settings.animations
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(g.teamNames[g.currentTeam], fontWeight = FontWeight.Black, fontSize = 20.sp, color = Dsd.Ink, maxLines = 1)
            val sub = buildString {
                append(if (g.totalRounds > 1) "Ronda ${g.round}/${g.totalRounds}" else g.mode.title)
                if (g.playersPerTeam > 1) append(" · Jugador ${g.describer + 1}")
            }
            Text(sub, fontSize = 12.sp, color = Dsd.Grey, maxLines = 1)
            g.rule?.let { Text("🎉 ${it.title}", fontSize = 12.sp, fontWeight = FontWeight.Black, color = Dsd.Red, maxLines = 1) }
            if (vm.settings.voiceAssist && vm.voiceState == VoiceState.LISTENING) {
                Text("🎤 Escuchando…", fontSize = 12.sp, color = Dsd.Blue, fontWeight = FontWeight.Bold)
            }
        }
        if (g.mode == GameMode.SURVIVAL) {
            Text("❤️".repeat(g.livesLeft.coerceAtLeast(0)) + "🖤".repeat((g.maxLives - g.livesLeft).coerceAtLeast(0)), fontSize = 16.sp)
            Spacer(Modifier.width(8.dp))
        }
        Pill("⭐", g.scores[g.currentTeam], Dsd.Yellow, animations)
        Spacer(Modifier.width(6.dp))
        Pill("✔", g.turnHits, Dsd.GreenSoft, animations)
        Spacer(Modifier.width(8.dp))
        BrutalButton(
            "⏸",
            { if (paused) vm.resumeGame() else vm.pauseGame() },
            Modifier.width(54.dp),
            color = Dsd.Paper,
            contentColor = Dsd.Ink,
            height = 48.dp,
            fontSize = 20.sp,
        )
    }
}

@Composable
private fun Pill(icon: String, value: Int, color: Color, animations: Boolean) {
    val scale = remember { Animatable(1f) }
    LaunchedEffect(value) {
        if (animations) {
            scale.snapTo(1.4f)
            scale.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 400f))
        }
    }
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
            }
            .clip(shape)
            .background(color)
            .border(2.5.dp, Dsd.Ink, shape)
            .padding(horizontal = 9.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(icon, fontSize = 13.sp)
        Spacer(Modifier.width(4.dp))
        Text("$value", fontWeight = FontWeight.Black, fontSize = 18.sp, color = Dsd.Ink)
    }
}

@Composable
private fun TimerBar(g: GameSnapshot) {
    val total = g.turnTotalMs.coerceAtLeast(1)
    val fraction = (g.timeLeftMs.toFloat() / total).coerceIn(0f, 1f)
    val seconds = ((g.timeLeftMs + 999) / 1000).toInt()
    val danger = g.timeLeftMs <= 10_000
    val shape = RoundedCornerShape(10.dp)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            "$seconds",
            fontSize = 40.sp,
            fontWeight = FontWeight.Black,
            color = if (danger) Dsd.Red else Dsd.Ink,
            modifier = Modifier.width(78.dp),
        )
        Box(
            Modifier
                .weight(1f)
                .height(20.dp)
                .clip(shape)
                .background(Dsd.CreamDark)
                .border(2.5.dp, Dsd.Ink, shape),
        ) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction)
                    .background(if (danger) Dsd.Red else Dsd.Blue),
            )
        }
    }
}

@Composable
private fun CardArea(vm: AppViewModel, card: Card, animations: Boolean) {
    if (animations) {
        AnimatedContent(
            targetState = card,
            transitionSpec = {
                (slideInHorizontally(tween(220)) { it / 2 } + fadeIn(tween(220))) togetherWith
                    (slideOutHorizontally(tween(160)) { -it / 2 } + fadeOut(tween(140)))
            },
            label = "cardChange",
        ) { target -> CardFace(vm, target) }
    } else {
        CardFace(vm, card)
    }
}

@Composable
private fun CardFace(vm: AppViewModel, card: Card) {
    val favorite = card.id in vm.favorites
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // El tamaño de letra de las prohibidas se adapta a la altura disponible.
        val budget = maxHeight.value - 40f - 62f - 34f - 48f
        val perRow = budget / card.forbidden.size.coerceAtLeast(1)
        val fontSize = ((perRow - 18f) / 1.25f).coerceIn(15f, 24f)
        BrutalCard(Modifier.fillMaxSize(), cornerRadius = 26.dp) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(Dsd.Blue)
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        vm.categoryName(card.categoryId),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        maxLines = 1,
                        modifier = Modifier.weight(1f),
                    )
                    Text("★".repeat(card.difficulty.coerceIn(1, 3)), color = Dsd.Yellow, fontSize = 14.sp)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (favorite) "⭐" else "☆",
                        color = Color.White,
                        fontSize = 22.sp,
                        modifier = Modifier
                            .clickable { vm.toggleFavorite(card.id) }
                            .padding(4.dp),
                    )
                }
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    AutoSizeText(
                        card.word.uppercase(),
                        Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        maxFontSize = 46.sp,
                        minFontSize = 20.sp,
                        color = Dsd.Blue,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text("PROHIBIDAS", color = Dsd.Red, fontWeight = FontWeight.Black, fontSize = 13.sp, letterSpacing = 3.sp)
                    Spacer(Modifier.height(4.dp))
                    card.forbidden.forEach { word ->
                        val shape = RoundedCornerShape(12.dp)
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp)
                                .clip(shape)
                                .background(Dsd.RedSoft)
                                .border(2.dp, Dsd.Red, shape)
                                .padding(horizontal = 12.dp, vertical = 4.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "✖  $word",
                                fontWeight = FontWeight.Black,
                                fontSize = fontSize.sp,
                                color = Dsd.Ink,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PauseOverlay(vm: AppViewModel) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Dsd.Ink.copy(alpha = 0.88f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        contentAlignment = Alignment.Center,
    ) {
        BrutalCard(Modifier.fillMaxWidth().padding(24.dp)) {
            Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("PAUSA", fontWeight = FontWeight.Black, fontSize = 34.sp, color = Dsd.Ink)
                Text("El tiempo está detenido y la carta oculta.", fontSize = 14.sp, color = Dsd.Grey, textAlign = TextAlign.Center)
                Spacer(Modifier.height(18.dp))
                BrutalButton("▶ REANUDAR", { vm.resumeGame() }, Modifier.fillMaxWidth(), color = Dsd.Green, fontSize = 22.sp)
                Spacer(Modifier.height(12.dp))
                BrutalButton("Terminar turno", { vm.endTurnNow() }, Modifier.fillMaxWidth(), color = Dsd.Blue, fontSize = 18.sp, height = 54.dp)
                Spacer(Modifier.height(12.dp))
                BrutalButton("Salir de la partida", { vm.quitGame() }, Modifier.fillMaxWidth(), color = Dsd.Paper, contentColor = Dsd.Red, fontSize = 18.sp, height = 54.dp)
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Resumen del turno
// ---------------------------------------------------------------------------------------------

private fun reasonText(reason: EndReason): String = when (reason) {
    EndReason.TIME_UP -> "¡Se acabó el tiempo!"
    EndReason.CARD_LIMIT -> "¡Límite de cartas alcanzado!"
    EndReason.NO_LIVES -> "¡Te quedaste sin vidas!"
    EndReason.DECK_EMPTY -> "¡Se acabaron las cartas del mazo!"
    EndReason.MANUAL -> "Turno terminado"
}

@Composable
private fun TurnSummaryScreen(vm: AppViewModel, g: GameSnapshot, onQuit: () -> Unit) {
    val summary = g.summary
    Column(
        Modifier
            .fillMaxSize()
            .background(Dsd.Cream)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(18.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("FIN DEL TURNO", fontWeight = FontWeight.Black, color = Dsd.Blue, fontSize = 15.sp, letterSpacing = 1.5.sp, modifier = Modifier.weight(1f))
            Chip("Salir", false, onQuit)
        }
        if (summary == null) {
            BrutalButton("SIGUIENTE", { vm.nextTurn() }, Modifier.fillMaxWidth())
            return@Column
        }
        Text(g.teamNames[summary.teamIndex], fontWeight = FontWeight.Black, fontSize = 30.sp, color = Dsd.Ink)
        Text(reasonText(summary.reason), fontSize = 15.sp, color = Dsd.Grey)
        Spacer(Modifier.height(12.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatBox("Aciertos", "${summary.hits}", Dsd.GreenSoft, Modifier.weight(1f))
            StatBox("Infracciones", "${summary.violations}", Dsd.RedSoft, Modifier.weight(1f))
            StatBox("Pasadas", "${summary.passes}", Dsd.BlueSoft, Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatBox("Puntos del turno", (if (summary.points > 0) "+" else "") + summary.points, Dsd.Yellow.copy(alpha = 0.6f), Modifier.weight(1f))
            StatBox("Tiempo restante", "${(summary.timeLeftMs + 999) / 1000} s", Dsd.Paper, Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        ScoreStrip(g, highlightCurrent = false)
        Spacer(Modifier.height(10.dp))

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        ) {
            summary.cards.forEach { played ->
                val (icon, tint) = when (played.result) {
                    CardResult.HIT -> "✔" to Dsd.Green
                    CardResult.PASS -> "↷" to Dsd.Blue
                    CardResult.VIOLATION -> "✖" to Dsd.Red
                }
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(icon, color = tint, fontWeight = FontWeight.Black, fontSize = 20.sp, modifier = Modifier.width(30.dp))
                    Text(played.card.word.uppercase(), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Dsd.Ink, modifier = Modifier.weight(1f))
                    Text(
                        if (played.card.id in vm.favorites) "⭐" else "☆",
                        fontSize = 22.sp,
                        modifier = Modifier.clickable { vm.toggleFavorite(played.card.id) }.padding(6.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        BrutalButton("SIGUIENTE", { vm.nextTurn() }, Modifier.fillMaxWidth(), color = Dsd.Red, fontSize = 24.sp, height = 70.dp)
    }
}

@Composable
private fun StatBox(label: String, value: String, color: Color, modifier: Modifier) {
    BrutalCard(modifier, color = color, cornerRadius = 16.dp, shadow = 4.dp) {
        Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, fontWeight = FontWeight.Black, fontSize = 26.sp, color = Dsd.Ink)
            Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Dsd.Ink, textAlign = TextAlign.Center)
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Fin de la partida: victoria, empate o derrota
// ---------------------------------------------------------------------------------------------

@Composable
private fun GameOverScreen(vm: AppViewModel, g: GameSnapshot) {
    val kind = vm.resultKind
    val outcome = vm.outcome
    val ranking = g.scores.withIndex().sortedByDescending { it.value }
    val bestScore = ranking.firstOrNull()?.value ?: 0
    val winners = ranking.filter { it.value == bestScore }.map { g.teamNames[it.index] }

    Box(Modifier.fillMaxSize().background(if (kind == ResultKind.DEFEAT) Dsd.CreamDark else Dsd.Cream)) {
        if (kind == ResultKind.VICTORY) Confetti(enabled = vm.settings.animations)
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(8.dp))
            Text(
                when (kind) {
                    ResultKind.VICTORY -> "🏆"
                    ResultKind.DRAW -> "🤝"
                    ResultKind.DEFEAT -> "💥"
                },
                fontSize = 72.sp,
            )
            Text(
                when (kind) {
                    ResultKind.VICTORY -> "¡VICTORIA!"
                    ResultKind.DRAW -> "¡EMPATE!"
                    ResultKind.DEFEAT -> "DERROTA"
                },
                fontWeight = FontWeight.Black,
                fontSize = 42.sp,
                color = if (kind == ResultKind.DEFEAT) Dsd.Red else Dsd.Blue,
            )
            Text(
                when {
                    kind == ResultKind.DEFEAT && g.teamNames.size == 1 -> "No superaste tu récord. ¡Inténtalo otra vez!"
                    kind == ResultKind.DEFEAT -> "Nadie consiguió puntos."
                    kind == ResultKind.DRAW -> "Empatan: ${winners.joinToString(", ")}"
                    g.teamNames.size == 1 -> "${g.teamNames[0]} suma $bestScore puntos"
                    else -> "Gana ${winners.first()} con $bestScore puntos"
                },
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = Dsd.Ink,
                textAlign = TextAlign.Center,
            )
            if (g.deckExhausted) {
                Text("Se acabaron las cartas del mazo, así que la partida terminó.", fontSize = 13.sp, color = Dsd.Grey, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(16.dp))

            BrutalCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    ranking.forEachIndexed { place, entry ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                when (place) {
                                    0 -> "🥇"
                                    1 -> "🥈"
                                    2 -> "🥉"
                                    else -> "${place + 1}."
                                },
                                fontSize = 22.sp,
                                modifier = Modifier.width(40.dp),
                            )
                            Text(g.teamNames[entry.index], fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Dsd.Ink, modifier = Modifier.weight(1f))
                            Text("${entry.value}", fontWeight = FontWeight.Black, fontSize = 24.sp, color = Dsd.Blue)
                        }
                    }
                }
            }

            outcome?.let { o ->
                o.recordsBroken.forEach { record ->
                    Spacer(Modifier.height(10.dp))
                    BrutalCard(Modifier.fillMaxWidth(), color = Dsd.Yellow) {
                        Text("🎖️ $record", Modifier.padding(12.dp), fontWeight = FontWeight.Black, color = Dsd.Ink)
                    }
                }
                o.challengeCompleted?.let { c ->
                    Spacer(Modifier.height(10.dp))
                    BrutalCard(Modifier.fillMaxWidth(), color = Dsd.GreenSoft) {
                        Text("🎯 ¡Desafío del día completado: ${c.title}!", Modifier.padding(12.dp), fontWeight = FontWeight.Black, color = Dsd.Ink)
                    }
                }
                o.newAchievements.forEach { a ->
                    Spacer(Modifier.height(10.dp))
                    BrutalCard(Modifier.fillMaxWidth(), color = Dsd.BlueSoft) {
                        Column(Modifier.padding(12.dp)) {
                            Text("${a.emoji} Logro desbloqueado: ${a.title}", fontWeight = FontWeight.Black, color = Dsd.Ink)
                            Text(a.description, fontSize = 13.sp, color = Dsd.Ink)
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            Text("Semilla de la partida: ${vm.lastSeed}", fontSize = 12.sp, color = Dsd.Grey)
            Spacer(Modifier.height(14.dp))
            BrutalButton("JUGAR OTRA VEZ", { vm.playAgain() }, Modifier.fillMaxWidth(), color = Dsd.Red, fontSize = 24.sp, height = 70.dp)
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BrutalButton("INICIO", { vm.quitGame() }, Modifier.weight(1f), color = Dsd.Blue, fontSize = 18.sp, height = 58.dp)
                BrutalButton(
                    "ESTADÍSTICAS",
                    {
                        vm.quitGame()
                        vm.navigate(Screen.Stats)
                    },
                    Modifier.weight(1f),
                    color = Dsd.Paper,
                    contentColor = Dsd.Ink,
                    fontSize = 15.sp,
                    height = 58.dp,
                )
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
