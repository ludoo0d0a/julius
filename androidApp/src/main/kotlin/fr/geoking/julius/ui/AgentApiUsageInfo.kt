package fr.geoking.julius.ui

import fr.geoking.julius.AgentType
import fr.geoking.julius.api.codingagent.CodingAgentBackend

/** Which API provider the usage / billing page describes. */
sealed class AgentApiUsageTarget {
    abstract val displayName: String

    data class Voice(val agent: AgentType) : AgentApiUsageTarget() {
        override val displayName: String = agent.name
    }

    data class Coding(val backend: CodingAgentBackend) : AgentApiUsageTarget() {
        override val displayName: String = when (backend) {
            CodingAgentBackend.JULES -> "Jules"
            CodingAgentBackend.CLAUDE_CODE -> "Claude Code"
        }
    }

    fun encode(): String = when (this) {
        is Voice -> "v:${agent.name}"
        is Coding -> "c:${backend.name}"
    }

    companion object {
        fun decode(raw: String): AgentApiUsageTarget? = when {
            raw.startsWith("v:") -> runCatching {
                Voice(AgentType.valueOf(raw.removePrefix("v:")))
            }.getOrNull()
            raw.startsWith("c:") -> runCatching {
                Coding(CodingAgentBackend.valueOf(raw.removePrefix("c:")))
            }.getOrNull()
            else -> null
        }
    }
}

data class AgentApiUsageDocLink(val label: String, val url: String)

data class AgentApiUsageInfo(
    val summary: String,
    val billingModel: String,
    val tokenUsage: List<String>,
    val rateLimits: List<String>,
    val costNotes: List<String>,
    val docLinks: List<AgentApiUsageDocLink>,
)

object AgentApiUsageCatalog {

    fun infoFor(target: AgentApiUsageTarget): AgentApiUsageInfo = when (target) {
        is AgentApiUsageTarget.Voice -> voiceInfo(target.agent)
        is AgentApiUsageTarget.Coding -> codingInfo(target.backend)
    }

