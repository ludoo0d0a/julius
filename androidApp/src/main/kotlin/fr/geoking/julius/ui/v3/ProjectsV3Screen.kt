package fr.geoking.julius.ui.v3

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import kotlinx.coroutines.launch
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.geoking.julius.api.jules.JulesClient
import fr.geoking.julius.queue.julesApiKeys

@Composable
fun ProjectsV3Screen(
    deps: V3Deps,
    onOpenProject: (String) -> Unit,
) {
    val settings by deps.settingsManager.settings.collectAsState()
    val apiKeys = remember(settings) { settings.julesApiKeys() }
    val sourcesFlow = remember { deps.julesRepository.getSourcesFlow() }
    val sources by sourcesFlow.collectAsState(initial = emptyList())

    var isRefreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(apiKeys) {
        if (apiKeys.isNotEmpty()) {
            isRefreshing = true
            deps.julesRepository.refreshSources(apiKeys)
            isRefreshing = false
        }
    }

    val featuresFlow = remember { deps.featureRepository.getAllFeatures() }
    val features by featuresFlow.collectAsState(initial = emptyList())

    var query by remember { mutableStateOf("") }
    val countBySource = remember(features) { features.groupingBy { it.sourceName }.eachCount() }
    val activeBySource = remember(features) {
        features.filter { it.status.uppercase() in setOf("IN_PROGRESS", "PLANNING") }
            .groupingBy { it.sourceName }.eachCount()
    }

    fun displayName(s: JulesClient.JulesSource): String =
        s.githubRepo?.let { "${it.owner}/${it.repo}" }?.takeIf { it != "/" } ?: s.name

    val filtered = remember(sources, query) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) sources else sources.filter { displayName(it).lowercase().contains(q) }
    }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = {
            scope.launch {
                isRefreshing = true
                deps.julesRepository.refreshSources(apiKeys)
                isRefreshing = false
            }
        },
        modifier = Modifier.fillMaxSize()
    ) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Column(Modifier.padding(horizontal = 18.dp).padding(top = 16.dp)) {
                OutlinedTextField(
                value = query, onValueChange = { query = it },
                placeholder = { Text("Rechercher…", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                leadingIcon = { Icon(Icons.Filled.Search, null, tint = V3.Faint) },
                singleLine = true, shape = RoundedCornerShape(13.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            )

            SectionLabel("Dépôts", "${filtered.size}")
            if (filtered.isEmpty()) {
                if (isRefreshing) {
                    Box(Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = V3.Accent)
                    }
                } else {
                    EmptyHint(if (query.isEmpty()) "Aucun dépôt." else "Aucun résultat pour « $query »")
                }
            } else {
                V3Card {
                    filtered.forEachIndexed { i, s ->
                        if (i > 0) HorizontalDivider(color = V3.Border)
                        val name = displayName(s)
                        val total = countBySource[s.name] ?: 0
                        val active = activeBySource[s.name] ?: 0
                        val isCurrent = s.name == settings.lastJulesRepoName

                        V3Row(
                            title = name,
                            subtitle = if (total > 0) "$total feature(s)" + if (active > 0) " · $active en cours" else "" else "—",
                            leadingIcon = if (isCurrent) Icons.Filled.CheckCircle else Icons.Filled.FolderOpen,
                            leadingTint = if (isCurrent) V3.Accent else if (active > 0) V3.Accent.copy(alpha = 0.7f) else V3.Muted,
                            onClick = {
                                deps.settingsManager.saveSettings(settings.copy(lastJulesRepoName = s.name, lastJulesRepoId = s.name))
                            },
                            trailing = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (isCurrent) {
                                        StatusPill(StatusVisual("ACTIF", "", V3.Accent))
                                    } else if (active > 0) {
                                        StatusPill(StatusVisual("$active actives", "", V3.Accent, pulse = true), showEnum = false)
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    IconButton(onClick = { onOpenProject(s.name) }) {
                                        Icon(Icons.Filled.List, "Features", tint = V3.Muted)
                                    }
                                }
                            },
                        )
                    }
                }
            }
                Spacer(Modifier.height(96.dp))
            }
        }
    }
}
