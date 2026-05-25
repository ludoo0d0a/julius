package fr.geoking.julius.designassistant

import fr.geoking.julius.api.github.parseGitHubPullRequestUrl
import fr.geoking.julius.api.jules.JulesChatItem
import fr.geoking.julius.api.jules.JulesClient
import fr.geoking.julius.persistence.FeatureEntity
import fr.geoking.julius.persistence.JulesSessionEntity
import fr.geoking.julius.repository.BuildStatusSummary
import kotlinx.serialization.json.Json
import java.util.concurrent.TimeUnit
import kotlin.math.abs

object DesignAssistantMapper {

    const val ALL_OTHERS_FEATURE_ID = "__all_others__"
    const val ALL_OTHERS_TITLE = "All others"

    private val json = Json { ignoreUnknownKeys = true }

    fun sourceDisplayName(source: JulesClient.JulesSource): String =
        source.githubRepo?.let { "${it.owner}/${it.repo}" } ?: source.name

    fun sourceEmoji(source: JulesClient.JulesSource): String {
        val name = sourceDisplayName(source)
        return when (name.hashCode() % 4) {
            0 -> "📦"
            1 -> "🛒"
            2 -> "✈️"
            else -> "🔧"
        }
    }

    fun featureStatusFromEntity(status: String): FeatureStatus = when (status) {
        "IN_PROGRESS" -> FeatureStatus.IN_PROGRESS
        "COMPLETED" -> FeatureStatus.DONE
        "FAILED" -> FeatureStatus.TODO
        "QUEUED" -> FeatureStatus.READY
        else -> FeatureStatus.IDEA
    }

    fun sourceToProject(
        source: JulesClient.JulesSource,
        features: List<FeatureEntity>,
        sessions: List<JulesSessionEntity>,
        mainBranch: String,
    ): DesignProject {
        val sourceName = source.name
        val repoFeatures = features.filter { it.sourceName == sourceName }
        val repoSessions = sessions.filter { it.sourceName == sourceName && !it.isArchived }
        val lastMs = maxOf(
            repoFeatures.maxOfOrNull { it.updatedAt } ?: 0L,
            repoSessions.maxOfOrNull { it.lastUpdated } ?: 0L,
        )
        return DesignProject(
            id = sourceName,
            name = sourceDisplayName(source),
            emoji = sourceEmoji(source),
            activeFeaturesCount = repoFeatures.count { it.status == "IN_PROGRESS" },
            promptCount = repoSessions.size,
            mainBranch = mainBranch,
            lastModifiedLabel = formatRelativeTime(lastMs),
            description = "",
            features = emptyList(),
        )
    }

    fun buildProjectFeatures(
        sourceName: String,
        entities: List<FeatureEntity>,
        sessions: List<JulesSessionEntity>,
        allOthersSessionCount: Int,
    ): List<DesignFeature> {
        val repoSessions = sessions.filter { it.sourceName == sourceName && !it.isArchived }
        val latestByFeature = repoSessions
            .filter { !it.featureId.isNullOrBlank() }
            .groupBy { it.featureId!! }
            .mapValues { (_, list) -> list.maxByOrNull { it.lastUpdated } }

        val real = entities
            .filter { it.sourceName == sourceName }
            .sortedBy { it.position }
            .map { entity ->
                featureEntityToDesign(entity, latestByFeature[entity.id])
            }

        val allOthers = allOthersFeature(allOthersSessionCount, repoSessions)
        return real + allOthers
    }

    fun allOthersFeature(
        sessionCount: Int,
        unlinkedSessions: List<JulesSessionEntity>,
    ): DesignFeature {
        val latest = unlinkedSessions.filter { it.featureId == null }.maxByOrNull { it.lastUpdated }
        val name = if (sessionCount > 0) "$ALL_OTHERS_TITLE ($sessionCount)" else ALL_OTHERS_TITLE
        return DesignFeature(
            id = ALL_OTHERS_FEATURE_ID,
            name = name,
            status = if (sessionCount > 0) FeatureStatus.IDEA else FeatureStatus.TODO,
            branch = latest?.prBranch,
            prNumber = prNumberFromSession(latest),
            prTitle = latest?.prTitle,
        )
    }

    fun featureEntityToDesign(
        entity: FeatureEntity,
        latestSession: JulesSessionEntity?,
    ): DesignFeature = DesignFeature(
        id = entity.id,
        name = entity.title,
        status = featureStatusFromEntity(entity.status),
        branch = latestSession?.prBranch,
        prNumber = prNumberFromSession(latestSession),
        prTitle = latestSession?.prTitle ?: entity.description.takeIf { it.isNotBlank() },
    )

    fun isAllOthers(feature: DesignFeature): Boolean = feature.id == ALL_OTHERS_FEATURE_ID

    fun resolveSessionsForFeature(
        feature: DesignFeature,
        sourceName: String,
        sessions: List<JulesSessionEntity>,
        featureSessionId: String?,
    ): List<JulesSessionEntity> {
        val repoSessions = sessions
            .filter { it.sourceName == sourceName && !it.isArchived }
            .sortedByDescending { it.lastUpdated }
        return if (isAllOthers(feature)) {
            repoSessions.filter { it.featureId == null }
        } else {
            repoSessions.filter { it.featureId == feature.id }
        }
    }

