package fr.geoking.julius.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.geoking.julius.api.github.GitHubWorkflow
import fr.geoking.julius.api.github.GitHubWorkflowJob
import fr.geoking.julius.api.github.GitHubWorkflowRun
import fr.geoking.julius.api.github.displayConclusion
import fr.geoking.julius.api.github.isInProgress
import fr.geoking.julius.api.github.isSuccess
import fr.geoking.julius.repository.BuildRunDetail
import fr.geoking.julius.repository.BuildStatusSummary
import fr.geoking.julius.repository.GitHubBuildRepository
import kotlinx.coroutines.launch

@Composable
fun BuildStatusSummaryCard(
    summary: BuildStatusSummary?,
    loading: Boolean,
    error: String?,
    hasGitHubRepo: Boolean,
    hasToken: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!hasGitHubRepo) return

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = hasToken && summary != null, onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = ColorHelper.JulesCardAgent),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            when {
                loading -> CircularProgressIndicator(
                    modifier = Modifier.size(28.dp),
                    color = ColorHelper.JulesAccent,
                    strokeWidth = 2.dp
                )
                summary != null -> BuildStatusIcon(summary)
                else -> Icon(
                    Icons.Default.Build,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.size(28.dp)
                )
            }
            Spacer(modifier = Modifier.size(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("CI / Build", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                when {
                    !hasToken -> Text(
                        "Add GitHub token in Settings",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 12.sp
                    )
                    error != null -> Text(error, color = ColorHelper.JulesErrorBg, fontSize = 12.sp, maxLines = 2)
                    summary != null -> {
                        Text(
                            "${summary.workflowName} · ${summary.latestConclusion}",
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val sub = buildString {
                            if (summary.isInProgress) append("Running")
                            if (summary.runsSinceLastSuccess > 0) {
                                if (isNotEmpty()) append(" · ")
                                append("${summary.runsSinceLastSuccess} since last success")
                            }
                            if (summary.latestRunNumber != null && summary.latestRunNumber > 0) {
                                if (isNotEmpty()) append(" · ")
                                append("#${summary.latestRunNumber}")
                            }
                        }
                        if (sub.isNotEmpty()) {
                            Text(sub, color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp)
                        }
                    }
                    else -> Text("Tap to load build history", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun BuildStatusIcon(summary: BuildStatusSummary) {
    val (icon, tint) = when {
        summary.isInProgress -> Icons.Default.Schedule to ColorHelper.JulesAccent
        summary.latestConclusion.equals("success", ignoreCase = true) -> Icons.Default.CheckCircle to Color.Green
        else -> Icons.Default.Error to Color.Red
    }
    Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(28.dp))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BuildRunsDetailScreen(
    owner: String,
    repo: String,
    githubToken: String,
    buildRepository: GitHubBuildRepository,
    onBack: () -> Unit
) {
    var workflows by remember { mutableStateOf<List<GitHubWorkflow>>(emptyList()) }
    var selectedWorkflowId by remember { mutableStateOf<Long?>(null) }
    var selectedWorkflowName by remember { mutableStateOf("") }
    var runDetails by remember { mutableStateOf<List<BuildRunDetail>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var refreshing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var workflowMenuExpanded by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current

    fun loadRuns(isRefresh: Boolean = false) {
        val workflowId = selectedWorkflowId ?: return
        if (githubToken.isBlank()) {
            error = "GitHub token required"
            loading = false
            return
        }
        scope.launch {
            if (isRefresh) refreshing = true else loading = true
            error = null
            try {
                val runs = buildRepository.runsSinceLastSuccess(githubToken, owner, repo, workflowId)
                runDetails = buildRepository.loadRunDetails(githubToken, owner, repo, runs)
            } catch (e: Exception) {
                error = e.message ?: "Failed to load runs"
            } finally {
                loading = false
                refreshing = false
            }
        }
    }

    LaunchedEffect(owner, repo, githubToken) {
        if (githubToken.isBlank()) {
            loading = false
            error = "GitHub token required"
            return@LaunchedEffect
        }
        loading = true
        error = null
        try {
            workflows = buildRepository.listWorkflows(githubToken, owner, repo)
            val resolved = buildRepository.resolveWorkflowId(githubToken, owner, repo)
            if (resolved != null) {
                selectedWorkflowId = resolved.first
                selectedWorkflowName = resolved.second
                loadRuns()
            } else {
                error = "No workflows found"
                loading = false
            }
        } catch (e: Exception) {
            error = e.message ?: "Failed to load workflows"
            loading = false
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Text(
                "Build history",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                modifier = Modifier.weight(1f)
            )
        }

        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            Text(
                "$owner/$repo",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 12.sp
            )
            Spacer(modifier = Modifier.height(8.dp))

            if (workflows.isNotEmpty()) {
                ExposedDropdownMenuBox(
                    expanded = workflowMenuExpanded,
                    onExpandedChange = { workflowMenuExpanded = it }
                ) {
                    OutlinedTextField(
                        value = selectedWorkflowName.ifBlank { "Select workflow" },
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Workflow") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = workflowMenuExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(type = ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled = true),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = ColorHelper.JulesAccent,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.2f)
                        )
                    )
                    ExposedDropdownMenu(
                        expanded = workflowMenuExpanded,
                        onDismissRequest = { workflowMenuExpanded = false }
                    ) {
                        workflows.forEach { wf ->
                            DropdownMenuItem(
                                text = { Text(wf.name) },
                                onClick = {
                                    selectedWorkflowId = wf.id
                                    selectedWorkflowName = wf.name
                                    buildRepository.saveWorkflowId(owner, repo, wf.id)
                                    workflowMenuExpanded = false
                                    loadRuns()
                                }
                            )
                        }
                    }
                }
            }
        }

        if (error != null) {
            Text(
                error!!,
                color = Color.Red.copy(alpha = 0.9f),
                modifier = Modifier.padding(16.dp),
                fontSize = 13.sp
            )
        }

        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { loadRuns(isRefresh = true) },
            modifier = Modifier.weight(1f)
        ) {
            when {
                loading && runDetails.isEmpty() -> BoxLoading()
                runDetails.isEmpty() -> Text(
                    "No runs since last success",
                    color = Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.padding(16.dp)
                )
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(runDetails, key = { it.run.id }) { detail ->
                        BuildRunCard(
                            detail = detail,
                            onOpenUrl = { url ->
                                if (url.isNotBlank()) uriHandler.openUri(url)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BoxLoading() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(color = ColorHelper.JulesAccent)
    }
}

@Composable
private fun BuildRunCard(
    detail: BuildRunDetail,
    onOpenUrl: (String) -> Unit
) {
    val run = detail.run
    val statusColor = when {
        run.isInProgress() -> ColorHelper.JulesAccent
        run.isSuccess() -> Color.Green
        else -> Color.Red
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = ColorHelper.JulesCardAgent),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        run.name ?: "Run #${run.runNumber}",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "#${run.runNumber} · ${run.displayConclusion()}",
                        color = statusColor,
                        fontSize = 12.sp
                    )
                    if (run.createdAt.isNotBlank()) {
                        Text(
                            run.createdAt.take(19).replace('T', ' '),
                            color = Color.White.copy(alpha = 0.45f),
                            fontSize = 11.sp
                        )
                    }
                }
                if (run.htmlUrl.isNotBlank()) {
                    IconButton(onClick = { onOpenUrl(run.htmlUrl) }) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "Open on GitHub", tint = Color.White.copy(0.6f))
                    }
                }
            }

            detail.jobs.forEach { job ->
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                Spacer(modifier = Modifier.height(8.dp))
                JobSection(job = job)
            }
        }
    }
}

@Composable
private fun JobSection(job: GitHubWorkflowJob) {
    val jobColor = when (job.conclusion) {
        "success" -> Color.Green
        "failure", "cancelled", "timed_out" -> Color.Red
        else -> Color.White.copy(alpha = 0.7f)
    }
    Text(job.name, color = Color.White, fontWeight = FontWeight.Medium, fontSize = 13.sp)
    Text(
        "${job.status ?: "?"}${job.conclusion?.let { " · $it" } ?: ""}",
        color = jobColor,
        fontSize = 11.sp
    )
    job.steps.forEach { step ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, top = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val stepColor = when (step.conclusion) {
                "success" -> Color.Green
                "failure" -> Color.Red
                else -> Color.White.copy(alpha = 0.5f)
            }
            Surface(
                color = stepColor.copy(alpha = 0.15f),
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    step.name,
                    color = stepColor,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