    private fun codingInfo(backend: CodingAgentBackend): AgentApiUsageInfo = when (backend) {
        CodingAgentBackend.CLAUDE_CODE -> AgentApiUsageInfo(
            summary = "Claude Managed Agents bill through your Anthropic organization: model tokens per API call plus managed sandbox runtime.",
            billingModel = "Anthropic Console billing. Model inference is charged at standard Claude API token rates (input + output tokens). " +
                "Managed Agents sessions also incur runtime charges for active sandbox time (see Anthropic pricing for current session-hour rates). " +
                "All usage shares your organization spend limit and usage tier.",
            tokenUsage = listOf(
                "Each session accumulates input_tokens and output_tokens in session.usage (also cache_creation / cache_read when prompt caching applies).",
                "Event stream includes span.model_request_end with per-request token counts.",
                "Use POST /v1/messages/count_tokens before direct Messages API calls to estimate cost (Managed Agents sessions report usage on the session object).",
                "Julius maps session.status and events locally; check platform.claude.com or the Console for authoritative usage totals.",
            ),
            rateLimits = listOf(
                "Rate limits and spend limits are enforced per Anthropic organization and usage tier.",
                "Each tier defines maximum requests per minute (RPM), tokens per minute (TPM), and monthly spend.",
                "Limits increase automatically as you use the API; view current limits in the Claude Console.",
                "429 / rate-limit errors mean RPM or TPM was exceeded; retry after the window resets or upgrade tier / Priority Tier.",
                "Managed Agents beta endpoints require the anthropic-beta: managed-agents-2026-04-01 header.",
            ),
            costNotes = listOf(
                "Long-running coding sessions with many tool calls consume more output tokens than short chat turns.",
                "Opus-class models cost more per token than Sonnet or Haiku; agent defaults may use frontier models.",
                "Workspaces in Console help segment API keys and control spend by use case.",
            ),
            docLinks = listOf(
                AgentApiUsageDocLink("Claude API — rate limits & availability", CredentialUrls.ANTHROPIC_RATE_LIMITS),
                AgentApiUsageDocLink("Anthropic rate limits (tiers & token bucket)", CredentialUrls.ANTHROPIC_RATE_LIMITS_DETAIL),
                AgentApiUsageDocLink("Claude pricing", CredentialUrls.ANTHROPIC_PRICING),
                AgentApiUsageDocLink("Managed Agents overview", CredentialUrls.ANTHROPIC_MANAGED_AGENTS),
                AgentApiUsageDocLink("Console — limits & usage", CredentialUrls.ANTHROPIC_CONSOLE_LIMITS),
            ),
        )
        CodingAgentBackend.JULES -> AgentApiUsageInfo(
            summary = "Jules API usage is measured in tasks (sessions), not tokens. Limits depend on your Jules / Google AI plan.",
            billingModel = "Task-based quotas on a rolling 24-hour window plus a concurrent-task cap. " +
                "Paid tiers are bundled with Google AI Pro / Ultra subscriptions (individual @gmail.com accounts at launch). " +
                "The Jules API (v1alpha) is experimental; limits and plans may change.",
            tokenUsage = listOf(
                "Jules does not expose per-request token billing in the public API — consumption is counted as tasks/sessions.",
                "Each createSession call that runs work consumes daily task quota; parallel queue slots consume concurrent quota.",
                "Julius queue policy (daily limit per account, parallel limit) mirrors Jules limits locally — align with your plan.",
                "Activities and session outputs reflect agent progress; they are not a billing meter.",
            ),
            rateLimits = listOf(
                "Free: 15 daily tasks (rolling 24 h), 3 concurrent tasks.",
                "Jules in Pro (Google AI Pro): 100 daily tasks, 15 concurrent.",
                "Jules in Ultra (Google AI Ultra): 300 daily tasks, 60 concurrent.",
                "When daily quota is exhausted, new sessions fail until the rolling window resets.",
                "API authentication uses X-Goog-Api-Key; quota exceeded returns a quota / rate error from the API.",
            ),
            costNotes = listOf(
                "Up to 3 API keys per Jules account; rotate keys in jules.google.com settings.",
                "Same Gemini models power Jules; higher plans unlock newer / higher-access models.",
                "No separate token invoice — upgrade Google AI plan for higher task ceilings.",
            ),
            docLinks = listOf(
                AgentApiUsageDocLink("Jules limits & plans", CredentialUrls.JULES_USAGE_LIMITS),
                AgentApiUsageDocLink("Jules FAQ (pricing)", CredentialUrls.JULES_FAQ),
                AgentApiUsageDocLink("Jules API reference", CredentialUrls.JULES_API_DOCS),
            ),
        )
    }

