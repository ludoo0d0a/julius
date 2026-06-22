package fr.geoking.julius.ui.v3

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Build
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import fr.geoking.julius.api.jules.JulesClient
import fr.geoking.julius.queue.julesApiKeys
import fr.geoking.julius.repository.BuildStatusSummary
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectsV3Screen(
    deps: V3Deps,
    onOpenProject: (String) -> Unit,
) {
    val settings by deps.settingsManager.settings.collectAsState()
    val apiKeys = remember(settings) { settings.julesApiKeys() }

    var isRefreshing by remember(apiKeys) { mutableStateOf(apiKeys.isNotEmpty()) }
    val scope = rememberCoroutineScope()
    var sources by remember { mutableStateOf<List<JulesClient.JulesSource>>(emptyList()) }

    // DB first; API refresh only when cache is empty or stale (not on every tab visit).
    LaunchedEffect(apiKeys) {
        if (apiKeys.isEmpty()) {
            sources = emptyList()
            isRefreshing = false
            return@LaunchedEffect
        }

        sources = deps.julesRepository.getSourcesCached()
        isRefreshing = sources.isEmpty()

        val refreshJob = if (deps.julesRepository.shouldRefreshSources()) {
            launch {
                try {
                    deps.julesRepository.refreshSources(apiKeys)
                } finally {
                    isRefreshing = false
                }
            }
        } else {
            isRefreshing = false
            null
        }

        try {
            deps.julesRepository.getSourcesFlow().collect { list ->
                sources = list
            }
        } finally {
            refreshJob?.cancel()
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

    val buildStatuses = remember(sources, apiKeys) {
        mutableStateMapOf<String, BuildStatusSummary?>()
    }

    LaunchedEffect(sources, apiKeys) {
        val githubToken = settings.githubApiKey
        if (githubToken.isNotBlank()) {
            sources.forEach { source ->
                val owner = source.githubRepo?.owner
                val repo = source.githubRepo?.repo
                if (owner != null && repo != null) {
                    launch {
                        try {
                            val wf = deps.buildRepository.resolveWorkflowId(githubToken, owner, repo)
                            if (wf != null) {
                                val summary = deps.buildRepository.loadSummary(githubToken, owner, repo, wf.first, wf.second)
                                buildStatuses[source.name] = summary
                            }
                        } catch (_: Exception) {}
                    }
                }
            }
        }
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
                Box(Modifier.fillMaxSize())
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
                                    buildStatus = buildStatuses[recent.name],
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
                                    buildStatus = buildStatuses[s.name],
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
    buildStatus: BuildStatusSummary?,
    name: String,
    onOpen: () -> Unit,
) {
    val subtitleParts = remember(count, active, prs) {
        mutableListOf<String>().apply {
            if (count > 0) add("$count feature(s)")
            if (active > 0) add("$active en cours")
            if (prs > 0) add("$prs PR(s)")
        }
    }

    V3Row(
        title = name,
        subtitle = if (subtitleParts.isNotEmpty()) {
            subtitleParts.joinToString(" · ")
        } else "—",
        leadingIcon = if (isRecent) Icons.Filled.CheckCircle else Icons.Filled.FolderOpen,
        leadingTint = if (isRecent || active > 0) V3.Accent else V3.Muted,
        onClick = onOpen,
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (buildStatus != null) {
                    val visual = workflowStatusVisual(if (buildStatus.isInProgress) "in_progress" else "completed", buildStatus.latestConclusion)
                    Icon(
                        Icons.Filled.Build, null,
                        tint = visual.color,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                }
                if (isRecent) StatusPill(StatusVisual("Actif", "", V3.Accent), showEnum = false)
                else if (active > 0) StatusPill(StatusVisual("$active actives", "", V3.Accent, pulse = true), showEnum = false)
                Spacer(Modifier.width(6.dp))
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = V3.Faint)
            }
        },
    )
}