    fun resolveActiveSession(
        feature: DesignFeature,
        sourceName: String,
        sessions: List<JulesSessionEntity>,
        featureSessionId: String?,
        selectedSessionId: String?,
    ): JulesSessionEntity? {
        if (selectedSessionId != null) {
            return sessions.find { it.id == selectedSessionId }
        }
        if (!isAllOthers(feature) && !featureSessionId.isNullOrBlank()) {
            sessions.find { it.id == featureSessionId }?.let { return it }
        }
        return resolveSessionsForFeature(feature, sourceName, sessions, featureSessionId).firstOrNull()
    }

    fun prNumberFromSession(session: JulesSessionEntity?): Int? {
        if (session == null) return null
        session.prUrl?.let { parseGitHubPullRequestUrl(it)?.number }?.let { return it }
        session.prId?.toIntOrNull()?.let { return it }
        return null
    }

    fun chatItemsToDesignMessages(items: List<JulesChatItem>): List<DesignChatMessage> =
        items.map { item ->
            when (item) {
                is JulesChatItem.UserMessage -> DesignChatMessage(
                    id = item.id,
                    kind = ChatMessageKind.USER,
                    text = item.text,
                )
                is JulesChatItem.AgentMessage -> {
                    val code = extractCodeFromText(item.text)
                    if (code != null) {
                        DesignChatMessage(
                            id = item.id,
                            kind = ChatMessageKind.CODE,
                            text = item.title,
                            codeSnippet = code,
                        )
                    } else {
                        DesignChatMessage(
                            id = item.id,
                            kind = ChatMessageKind.AGENT,
                            text = item.text.ifBlank { item.title },
                        )
                    }
                }
            }
        }

    fun buildCiMessage(summary: BuildStatusSummary?): DesignChatMessage? {
        if (summary == null) return null
        val status = when {
            summary.isInProgress -> "CI en cours — ${summary.workflowName}"
            summary.latestConclusion.equals("success", ignoreCase = true) -> "CI verte — ${summary.workflowName}"
            else -> "CI — ${summary.workflowName}: ${summary.latestConclusion}"
        }
        return DesignChatMessage(
            id = "ci_${summary.latestRunNumber ?: 0}",
            kind = ChatMessageKind.CI,
            text = status,
        )
    }

    data class WorkspaceArtifacts(
        val code: String,
        val files: List<String>,
    )

    fun extractWorkspaceArtifacts(activities: List<JulesClient.JulesActivity>): WorkspaceArtifacts {
        val files = mutableSetOf<String>()
        val codeParts = mutableListOf<String>()
        for (activity in activities) {
            activity.artifacts.orEmpty().forEach { artifact ->
                artifact.changeSet?.source?.takeIf { it.isNotBlank() }?.let { files.add(it) }
                artifact.changeSet?.gitPatch?.unidiffPatch?.takeIf { it.isNotBlank() }?.let { codeParts.add(it) }
                artifact.bashOutput?.let { bash ->
                    val block = buildString {
                        if (bash.command.isNotBlank()) append("$ ${bash.command}\n")
                        append(bash.output)
                    }
                    if (block.isNotBlank()) codeParts.add(block)
                }
            }
        }
        return WorkspaceArtifacts(
            code = codeParts.joinToString("\n\n---\n\n"),
            files = files.sorted(),
        )
    }

    fun decodeActivitiesFromCache(
        entities: List<fr.geoking.julius.persistence.JulesActivityEntity>,
    ): List<JulesClient.JulesActivity> =
        entities.mapNotNull { entity ->
            entity.activityJson?.let { aj ->
                try {
                    json.decodeFromString(JulesClient.JulesActivity.serializer(), aj)
                } catch (_: Exception) {
                    null
                }
            }
        }

    fun deployStatusLabel(summary: BuildStatusSummary?, loading: Boolean, error: String?): String? {
        if (loading) return "Deploy: chargement…"
        if (error != null) return "Deploy: $error"
        if (summary == null) return null
        return when {
            summary.isInProgress -> "Deploy: ${summary.workflowName} en cours"
            summary.latestConclusion.equals("success", ignoreCase = true) ->
                "Deploy: ${summary.workflowName} — succès"
            else -> "Deploy: ${summary.workflowName} — ${summary.latestConclusion}"
        }
    }

    fun prStateEmoji(prState: String?): String = when (prState) {
        "merged" -> "🟣"
        "closed" -> "🔴"
        "open" -> "🟢"
        else -> "🟡"
    }

    private fun extractCodeFromText(text: String): String? {
        val fenced = Regex("```[\\w]*\\n([\\s\\S]*?)```").find(text)?.groupValues?.get(1)?.trim()
        if (!fenced.isNullOrBlank()) return fenced
        return null
    }

    fun formatRelativeTime(epochMs: Long): String {
        if (epochMs <= 0L) return "—"
        val diff = abs(System.currentTimeMillis() - epochMs)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(diff)
        return when {
            minutes < 1 -> "à l'instant"
            minutes < 60 -> "il y a ${minutes} min"
            minutes < 24 * 60 -> "il y a ${minutes / 60} h"
            else -> "il y a ${minutes / (24 * 60)} j"
        }
    }
}
