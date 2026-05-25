package fr.geoking.julius.api.github

import fr.geoking.julius.shared.network.NetworkException
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * Minimal GitHub REST client for pull requests on a single repo (status, merge, close, issue comments).
 * Use a [classic PAT](https://github.com/settings/tokens) or fine-grained token with Contents + Pull requests (and Issues for comments).
 */
class GitHubClient(
    private val client: HttpClient,
    private val baseUrl: String = "https://api.github.com"
) {
    private val json = Json { ignoreUnknownKeys = true }

    private fun requireToken(token: String): String {
        if (token.isBlank()) {
            throw NetworkException(
                httpCode = null,
                message = "GitHub token is required. Add one in Settings → API keys → GitHub.",
                provider = "GitHub"
            )
        }
        return token
    }

    private fun HttpRequestBuilder.githubHeaders(token: String) {
        header("Authorization", "Bearer ${requireToken(token)}")
        header("Accept", "application/vnd.github+json")
        header("X-GitHub-Api-Version", "2022-11-28")
    }

    @Serializable
    data class GitHubPullRequestSummary(
        val number: Int = 0,
        val title: String = "",
        val state: String = "",
        @SerialName("html_url") val htmlUrl: String = "",
        val draft: Boolean = false,
        @SerialName("mergeable_state") val mergeableState: String? = null
    )

    suspend fun listOpenPullRequests(token: String, owner: String, repo: String): List<GitHubPullRequestSummary> =
        listPullRequests(token, owner, repo, state = "open")

    suspend fun listPullRequests(
        token: String,
        owner: String,
        repo: String,
        state: String = "open",
        head: String? = null
    ): List<GitHubPullRequestSummary> {
        requireToken(token)
        val url = "$baseUrl/repos/$owner/$repo/pulls"
        val response = client.get(url) {
            githubHeaders(token)
            parameter("state", state)
            if (head != null) parameter("head", head)
            parameter("per_page", "30")
            parameter("sort", "updated")
            parameter("direction", "desc")
        }
        val body = response.bodyAsText()
        if (response.status.value != 200) {
            throw NetworkException(
                httpCode = response.status.value,
                message = "GitHub list PRs: $body",
                url = url,
                provider = "GitHub"
            )
        }
        return json.decodeFromString(ListSerializer(GitHubPullRequestSummary.serializer()), body)
    }

    @Serializable
    data class GitHubPullRequestDetail(
        val number: Int = 0,
        val title: String = "",
        val state: String = "",
        val body: String? = null,
        @SerialName("html_url") val htmlUrl: String = "",
        val draft: Boolean = false,
        val merged: Boolean = false,
        val mergeable: Boolean? = null,
        @SerialName("mergeable_state") val mergeableState: String? = null,
        val head: GitHubRef? = null,
        val base: GitHubRef? = null,
        val repository: GitHubRepoSummary? = null
    )

    @Serializable
    data class GitHubRepoSummary(
        @SerialName("full_name") val fullName: String = ""
    )

    @Serializable
    data class GitHubRepositoryInfo(
        @SerialName("default_branch") val defaultBranch: String = "main",
    )

    suspend fun getRepository(token: String, owner: String, repo: String): GitHubRepositoryInfo {
        requireToken(token)
        val url = "$baseUrl/repos/$owner/$repo"
        val response = client.get(url) {
            githubHeaders(token)
        }
        val body = response.bodyAsText()
        if (response.status.value != 200) {
            throw NetworkException(
                httpCode = response.status.value,
                message = "GitHub get repository: $body",
                url = url,
                provider = "GitHub"
            )
        }
        return json.decodeFromString(GitHubRepositoryInfo.serializer(), body)
    }

    @Serializable
    data class GitHubRef(
        val ref: String = "",
        val sha: String = "",
        val repo: GitHubRepoSummary? = null
    )

    suspend fun getPullRequest(token: String, owner: String, repo: String, number: Int): GitHubPullRequestDetail {
        requireToken(token)
        val url = "$baseUrl/repos/$owner/$repo/pulls/$number"
        val response = client.get(url) {
            githubHeaders(token)
        }
        val body = response.bodyAsText()
        if (response.status.value != 200) {
            throw NetworkException(
                httpCode = response.status.value,
                message = "GitHub get PR: $body",
                url = url,
                provider = "GitHub"
            )
        }
        return json.decodeFromString(GitHubPullRequestDetail.serializer(), body)
    }

    @Serializable
    data class GitHubFile(
        val filename: String = "",
        val status: String = "",
        val sha: String = "",
        @SerialName("raw_url") val rawUrl: String = ""
    )

    suspend fun getPullRequestFiles(token: String, owner: String, repo: String, number: Int): List<GitHubFile> {
        requireToken(token)
        val url = "$baseUrl/repos/$owner/$repo/pulls/$number/files"
        val response = client.get(url) {
            githubHeaders(token)
        }
        val body = response.bodyAsText()
        if (response.status.value != 200) {
            throw NetworkException(
                httpCode = response.status.value,
                message = "GitHub list PR files: $body",
                url = url,
                provider = "GitHub"
            )
        }
        return json.decodeFromString(ListSerializer(GitHubFile.serializer()), body)
    }

    @Serializable
    data class GitHubContent(
        val content: String? = null,
        val sha: String = "",
        val encoding: String? = null
    )

    suspend fun getFileContent(token: String, owner: String, repo: String, path: String, ref: String): GitHubContent {
        requireToken(token)
        val url = "$baseUrl/repos/$owner/$repo/contents/$path"
        val response = client.get(url) {
            githubHeaders(token)
            parameter("ref", ref)
        }
        val body = response.bodyAsText()
        if (response.status.value != 200) {
            throw NetworkException(
                httpCode = response.status.value,
                message = "GitHub get file content: $body",
                url = url,
                provider = "GitHub"
            )
        }
        return json.decodeFromString(GitHubContent.serializer(), body)
    }

    @Serializable
    private data class UpdateFileBody(
        val message: String,
        val content: String,
        val sha: String,
        val branch: String
    )

    suspend fun updateFileContent(
        token: String,
        owner: String,
        repo: String,
        path: String,
        contentBase64: String,
        message: String,
        sha: String,
        branch: String
    ) {
        requireToken(token)
        val url = "$baseUrl/repos/$owner/$repo/contents/$path"
        val response = client.put(url) {
            githubHeaders(token)
            contentType(ContentType.Application.Json)
            setBody(UpdateFileBody(message, contentBase64, sha, branch))
        }
        if (response.status.value !in 200..299) {
            throw NetworkException(
                httpCode = response.status.value,
                message = "GitHub update file: ${response.bodyAsText()}",
                url = url,
                provider = "GitHub"
            )
        }
    }

    @Serializable
    private data class MergeBody(val merge_method: String = "merge")

    suspend fun mergePullRequest(token: String, owner: String, repo: String, number: Int) {
        requireToken(token)
        val url = "$baseUrl/repos/$owner/$repo/pulls/$number/merge"
        val response = client.put(url) {
            githubHeaders(token)
            contentType(ContentType.Application.Json)
            setBody(MergeBody())
        }
        if (response.status.value !in 200..299) {
            throw NetworkException(
                httpCode = response.status.value,
                message = "GitHub merge PR: ${response.bodyAsText()}",
                url = url,
                provider = "GitHub"
            )
        }
    }

    @Serializable
    private data class PatchPrBody(val state: String)

    suspend fun closePullRequest(token: String, owner: String, repo: String, number: Int) {
        requireToken(token)
        val url = "$baseUrl/repos/$owner/$repo/pulls/$number"
        val response = client.patch(url) {
            githubHeaders(token)
            contentType(ContentType.Application.Json)
            setBody(PatchPrBody(state = "closed"))
        }
        if (response.status.value !in 200..299) {
            throw NetworkException(
                httpCode = response.status.value,
                message = "GitHub close PR: ${response.bodyAsText()}",
                url = url,
                provider = "GitHub"
            )
        }
    }

    @Serializable
    private data class IssueCommentBody(val body: String)

    /**
     * Posts an issue comment on the PR. [commentText] is sent prefixed with "@jules " when it does not already start with that (case-insensitive).
     */
    suspend fun addCommentOnPullRequest(token: String, owner: String, repo: String, number: Int, commentText: String) {
        requireToken(token)
        val trimmed = commentText.trim()
        val bodyText = if (trimmed.startsWith("@jules", ignoreCase = true)) {
            trimmed
        } else {
            "@jules $trimmed"
        }
        val url = "$baseUrl/repos/$owner/$repo/issues/$number/comments"
        val response = client.post(url) {
            githubHeaders(token)
            contentType(ContentType.Application.Json)
            setBody(IssueCommentBody(body = bodyText))
        }
        if (response.status.value !in 200..299) {
            throw NetworkException(
                httpCode = response.status.value,
                message = "GitHub PR comment: ${response.bodyAsText()}",
                url = url,
                provider = "GitHub"
            )
        }
    }

    // --- GitHub Actions (workflows / runs / jobs) ---

    suspend fun listWorkflows(token: String, owner: String, repo: String): List<GitHubWorkflow> {
        requireToken(token)
        val url = "$baseUrl/repos/$owner/$repo/actions/workflows"
        val response = client.get(url) {
            githubHeaders(token)
            parameter("per_page", "100")
        }
        val body = response.bodyAsText()
        if (response.status.value != 200) {
            throw NetworkException(
                httpCode = response.status.value,
                message = "GitHub list workflows: $body",
                url = url,
                provider = "GitHub"
            )
        }
        return json.decodeFromString(GitHubWorkflowsResponse.serializer(), body).workflows
    }

    suspend fun listWorkflowRuns(
        token: String,
        owner: String,
        repo: String,
        workflowId: Long,
        perPage: Int = 30
    ): List<GitHubWorkflowRun> {
        requireToken(token)
        val url = "$baseUrl/repos/$owner/$repo/actions/workflows/$workflowId/runs"
        val response = client.get(url) {
            githubHeaders(token)
            parameter("per_page", perPage.toString())
        }
        val body = response.bodyAsText()
        if (response.status.value != 200) {
            throw NetworkException(
                httpCode = response.status.value,
                message = "GitHub list workflow runs: $body",
                url = url,
                provider = "GitHub"
            )
        }
        return json.decodeFromString(GitHubWorkflowRunsResponse.serializer(), body).workflowRuns
    }

    suspend fun listRunJobs(token: String, owner: String, repo: String, runId: Long): List<GitHubWorkflowJob> {
        requireToken(token)
        val url = "$baseUrl/repos/$owner/$repo/actions/runs/$runId/jobs"
        val response = client.get(url) {
            githubHeaders(token)
            parameter("per_page", "100")
        }
        val body = response.bodyAsText()
        if (response.status.value != 200) {
            throw NetworkException(
                httpCode = response.status.value,
                message = "GitHub list run jobs: $body",
                url = url,
                provider = "GitHub"
            )
        }
        return json.decodeFromString(GitHubWorkflowJobsResponse.serializer(), body).jobs
    }

    @Serializable
    private data class CreatePullRequestBody(
        val title: String,
        val head: String,
        val base: String,
        val body: String? = null
    )

    suspend fun createPullRequest(
        token: String,
        owner: String,
        repo: String,
        title: String,
        head: String,
        base: String,
        body: String? = null
    ): GitHubPullRequestDetail {
        requireToken(token)
        val url = "$baseUrl/repos/$owner/$repo/pulls"
        val response = client.post(url) {
            githubHeaders(token)
            contentType(ContentType.Application.Json)
            setBody(CreatePullRequestBody(title, head, base, body))
        }
        val responseBody = response.bodyAsText()
        if (response.status.value !in 200..299) {
            throw NetworkException(
                httpCode = response.status.value,
                message = "GitHub create PR: $responseBody",
                url = url,
                provider = "GitHub"
            )
        }
        return json.decodeFromString(GitHubPullRequestDetail.serializer(), responseBody)
    }
}

data class GitHubPrRef(val owner: String, val repo: String, val number: Int)

fun parseGitHubPullRequestUrl(url: String): GitHubPrRef? {
    val m = Regex("github\\.com/([^/]+)/([^/]+)/pull/(\\d+)", RegexOption.IGNORE_CASE).find(url.trim()) ?: return null
    return GitHubPrRef(m.groupValues[1], m.groupValues[2], m.groupValues[3].toInt())
}

data class GitHubBranchRef(val owner: String, val repo: String, val branch: String)

fun parseGitHubBranchUrl(url: String): GitHubBranchRef? {
    // GitHub branch URLs are in the form https://github.com/owner/repo/tree/branch-name
    // Branch name can contain slashes, so we match everything after 'tree/' until a space or end of string.
    val m = Regex("github\\.com/([^/]+)/([^/]+)/tree/(\\S+)", RegexOption.IGNORE_CASE).find(url.trim()) ?: return null
    return GitHubBranchRef(m.groupValues[1], m.groupValues[2], m.groupValues[3])
}
