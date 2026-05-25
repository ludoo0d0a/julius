package fr.geoking.julius.api.github

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GitHubWorkflow(
    val id: Long = 0,
    val name: String = "",
    val path: String = "",
    val state: String = ""
)

@Serializable
data class GitHubWorkflowsResponse(
    @SerialName("total_count") val totalCount: Int = 0,
    val workflows: List<GitHubWorkflow> = emptyList()
)

@Serializable
data class GitHubWorkflowRun(
    val id: Long = 0,
    val name: String? = null,
    val status: String? = null,
    val conclusion: String? = null,
    @SerialName("workflow_id") val workflowId: Long = 0,
    @SerialName("run_number") val runNumber: Int = 0,
    @SerialName("html_url") val htmlUrl: String = "",
    @SerialName("created_at") val createdAt: String = "",
    @SerialName("updated_at") val updatedAt: String = ""
)

@Serializable
data class GitHubWorkflowRunsResponse(
    @SerialName("total_count") val totalCount: Int = 0,
    @SerialName("workflow_runs") val workflowRuns: List<GitHubWorkflowRun> = emptyList()
)

@Serializable
data class GitHubWorkflowJobStep(
    val name: String = "",
    val status: String? = null,
    val conclusion: String? = null,
    val number: Int = 0
)

@Serializable
data class GitHubWorkflowJob(
    val id: Long = 0,
    val name: String = "",
    val status: String? = null,
    val conclusion: String? = null,
    @SerialName("html_url") val htmlUrl: String = "",
    val steps: List<GitHubWorkflowJobStep> = emptyList()
)

@Serializable
data class GitHubWorkflowJobsResponse(
    @SerialName("total_count") val totalCount: Int = 0,
    val jobs: List<GitHubWorkflowJob> = emptyList()
)

fun GitHubWorkflowRun.isSuccess(): Boolean =
    status == "completed" && conclusion == "success"

fun GitHubWorkflowRun.isInProgress(): Boolean =
    status == "in_progress" || status == "queued" || status == "waiting" || status == "pending"

fun GitHubWorkflowRun.displayConclusion(): String = when {
    isInProgress() -> status?.replace('_', ' ') ?: "in progress"
    conclusion != null -> conclusion
    status != null -> status
    else -> "unknown"
}

/** Prefer deploy / Play Store style workflow names when auto-selecting. */
fun pickDefaultWorkflow(workflows: List<GitHubWorkflow>): GitHubWorkflow? {
    if (workflows.isEmpty()) return null
    val deployPattern = Regex("deploy|play\\s*store", RegexOption.IGNORE_CASE)
    return workflows.firstOrNull { deployPattern.containsMatchIn(it.name) }
        ?: workflows.firstOrNull { deployPattern.containsMatchIn(it.path) }
        ?: workflows.first()
}
