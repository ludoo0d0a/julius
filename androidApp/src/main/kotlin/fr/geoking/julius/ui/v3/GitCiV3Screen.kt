package fr.geoking.julius.ui.v3

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
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

private fun runVisual(run: GitHubWorkflowRun): StatusVisual {
    val concl = run.conclusion?.lowercase()
    val st = run.status?.lowercase()
    return when {
        concl == "success" -> StatusVisual("Succès", "success", V3.Success)
        concl in listOf("failure", "timed_out", "startup_failure") -> StatusVisual("Échec", concl ?: "failure", V3.Danger)
        concl == "cancelled" -> StatusVisual("Annulé", "cancelled", V3.Muted)
        st in listOf("in_progress", "queued", "pending", "waiting", "requested") || concl == null ->
            StatusVisual("En cours", st ?: "in_progress", V3.Warn, pulse = true)
        else -> StatusVisual(concl ?: st ?: "—", concl ?: "", V3.Muted)
    }
}

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

    val state by produceState<CiState?>(initialValue = null, owner, repo, token) {
        value = runCatching {
            val wf = deps.buildRepository.resolveWorkflowId(token, owner, repo)
                ?: return@runCatching CiState(null, null, false, 0, emptyList(), "Aucun workflow")
            val summary = deps.buildRepository.loadSummary(token, owner, repo, wf.first, wf.second)
            val runs = deps.buildRepository.runsSinceLastSuccess(token, owner, repo, wf.first)
            CiState(summary.workflowName, summary.latestConclusion, summary.isInProgress, summary.runsSinceLastSuccess, runs, null)
        }.getOrElse { CiState(null, null, false, 0, emptyList(), "Erreur GitHub (token / accès)") }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Retour", tint = V3.Fg) }
            Text("Git & CI", color = V3.Fg, fontSize = 22.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
        }
        Text("$owner/$repo", color = V3.Muted, fontSize = 13.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(horizontal = 18.dp))

        Column(Modifier.padding(horizontal = 18.dp)) {
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
            SectionLabel("Workflow", s.workflowName ?: "—")
            V3Card {
                Row(Modifier.fillMaxWidth().padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(s.workflowName ?: "—", color = V3.Fg, fontSize = 15.sp)
                        Text(
                            if (s.inProgress) "Exécution en cours" else "Dernier statut · ${s.latestConclusion ?: "—"}",
                            color = V3.Muted, fontSize = 12.sp, fontFamily = FontFamily.Monospace,
                        )
                    }
                    val v = when {
                        s.inProgress -> StatusVisual("En cours", "", V3.Warn, pulse = true)
                        s.latestConclusion?.lowercase() == "success" -> StatusVisual("Succès", "", V3.Success)
                        s.latestConclusion == null -> StatusVisual("—", "", V3.Muted)
                        else -> StatusVisual("Échec", "", V3.Danger)
                    }
                    StatusPill(v)
                }
            }

            SectionLabel("Runs depuis le dernier succès", "${s.sinceSuccess}")
            if (s.runs.isEmpty()) {
                EmptyHint("Tout est à jour — dernier run au vert.")
            } else {
                V3Card {
                    s.runs.forEachIndexed { i, run ->
                        if (i > 0) HorizontalDivider(color = V3.Border)
                        val v = runVisual(run)
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
}
