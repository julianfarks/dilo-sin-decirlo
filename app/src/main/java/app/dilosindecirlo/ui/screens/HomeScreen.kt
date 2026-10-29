package app.dilosindecirlo.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.dilosindecirlo.engine.GameMode
import app.dilosindecirlo.ui.AppViewModel
import app.dilosindecirlo.ui.Screen
import app.dilosindecirlo.ui.components.AnimatedBackground
import app.dilosindecirlo.ui.components.BrutalButton
import app.dilosindecirlo.ui.components.BrutalCard
import app.dilosindecirlo.ui.components.Logo
import app.dilosindecirlo.ui.theme.Dsd

@Composable
fun HomeScreen(vm: AppViewModel) {
    Box(Modifier.fillMaxSize().background(Dsd.Cream)) {
        AnimatedBackground(enabled = vm.settings.animations)
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(14.dp))
            Logo()
            Spacer(Modifier.height(18.dp))

            vm.loadError?.let { error ->
                BrutalCard(Modifier.fillMaxWidth(), color = Dsd.RedSoft) {
                    Text(error, Modifier.padding(14.dp), color = Dsd.Ink, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(12.dp))
            }

            ChallengeCard(vm)
            Spacer(Modifier.height(16.dp))

            BrutalButton(
                "¡JUGAR!",
                { vm.openSetup(GameMode.CLASSIC) },
                Modifier.fillMaxWidth(),
                color = Dsd.Red,
                fontSize = 30.sp,
                height = 78.dp,
            )
            Spacer(Modifier.height(12.dp))
            BrutalButton(
                "⚡ PARTIDA RÁPIDA",
                { vm.startQuickGame() },
                Modifier.fillMaxWidth(),
                color = Dsd.Blue,
                fontSize = 20.sp,
                height = 62.dp,
            )
            Spacer(Modifier.height(18.dp))

            TileRow(
                Tile("👥", "Por equipos", "Turnos por equipo") { vm.openSetup(GameMode.CLASSIC) },
                Tile("🥊", "Todos contra todos", "Cada quien por su cuenta") { vm.openSetup(GameMode.FFA) },
            )
            TileRow(
                Tile("⏱️", "Contrarreloj", "Máximos aciertos") { vm.openSetup(GameMode.TIME_ATTACK) },
                Tile("❤️", "Supervivencia", "Récord: ${vm.stats.bestSurvival}") { vm.openSetup(GameMode.SURVIVAL) },
            )
            TileRow(
                Tile("🎉", "Fiesta", "Reglas locas") { vm.openSetup(GameMode.PARTY) },
                Tile("🔥", "Difícil", "Solo para valientes") { vm.openSetup(GameMode.HARD) },
            )
            TileRow(
                Tile("🎲", "Aleatorio", "Todo mezclado") { vm.openSetup(GameMode.RANDOM) },
                Tile("🗂️", "Categorías", "Elige el tema") { vm.navigate(Screen.Categories) },
            )
            TileRow(
                Tile("📊", "Estadísticas", "Récords y logros") { vm.navigate(Screen.Stats) },
                Tile("✏️", "Mis cartas", "${vm.customCards.size} creadas") { vm.navigate(Screen.CustomCards) },
            )
            TileRow(
                Tile("⚙️", "Ajustes", "Sonido y más") { vm.navigate(Screen.Settings) },
                Tile("❓", "Cómo jugar", "Las reglas") { vm.navigate(Screen.HowTo) },
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "${vm.allCards.size} cartas · Funciona sin Internet",
                color = Dsd.Grey,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun ChallengeCard(vm: AppViewModel) {
    val challenge = vm.todayChallenge
    val done = vm.todayChallengeDone
    BrutalCard(Modifier.fillMaxWidth(), color = if (done) Dsd.GreenSoft else Dsd.Yellow.copy(alpha = 0.55f)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (done) "✅" else challenge.emoji, fontSize = 32.sp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (done) "Desafío de hoy completado" else "Desafío de hoy: ${challenge.title}",
                    fontWeight = FontWeight.Black,
                    fontSize = 16.sp,
                    color = Dsd.Ink,
                )
                Text(challenge.description, fontSize = 13.sp, color = Dsd.Ink)
            }
        }
    }
}

private data class Tile(val emoji: String, val title: String, val subtitle: String, val onClick: () -> Unit)

@Composable
private fun TileRow(left: Tile, right: Tile) {
    Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        TileCard(left, Modifier.weight(1f))
        TileCard(right, Modifier.weight(1f))
    }
}

@Composable
private fun TileCard(tile: Tile, modifier: Modifier) {
    BrutalCard(modifier.height(104.dp), onClick = tile.onClick) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp).fillMaxSize(), verticalArrangement = Arrangement.Center) {
            Text(tile.emoji, fontSize = 26.sp)
            Text(tile.title, fontWeight = FontWeight.Black, fontSize = 16.sp, color = Dsd.Ink, lineHeight = 18.sp)
            Text(tile.subtitle, fontSize = 12.sp, color = Dsd.Grey, textAlign = TextAlign.Start)
        }
    }
}
