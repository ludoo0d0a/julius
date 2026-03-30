package fr.geoking.julius.persistence

/**
 * No-op implementation of JulesDao for when the database fails to initialize.
 * All methods return empty results or do nothing, allowing the app to continue running
 * in a degraded mode without persistence.
 */
class NoOpJulesDao : JulesDao {
    override suspend fun insertSessions(sessions: List<JulesSessionEntity>) {
        // Do nothing
    }

    override suspend fun getSessionsBySource(sourceName: String): List<JulesSessionEntity> {
        return emptyList()
    }

    override suspend fun archiveSession(sessionId: String) {
        // Do nothing
    }

    override suspend fun getSession(sessionId: String): JulesSessionEntity? {
        return null
    }

    override suspend fun updateSessionPrState(sessionId: String, state: String) {
        // Do nothing
    }

    override suspend fun insertActivities(activities: List<JulesActivityEntity>) {
        // Do nothing
    }

    override suspend fun getActivitiesBySession(sessionId: String): List<JulesActivityEntity> {
        return emptyList()
    }

    override suspend fun clearActivitiesBySession(sessionId: String) {
        // Do nothing
    }
}
