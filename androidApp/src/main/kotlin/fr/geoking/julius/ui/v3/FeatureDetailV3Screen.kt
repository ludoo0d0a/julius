package fr.geoking.julius.ui.v3

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.geoking.julius.persistence.FeatureEntity
import fr.geoking.julius.persistence.JulesSessionEntity

@Composable
fun FeatureDetailV3Screen(
    deps: V3Deps,
    featureId: String,
    onBack: () -> Unit,
    onOpenConversation: (String) -> Unit,
    onLaunch: () -> Unit,
) {
    val feature by produceState<FeatureEntity?>(initialValue = null, featureId) {
        value = deps.featureRepository.getFeature(featureId)
    }
    val sessions by produceState<List<JulesSessionEntity>>(initialValue = emptyList(), featureId) {
        value = deps.julesRepository.getSessionsByFeature(featureId)
    }

    val scope = rememberCoroutineScope()
    val settings by deps.settingsManager.settings.collectAsState()
    LaunchedEffect(feature?.sourceName, settings.julesKeys, settings.githubApiKey) {
        val sourceName = feature?.sourceName ?: return@LaunchedEffect
        deps.julesRepository.getSessions(settings.julesKeys, sourceName, settings.githubApiKey).collect { list ->
            deps.featureRepository.autoPromoteOrphans(scope, sourceName, list)
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

            SectionLabel("Conversations", "${sessions.size} session(s)")
            if (sessions.isEmpty()) {
                EmptyHint("Aucune conversation. Lance un agent sur cette feature.")
            } else {
                V3Card {
                    sessions.forEachIndexed { i, s ->
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

            Spacer(Modifier.height(18.dp))
            Button(
                onClick = onLaunch,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = V3.Accent, contentColor = V3.AccentInk),
            ) {
                Icon(Icons.Filled.Add, null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Lancer une conversation", fontSize = 15.sp)
            }
            Text(
                "Plusieurs agents peuvent traiter la même feature en parallèle.",
                color = V3.Faint, fontSize = 12.sp, modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Spacer(Modifier.height(96.dp))
        }
    }
}