    private fun voiceInfo(agent: AgentType): AgentApiUsageInfo = when (agent) {
        AgentType.OpenAI -> AgentApiUsageInfo(
            summary = "OpenAI bills per token for chat (GPT) and per character for TTS when audio is generated.",
            billingModel = "Pay-as-you-go on your OpenAI platform account. Chat uses input + output tokens; TTS-1-HD is billed by synthesized characters.",
            tokenUsage = listOf(
                "GPT-4o chat: input and output tokens per conversation turn.",
                "TTS-1-HD: billed by characters in the spoken response when Julius uses OpenAI audio.",
                "Typical voice turn: roughly $0.01–0.05 depending on length (chat + TTS).",
            ),
            rateLimits = listOf(
                "RPM and TPM limits depend on your OpenAI usage tier and model.",
                "429 responses indicate rate limit; check platform.openai.com usage dashboard.",
            ),
            costNotes = listOf(
                "Premium quality; highest cost among built-in cloud agents.",
                "No free production tier for GPT-4o + TTS-1-HD at scale.",
            ),
            docLinks = listOf(
                AgentApiUsageDocLink("OpenAI pricing", CredentialUrls.OPENAI_PRICING),
                AgentApiUsageDocLink("OpenAI usage limits", CredentialUrls.OPENAI_USAGE_POLICIES),
            ),
        )
        AgentType.Gemini -> AgentApiUsageInfo(
            summary = "Gemini API offers a generous free tier; paid usage is token-based beyond free limits.",
            billingModel = "Google AI / Gemini API billing. Free tier: ~60 RPM, 1,500 RPD, 1M tokens/day (check current quotas). Paid tier charges per million input/output tokens.",
            tokenUsage = listOf(
                "Flash models: input + output tokens per request.",
                "Julius uses system TTS — no separate TTS API cost.",
                "Typical turn on free tier: $0; paid tier ~$0.001–0.005 per interaction.",
            ),
            rateLimits = listOf(
                "Free tier: requests/minute and requests/day caps apply per project/API key.",
                "429 RESOURCE_EXHAUSTED when quota exceeded; resets daily or per minute window.",
            ),
            costNotes = listOf(
                "Best default for high-volume testing.",
                "Monitor ai.google.dev or Google AI Studio usage dashboard.",
            ),
            docLinks = listOf(
                AgentApiUsageDocLink("Gemini API pricing", CredentialUrls.GEMINI_PRICING),
                AgentApiUsageDocLink("Gemini rate limits", CredentialUrls.GEMINI_RATE_LIMITS),
            ),
        )
        AgentType.ElevenLabs -> AgentApiUsageInfo(
            summary = "Hybrid agent: Perplexity tokens for chat + ElevenLabs characters for voice synthesis.",
            billingModel = "Two providers — Perplexity (chat tokens) and ElevenLabs (TTS characters). Both keys must stay within their respective quotas.",
            tokenUsage = listOf(
                "Perplexity sonar: input + output tokens per answer (web-aware).",
                "ElevenLabs: characters synthesized per spoken response.",
                "ElevenLabs free tier ~10k characters/month; Perplexity pay-per-use.",
            ),
            rateLimits = listOf(
                "Perplexity: model-specific RPM; check perplexity.ai settings.",
                "ElevenLabs: character quota per subscription tier.",
            ),
            costNotes = listOf(
                "Two API calls per voice turn → higher latency and combined cost.",
                "Premium voice quality; good balance of web search + TTS.",
            ),
            docLinks = listOf(
                AgentApiUsageDocLink("Perplexity pricing", CredentialUrls.PERPLEXITY_PRICING),
                AgentApiUsageDocLink("ElevenLabs pricing", CredentialUrls.ELEVENLABS_PRICING),
            ),
        )
        AgentType.DeepSeek -> AgentApiUsageInfo(
            summary = "DeepSeek chat API — token-based pricing with competitive rates and free tier for new users.",
            billingModel = "Per-million-token pricing on DeepSeek platform; system TTS (no extra TTS fee).",
            tokenUsage = listOf("Input + output tokens per chat completion."),
            rateLimits = listOf("Provider rate limits apply per API key tier."),
            costNotes = listOf("Check platform for current free credits and model pricing."),
            docLinks = listOf(AgentApiUsageDocLink("DeepSeek pricing", CredentialUrls.DEEPSEEK_PRICING)),
        )
        AgentType.Groq -> AgentApiUsageInfo(
            summary = "Groq inference — fast open-model hosting with generous free tier.",
            billingModel = "Token-based; free tier available for many models.",
            tokenUsage = listOf("Input + output tokens per request on selected Groq model."),
            rateLimits = listOf("Groq enforces RPM/TPM per model on free and paid tiers."),
            costNotes = listOf("Very fast inference; monitor console.groq.com usage."),
            docLinks = listOf(AgentApiUsageDocLink("Groq pricing & limits", CredentialUrls.GROQ_PRICING)),
        )
        AgentType.OpenRouter -> AgentApiUsageInfo(
            summary = "Unified gateway to many models — billing varies by routed model.",
            billingModel = "OpenRouter credits; each model has its own $/token rate on the model page.",
            tokenUsage = listOf(
                "Tokens counted for the selected model route.",
                "Some models offer free or discounted access through OpenRouter.",
            ),
            rateLimits = listOf("Limits depend on upstream model and OpenRouter account tier."),
            costNotes = listOf("Pick model ID carefully — costs span free to premium."),
            docLinks = listOf(AgentApiUsageDocLink("OpenRouter models & pricing", CredentialUrls.OPENROUTER_PRICING)),
        )
        AgentType.Deepgram -> AgentApiUsageInfo(
            summary = "Deepgram voice API (STT-focused; full Julius agent integration may be incomplete).",
            billingModel = "Usage-based per audio minute for speech-to-text when used.",
            tokenUsage = listOf("STT billed by audio duration, not LLM tokens."),
            rateLimits = listOf("Free tier: ~12,000 minutes/month (verify current Deepgram plan)."),
            costNotes = listOf("Implementation status in Julius may be partial — check agent availability."),
            docLinks = listOf(AgentApiUsageDocLink("Deepgram pricing", CredentialUrls.DEEPGRAM_PRICING)),
        )
        AgentType.FirebaseAI -> AgentApiUsageInfo(
            summary = "Firebase / Google GenAI backend — typically token-based via Google Cloud or Firebase billing.",
            billingModel = "Linked to Firebase/Google Cloud project quotas and billing account.",
            tokenUsage = listOf("Model tokens per generateContent call."),
            rateLimits = listOf("Firebase AI quotas per project; see Google Cloud console."),
            costNotes = listOf("Enable billing on GCP project for production scale."),
            docLinks = listOf(AgentApiUsageDocLink("Firebase AI / Gemini on Google Cloud", CredentialUrls.GEMINI_PRICING)),
        )
        AgentType.OpenCodeZen -> AgentApiUsageInfo(
            summary = "OpenCode Zen API — token-based chat; verify current plan on provider site.",
            billingModel = "Provider-specific token or subscription pricing.",
            tokenUsage = listOf("Input + output tokens per completion."),
            rateLimits = listOf("See OpenCode Zen dashboard for RPM and quotas."),
            costNotes = emptyList(),
            docLinks = emptyList(),
        )
        AgentType.CompletionsMe -> AgentApiUsageInfo(
            summary = "Completions.me API — token-based chat completions.",
            billingModel = "Provider account billing; typically per-token or bundled credits.",
            tokenUsage = listOf("Input + output tokens per request."),
            rateLimits = listOf("Check provider dashboard for limits."),
            costNotes = emptyList(),
            docLinks = emptyList(),
        )
        AgentType.ApiFreeLLM -> AgentApiUsageInfo(
            summary = "ApiFreeLLM — free/low-cost LLM gateway with usage caps.",
            billingModel = "Free tier with rate limits; paid upgrades on provider site.",
            tokenUsage = listOf("Tokens per request; free tier may cap daily requests."),
            rateLimits = listOf("Strict RPM/RPD on free keys — expect throttling under load."),
            costNotes = listOf("Good for experiments; not for production volume."),
            docLinks = emptyList(),
        )
        else -> embeddedInfo(agent)
    }

    private fun embeddedInfo(agent: AgentType): AgentApiUsageInfo = AgentApiUsageInfo(
        summary = "${agent.name} runs on-device — no cloud API token billing.",
        billingModel = "No API key cost. One-time model download (storage) and on-device CPU/GPU inference. Battery and RAM usage increase during inference.",
        tokenUsage = listOf(
            "Inference is local — no input/output token charges from a cloud provider.",
            "Model size affects storage (roughly 650 MB – 3 GB+ for GGUF variants).",
        ),
        rateLimits = listOf(
            "Limited by device CPU/GPU throughput and available RAM.",
            "No cloud RPM/TPM — only local queueing in Julius.",
        ),
        costNotes = listOf(
            "100% offline capable after model download.",
            "Smaller quantized models trade quality for speed and memory.",
        ),
        docLinks = emptyList(),
    )
}
