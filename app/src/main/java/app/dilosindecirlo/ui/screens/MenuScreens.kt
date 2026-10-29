package app.dilosindecirlo.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.dilosindecirlo.data.Card
import app.dilosindecirlo.engine.GameMode
import app.dilosindecirlo.ui.AppViewModel
import app.dilosindecirlo.ui.components.BrutalButton
import app.dilosindecirlo.ui.components.BrutalCard
import app.dilosindecirlo.ui.components.ScreenScaffold
import app.dilosindecirlo.ui.components.SectionTitle
import app.dilosindecirlo.ui.components.SwitchRow
import app.dilosindecirlo.ui.theme.Dsd
import app.dilosindecirlo.voice.VoiceAssist
import java.text.DateFormat
import java.util.Date

// ---------------------------------------------------------------------------------------------
// Categorías
// ---------------------------------------------------------------------------------------------

@Composable
fun CategoriesScreen(vm: AppViewModel) {
    var selected by remember { mutableStateOf(setOf<String>()) }
    val counts = vm.allCards.groupingBy { it.categoryId }.eachCount()
    ScreenScaffold("🗂️ Categorías", onBack = { vm.back() }) {
        Text(
            "Elige una o varias categorías para jugar, o oculta las que no quieras ver en ninguna partida.",
            Modifier.padding(horizontal = 18.dp),
            color = Dsd.Grey,
            fontSize = 14.sp,
        )
        Spacer(Modifier.height(8.dp))
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            vm.catalog.categories.forEach { cat ->
                val hidden = cat.id in vm.settings.hiddenCategories
                val isSelected = cat.id in selected && !hidden
                BrutalCard(
                    Modifier.fillMaxWidth(),
                    color = when {
                        hidden -> Dsd.CreamDark
                        isSelected -> Dsd.BlueSoft
                        else -> Dsd.Paper
                    },
                    cornerRadius = 18.dp,
                    shadow = 4.dp,
                    onClick = { if (!hidden) selected = if (isSelected) selected - cat.id else selected + cat.id },
                ) {
                    Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(cat.emoji, fontSize = 28.sp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                cat.name,
                                fontWeight = FontWeight.Black,
                                fontSize = 16.sp,
                                color = if (hidden) Dsd.Grey else Dsd.Ink,
                            )
                            Text("${counts[cat.id] ?: 0} cartas" + if (hidden) " · oculta" else "", fontSize = 12.sp, color = Dsd.Grey)
                        }
                        Text(if (isSelected) "☑" else "☐", fontSize = 26.sp, color = Dsd.Blue)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (hidden) "🙈" else "👁️",
                            fontSize = 24.sp,
                            modifier = Modifier
                                .clickable {
                                    selected = selected - cat.id
                                    vm.toggleHiddenCategory(cat.id)
                                }
                                .padding(6.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
        }
        BrutalButton(
            if (selected.isEmpty()) "ELIGE UNA CATEGORÍA" else "JUGAR CON ${selected.size} CATEGORÍA${if (selected.size == 1) "" else "S"}",
            { vm.openSetup(GameMode.CATEGORIES, selected) },
            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp),
            color = Dsd.Red,
            fontSize = 20.sp,
            height = 66.dp,
            enabled = selected.isNotEmpty(),
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Estadísticas, récords y logros
// ---------------------------------------------------------------------------------------------

@Composable
fun StatsScreen(vm: AppViewModel) {
    val s = vm.stats
    var confirmReset by remember { mutableStateOf(false) }
    val accuracy = if (s.totalHits + s.totalViolations + s.totalPasses == 0) 0 else s.totalHits * 100 / (s.totalHits + s.totalViolations + s.totalPasses)

    ScreenScaffold("📊 Estadísticas", onBack = { vm.back() }) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BigStat("Partidas", "${s.gamesPlayed}", Modifier.weight(1f))
                BigStat("Ganadas", "${s.gamesWon}", Modifier.weight(1f))
                BigStat("Aciertos", "${s.totalHits}", Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BigStat("Infracciones", "${s.totalViolations}", Modifier.weight(1f))
                BigStat("Pasadas", "${s.totalPasses}", Modifier.weight(1f))
                BigStat("Acierto", "$accuracy %", Modifier.weight(1f))
            }

            SectionTitle("Récords")
            RecordLine("Más aciertos en un turno", s.bestTurnHits)
            RecordLine("Supervivencia (aciertos)", s.bestSurvival)
            RecordLine("Contrarreloj (aciertos)", s.bestTimeAttack)

            if (s.teamBestScore.isNotEmpty()) {
                SectionTitle("Equipos y jugadores")
                s.teamGames.entries.sortedByDescending { s.teamWins[it.key] ?: 0 }.take(8).forEach { (name, games) ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                        Text(name, Modifier.weight(1f), fontWeight = FontWeight.Bold, color = Dsd.Ink)
                        Text(
                            "${s.teamWins[name] ?: 0} victorias / $games partidas · mejor ${s.teamBestScore[name] ?: 0}",
                            fontSize = 12.sp,
                            color = Dsd.Grey,
                        )
                    }
                }
            }

            val favoriteCategories = s.favoriteCategories(3)
            if (favoriteCategories.isNotEmpty()) {
                SectionTitle("Categorías favoritas")
                favoriteCategories.forEach { (id, plays) ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                        Text(vm.categoryName(id), Modifier.weight(1f), fontWeight = FontWeight.Bold, color = Dsd.Ink)
                        Text("$plays cartas jugadas", fontSize = 12.sp, color = Dsd.Grey)
                    }
                }
            }

            val topHits = s.wordHits.entries.sortedByDescending { it.value }.take(5)
            val topMisses = s.wordMisses.entries.sortedByDescending { it.value }.take(5)
            if (topHits.isNotEmpty() || topMisses.isNotEmpty()) {
                SectionTitle("Palabras")
                if (topHits.isNotEmpty()) {
                    Text("Las que más aciertas", fontWeight = FontWeight.Bold, color = Dsd.Green)
                    topHits.forEach { WordLine(vm, it.key, it.value) }
                }
                if (topMisses.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text("Las que más fallas o pasas", fontWeight = FontWeight.Bold, color = Dsd.Red)
                    topMisses.forEach { WordLine(vm, it.key, it.value) }
                }
            }

            val favoriteCards = vm.favorites.mapNotNull { vm.cardById(it) }
            SectionTitle("Cartas favoritas (${favoriteCards.size})")
            if (favoriteCards.isEmpty()) {
                Text("Toca la ⭐ en una carta durante la partida para guardarla aquí.", fontSize = 13.sp, color = Dsd.Grey)
            }
            favoriteCards.forEach { card -> FavoriteLine(vm, card) }

            SectionTitle("Últimas partidas")
            if (s.history.isEmpty()) Text("Todavía no has terminado ninguna partida.", fontSize = 13.sp, color = Dsd.Grey)
            val dateFormat = remember { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT) }
            s.history.forEach { record ->
                val modeTitle = GameMode.entries.firstOrNull { it.name == record.mode }?.title ?: record.mode
                BrutalCard(Modifier.fillMaxWidth().padding(vertical = 5.dp), cornerRadius = 14.dp, shadow = 3.dp) {
                    Column(Modifier.padding(10.dp)) {
                        Text("$modeTitle · ${dateFormat.format(Date(record.timeMillis))}", fontSize = 12.sp, color = Dsd.Grey)
                        Text(record.teams.joinToString("  ·  ") { "${it.name} ${it.score}" }, fontWeight = FontWeight.Bold, color = Dsd.Ink)
                        Text(
                            "Ganó: ${record.winners.joinToString(", ").ifEmpty { "—" }} · ${record.hits} aciertos, ${record.violations} infracciones",
                            fontSize = 12.sp,
                            color = Dsd.Grey,
                        )
                    }
                }
            }

            SectionTitle("Logros (${s.unlocked.size}/${vm.achievementList().size})")
            vm.achievementList().forEach { (achievement, unlocked) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (unlocked) achievement.emoji else "🔒", fontSize = 26.sp, modifier = Modifier.width(44.dp))
                    Column {
                        Text(achievement.title, fontWeight = FontWeight.Black, color = if (unlocked) Dsd.Ink else Dsd.Grey)
                        Text(achievement.description, fontSize = 12.sp, color = Dsd.Grey)
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            BrutalButton(
                "Borrar estadísticas",
                { confirmReset = true },
                Modifier.fillMaxWidth(),
                color = Dsd.Paper,
                contentColor = Dsd.Red,
                fontSize = 16.sp,
                height = 52.dp,
            )
            Spacer(Modifier.height(24.dp))
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            containerColor = Dsd.Paper,
            title = { Text("¿Borrar todas las estadísticas?", fontWeight = FontWeight.Black) },
            text = { Text("Se borrarán récords, historial y logros. Tus cartas personalizadas y favoritas se conservan.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmReset = false
                    vm.resetStats()
                }) { Text("Borrar", color = Dsd.Red, fontWeight = FontWeight.Black) }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancelar", color = Dsd.Blue) } },
        )
    }
}

