package fr.geoking.julius

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AgentType { OpenAI, ElevenLabs, Deepgram, Native, Gemini, FirebaseAI, OpenCodeZen, CompletionsMe, ApiFreeLLM, Local, Offline }

val DEFAULT_AGENT = AgentType.Gemini

enum class AppTheme { Particles, Sphere, Waves, Fractal, Micro }
enum class TextAnimation { None, Genie, Blur, Fade, Zoom, Falling }
enum class FractalQuality { Low, Medium, High }
enum class FractalColorIntensity { Low, Medium, High }
enum class IaModel(val modelName: String, val displayName: String) {
    LLAMA_3_1_SONAR_SMALL("llama-3.1-sonar-small-128k-online", "Sonar Small"),
    LLAMA_3_1_SONAR_LARGE("llama-3.1-sonar-large-128k-online", "Sonar Large"),
    LLAMA_3_1_8B_INSTRUCT("llama-3.1-8b-instruct", "Llama 3.1 Instruct"),
    LLAMA_3_1_70B_INSTRUCT("llama-3.1-70b-instruct", "Llama 3.1 70B Instruct"),
    GEMMA_2_9B_IT("gemma-2-9b-it", "Gemma 2 9B"),
    GEMMA_2_27B_IT("gemma-2-27b-it", "Gemma 2 27B")
}

data class AppSettings(
    val selectedPoiProvider: fr.geoking.julius.providers.PoiProviderType = fr.geoking.julius.providers.PoiProviderType.Routex,
    val openAiKey: String = "",
    val elevenLabsKey: String = "",
    val perplexityKey: String = "",
    val geminiKey: String = "",
    val deepgramKey: String = "",
    val firebaseAiKey: String = "",
    val firebaseAiModel: String = "gemini-1.5-flash-latest",
    val opencodeZenKey: String = "",
    val opencodeZenModel: String = "minimax-m2.5-free",
    val completionsMeKey: String = "",
    val completionsMeModel: String = "claude-sonnet-4.5",
    val apifreellmKey: String = "",
    val julesKey: String = "",
    val selectedAgent: AgentType = DEFAULT_AGENT,
    val selectedTheme: AppTheme = AppTheme.Particles,
    val selectedModel: IaModel = IaModel.LLAMA_3_1_SONAR_SMALL,
    val fractalQuality: FractalQuality = FractalQuality.Medium,
    val fractalColorIntensity: FractalColorIntensity = FractalColorIntensity.Medium,
    val extendedActionsEnabled: Boolean = false,
    val textAnimation: TextAnimation = TextAnimation.Fade
)

