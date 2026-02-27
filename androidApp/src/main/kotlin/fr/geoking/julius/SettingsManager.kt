package fr.geoking.julius

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AgentType { OpenAI, ElevenLabs, Deepgram, Native, Gemini, Genkit, FirebaseAI, Embedded }
enum class AppTheme { Particles, Sphere, Waves, Fractal, Micro }
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
    val openAiKey: String = "",
    val elevenLabsKey: String = "",
    val perplexityKey: String = "",
    val geminiKey: String = "",
    val deepgramKey: String = "",
    val genkitApiKey: String = "",
    val genkitEndpoint: String = "",
    val firebaseAiKey: String = "",
    val firebaseAiModel: String = "gemini-1.5-flash-latest",
    val selectedAgent: AgentType = AgentType.Deepgram,
    val selectedTheme: AppTheme = AppTheme.Particles,
    val selectedModel: IaModel = IaModel.LLAMA_3_1_SONAR_SMALL,
    val fractalQuality: FractalQuality = FractalQuality.Medium,
    val fractalColorIntensity: FractalColorIntensity = FractalColorIntensity.Medium
)

open class SettingsManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("voice_ai_prefs", Context.MODE_PRIVATE)
    
    // Simple state flow to observe changes
    private val _settings = MutableStateFlow(loadSettings())
    open val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private fun loadSettings(): AppSettings {
        return AppSettings(
            openAiKey = prefs.getString("openai_key", "") ?.takeIf { it.isNotEmpty() } ?: fr.geoking.julius.BuildConfig.OPENAI_KEY,
            elevenLabsKey = prefs.getString("elevenlabs_key", "")?.takeIf { it.isNotEmpty() } ?: fr.geoking.julius.BuildConfig.ELEVENLABS_KEY,
            perplexityKey = prefs.getString("perplexity_key", "") ?.takeIf { it.isNotEmpty() } ?: fr.geoking.julius.BuildConfig.PERPLEXITY_KEY,
            geminiKey = prefs.getString("gemini_key", "")?.takeIf { it.isNotEmpty() } ?: fr.geoking.julius.BuildConfig.GEMINI_KEY,
            deepgramKey = prefs.getString("deepgram_key", "")?.takeIf { it.isNotEmpty() } ?: fr.geoking.julius.BuildConfig.DEEPGRAM_KEY,
            genkitApiKey = prefs.getString("genkit_key", "")?.takeIf { it.isNotEmpty() } ?: fr.geoking.julius.BuildConfig.GENKIT_KEY,
            genkitEndpoint = prefs.getString("genkit_endpoint", "")?.takeIf { it.isNotEmpty() } ?: fr.geoking.julius.BuildConfig.GENKIT_ENDPOINT,
            firebaseAiKey = prefs.getString("firebase_ai_key", "")?.takeIf { it.isNotEmpty() } ?: fr.geoking.julius.BuildConfig.FIREBASE_AI_KEY,
            firebaseAiModel = prefs.getString("firebase_ai_model", "")?.takeIf { it.isNotEmpty() } ?: fr.geoking.julius.BuildConfig.FIREBASE_AI_MODEL,
            selectedAgent = try {
                val agentName = prefs.getString("agent", null)
                if (agentName != null) {
                    AgentType.valueOf(agentName)
                } else {
                    AgentType.Deepgram // Default fallback
                }
            } catch (e: IllegalArgumentException) {
                // Invalid agent name in preferences, use default
                android.util.Log.w("SettingsManager", "Invalid agent name in preferences, using default: ${e.message}")
                AgentType.Deepgram
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
            }
        )
    }

    open fun saveSettings(
        openAiKey: String,
        elevenLabsKey: String,
        perplexityKey: String,
        geminiKey: String,
        deepgramKey: String,
        genkitApiKey: String,
        genkitEndpoint: String,
        firebaseAiKey: String,
        firebaseAiModel: String,
        agent: AgentType,
        theme: AppTheme,
        model: IaModel,
        fractalQuality: FractalQuality = FractalQuality.Medium,
        fractalColorIntensity: FractalColorIntensity = FractalColorIntensity.Medium
    ) {
        prefs.edit()
            .putString("openai_key", openAiKey)
            .putString("elevenlabs_key", elevenLabsKey)
            .putString("perplexity_key", perplexityKey)
            .putString("gemini_key", geminiKey)
            .putString("deepgram_key", deepgramKey)
            .putString("genkit_key", genkitApiKey)
            .putString("genkit_endpoint", genkitEndpoint)
            .putString("firebase_ai_key", firebaseAiKey)
            .putString("firebase_ai_model", firebaseAiModel)
            .putString("agent", agent.name)
            .putString("theme", theme.name)
            .putString("model", model.name)
            .putString("fractal_quality", fractalQuality.name)
            .putString("fractal_color_intensity", fractalColorIntensity.name)
            .apply()
        
        // Update StateFlow immediately with the new values to ensure UI and agent switching update right away
        _settings.value = AppSettings(
            openAiKey = openAiKey,
            elevenLabsKey = elevenLabsKey,
            perplexityKey = perplexityKey,
            geminiKey = geminiKey,
            deepgramKey = deepgramKey,
            genkitApiKey = genkitApiKey,
            genkitEndpoint = genkitEndpoint,
            firebaseAiKey = firebaseAiKey,
            firebaseAiModel = firebaseAiModel,
            selectedAgent = agent,
            selectedTheme = theme,
            selectedModel = model,
            fractalQuality = fractalQuality,
            fractalColorIntensity = fractalColorIntensity
        )
    }
}