@Composable
private fun BigStat(label: String, value: String, modifier: Modifier) {
    BrutalCard(modifier, color = Dsd.Paper, cornerRadius = 16.dp, shadow = 4.dp) {
        Column(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, fontWeight = FontWeight.Black, fontSize = 24.sp, color = Dsd.Blue)
            Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Dsd.Ink)
        }
    }
}

@Composable
private fun RecordLine(label: String, value: Int) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, Modifier.weight(1f), color = Dsd.Ink, fontWeight = FontWeight.Bold)
        Text(if (value == 0) "—" else "$value", fontWeight = FontWeight.Black, color = Dsd.Blue, fontSize = 18.sp)
    }
}

@Composable
private fun WordLine(vm: AppViewModel, cardId: String, count: Int) {
    val card = vm.cardById(cardId) ?: return
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(card.word.uppercase(), Modifier.weight(1f), color = Dsd.Ink)
        Text("$count", color = Dsd.Grey, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun FavoriteLine(vm: AppViewModel, card: Card) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(card.word.uppercase(), fontWeight = FontWeight.Black, color = Dsd.Ink)
            Text(card.forbidden.joinToString(", "), fontSize = 12.sp, color = Dsd.Grey)
        }
        Text("⭐", fontSize = 22.sp, modifier = Modifier.clickable { vm.toggleFavorite(card.id) }.padding(8.dp))
    }
}

