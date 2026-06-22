package fr.geoking.julius.ui.v3

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import fr.geoking.julius.api.jules.JulesClient
import fr.geoking.julius.queue.julesApiKeys
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectsV3Screen(
    deps: V3Deps,
    onOpenProject: (String) -> Unit,
) {
    val settings by deps.settingsManager.settings.collectAsState()
    val apiKeys = remember(settings) { settings.julesApiKeys() }

    val sourcesFlow = remember(deps.julesRepository) { deps.julesRepository.getSourcesFlow() }
    val sources by sourcesFlow.collectAsState(initial = emptyList())

    var isRefreshing by remember(apiKeys) { mutableStateOf(apiKeys.isNotEmpty()) }
    val scope = rememberCoroutineScope()

    // Background API refresh only when cache is empty or stale.
    LaunchedEffect(apiKeys) {
        if (apiKeys.isEmpty()) {
            isRefreshing = false
            return@LaunchedEffect
        }

        if (deps.julesRepository.shouldRefreshSources()) {
            isRefreshing = true
            try {
                deps.julesRepository.refreshSources(apiKeys)
            } finally {
                isRefreshing = false
            }
        } else {
            isRefreshing = false
        }
    }

    val featuresFlow = remember { deps.featureRepository.getAllFeatures() }
    val features by featuresFlow.collectAsState(initial = emptyList())
    val countBySource = remember(features) { features.groupingBy { it.sourceName }.eachCount() }
    val activeBySource = remember(features) {
        features.filter { it.status.uppercase() in setOf("IN_PROGRESS", "PLANNING") }
            .groupingBy { it.sourceName }.eachCount()
    }

    val sessionsFlow = remember(deps.julesRepository) { deps.julesRepository.getAllSessionsFlow() }
    val sessions by sessionsFlow.collectAsState(initial = emptyList())
    val prsBySource = remember(sessions) {
        sessions.filter { it.prUrl != null && !it.isFinished }
            .groupingBy { it.sourceName }.eachCount()
    }

    fun displayName(s: JulesClient.JulesSource): String =
        s.githubRepo?.let { "${it.owner}/${it.repo}" }?.takeIf { it != "/" } ?: s.name

    val recentName = settings.lastJulesRepoName
    val recent = remember(sources, recentName) { sources.firstOrNull { it.name == recentName } }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = {
            if (apiKeys.isEmpty()) return@PullToRefreshBox
            scope.launch {
                isRefreshing = true
                try {
                    deps.julesRepository.refreshSources(apiKeys)
                } finally {
                    isRefreshing = false
                }
            }
        },
        modifier = Modifier.fillMaxSize(),
    ) {
        when {
            sources.isEmpty() && isRefreshing ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = V3.Accent)
                }
            sources.isEmpty() && !isRefreshing ->
                Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp)) {
                    EmptyHint("Aucun dépôt — vérifie la clé Jules dans Réglages.")
                }
            else ->
                LazyColumn(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
                    if (recent != null) {
                        item { SectionLabel("Récent") }
                        item {
                            V3Card {
                                ProjectRow(
                                    source = recent, isRecent = true,
                                    count = countBySource[recent.name] ?: 0, active = activeBySource[recent.name] ?: 0,
                                    prs = prsBySource[recent.name] ?: 0,
                                    name = displayName(recent),
                                    onOpen = {
                                        deps.settingsManager.saveSettings(settings.copy(lastJulesRepoName = recent.name, lastJulesRepoId = recent.name))
                                        onOpenProject(recent.name)
                                    },
                                )
                            }
                        }
                    }
                    item { SectionLabel("Tous les dépôts", "${sources.size}") }
                    item {
                        V3Card {
                            sources.forEachIndexed { i, s ->
                                if (i > 0) HorizontalDivider(color = V3.Border)
                                ProjectRow(
                                    source = s, isRecent = s.name == recentName,
                                    count = countBySource[s.name] ?: 0, active = activeBySource[s.name] ?: 0,
                                    prs = prsBySource[s.name] ?: 0,
                                    name = displayName(s),
                                    onOpen = {
                                        deps.settingsManager.saveSettings(settings.copy(lastJulesRepoName = s.name, lastJulesRepoId = s.name))
                                        onOpenProject(s.name)
                                    },
                                )
                            }
                        }
                    }
                    item { Spacer(Modifier.height(96.dp)) }
                }
        }
    }
}

@Composable
private fun ProjectRow(
    source: JulesClient.JulesSource,
    isRecent: Boolean,
    count: Int,
    active: Int,
    prs: Int,
    name: String,
    onOpen: () -> Unit,
) {
    V3Row(
        title = name,
        subtitle = if (count > 0) {
            "$count feature(s)" +
                (if (active > 0) " · $active en cours" else "") +
                (if (prs > 0) " · $prs PR(s)" else "")
        } else "—",
        leadingIcon = if (isRecent) Icons.Filled.CheckCircle else Icons.Filled.FolderOpen,
        leadingTint = if (isRecent || active > 0) V3.Accent else V3.Muted,
        onClick = onOpen,
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isRecent) StatusPill(StatusVisual("Actif", "", V3.Accent), showEnum = false)
                else if (active > 0) StatusPill(StatusVisual("$active actives", "", V3.Accent, pulse = true), showEnum = false)
                Spacer(Modifier.width(6.dp))
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = V3.Faint)
            }
        },
    )
}
