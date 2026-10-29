package app.dilosindecirlo.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.dilosindecirlo.engine.Difficulty
import app.dilosindecirlo.engine.GameConfig
import app.dilosindecirlo.engine.GameMode
import app.dilosindecirlo.engine.PartyRules
import app.dilosindecirlo.engine.PoolBuilder
import app.dilosindecirlo.ui.AppViewModel
import app.dilosindecirlo.ui.components.BrutalButton
import app.dilosindecirlo.ui.components.BrutalCard
import app.dilosindecirlo.ui.components.Chip
import app.dilosindecirlo.ui.components.ChoiceChips
import app.dilosindecirlo.ui.components.ScreenScaffold
import app.dilosindecirlo.ui.components.SectionTitle
import app.dilosindecirlo.ui.components.StepperRow
import app.dilosindecirlo.ui.components.SwitchRow
import app.dilosindecirlo.ui.theme.Dsd

private val teamDefaults = listOf("Equipo Azul", "Equipo Rojo", "Equipo Verde", "Equipo Amarillo", "Equipo Naranja", "Equipo Morado")

private fun defaultName(mode: GameMode, index: Int): String =
    if (mode == GameMode.FFA || mode == GameMode.SURVIVAL) "Jugador ${index + 1}" else teamDefaults.getOrElse(index) { "Equipo ${index + 1}" }

