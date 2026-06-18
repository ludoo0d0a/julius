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

    // --- Usage / billing documentation ---

    const val ANTHROPIC_RATE_LIMITS = "https://platform.claude.com/docs/en/api/overview#rate-limits-and-availability"
    const val ANTHROPIC_RATE_LIMITS_DETAIL = "https://docs.anthropic.com/en/api/rate-limits"
    const val ANTHROPIC_PRICING = "https://docs.anthropic.com/en/about-claude/pricing"
    const val ANTHROPIC_MANAGED_AGENTS = "https://platform.claude.com/docs/en/managed-agents/overview"
    const val ANTHROPIC_CONSOLE_LIMITS = "https://console.anthropic.com/settings/limits"

    const val JULES_USAGE_LIMITS = "https://jules.google/docs/usage-limits/"
    const val JULES_FAQ = "https://jules.google/docs/faq/"
    const val JULES_API_DOCS = "https://developers.google.com/jules/api"

    const val OPENAI_PRICING = "https://openai.com/api/pricing/"
    const val OPENAI_USAGE_POLICIES = "https://platform.openai.com/docs/guides/rate-limits"

    const val GEMINI_PRICING = "https://ai.google.dev/gemini-api/docs/pricing"
    const val GEMINI_RATE_LIMITS = "https://ai.google.dev/gemini-api/docs/rate-limits"

    const val PERPLEXITY_PRICING = "https://docs.perplexity.ai/guides/pricing"
    const val ELEVENLABS_PRICING = "https://elevenlabs.io/pricing"

    const val DEEPSEEK_PRICING = "https://api-docs.deepseek.com/quick_start/pricing"
    const val GROQ_PRICING = "https://groq.com/pricing"
    const val OPENROUTER_PRICING = "https://openrouter.ai/models"
    const val DEEPGRAM_PRICING = "https://deepgram.com/pricing"
}
