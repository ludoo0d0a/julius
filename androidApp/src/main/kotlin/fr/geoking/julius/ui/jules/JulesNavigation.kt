package fr.geoking.julius.ui.jules

import fr.geoking.julius.persistence.FeatureEntity
import fr.geoking.julius.persistence.JulesSessionEntity

enum class JulesScreenLevel {
    Projects,
    Features,
    Conversations,
    GitDetails,
}

object JulesNavigation {
    const val ORPHAN_FEATURE_ID = "__orphan__"
    const val ORPHAN_FEATURE_TITLE = "Other conversations"

    fun isOrphanFeature(featureId: String?): Boolean = featureId == ORPHAN_FEATURE_ID

    fun sessionsForFeature(
        sessions: List<JulesSessionEntity>,
        sourceName: String,
        featureId: String,
    ): List<JulesSessionEntity> {
        val repoSessions = sessions.filter { it.sourceName == sourceName && !it.isArchived }
        return if (isOrphanFeature(featureId)) {
            repoSessions.filter { it.featureId.isNullOrBlank() }
        } else {
            repoSessions.filter { it.featureId == featureId }
        }
    }

    fun featureTitle(featureId: String, features: List<FeatureEntity>): String {
        if (isOrphanFeature(featureId)) return ORPHAN_FEATURE_TITLE
        return features.find { it.id == featureId }?.title ?: "Feature"
    }

    fun sessionCountForFeature(
        sessions: List<JulesSessionEntity>,
        sourceName: String,
        featureId: String,
    ): Int = sessionsForFeature(sessions, sourceName, featureId).size
}
