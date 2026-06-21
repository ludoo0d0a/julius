package fr.geoking.julius.queue

import fr.geoking.julius.api.github.GitHubClient
import fr.geoking.julius.api.github.parseGitHubPullRequestUrl
import fr.geoking.julius.persistence.FeatureEntity
import fr.geoking.julius.persistence.JulesSessionEntity
import fr.geoking.julius.repository.GitHubBuildRepository
import fr.geoking.julius.repository.JulesRepository

class FeatureGitHubLifecycle(
    private val julesRepository: JulesRepository,
    private val buildRepository: GitHubBuildRepository,
    private val githubClient: GitHubClient,
) {

    suspend fun processOpenPr(
        feature: FeatureEntity,
        session: JulesSessionEntity,
        githubToken: String,
        autoMergeOnCiSuccess: Boolean,
    ): FeatureEntity {
        val prUrl = session.prUrl ?: return feature
        if (session.prState != "open" && session.prState != "draft") return feature

        val prRef = parseGitHubPullRequestUrl(prUrl) ?: return feature
        val prDetail = try {
            githubClient.getPullRequest(githubToken, prRef.owner, prRef.repo, prRef.number)
        } catch (_: Exception) {
            return feature
        }

        val state = prDetail.toPrState()
        val mergeable = prDetail.mergeable
        val mergeableState = prDetail.mergeableState
        julesRepository.updateSessionPrStatus(session.id, state, mergeable, mergeableState)

        if (!autoMergeOnCiSuccess || mergeable != true || state != "open") return feature

        val resolved = buildRepository.resolveWorkflowId(githubToken, prRef.owner, prRef.repo)
            ?: return feature
        val (workflowId, workflowName) = resolved

        val summary = try {
            buildRepository.loadSummary(githubToken, prRef.owner, prRef.repo, workflowId, workflowName)
        } catch (_: Exception) {
            return feature
        }

        if (summary.isInProgress) return feature
        if (!summary.latestConclusion.equals("success", ignoreCase = true)) return feature

        julesRepository.mergePr(githubToken, session.id, prUrl, deleteBranch = true)
        return feature
    }
}