// ---------------------------------------------------------------------------------------------
// Ajustes
// ---------------------------------------------------------------------------------------------

@Composable
fun SettingsScreen(vm: AppViewModel) {
    val s = vm.settings
    val context = LocalContext.current
    val voiceAvailable = remember { VoiceAssist.isAvailable(context) }
    ScreenScaffold("⚙️ Ajustes", onBack = { vm.back() }) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp),
        ) {
            SectionTitle("Experiencia")
            SwitchRow("Efectos de sonido", s.sound, { v -> vm.updateSettings { it.copy(sound = v) } })
            SwitchRow("Vibración", s.vibration, { v -> vm.updateSettings { it.copy(vibration = v) } })
            SwitchRow(
                "Animaciones",
                s.animations,
                { v -> vm.updateSettings { it.copy(animations = v) } },
                description = "Desactívalas si tu teléfono va justo de rendimiento.",
            )
            SwitchRow(
                "Mantener la pantalla encendida",
                s.keepScreenOn,
                { v -> vm.updateSettings { it.copy(keepScreenOn = v) } },
                description = "Solo durante las partidas.",
            )

            SectionTitle("Asistente de voz (experimental)")
            SwitchRow(
                "Detectar palabras prohibidas con la voz",
                s.voiceAssist,
                { v -> vm.updateSettings { it.copy(voiceAssist = v) } },
                description = if (voiceAvailable) {
                    "Escucha mientras describes y avisa si oye una palabra prohibida."
                } else {
                    "Este teléfono no tiene un servicio de reconocimiento de voz disponible."
                },
            )
            SwitchRow(
                "Aplicar la infracción automáticamente",
                s.voiceAutoApply,
                { v -> vm.updateSettings { it.copy(voiceAutoApply = v) } },
                description = "Si está apagado, la app te pregunta antes de restar puntos.",
            )
            BrutalCard(Modifier.fillMaxWidth().padding(top = 6.dp), color = Dsd.Yellow.copy(alpha = 0.45f), cornerRadius = 16.dp, shadow = 4.dp) {
                Text(
                    "⚠️ Limitaciones: el reconocimiento de voz lo hace el servicio del teléfono y NO es perfecto. " +
                        "Puede no oír una palabra, confundirla o necesitar Internet según el dispositivo, y a veces suena un pitido al reiniciar la escucha. " +
                        "Solo reconoce palabras completas (con plurales sencillos y sin importar acentos). " +
                        "No se usa en reglas de mímica o susurros. Úsalo como ayuda, no como árbitro: el botón manual siempre manda.",
                    Modifier.padding(12.dp),
                    fontSize = 12.sp,
                    color = Dsd.Ink,
                    lineHeight = 16.sp,
                )
            }

            SectionTitle("Categorías ocultas")
            Text(
                if (s.hiddenCategories.isEmpty()) "Ninguna. Puedes ocultar categorías desde la pantalla de Categorías."
                else s.hiddenCategories.joinToString(", ") { vm.categoryName(it) },
                fontSize = 13.sp,
                color = Dsd.Grey,
            )

            SectionTitle("Acerca de")
            Text(
                "¡Dilo sin decirlo! · versión 1.0.0\n" +
                    "${vm.catalog.cards.size} cartas integradas + ${vm.customCards.size} personalizadas.\n" +
                    "Todo funciona sin Internet, sin cuentas y sin anuncios. Tus datos se guardan solo en este teléfono. " +
                    "Los sonidos se generan en el propio dispositivo.",
                fontSize = 13.sp,
                color = Dsd.Grey,
                lineHeight = 18.sp,
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Cómo jugar
// ---------------------------------------------------------------------------------------------

@Composable
fun HowToScreen(vm: AppViewModel) {
    ScreenScaffold("❓ Cómo jugar", onBack = { vm.back() }) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp),
        ) {
            HowToBlock("🎯", "El objetivo", "Haz que tu equipo adivine la palabra secreta sin decir ninguna de las palabras prohibidas que aparecen en la carta. ¡Ni la propia palabra secreta!")
            HowToBlock("📱", "Un solo teléfono", "Pásalo al jugador que describe. Los demás no deben ver la pantalla. Al terminar el turno, el teléfono pasa al siguiente equipo.")
            HowToBlock("🕹️", "Los botones", "✔ ACERTADA: tu equipo adivinó (+1 punto).\nPASAR: saltas a otra carta (sin puntos, salvo que configures penalización).\nPALABRA PROHIBIDA: el equipo rival oyó una palabra prohibida (−1 punto). Salta a otra carta.\n⏸ PAUSA: detiene el tiempo y oculta la carta.")
            HowToBlock("⏱️", "El turno", "Cada turno dura 60 segundos por defecto. Cuando quedan 10 segundos suena un aviso. Al final verás un resumen con aciertos, infracciones, pasadas y puntos.")
            HowToBlock("🏁", "Fin de la partida", "Termina al completar las rondas. Si pusiste objetivo de puntos y alguien lo alcanza, la partida acaba al final de esa ronda para que todos jueguen los mismos turnos. Gana quien tenga más puntos.")
            HowToBlock("🎮", "Modos", "👥 Por equipos · 🥊 Todos contra todos · ⏱️ Contrarreloj (máximos aciertos en poco tiempo) · ❤️ Supervivencia (vidas y dificultad creciente, con récord) · 🗂️ Categorías · 🎲 Aleatorio · 🔥 Difícil · 🎉 Fiesta (cada turno trae una regla loca que se anuncia antes de empezar).")
            HowToBlock("🎤", "Asistente de voz", "Es opcional (Ajustes). Escucha la descripción y avisa si detecta una palabra prohibida. No es perfecto: puede equivocarse, por eso te pide confirmar antes de penalizar.")
            HowToBlock("✏️", "Tus propias cartas", "En «Mis cartas» puedes crear, editar, importar y exportar cartas con 5 a 10 palabras prohibidas.")
            HowToBlock("🔌", "Sin conexión", "No necesita Internet, cuentas ni suscripciones. Todo se guarda en tu teléfono.")
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun HowToBlock(emoji: String, title: String, body: String) {
    BrutalCard(Modifier.fillMaxWidth().padding(vertical = 7.dp), cornerRadius = 18.dp, shadow = 4.dp) {
        Row(Modifier.padding(14.dp)) {
            Text(emoji, fontSize = 28.sp, modifier = Modifier.width(44.dp))
            Column {
                Text(title, fontWeight = FontWeight.Black, fontSize = 17.sp, color = Dsd.Ink)
                Text(body, fontSize = 14.sp, color = Dsd.Ink, lineHeight = 19.sp)
            }
        }
    }
}
