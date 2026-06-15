package fr.geoking.julius.ui.v3

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.geoking.julius.api.github.parseGitHubPullRequestUrl
import fr.geoking.julius.api.jules.JulesChatItem
import fr.geoking.julius.persistence.JulesSessionEntity
import kotlinx.coroutines.launch

@Composable
fun ConversationV3Screen(
    deps: V3Deps,
    sessionId: String,
    onBack: () -> Unit,
    onOpenGitCi: (String, String) -> Unit,
    onOpenConflict: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by deps.settingsManager.settings.collectAsState()
    val token = settings.githubApiKey

    var session by remember(sessionId) { mutableStateOf<JulesSessionEntity?>(null) }
    LaunchedEffect(sessionId) {
        // 1) show the cached row immediately, 2) poll Jules/Claude + GitHub to refresh
        // prUrl/prState/prMergeable/branch so the PR & git actions are accurate, 3) re-read.
        session = deps.julesRepository.getSession(sessionId)
        runCatching { deps.julesRepository.pollSessionStatus(sessionId, token) }
        session = deps.julesRepository.getSession(sessionId)
    }

    val activitiesFlow = remember(sessionId) { deps.julesRepository.getActivities(sessionId) }
    val items by activitiesFlow.collectAsState(initial = emptyList())

    var feedback by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    val s = session
    val backend = if (sessionId.startsWith("sesn_")) "CLAUDE_CODE" else "JULES"

    Column(Modifier.fillMaxSize()) {
        // header
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, top = 6.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Retour", tint = V3.Fg) }
            Column(Modifier.weight(1f)) {
                Text(
                    s?.prTitle ?: s?.title?.ifBlank { s.prompt.take(48) } ?: backend,
                    color = V3.Fg, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Text(
                    "$backend${if (s?.prUrl.isNullOrBlank()) "" else " · PR"} · ${s?.sourceName ?: "…"}",
                    color = V3.Muted, fontSize = 11.5.sp, fontFamily = FontFamily.Monospace,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            s?.let { StatusPill(sessionStatusVisual(it)) }
            if (s != null && !s.prUrl.isNullOrBlank()) {
                IconButton(onClick = { parseGitHubPullRequestUrl(s.prUrl!!)?.let { onOpenGitCi(it.owner, it.repo) } }) {
                    Icon(Icons.Filled.Build, "Git & CI", tint = V3.Muted)
                }
            }
        }

        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp)) {
            Spacer(Modifier.height(8.dp))
            if (items.isEmpty()) {
                Text("Pas encore d'activité.", color = V3.Faint, fontSize = 13.sp, modifier = Modifier.padding(vertical = 24.dp))
            }
            items.forEach { item -> ChatBubble(item) }

            // PR / source actions panel
            if (s != null && !s.prUrl.isNullOrBlank()) {
                PrPanel(
                    session = s, busy = busy,
                    onMerge = {
                        busy = true
                        scope.launch {
                            val res = deps.julesRepository.mergePr(token, sessionId, s.prUrl!!)
                            busy = false
                            if (res.isSuccess) { session = s.copy(prState = "merged", prMergeable = true); feedback = "PR mergée dans la base." }
                            else feedback = "Échec du merge : ${res.exceptionOrNull()?.message ?: "erreur"}"
                        }
                    },
                    onResolve = { onOpenConflict(s.prUrl!!) },
                    onRetry = {
                        busy = true
                        scope.launch {
                            try { deps.julesRepository.sendMessage(sessionId, "Corrige les problèmes et repousse la PR.") ; feedback = "Relance envoyée à l'agent." }
                            catch (_: Exception) { feedback = "Échec de la relance." }
                            busy = false
                        }
                    },
                    onView = {
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(s.prUrl))) }
                    },
                )
            }
            feedback?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, color = V3.Accent, fontSize = 13.sp)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ChatBubble(item: JulesChatItem) {
    when (item) {
        is JulesChatItem.UserMessage -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
            Bubble(label = "Vous", body = item.text, container = V3.SurfaceHi, alignEnd = true)
        }
        is JulesChatItem.AgentMessage -> {
            val body = buildString {
                if (item.title.isNotBlank()) append(item.title)
                if (item.text.isNotBlank()) { if (isNotEmpty()) append("\n"); append(item.text) }
                item.subItems.forEach { si -> if (si.text.isNotBlank()) { append("\n• "); append(si.text) } }
            }
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                Bubble(label = "Agent", body = body.ifBlank { "…" }, container = V3.Surface, alignEnd = false)
            }
        }
    }
}

@Composable
private fun Bubble(label: String, body: String, container: androidx.compose.ui.graphics.Color, alignEnd: Boolean) {
    Column(
        Modifier
            .padding(top = 12.dp)
            .fillMaxWidth(0.86f)
            .clip(RoundedCornerShape(16.dp))
            .background(container)
            .padding(horizontal = 14.dp, vertical = 11.dp),
    ) {
        Text(label.uppercase(), color = V3.Faint, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Spacer(Modifier.height(5.dp))
        Text(body, color = V3.Fg, fontSize = 14.5.sp, lineHeight = 21.sp)
    }
}

@Composable
private fun PrPanel(
    session: JulesSessionEntity,
    busy: Boolean,
    onMerge: () -> Unit,
    onResolve: () -> Unit,
    onRetry: () -> Unit,
    onView: () -> Unit,
) {
    val conflict = session.prMergeable == false
    val merged = session.prState == "merged"
    Column(
        Modifier
            .padding(top = 16.dp).fillMaxWidth()
            .clip(RoundedCornerShape(18.dp)).background(V3.Surface)
            .padding(15.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(session.prTitle ?: "Pull request", color = V3.Fg, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            StatusPill(sessionStatusVisual(session))
        }
        session.prBranch?.let {
            Spacer(Modifier.height(8.dp))
            Text("$it → main", color = V3.Muted, fontSize = 12.5.sp, fontFamily = FontFamily.Monospace)
        }
        Spacer(Modifier.height(14.dp))
        if (merged) {
            OutlinedButton(onClick = onView, modifier = Modifier.fillMaxWidth()) { Text("Mergé · voir la PR ↗", color = V3.Success) }
        } else if (conflict) {
            Button(
                onClick = onResolve, enabled = !busy, modifier = Modifier.fillMaxWidth().height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = V3.Danger.copy(alpha = 0.18f), contentColor = V3.Danger),
            ) { Text("Résoudre les conflits") }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                OutlinedButton(onClick = onRetry, enabled = !busy, modifier = Modifier.weight(1f)) { Text("Relancer", color = V3.Fg) }
                OutlinedButton(onClick = onView, modifier = Modifier.weight(1f)) { Text("Voir la PR ↗", color = V3.Fg) }
            }
        } else {
            Button(
                onClick = onMerge, enabled = !busy, modifier = Modifier.fillMaxWidth().height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = V3.Accent, contentColor = V3.AccentInk),
            ) { Text(if (busy) "…" else "Merger") }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                OutlinedButton(onClick = onRetry, enabled = !busy, modifier = Modifier.weight(1f)) { Text("Relancer", color = V3.Fg) }
                OutlinedButton(onClick = onView, modifier = Modifier.weight(1f)) { Text("Voir la PR ↗", color = V3.Fg) }
            }
        }
    }
}