private fun resize(config: GameConfig, count: Int): GameConfig =
    config.copy(teamNames = List(count) { config.teamNames.getOrNull(it) ?: defaultName(config.mode, it) })

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SetupScreen(vm: AppViewModel, mode: GameMode) {
    val c = vm.setup
    val isFfa = mode == GameMode.FFA
    val isSurvival = mode == GameMode.SURVIVAL
    val singleRound = c.singleRound
    val participantsLabel = if (isFfa || isSurvival) "Jugadores" else "Equipos"
    val minParticipants = if (isFfa) 2 else if (isSurvival || mode == GameMode.TIME_ATTACK) 1 else 2
    val maxParticipants = if (isFfa) 8 else 6
    val pool = remember(c.categoryIds, c.difficulty, vm.settings.hiddenCategories, vm.customCards) {
        PoolBuilder.build(vm.allCards, c, vm.settings.hiddenCategories)
    }
    val visibleCategories = vm.catalog.categories.filter { it.id !in vm.settings.hiddenCategories }

    ScreenScaffold(title = "${mode.emoji} ${mode.title}", onBack = { vm.back() }) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp),
        ) {
            Text(mode.tagline, color = Dsd.Grey, fontSize = 14.sp)

            if (!singleRound || mode == GameMode.TIME_ATTACK) {
                SectionTitle("Configuración rápida")
                ChoiceChips(
                    options = listOf(0 to "⚡ Rápida", 1 to "Estándar", 2 to "Larga"),
                    selected = when {
                        c.turnSeconds == 30 && c.rounds == 2 -> 0
                        c.turnSeconds == 60 && c.rounds == 3 -> 1
                        c.turnSeconds == 90 && c.rounds == 5 -> 2
                        else -> -1
                    },
                    onSelect = { preset ->
                        vm.updateSetup {
                            when (preset) {
                                0 -> it.copy(turnSeconds = 30, rounds = if (singleRound) 1 else 2, targetPoints = 0)
                                1 -> it.copy(turnSeconds = 60, rounds = if (singleRound) 1 else 3, targetPoints = 0)
                                else -> it.copy(turnSeconds = 90, rounds = if (singleRound) 1 else 5, targetPoints = 0)
                            }
                        }
                    },
                )
            }

            SectionTitle(participantsLabel)
            StepperRow(
                label = "Número de ${participantsLabel.lowercase()}",
                valueText = "${c.teamCount}",
                onMinus = { vm.updateSetup { resize(it, (it.teamCount - 1).coerceAtLeast(minParticipants)) } },
                onPlus = { vm.updateSetup { resize(it, (it.teamCount + 1).coerceAtMost(maxParticipants)) } },
                minusEnabled = c.teamCount > minParticipants,
                plusEnabled = c.teamCount < maxParticipants,
            )
            c.teamNames.forEachIndexed { index, name ->
                OutlinedTextField(
                    value = name,
                    onValueChange = { text ->
                        vm.updateSetup { cfg -> cfg.copy(teamNames = cfg.teamNames.mapIndexed { i, n -> if (i == index) text.take(20) else n }) }
                    },
                    label = { Text(if (isFfa || isSurvival) "Nombre del jugador ${index + 1}" else "Nombre del equipo ${index + 1}") },
                    singleLine = true,
                    colors = fieldColors(),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                )
            }
            if (!isFfa && !isSurvival) {
                StepperRow(
                    label = "Jugadores por equipo",
                    hint = "El que describe va rotando en cada turno",
                    valueText = "${c.playersPerTeam}",
                    onMinus = { vm.updateSetup { it.copy(playersPerTeam = (it.playersPerTeam - 1).coerceAtLeast(1)) } },
                    onPlus = { vm.updateSetup { it.copy(playersPerTeam = (it.playersPerTeam + 1).coerceAtMost(6)) } },
                )
            }

            SectionTitle("Tiempo y rondas")
            StepperRow(
                label = if (isSurvival) "Tiempo inicial" else "Duración del turno",
                valueText = "${c.turnSeconds} s",
                onMinus = { vm.updateSetup { it.copy(turnSeconds = (it.turnSeconds - 5).coerceAtLeast(15)) } },
                onPlus = { vm.updateSetup { it.copy(turnSeconds = (it.turnSeconds + 5).coerceAtMost(180)) } },
            )
            if (!singleRound) {
                StepperRow(
                    label = "Número de rondas",
                    hint = "En cada ronda juega cada ${if (isFfa) "jugador" else "equipo"} una vez",
                    valueText = "${c.rounds}",
                    onMinus = { vm.updateSetup { it.copy(rounds = (it.rounds - 1).coerceAtLeast(1)) } },
                    onPlus = { vm.updateSetup { it.copy(rounds = (it.rounds + 1).coerceAtMost(10)) } },
                )
                Text("Objetivo de puntos", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Dsd.Ink, modifier = Modifier.padding(top = 8.dp))
                Text("Si alguien lo alcanza, la partida termina al acabar la ronda.", fontSize = 12.sp, color = Dsd.Grey)
                Spacer(Modifier.height(6.dp))
                ChoiceChips(
                    options = listOf(0 to "Sin objetivo", 10 to "10", 15 to "15", 20 to "20", 30 to "30", 50 to "50"),
                    selected = c.targetPoints,
                    onSelect = { v -> vm.updateSetup { it.copy(targetPoints = v) } },
                )
            }
            if (isSurvival) {
                StepperRow(
                    label = "Vidas",
                    hint = "Cada infracción cuesta una vida",
                    valueText = "${c.lives}",
                    onMinus = { vm.updateSetup { it.copy(lives = (it.lives - 1).coerceAtLeast(1)) } },
                    onPlus = { vm.updateSetup { it.copy(lives = (it.lives + 1).coerceAtMost(5)) } },
                )
                StepperRow(
                    label = "Segundos extra por acierto",
                    valueText = "+${c.bonusSeconds} s",
                    onMinus = { vm.updateSetup { it.copy(bonusSeconds = (it.bonusSeconds - 1).coerceAtLeast(0)) } },
                    onPlus = { vm.updateSetup { it.copy(bonusSeconds = (it.bonusSeconds + 1).coerceAtMost(8)) } },
                )
                Text(
                    "La dificultad sube a medida que aciertas: empiezan cartas fáciles y terminan las más difíciles.",
                    fontSize = 12.sp,
                    color = Dsd.Grey,
                )
            }
            Text("Cartas por turno", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Dsd.Ink, modifier = Modifier.padding(top = 10.dp))
            Spacer(Modifier.height(6.dp))
            ChoiceChips(
                options = listOf(0 to "Sin límite", 5 to "5", 10 to "10", 15 to "15", 20 to "20"),
                selected = c.cardsPerTurn,
                onSelect = { v -> vm.updateSetup { it.copy(cardsPerTurn = v) } },
            )

            SectionTitle("Puntuación")
            StepperRow(
                label = "Penalización por palabra prohibida",
                hint = "0 = sin penalización",
                valueText = if (c.rules.violationPenalty == 0) "Ninguna" else "−${c.rules.violationPenalty}",
                onMinus = { vm.updateSetup { it.copy(rules = it.rules.copy(violationPenalty = (it.rules.violationPenalty - 1).coerceAtLeast(0))) } },
                onPlus = { vm.updateSetup { it.copy(rules = it.rules.copy(violationPenalty = (it.rules.violationPenalty + 1).coerceAtMost(3))) } },
            )
            StepperRow(
                label = "Penalización por pasar",
                valueText = if (c.rules.passPenalty == 0) "Ninguna" else "−${c.rules.passPenalty}",
                onMinus = { vm.updateSetup { it.copy(rules = it.rules.copy(passPenalty = (it.rules.passPenalty - 1).coerceAtLeast(0))) } },
                onPlus = { vm.updateSetup { it.copy(rules = it.rules.copy(passPenalty = (it.rules.passPenalty + 1).coerceAtMost(2))) } },
                minusEnabled = c.allowPass,
                plusEnabled = c.allowPass,
            )
            SwitchRow(
                "Permitir pasar cartas",
                c.allowPass,
                { v -> vm.updateSetup { it.copy(allowPass = v) } },
            )
            SwitchRow(
                "Los puntos pueden bajar de cero",
                c.rules.allowNegative,
                { v -> vm.updateSetup { it.copy(rules = it.rules.copy(allowNegative = v)) } },
                description = "Si lo apagas, las penalizaciones nunca dejan a un equipo por debajo de 0.",
            )

            SectionTitle("Cartas")
            Text("Dificultad", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Dsd.Ink)
            Spacer(Modifier.height(6.dp))
            ChoiceChips(
                options = Difficulty.entries.map { it to it.label },
                selected = c.difficulty,
                onSelect = { d -> vm.updateSetup { it.copy(difficulty = d) } },
            )
            Text("Categorías", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Dsd.Ink, modifier = Modifier.padding(top = 12.dp))
            if (mode == GameMode.CATEGORIES) Text("Elige al menos una.", fontSize = 12.sp, color = Dsd.Grey)
            Spacer(Modifier.height(6.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (mode != GameMode.CATEGORIES) {
                    Chip("Todas", c.categoryIds.isEmpty(), { vm.updateSetup { it.copy(categoryIds = emptySet()) } })
                }
                visibleCategories.forEach { cat ->
                    Chip(
                        "${cat.emoji} ${cat.name}",
                        cat.id in c.categoryIds,
                        {
                            vm.updateSetup {
                                it.copy(categoryIds = if (cat.id in it.categoryIds) it.categoryIds - cat.id else it.categoryIds + cat.id)
                            }
                        },
                    )
                }
            }
            Text(
                "Cartas disponibles con estos filtros: ${pool.cards.size}",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = if (pool.cards.size < 8) Dsd.Red else Dsd.Grey,
                modifier = Modifier.padding(top = 10.dp),
            )

            if (mode == GameMode.PARTY || c.partyRules) {
                SectionTitle("Reglas del modo fiesta")
                Text("Antes de cada turno se sortea una de estas reglas y se muestra a todos:", fontSize = 13.sp, color = Dsd.Grey)
                Spacer(Modifier.height(8.dp))
                BrutalCard(Modifier.fillMaxWidth(), color = Dsd.Yellow.copy(alpha = 0.45f)) {
                    Column(Modifier.padding(14.dp)) {
                        PartyRules.all.forEach { rule ->
                            Text(rule.title, fontWeight = FontWeight.Black, fontSize = 15.sp, color = Dsd.Ink)
                            Text(rule.description, fontSize = 13.sp, color = Dsd.Ink, modifier = Modifier.padding(bottom = 8.dp))
                        }
                    }
                }
            }

            SectionTitle("Avanzado")
            OutlinedTextField(
                value = vm.seedText,
                onValueChange = { text -> vm.seedText = text.filter { it.isDigit() }.take(18) },
                label = { Text("Semilla (opcional)") },
                supportingText = { Text("Con la misma semilla y ajustes se repite el mismo orden de cartas. Útil para probar.") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = fieldColors(),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(18.dp))
        }

        BrutalButton(
            "¡EMPEZAR!",
            onClick = {
                val names = c.teamNames.mapIndexed { i, n -> n.trim().ifEmpty { defaultName(mode, i) } }
                if (names.map { it.lowercase() }.toSet().size != names.size) {
                    vm.showMessage("Los nombres no pueden repetirse.")
                } else if (mode == GameMode.CATEGORIES && c.categoryIds.isEmpty()) {
                    vm.showMessage("Elige al menos una categoría.")
                } else {
                    val partyOn = mode == GameMode.PARTY || c.partyRules
                    vm.startGame(c.copy(teamNames = names, partyRules = partyOn))
                }
            },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp),
            color = Dsd.Red,
            fontSize = 26.sp,
            height = 70.dp,
        )
    }
}

@Composable
internal fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Dsd.Ink,
    unfocusedBorderColor = Dsd.Ink,
    focusedLabelColor = Dsd.Blue,
    unfocusedLabelColor = Dsd.Grey,
    cursorColor = Dsd.Blue,
    focusedContainerColor = Dsd.Paper,
    unfocusedContainerColor = Dsd.Paper,
    focusedTextColor = Dsd.Ink,
    unfocusedTextColor = Dsd.Ink,
)
