package fr.geoking.julius.ui.v3

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.geoking.julius.api.github.GitHubWorkflowRun

private data class CiState(
    val workflowName: String?,
    val latestConclusion: String?,
    val inProgress: Boolean,
    val sinceSuccess: Int,
    val runs: List<GitHubWorkflowRun>,
    val error: String?,
)

@Composable
fun GitCiV3Screen(
    deps: V3Deps,
    owner: String,
    repo: String,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val settings by deps.settingsManager.settings.collectAsState()
    val token = settings.githubApiKey

    var refreshTick by remember { mutableStateOf(0) }
    val state by produceState<CiState?>(initialValue = null, owner, repo, token, refreshTick) {
        value = runCatching {
            val wf = deps.buildRepository.resolveWorkflowId(token, owner, repo)
                ?: return@runCatching CiState(null, null, false, 0, emptyList(), "Aucun workflow")
            val summary = deps.buildRepository.loadSummary(token, owner, repo, wf.first, wf.second)
            val runs = deps.buildRepository.runsSinceLastSuccess(token, owner, repo, wf.first)
            CiState(summary.workflowName, summary.latestConclusion, summary.isInProgress, summary.runsSinceLastSuccess, runs, null)
        }.getOrElse { CiState(null, null, false, 0, emptyList(), "Erreur GitHub (token / accès)") }
    }

    var showWorkflowPicker by remember { mutableStateOf(false) }
    var workflows by remember { mutableStateOf<List<fr.geoking.julius.api.github.GitHubWorkflow>>(emptyList()) }
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Column(Modifier.padding(horizontal = 18.dp).padding(top = 16.dp)) {
            Text("$owner/$repo", color = V3.Muted, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
            val s = state
            if (s == null) {
                Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = V3.Accent) }
                return@Column
            }
            if (s.error != null) {
                EmptyHint(s.error)
                return@Column
            }

            // headline
            Row(
                Modifier.fillMaxWidth().padding(top = 22.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Workflow".uppercase(),
                    color = V3.Muted,
                    fontSize = 12.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                    letterSpacing = 1.4.sp,
                )
                AssistChip(
                    onClick = {
                        scope.launch {
                            workflows = deps.buildRepository.listWorkflows(token, owner, repo)
                            showWorkflowPicker = true
                        }
                    },
                    label = { Text("Changer", fontSize = 11.sp) },
                    trailingIcon = { Icon(Icons.Filled.KeyboardArrowDown, null, Modifier.size(14.dp)) },
                    colors = AssistChipDefaults.assistChipColors(labelColor = V3.Accent),
                    border = androidx.compose.foundation.BorderStroke(1.dp, V3.Accent.copy(alpha = 0.2f))
                )
            }

            V3Card {
                Row(Modifier.fillMaxWidth().padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(s.workflowName ?: "—", color = V3.Fg, fontSize = 15.sp)
                        Text(
                            if (s.inProgress) "Exécution en cours" else "Dernier statut · ${s.latestConclusion ?: "—"}",
                            color = V3.Muted, fontSize = 12.sp, fontFamily = FontFamily.Monospace,
                        )
                    }
                    val v = workflowStatusVisual(if (s.inProgress) "in_progress" else "completed", s.latestConclusion)
                    StatusPill(v)
                }
            }

            SectionLabel("Runs depuis le dernier succès", "${s.sinceSuccess}")
            if (s.runs.isEmpty()) {
                EmptyHint("Tout est à jour.")
            } else {
                V3Card {
                    s.runs.forEachIndexed { i, run ->
                        if (i > 0) HorizontalDivider(color = V3.Border)
                        val v = workflowStatusVisual(run.status, run.conclusion)
                        Row(
                            Modifier.fillMaxWidth()
                                .clickable { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(run.htmlUrl))) } }
                                .padding(horizontal = 15.dp, vertical = 13.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(run.name ?: "Run #${run.runNumber}", color = V3.Fg, fontSize = 14.sp)
                                Text("#${run.runNumber}", color = V3.Muted, fontSize = 11.5.sp, fontFamily = FontFamily.Monospace)
                            }
                            StatusPill(v)
                        }
                    }
                }
            }
            Spacer(Modifier.height(96.dp))
        }
    }

    if (showWorkflowPicker) {
        val savedId = deps.buildRepository.getSavedWorkflowId(owner, repo)
        AlertDialog(
            onDismissRequest = { showWorkflowPicker = false },
            title = { Text("Sélectionner un workflow") },
            text = {
                Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                    workflows.forEach { wf ->
                        Row(
                            Modifier.fillMaxWidth()
                                .clickable {
                                    deps.buildRepository.saveWorkflowId(owner, repo, wf.id)
                                    showWorkflowPicker = false
                                    refreshTick++
                                }
                                .padding(vertical = 12.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = wf.id == savedId, onClick = null)
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(wf.name, color = V3.Fg, fontSize = 14.sp)
                                Text(wf.path, color = V3.Muted, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showWorkflowPicker = false }) { Text("Fermer") } },
            containerColor = V3.Surface,
            titleContentColor = V3.Fg,
            textContentColor = V3.Muted,
        )
    }
}
