package fr.geoking.julius.ui.jules

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import fr.geoking.julius.repository.BuildStatusSummary
import fr.geoking.julius.repository.GitHubBuildRepository
import fr.geoking.julius.ui.BuildRunsDetailScreen
import fr.geoking.julius.ui.BuildStatusSummaryCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun JulesGitDetailsContent(
    githubToken: String,
    githubOwner: String?,
    githubRepo: String?,
    buildRepository: GitHubBuildRepository,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
) {
    var buildSummary by remember { mutableStateOf<BuildStatusSummary?>(null) }
    var buildLoading by remember { mutableStateOf(false) }
    var buildError by remember { mutableStateOf<String?>(null) }
    var showBuildDetail by remember { mutableStateOf(false) }
    var reloadKey by remember { mutableIntStateOf(0) }
    var localRefreshing by remember { mutableStateOf(false) }
    val hasGitHubRepo = !githubOwner.isNullOrBlank() && !githubRepo.isNullOrBlank()
    val owner = githubOwner
    val repo = githubRepo

    LaunchedEffect(owner, repo, githubToken, reloadKey) {
        buildSummary = null
        buildError = null
        if (!hasGitHubRepo || githubToken.isBlank() || owner == null || repo == null) {
            buildLoading = false
            localRefreshing = false
            return@LaunchedEffect
        }
        buildLoading = true
        try {
            val resolved = buildRepository.resolveWorkflowId(githubToken, owner, repo)
            if (resolved != null) {
                buildSummary = buildRepository.loadSummary(
                    githubToken,
                    owner,
                    repo,
                    resolved.first,
                    resolved.second,
                )
            }
        } catch (e: Exception) {
            buildError = e.message ?: "Build status unavailable"
        } finally {
            buildLoading = false
            localRefreshing = false
        }
    }

    if (showBuildDetail && hasGitHubRepo && owner != null && repo != null) {
        BackHandler { showBuildDetail = false }
        BuildRunsDetailScreen(
            owner = owner,
            repo = repo,
            githubToken = githubToken,
            buildRepository = buildRepository,
            onBack = { showBuildDetail = false },
        )
        return
    }

    JulesPullToRefreshBox(
        isRefreshing = isRefreshing || localRefreshing,
        onRefresh = {
            localRefreshing = true
            reloadKey++
            onRefresh()
        },
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
        ) {
            item {
                BuildStatusSummaryCard(
                    summary = buildSummary,
                    loading = buildLoading,
                    error = buildError,
                    hasGitHubRepo = hasGitHubRepo,
                    hasToken = githubToken.isNotBlank(),
                    onClick = { if (hasGitHubRepo && githubToken.isNotBlank()) showBuildDetail = true },
                )
            }
        }
    }
}
