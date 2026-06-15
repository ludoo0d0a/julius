package fr.geoking.julius.ui.v3

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.geoking.julius.repository.JulesRepository
import kotlinx.coroutines.launch

private fun buildResolved(content: String, conflicts: List<JulesRepository.Conflict>, choices: Map<Int, String>): String {
    val sb = StringBuilder(content)
    conflicts.sortedByDescending { it.startIndex }.forEach { c ->
        val pick = if (choices[c.startIndex] == "incoming") c.incoming else c.mine
        if (c.startIndex in 0..sb.length && c.endIndex in c.startIndex..sb.length) {
            sb.replace(c.startIndex, c.endIndex, pick)
        }
    }
    return sb.toString()
}

@Composable
fun PrConflictV3Screen(
    deps: V3Deps,
    prUrl: String,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val settings by deps.settingsManager.settings.collectAsState()
    val token = settings.githubApiKey

    var files by remember { mutableStateOf<List<String>?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableStateOf(0) }
    LaunchedEffect(prUrl, token, reload) {
        deps.julesRepository.getConflictingFiles(token, prUrl)
            .onSuccess { files = it; loadError = null }
            .onFailure { files = emptyList(); loadError = "Impossible de lister les conflits." }
    }

    var selected by remember { mutableStateOf<String?>(null) }
    var content by remember { mutableStateOf<String?>(null) }
    var sha by remember { mutableStateOf<String?>(null) }
    var conflicts by remember { mutableStateOf<List<JulesRepository.Conflict>>(emptyList()) }
    val choices = remember(selected) { mutableStateMapOf<Int, String>() }
    var feedback by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(selected) {
        val path = selected ?: return@LaunchedEffect
        content = null; conflicts = emptyList()
        deps.julesRepository.getFileContent(token, prUrl, path)
            .onSuccess { (c, s) -> content = c; sha = s; conflicts = deps.julesRepository.parseConflicts(c) }
            .onFailure { feedback = "Lecture impossible : $path" }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        // Handle back manually for file selection
        BackHandler(enabled = selected != null) { selected = null }

        Column(Modifier.padding(horizontal = 18.dp).padding(top = 16.dp)) {
            if (selected != null) {
                Text(selected!!, color = V3.Accent, fontSize = 14.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(bottom = 12.dp))
            }

            val f = files
            if (f == null) {
                Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = V3.Accent) }
                return@Column
            }
            if (loadError != null) { EmptyHint(loadError!!); return@Column }

            if (selected == null) {
                // file list
                if (f.isEmpty()) {
                    EmptyHint("Aucun conflit.")
                } else {
                    SectionLabel("Fichiers en conflit", "${f.size}")
                    V3Card {
                        f.forEachIndexed { i, path ->
                            if (i > 0) HorizontalDivider(color = V3.Border)
                            V3Row(
                                title = path.substringAfterLast('/'),
                                subtitle = path,
                                leadingIcon = Icons.Filled.Description,
                                leadingTint = V3.Danger,
                                onClick = { feedback = null; selected = path },
                            )
                        }
                    }
                }
            } else {
                // conflict resolution for the selected file
                if (content == null) {
                    Box(Modifier.fillMaxWidth().padding(30.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = V3.Accent) }
                } else if (conflicts.isEmpty()) {
                    EmptyHint("Aucun marqueur.")
                } else {
                    SectionLabel("Conflits", "${conflicts.size}")
                    conflicts.forEachIndexed { idx, c ->
                        val pick = choices[c.startIndex]
                        V3Card {
                            Column(Modifier.padding(14.dp)) {
                                Text("Bloc ${idx + 1}", color = V3.Muted, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                                Spacer(Modifier.height(8.dp))
                                SidePreview("Le mien", c.mine, selected = pick == "mine" || pick == null) { choices[c.startIndex] = "mine" }
                                Spacer(Modifier.height(6.dp))
                                SidePreview("L'entrant", c.incoming, selected = pick == "incoming") { choices[c.startIndex] = "incoming" }
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                    }
                    Button(
                        onClick = {
                            val path = selected ?: return@Button
                            val body = content ?: return@Button
                            val fileSha = sha ?: return@Button
                            busy = true
                            scope.launch {
                                val resolved = buildResolved(body, conflicts, choices)
                                deps.julesRepository.saveResolvedFile(token, prUrl, path, resolved, fileSha)
                                    .onSuccess { feedback = "Fichier résolu et poussé."; selected = null; reload++ }
                                    .onFailure { feedback = "Échec de l'enregistrement." }
                                busy = false
                            }
                        },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = V3.Accent, contentColor = V3.AccentInk),
                    ) { Text(if (busy) "Enregistrement…" else "Enregistrer la résolution") }
                }
            }
            feedback?.let { Spacer(Modifier.height(12.dp)); Text(it, color = V3.Accent, fontSize = 13.sp) }
            Spacer(Modifier.height(96.dp))
        }
    }
}

@Composable
private fun SidePreview(label: String, code: String, selected: Boolean, onPick: () -> Unit) {
    OutlinedButton(
        onClick = onPick,
        modifier = Modifier.fillMaxWidth(),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) V3.Accent else V3.Border),
    ) {
        Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Text(label, color = if (selected) V3.Accent else V3.Muted, fontSize = 12.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(
                code.trim().ifBlank { "(vide)" }.let { if (it.length > 240) it.take(240) + "…" else it },
                color = V3.Fg, fontSize = 12.sp, fontFamily = FontFamily.Monospace, lineHeight = 17.sp,
            )
        }
    }
}
