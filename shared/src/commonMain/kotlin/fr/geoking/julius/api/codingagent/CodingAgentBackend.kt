package fr.geoking.julius.api.codingagent

/**
 * Remote coding agent provider for the Jules screen.
 * - [JULES]: Google Jules API (jules.google.com)
 * - [CLAUDE_CODE]: Anthropic Claude Managed Agents API (cloud GitHub workflow)
 */
enum class CodingAgentBackend {
    JULES,
    CLAUDE_CODE;

    fun matchesSessionId(sessionId: String): Boolean = when (this) {
        JULES -> !sessionId.startsWith(CLAUDE_SESSION_PREFIX) && !sessionId.startsWith("offline_")
        CLAUDE_CODE -> sessionId.startsWith(CLAUDE_SESSION_PREFIX) || sessionId.startsWith("offline_")
    }

    companion object {
        const val CLAUDE_SESSION_PREFIX = "sesn_"

        fun fromName(name: String?): CodingAgentBackend =
            entries.find { it.name.equals(name, ignoreCase = true) } ?: JULES
    }
}
