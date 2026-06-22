package fr.geoking.julius.ui.v3

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.geoking.julius.persistence.FeatureEntity
import fr.geoking.julius.persistence.JulesSessionEntity
import kotlinx.coroutines.launch

@Composable
fun FeatureDetailV3Screen(
    deps: V3Deps,
    featureId: String,
    onBack: () -> Unit,
    onOpenConversation: (String) -> Unit,
    onLaunch: () -> Unit,
) {
    val feature by remember(featureId) { deps.featureRepository.getFeatureFlow(featureId) }.collectAsState(initial = null)

    val scope = rememberCoroutineScope()
    val settings by deps.settingsManager.settings.collectAsState()

    // Cache-first: show this feature's conversations from Room immediately.
    var sessions by remember(featureId) { mutableStateOf<List<JulesSessionEntity>>(emptyList()) }
    LaunchedEffect(featureId) { sessions = deps.julesRepository.getSessionsByFeature(featureId) }

    // Then update from the agents (Jules API): refresh the source's sessions, auto-promote
    // orphan conversations, and re-read this feature's sessions from the refreshed cache.
    LaunchedEffect(feature?.sourceName, settings.julesKeys, settings.githubApiKey) {
        val sourceName = feature?.sourceName ?: return@LaunchedEffect
        deps.julesRepository.getSessions(
            this,
            settings.julesKeys,
            sourceName,
            pageSize = 50,
            refreshActivities = true,
        ).collect { list ->
            deps.featureRepository.autoPromoteOrphans(scope, sourceName, list)
        }
    }

    LaunchedEffect(featureId) {
        // Reactive local sessions for this feature (including newly promoted ones)
        deps.julesRepository.getSessionsFlow(feature?.sourceName ?: "").collect {
            sessions = deps.julesRepository.getSessionsByFeature(featureId)
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        val f = feature
        if (f == null) {
            Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = V3.Accent)
            }
            return@Column
        }

        val v = featureStatusVisual(f.status)
        Column(Modifier.padding(horizontal = 18.dp).padding(top = 16.dp)) {
            Text(f.title, color = V3.Fg, fontSize = 22.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
            Spacer(Modifier.height(10.dp))
            StatusPill(v)
            if (f.description.isNotBlank()) {
                Spacer(Modifier.height(14.dp))
                Text(f.description, color = V3.Muted, fontSize = 14.sp, lineHeight = 21.sp)
            }
            if (f.errorMessage != null) {
                Spacer(Modifier.height(10.dp))
                Text(f.errorMessage!!, color = V3.Danger, fontSize = 13.sp)
            }

            var query by remember { mutableStateOf("") }
            val filtered = remember(sessions, query) {
                val q = query.trim().lowercase()
                if (q.isEmpty()) sessions
                else sessions.filter {
                    (it.prTitle ?: "").lowercase().contains(q) ||
                        it.title.lowercase().contains(q) ||
                        it.prompt.lowercase().contains(q)
                }
            }

            SectionLabel("Conversations", "${filtered.size}")
            OutlinedTextField(
                value = query, onValueChange = { query = it },
                placeholder = { Text("Rechercher une conversation…") },
                leadingIcon = { Icon(Icons.Filled.Search, null, tint = V3.Faint) },
                singleLine = true, shape = RoundedCornerShape(13.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            if (filtered.isEmpty()) {
                EmptyHint(if (query.isEmpty()) "Aucune conversation." else "Aucun résultat.")
            } else {
                V3Card {
                    filtered.forEachIndexed { i, s ->
                        if (i > 0) HorizontalDivider(color = V3.Border)
                        val sv = sessionStatusVisual(s)
                        val backend = if (s.id.startsWith("sesn_")) "CLAUDE_CODE" else "JULES"
                        V3Row(
                            title = s.prTitle ?: s.title.ifBlank { s.prompt.take(48) },
                            subtitle = "$backend${if (s.prUrl.isNullOrBlank()) "" else " · PR"} · ${f.sourceName}",
                            leadingIcon = Icons.Filled.Chat,
                            leadingTint = sv.color,
                            onClick = { onOpenConversation(s.id) },
                            trailing = { StatusPill(sv) },
                        )
                    }
                }
            }
            Spacer(Modifier.height(96.dp))
        }
    }
}
