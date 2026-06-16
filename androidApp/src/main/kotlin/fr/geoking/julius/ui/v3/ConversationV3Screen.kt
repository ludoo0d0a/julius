package fr.geoking.julius.ui.v3

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.geoking.julius.api.github.parseGitHubPullRequestUrl
import fr.geoking.julius.api.jules.JulesChatItem
import fr.geoking.julius.persistence.JulesSessionEntity
import fr.geoking.julius.ui.components.VoiceInputIcon
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationV3Screen(
    deps: V3Deps,
    sessionId: String,
    onBack: () -> Unit,
    onOpenGitCi: (String, String) -> Unit,
    onOpenConflict: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    val settings by deps.settingsManager.settings.collectAsState()
    val token = settings.githubApiKey

    var sendTick by remember { mutableStateOf(0) }
    var session by remember(sessionId) { mutableStateOf<JulesSessionEntity?>(null) }
    LaunchedEffect(sessionId, sendTick) {
        // cache-first then refresh from Jules/Claude + GitHub
        session = deps.julesRepository.getSession(sessionId)
        runCatching { deps.julesRepository.pollSessionStatus(sessionId, token) }
        session = deps.julesRepository.getSession(sessionId)
    }

    val activitiesFlow = remember(sessionId, sendTick) { deps.julesRepository.getActivities(sessionId) }
    val items by activitiesFlow.collectAsState(initial = emptyList())

    var prompt by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    val s = session
    val hasPr = s != null && !s.prUrl.isNullOrBlank()
    val conflict = s?.prMergeable == false
    val merged = s?.prState == "merged"

    fun send() {
        val text = prompt.trim()
        if (text.isEmpty()) return
        sending = true
        scope.launch {
            runCatching { deps.julesRepository.sendMessage(sessionId, text) }
            prompt = ""; sending = false; sendTick++
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            // ---- chat ----
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp).padding(top = 8.dp),
            ) {
                if (items.isEmpty()) {
                    Text("Pas encore d'activité.", color = V3.Faint, fontSize = 13.sp, modifier = Modifier.padding(vertical = 24.dp))
                }
                items.forEach { item -> ChatBubble(item) }
                Spacer(Modifier.height(if (hasPr) 90.dp else 12.dp))
            }

            // ---- bottom git status zone (PR / branch) ----
            if (s != null) {
                HorizontalDivider(color = V3.Border)
                Row(
                    Modifier.fillMaxWidth()
                        .then(if (hasPr) Modifier.clickable { parseGitHubPullRequestUrl(s.prUrl!!)?.let { onOpenGitCi(it.owner, it.repo) } } else Modifier)
                        .background(V3.Surface)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.AccountTree, null, tint = if (hasPr) V3.Accent else V3.Faint, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (hasPr) (s.prBranch?.let { "$it → main" } ?: (s.prTitle ?: "Pull request")) else "Pas encore de PR",
                            color = V3.Fg, fontSize = 13.sp,
                            fontFamily = if (hasPr) FontFamily.Monospace else FontFamily.Default,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            "sessionState · ${s.sessionState ?: "…"}",
                            color = V3.Muted, fontSize = 11.sp, fontFamily = FontFamily.Monospace,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    StatusPill(sessionStatusVisual(s))
                }
            }

            // ---- bottom input (prompt + mic + send) ----
            Surface(color = V3.Bg) {
                Row(
                    Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = prompt, onValueChange = { prompt = it },
                        placeholder = { Text("Message à l'agent…") },
                        modifier = Modifier.weight(1f),
                        maxLines = 4,
                        shape = RoundedCornerShape(22.dp),
                        trailingIcon = {
                            VoiceInputIcon(
                                voiceManager = deps.voiceManager,
                                onTranscriptionReceived = { prompt = (prompt + " " + it).trim() },
                                tint = V3.Accent,
                            )
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = V3.Fg, unfocusedTextColor = V3.Fg,
                            focusedBorderColor = V3.Accent, unfocusedBorderColor = V3.Border,
                        ),
                    )
                    Spacer(Modifier.width(8.dp))
                    FilledIconButton(
                        onClick = { send() },
                        enabled = prompt.isNotBlank() && !sending,
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = V3.Accent, contentColor = V3.AccentInk),
                    ) { Icon(Icons.AutoMirrored.Filled.Send, "Envoyer") }
                }
            }
        }

        // ---- PR actions FAB (speed dial, PR icon) ----
        if (s != null && hasPr && !merged) {
            PrFabSpeedDial(
                conflict = conflict,
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 150.dp),
                onMerge = {
                    if (!busy) {
                        busy = true
                        scope.launch {
                            runCatching { deps.julesRepository.mergePr(token, sessionId, s.prUrl!!) }
                            busy = false; sendTick++
                        }
                    }
                },
                onResolve = { onOpenConflict(s.prUrl!!) },
                onGitCi = { parseGitHubPullRequestUrl(s.prUrl!!)?.let { onOpenGitCi(it.owner, it.repo) } },
                onView = { uriHandler.openUri(s.prUrl!!) },
                onRetry = { scope.launch { runCatching { deps.julesRepository.sendMessage(sessionId, "Corrige les problèmes et repousse la PR.") }; sendTick++ } },
            )
        }
    }
}

