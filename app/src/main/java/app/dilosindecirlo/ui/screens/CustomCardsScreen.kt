package app.dilosindecirlo.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.dilosindecirlo.ui.AppViewModel
import app.dilosindecirlo.ui.ImportReport
import app.dilosindecirlo.ui.Screen
import app.dilosindecirlo.ui.components.BrutalButton
import app.dilosindecirlo.ui.components.BrutalCard
import app.dilosindecirlo.ui.components.ChoiceChips
import app.dilosindecirlo.ui.components.ScreenScaffold
import app.dilosindecirlo.ui.components.SectionTitle
import app.dilosindecirlo.ui.theme.Dsd

@Composable
fun CustomCardsScreen(vm: AppViewModel) {
    val context = LocalContext.current
    var report by remember { mutableStateOf<ImportReport?>(null) }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                val text = context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                report = if (text == null) ImportReport(0, listOf("No se pudo leer el archivo.")) else vm.importCustomJson(text)
            } catch (e: Exception) {
                report = ImportReport(0, listOf("No se pudo leer el archivo."))
            }
        }
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.bufferedWriter(Charsets.UTF_8)?.use { it.write(vm.exportCustomJson()) }
                vm.showMessage("Cartas exportadas")
            } catch (e: Exception) {
                vm.showMessage("No se pudo guardar el archivo")
            }
        }
    }

    ScreenScaffold("✏️ Mis cartas", onBack = { vm.back() }) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp)) {
            Text(
                "Crea tus propias cartas con 5 a 10 palabras prohibidas. Se guardan en este teléfono.",
                fontSize = 14.sp,
                color = Dsd.Grey,
            )
            Spacer(Modifier.height(10.dp))
            BrutalButton("＋ NUEVA CARTA", { vm.navigate(Screen.CardEditor(null)) }, Modifier.fillMaxWidth(), color = Dsd.Red, fontSize = 18.sp, height = 56.dp)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BrutalButton(
                    "Importar",
                    { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
                    Modifier.weight(1f), color = Dsd.Paper, contentColor = Dsd.Ink, fontSize = 16.sp, height = 50.dp,
                )
                BrutalButton(
                    "Exportar",
                    { if (vm.customCards.isEmpty()) vm.showMessage("Aún no tienes cartas propias") else exportLauncher.launch("mis-cartas-dilo-sin-decirlo.json") },
                    Modifier.weight(1f), color = Dsd.Paper, contentColor = Dsd.Ink, fontSize = 16.sp, height = 50.dp,
                )
            }
            SectionTitle("Tus cartas (${vm.customCards.size})")
            if (vm.customCards.isEmpty()) Text("Todavía no has creado ninguna.", color = Dsd.Grey, fontSize = 13.sp)
            vm.customCards.forEach { card ->
                BrutalCard(
                    Modifier.fillMaxWidth().padding(vertical = 5.dp),
                    cornerRadius = 14.dp, shadow = 3.dp,
                    onClick = { vm.navigate(Screen.CardEditor(card.id)) },
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(card.word.uppercase(), fontWeight = FontWeight.Black, fontSize = 17.sp, color = Dsd.Ink)
                        Text(card.forbidden.joinToString(", "), fontSize = 12.sp, color = Dsd.Grey)
                        Text(vm.categoryName(card.categoryId), fontSize = 11.sp, color = Dsd.Blue)
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    report?.let { r ->
        AlertDialog(
            onDismissRequest = { report = null },
            containerColor = Dsd.Paper,
            title = { Text("Importación", fontWeight = FontWeight.Black) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text("Cartas añadidas: ${r.added}")
                    if (r.skipped.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text("Omitidas (${r.skipped.size}):", fontWeight = FontWeight.Bold)
                        r.skipped.take(15).forEach { Text("• $it", fontSize = 12.sp) }
                        if (r.skipped.size > 15) Text("… y ${r.skipped.size - 15} más", fontSize = 12.sp)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { report = null }) { Text("Aceptar", color = Dsd.Blue, fontWeight = FontWeight.Black) } },
        )
    }
}

@Composable
fun CardEditorScreen(vm: AppViewModel, cardId: String?) {
    val existing = remember(cardId) { cardId?.let { id -> vm.customCards.firstOrNull { it.id == id } } }
    var word by remember { mutableStateOf(existing?.word ?: "") }
    val forbidden = remember {
        mutableStateListOf<String>().apply {
            addAll(existing?.forbidden ?: emptyList())
            while (size < 5) add("")
        }
    }
    var category by remember { mutableStateOf(existing?.categoryId ?: vm.catalog.categories.firstOrNull()?.id ?: "cultura") }
    var difficulty by remember { mutableStateOf(existing?.difficulty ?: 2) }
    var errors by remember { mutableStateOf(listOf<String>()) }
    var confirmDelete by remember { mutableStateOf(false) }

    ScreenScaffold(if (existing == null) "Nueva carta" else "Editar carta", onBack = { vm.back() }) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp)) {
            OutlinedTextField(
                value = word, onValueChange = { word = it }, singleLine = true,
                label = { Text("Palabra secreta") }, colors = fieldColors(),
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            )
            SectionTitle("Palabras prohibidas (${forbidden.count { it.isNotBlank() }}/10)")
            forbidden.forEachIndexed { i, value ->
                OutlinedTextField(
                    value = value, onValueChange = { forbidden[i] = it }, singleLine = true,
                    label = { Text("Prohibida ${i + 1}") }, colors = fieldColors(),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                )
            }
            if (forbidden.size < 10) {
                BrutalButton("＋ Otra palabra", { forbidden.add("") }, Modifier.fillMaxWidth().padding(top = 6.dp),
                    color = Dsd.Paper, contentColor = Dsd.Ink, fontSize = 15.sp, height = 46.dp)
            }
            SectionTitle("Categoría")
            ChoiceChips(vm.catalog.categories.map { it.id to "${it.emoji} ${it.name}" }, category, { category = it })
            SectionTitle("Dificultad")
            ChoiceChips(listOf(1 to "Fácil", 2 to "Normal", 3 to "Difícil"), difficulty, { difficulty = it })

            if (errors.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                BrutalCard(Modifier.fillMaxWidth(), color = Dsd.RedSoft, cornerRadius = 14.dp, shadow = 3.dp) {
                    Column(Modifier.padding(12.dp)) { errors.forEach { Text("• $it", fontSize = 13.sp, color = Dsd.Ink) } }
                }
            }
            Spacer(Modifier.height(14.dp))
            BrutalButton("GUARDAR", {
                val list = forbidden.map { it.trim() }.filter { it.isNotEmpty() }
                errors = vm.saveCustomCard(existing?.id, word, list, category, difficulty)
                if (errors.isEmpty()) { vm.showMessage("Carta guardada"); vm.back() }
            }, Modifier.fillMaxWidth(), color = Dsd.Red, fontSize = 20.sp, height = 60.dp)
            if (existing != null) {
                Spacer(Modifier.height(10.dp))
                BrutalButton("Eliminar carta", { confirmDelete = true }, Modifier.fillMaxWidth(),
                    color = Dsd.Paper, contentColor = Dsd.Red, fontSize = 16.sp, height = 50.dp)
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (confirmDelete && existing != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = Dsd.Paper,
            title = { Text("¿Eliminar esta carta?", fontWeight = FontWeight.Black) },
            text = { Text(existing.word.uppercase()) },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; vm.deleteCustomCard(existing.id); vm.back() }) {
                    Text("Eliminar", color = Dsd.Red, fontWeight = FontWeight.Black)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar", color = Dsd.Blue) } },
        )
    }
}