open class SettingsManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("voice_ai_prefs", Context.MODE_PRIVATE)
    
    // Simple state flow to observe changes
    private val _settings = MutableStateFlow(loadSettings())
    open val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private fun loadSettings(): AppSettings {
        val openAiKey = prefs.getString("openai_key", "")?.takeIf { it.isNotEmpty() } ?: fr.geoking.julius.BuildConfig.OPENAI_KEY
        val elevenLabsKey = prefs.getString("elevenlabs_key", "")?.takeIf { it.isNotEmpty() } ?: fr.geoking.julius.BuildConfig.ELEVENLABS_KEY
        val perplexityKey = prefs.getString("perplexity_key", "")?.takeIf { it.isNotEmpty() } ?: fr.geoking.julius.BuildConfig.PERPLEXITY_KEY
        val geminiKey = prefs.getString("gemini_key", "")?.takeIf { it.isNotEmpty() } ?: fr.geoking.julius.BuildConfig.GEMINI_KEY
        val deepgramKey = prefs.getString("deepgram_key", "")?.takeIf { it.isNotEmpty() } ?: fr.geoking.julius.BuildConfig.DEEPGRAM_KEY
        val firebaseAiKey = prefs.getString("firebase_ai_key", "")?.takeIf { it.isNotEmpty() } ?: fr.geoking.julius.BuildConfig.FIREBASE_AI_KEY
        val firebaseAiModel = prefs.getString("firebase_ai_model", "")?.takeIf { it.isNotEmpty() } ?: fr.geoking.julius.BuildConfig.FIREBASE_AI_MODEL
        val opencodeZenKey = prefs.getString("opencode_zen_key", "")?.takeIf { it.isNotEmpty() } ?: fr.geoking.julius.BuildConfig.OPENCODE_ZEN_KEY
        val opencodeZenModel = prefs.getString("opencode_zen_model", "minimax-m2.5-free") ?: "minimax-m2.5-free"
        val completionsMeKey = prefs.getString("completions_me_key", "")?.takeIf { it.isNotEmpty() } ?: fr.geoking.julius.BuildConfig.COMPLETIONS_ME_KEY
        val completionsMeModel = prefs.getString("completions_me_model", "claude-sonnet-4.5") ?: "claude-sonnet-4.5"
        val apifreellmKey = prefs.getString("apifreellm_key", "")?.takeIf { it.isNotEmpty() } ?: fr.geoking.julius.BuildConfig.APIFREELLM_KEY
        val julesKey = prefs.getString("jules_key", "")?.takeIf { it.isNotEmpty() } ?: fr.geoking.julius.BuildConfig.JULES_KEY

        // Persist build-time keys (from env/local.properties) when prefs were empty so they show in settings and are reused
        persistBuildTimeKeysIfUsed(
            openAiKey, elevenLabsKey, perplexityKey, geminiKey, deepgramKey,
            firebaseAiKey, firebaseAiModel, opencodeZenKey, opencodeZenModel,
            completionsMeKey, completionsMeModel, apifreellmKey, julesKey
        )

        return AppSettings(
            selectedPoiProvider = try {
                fr.geoking.julius.providers.PoiProviderType.valueOf(
                    prefs.getString("poi_provider", fr.geoking.julius.providers.PoiProviderType.Routex.name) ?: fr.geoking.julius.providers.PoiProviderType.Routex.name
                )
            } catch (e: IllegalArgumentException) {
                fr.geoking.julius.providers.PoiProviderType.Routex
            },
            openAiKey = openAiKey,
            elevenLabsKey = elevenLabsKey,
            perplexityKey = perplexityKey,
            geminiKey = geminiKey,
            deepgramKey = deepgramKey,
            firebaseAiKey = firebaseAiKey,
            firebaseAiModel = firebaseAiModel,
            opencodeZenKey = opencodeZenKey,
            opencodeZenModel = opencodeZenModel,
            completionsMeKey = completionsMeKey,
            completionsMeModel = completionsMeModel,
            apifreellmKey = apifreellmKey,
            julesKey = julesKey,
            selectedAgent = try {
                val agentName = prefs.getString("agent", null)
                if (agentName != null) AgentType.valueOf(agentName)
                else DEFAULT_AGENT
            } catch (e: IllegalArgumentException) {
                android.util.Log.w("SettingsManager", "Invalid agent name in preferences, using default: ${e.message}")
                DEFAULT_AGENT
            },
            selectedTheme = try {
                AppTheme.valueOf(prefs.getString("theme", AppTheme.Micro.name) ?: AppTheme.Micro.name)
            } catch (e: IllegalArgumentException) {
                AppTheme.Micro
            },
            selectedModel = IaModel.valueOf(prefs.getString("model", IaModel.LLAMA_3_1_SONAR_SMALL.name) ?: IaModel.LLAMA_3_1_SONAR_SMALL.name),
            fractalQuality = try {
                FractalQuality.valueOf(prefs.getString("fractal_quality", FractalQuality.Medium.name) ?: FractalQuality.Medium.name)
            } catch (e: IllegalArgumentException) {
                FractalQuality.Medium
            },
            fractalColorIntensity = try {
                FractalColorIntensity.valueOf(prefs.getString("fractal_color_intensity", FractalColorIntensity.Medium.name) ?: FractalColorIntensity.Medium.name)
            } catch (e: IllegalArgumentException) {
                FractalColorIntensity.Medium
            },
            extendedActionsEnabled = prefs.getBoolean("extended_actions_enabled", false),
            textAnimation = try {
                TextAnimation.valueOf(prefs.getString("text_animation", TextAnimation.Fade.name) ?: TextAnimation.Fade.name)
            } catch (e: IllegalArgumentException) {
                TextAnimation.Fade
            }
        )
    }

    /**
     * When loading settings we use BuildConfig as fallback when prefs are empty (e.g. keys from CI env).
     * Persist those build-time values to prefs once so they appear in settings and are reused.
     */
    private fun persistBuildTimeKeysIfUsed(
        openAiKey: String,
        elevenLabsKey: String,
        perplexityKey: String,
        geminiKey: String,
        deepgramKey: String,
        firebaseAiKey: String,
        firebaseAiModel: String,
        opencodeZenKey: String,
        opencodeZenModel: String,
        completionsMeKey: String,
        completionsMeModel: String,
        apifreellmKey: String,
        julesKey: String
    ) {
        val edit = prefs.edit()
        if (prefs.getString("openai_key", "")?.isEmpty() != false && openAiKey.isNotEmpty()) edit.putString("openai_key", openAiKey)
        if (prefs.getString("elevenlabs_key", "")?.isEmpty() != false && elevenLabsKey.isNotEmpty()) edit.putString("elevenlabs_key", elevenLabsKey)
        if (prefs.getString("perplexity_key", "")?.isEmpty() != false && perplexityKey.isNotEmpty()) edit.putString("perplexity_key", perplexityKey)
        if (prefs.getString("gemini_key", "")?.isEmpty() != false && geminiKey.isNotEmpty()) edit.putString("gemini_key", geminiKey)
        if (prefs.getString("deepgram_key", "")?.isEmpty() != false && deepgramKey.isNotEmpty()) edit.putString("deepgram_key", deepgramKey)
        if (prefs.getString("firebase_ai_key", "")?.isEmpty() != false && firebaseAiKey.isNotEmpty()) edit.putString("firebase_ai_key", firebaseAiKey)
        if (prefs.getString("firebase_ai_model", "")?.isEmpty() != false && firebaseAiModel.isNotEmpty()) edit.putString("firebase_ai_model", firebaseAiModel)
        if (prefs.getString("opencode_zen_key", "")?.isEmpty() != false && opencodeZenKey.isNotEmpty()) edit.putString("opencode_zen_key", opencodeZenKey)
        if (prefs.getString("opencode_zen_model", "")?.isEmpty() != false && opencodeZenModel.isNotEmpty()) edit.putString("opencode_zen_model", opencodeZenModel)
        if (prefs.getString("completions_me_key", "")?.isEmpty() != false && completionsMeKey.isNotEmpty()) edit.putString("completions_me_key", completionsMeKey)
        if (prefs.getString("completions_me_model", "")?.isEmpty() != false && completionsMeModel.isNotEmpty()) edit.putString("completions_me_model", completionsMeModel)
        if (prefs.getString("apifreellm_key", "")?.isEmpty() != false && apifreellmKey.isNotEmpty()) edit.putString("apifreellm_key", apifreellmKey)
        if (prefs.getString("jules_key", "")?.isEmpty() != false && julesKey.isNotEmpty()) edit.putString("jules_key", julesKey)
        edit.apply()
    }

    open fun setPoiProviderType(type: fr.geoking.julius.providers.PoiProviderType) {
        prefs.edit().putString("poi_provider", type.name).apply()
        _settings.value = _settings.value.copy(selectedPoiProvider = type)
    }

    open fun saveSettings(settings: AppSettings) {
        var currentSettings = _settings.value
        var finalSettings = settings

        if (settings.selectedTheme != currentSettings.selectedTheme) {
            // Theme changed, pick a random animation
            val animations = TextAnimation.entries.filter { it != TextAnimation.None }
            val randomAnimation = animations.random()
            finalSettings = settings.copy(textAnimation = randomAnimation)
        }

        saveSettingsInternal(finalSettings)
    }

    private fun saveSettingsInternal(settings: AppSettings) {
        prefs.edit()
            .putString("poi_provider", settings.selectedPoiProvider.name)
            .putString("openai_key", settings.openAiKey)
            .putString("elevenlabs_key", settings.elevenLabsKey)
            .putString("perplexity_key", settings.perplexityKey)
            .putString("gemini_key", settings.geminiKey)
            .putString("deepgram_key", settings.deepgramKey)
            .putString("firebase_ai_key", settings.firebaseAiKey)
            .putString("firebase_ai_model", settings.firebaseAiModel)
            .putString("opencode_zen_key", settings.opencodeZenKey)
            .putString("opencode_zen_model", settings.opencodeZenModel)
            .putString("completions_me_key", settings.completionsMeKey)
            .putString("completions_me_model", settings.completionsMeModel)
            .putString("apifreellm_key", settings.apifreellmKey)
            .putString("jules_key", settings.julesKey)
            .putString("agent", settings.selectedAgent.name)
            .putString("theme", settings.selectedTheme.name)
            .putString("model", settings.selectedModel.name)
            .putString("fractal_quality", settings.fractalQuality.name)
            .putString("fractal_color_intensity", settings.fractalColorIntensity.name)
            .putBoolean("extended_actions_enabled", settings.extendedActionsEnabled)
            .putString("text_animation", settings.textAnimation.name)
            .apply()

        // Update StateFlow immediately with the new values to ensure UI and agent switching update right away
        _settings.value = settings
    }

    open fun saveSettings(
        openAiKey: String,
        elevenLabsKey: String,
        perplexityKey: String,
        geminiKey: String,
        deepgramKey: String,
        firebaseAiKey: String,
        firebaseAiModel: String,
        opencodeZenKey: String = "",
        opencodeZenModel: String = "minimax-m2.5-free",
        completionsMeKey: String = "",
        completionsMeModel: String = "claude-sonnet-4.5",
        apifreellmKey: String = "",
        julesKey: String = "",
        agent: AgentType,
        theme: AppTheme,
        model: IaModel,
        fractalQuality: FractalQuality = FractalQuality.Medium,
        fractalColorIntensity: FractalColorIntensity = FractalColorIntensity.Medium,
        extendedActionsEnabled: Boolean = false
    ) {
        val newSettings = AppSettings(
            selectedPoiProvider = _settings.value.selectedPoiProvider,
            openAiKey = openAiKey,
            elevenLabsKey = elevenLabsKey,
            perplexityKey = perplexityKey,
            geminiKey = geminiKey,
            deepgramKey = deepgramKey,
            firebaseAiKey = firebaseAiKey,
            firebaseAiModel = firebaseAiModel,
            opencodeZenKey = opencodeZenKey,
            opencodeZenModel = opencodeZenModel,
            completionsMeKey = completionsMeKey,
            completionsMeModel = completionsMeModel,
            apifreellmKey = apifreellmKey,
            julesKey = julesKey.ifBlank { _settings.value.julesKey },
            selectedAgent = agent,
            selectedTheme = theme,
            selectedModel = model,
            fractalQuality = fractalQuality,
            fractalColorIntensity = fractalColorIntensity,
            extendedActionsEnabled = extendedActionsEnabled,
            textAnimation = _settings.value.textAnimation
        )
        saveSettings(newSettings)
    }
}
