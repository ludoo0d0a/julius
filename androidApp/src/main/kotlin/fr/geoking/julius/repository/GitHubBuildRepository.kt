package fr.geoking.julius.repository

import fr.geoking.julius.api.github.GitHubClient
import fr.geoking.julius.api.github.GitHubWorkflow
import fr.geoking.julius.api.github.GitHubWorkflowJob
import fr.geoking.julius.api.github.GitHubWorkflowRun
import fr.geoking.julius.api.github.displayConclusion
import fr.geoking.julius.api.github.isInProgress
import fr.geoking.julius.api.github.isSuccess
import fr.geoking.julius.api.github.pickDefaultWorkflow
import fr.geoking.julius.settings.ProjectWorkflowPreferences

data class BuildStatusSummary(
    val workflowName: String,
    val latestConclusion: String,
    val isInProgress: Boolean,
    val runsSinceLastSuccess: Int,
    val latestRunUrl: String?,
    val latestRunNumber: Int?
)

data class BuildRunDetail(
    val run: GitHubWorkflowRun,
    val jobs: List<GitHubWorkflowJob>
)

class GitHubBuildRepository(
    private val githubClient: GitHubClient,
    private val workflowPreferences: ProjectWorkflowPreferences
) {

    suspend fun listWorkflows(token: String, owner: String, repo: String): List<GitHubWorkflow> =
        githubClient.listWorkflows(token, owner, repo)

    fun getSavedWorkflowId(owner: String, repo: String): Long? =
        workflowPreferences.getWorkflowId(ProjectWorkflowPreferences.projectKey(owner, repo))

    fun saveWorkflowId(owner: String, repo: String, workflowId: Long) {
        workflowPreferences.setWorkflowId(
            ProjectWorkflowPreferences.projectKey(owner, repo),
            workflowId
        )
    }

    suspend fun resolveWorkflowId(
        token: String,
        owner: String,
        repo: String
    ): Pair<Long, String>? {
        val workflows = listWorkflows(token, owner, repo)
        if (workflows.isEmpty()) return null
        val saved = getSavedWorkflowId(owner, repo)
        val workflow = workflows.firstOrNull { it.id == saved }
            ?: pickDefaultWorkflow(workflows)
            ?: return null
        if (saved != workflow.id) {
            saveWorkflowId(owner, repo, workflow.id)
        }
        return workflow.id to workflow.name
    }

    suspend fun loadSummary(
        token: String,
        owner: String,
        repo: String,
        workflowId: Long,
        workflowName: String
    ): BuildStatusSummary {
        val runs = runsSinceLastSuccess(token, owner, repo, workflowId)
        val latest = runs.firstOrNull()
        val successIndex = runs.indexOfFirst { it.isSuccess() }
        val countSince = when {
            runs.isEmpty() -> 0
            successIndex < 0 -> runs.size
            else -> successIndex
        }
        return BuildStatusSummary(
            workflowName = workflowName,
            latestConclusion = latest?.displayConclusion() ?: "no runs",
            isInProgress = latest?.isInProgress() == true,
            runsSinceLastSuccess = countSince,
            latestRunUrl = latest?.htmlUrl?.takeIf { it.isNotBlank() },
            latestRunNumber = latest?.runNumber
        )
    }

    suspend fun runsSinceLastSuccess(
        token: String,
        owner: String,
        repo: String,
        workflowId: Long
    ): List<GitHubWorkflowRun> {
        val all = githubClient.listWorkflowRuns(token, owner, repo, workflowId)
        val result = mutableListOf<GitHubWorkflowRun>()
        for (run in all) {
            result.add(run)
            if (run.isSuccess()) break
        }
        return result
    }

    suspend fun loadRunDetails(
        token: String,
        owner: String,
        repo: String,
        runs: List<GitHubWorkflowRun>
    ): List<BuildRunDetail> = runs.map { run ->
        val jobs = try {
            githubClient.listRunJobs(token, owner, repo, run.id)
        } catch (_: Exception) {
            emptyList()
        }
        BuildRunDetail(run = run, jobs = jobs)
    }
}
