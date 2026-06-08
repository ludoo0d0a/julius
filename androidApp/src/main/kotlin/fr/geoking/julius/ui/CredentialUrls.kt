package fr.geoking.julius.ui

/**
 * Deep links to create or manage API keys and tokens used in Settings.
 */
object CredentialUrls {
    /** Anthropic Console — create Claude / Managed Agents API keys. */
    const val ANTHROPIC_API_KEYS = "https://console.anthropic.com/settings/keys"

    /** Jules web app — API key section in Settings. */
    const val JULES_API_KEYS = "https://jules.google.com/settings#api"

    /** GitHub — list existing personal access tokens. */
    const val GITHUB_MANAGE_TOKENS = "https://github.com/settings/tokens"
}