@Composable
private fun PrFabSpeedDial(
    conflict: Boolean,
    modifier: Modifier,
    onMerge: () -> Unit,
    onResolve: () -> Unit,
    onGitCi: () -> Unit,
    onView: () -> Unit,
    onRetry: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Column(modifier = modifier, horizontalAlignment = Alignment.End) {
        if (open) {
            ConvMiniAction("Relancer", Icons.Filled.Replay) { open = false; onRetry() }
            ConvMiniAction("Git & CI", Icons.Filled.Build) { open = false; onGitCi() }
            ConvMiniAction("Voir la PR", Icons.AutoMirrored.Filled.OpenInNew) { open = false; onView() }
            if (conflict) ConvMiniAction("Résoudre", Icons.Filled.Warning) { open = false; onResolve() }
            else ConvMiniAction("Merger", Icons.Filled.Done) { open = false; onMerge() }
        }
        FloatingActionButton(onClick = { open = !open }, containerColor = V3.Accent, contentColor = V3.AccentInk) {
            Icon(if (open) Icons.Filled.Close else Icons.Filled.AccountTree, "Actions PR")
        }
    }
}

@Composable
private fun ConvMiniAction(label: String, icon: ImageVector, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 12.dp)) {
        Surface(color = V3.Surface, shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, V3.Border)) {
            Text(label, color = V3.Fg, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp))
        }
        Spacer(Modifier.width(10.dp))
        SmallFloatingActionButton(onClick = onClick, containerColor = V3.SurfaceHi, contentColor = V3.Fg) {
            Icon(icon, label, modifier = Modifier.size(20.dp))
        }
    }
}

/**
 * Compact rendering: each parsed segment (subItem) is its own bubble with a small
 * contextual icon (by type) — no titles, mirroring the v1 parsing (JulesChatItem).
 */
@Composable
private fun ChatBubble(item: JulesChatItem) {
    when (item) {
        is JulesChatItem.UserMessage ->
            CompactBubble(item.text, Icons.Filled.Person, V3.Muted, V3.SurfaceHi, alignEnd = true)
        is JulesChatItem.AgentMessage -> {
            if (item.subItems.isEmpty()) {
                val body = buildString {
                    if (item.title.isNotBlank()) append(item.title)
                    if (item.text.isNotBlank()) { if (isNotEmpty()) append("\n"); append(item.text) }
                }.ifBlank { "…" }
                CompactBubble(body, Icons.AutoMirrored.Filled.Chat, V3.Muted, V3.Surface, alignEnd = false)
            } else {
                item.subItems.forEach { si ->
                    if (si.text.isNotBlank()) {
                        CompactBubble(si.text, subItemIcon(si.type), subItemTint(si.type), V3.Surface, alignEnd = false)
                    }
                }
            }
        }
    }
}

private fun subItemIcon(type: String?): ImageVector = when (type) {
    "plan" -> Icons.Filled.Checklist
    "progress" -> Icons.Filled.Autorenew
    "completion" -> Icons.Filled.CheckCircle
    else -> Icons.AutoMirrored.Filled.Chat
}

private fun subItemTint(type: String?): androidx.compose.ui.graphics.Color = when (type) {
    "plan" -> V3.Accent
    "completion" -> V3.Success
    else -> V3.Muted
}

@Composable
private fun CompactBubble(
    text: String,
    icon: ImageVector,
    iconTint: androidx.compose.ui.graphics.Color,
    container: androidx.compose.ui.graphics.Color,
    alignEnd: Boolean,
) {
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = if (alignEnd) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top,
    ) {
        if (!alignEnd) {
            Box(Modifier.padding(top = 2.dp).size(26.dp).clip(RoundedCornerShape(8.dp)).background(V3.SurfaceHi), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = iconTint, modifier = Modifier.size(15.dp))
            }
            Spacer(Modifier.width(8.dp))
        }
        Box(
            Modifier.weight(1f, fill = false)
                .clip(RoundedCornerShape(14.dp)).background(container)
                .padding(horizontal = 12.dp, vertical = 9.dp),
        ) {
            Text(text, color = V3.Fg, fontSize = 14.sp, lineHeight = 20.sp)
        }
        if (alignEnd) {
            Spacer(Modifier.width(8.dp))
            Box(Modifier.padding(top = 2.dp).size(26.dp).clip(RoundedCornerShape(8.dp)).background(V3.SurfaceHi), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = iconTint, modifier = Modifier.size(15.dp))
            }
        }
    }
}
