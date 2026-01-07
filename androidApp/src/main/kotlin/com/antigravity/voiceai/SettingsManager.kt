package com.antigravity.voiceai

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AgentType { OpenAI, ElevenLabs, Deepgram, Native, Gemini }
enum class AppTheme { Particles, Sphere, Waves }
enum class IaModel(val modelName: String, val displayName: String) {
    LLAMA_3_1_SONAR_SMALL("llama-3.1-sonar-small-128k-online", "Sonar Small"),
    LLAMA_3_1_SONAR_LARGE("llama-3.1-sonar-large-128k-online", "Sonar Large")
}

data class AppSettings(
    val openAiKey: String = "",
    val elevenLabsKey: String = "",
    val perplexityKey: String = "",
    val geminiKey: String = "",
    val deepgramKey: String = "",
    val selectedAgent: AgentType = AgentType.OpenAI,
    val selectedTheme: AppTheme = AppTheme.Particles,
    val selectedModel: IaModel = IaModel.LLAMA_3_1_SONAR_SMALL
)

class SettingsManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("voice_ai_prefs", Context.MODE_PRIVATE)
    
    // Simple state flow to observe changes
    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private fun loadSettings(): AppSettings {
        return AppSettings(
            openAiKey = prefs.getString("openai_key", "") ?: "",
            elevenLabsKey = prefs.getString("elevenlabs_key", "")?.takeIf { it.isNotEmpty() } ?: com.antigravity.voiceai.BuildConfig.ELEVENLABS_KEY,
            perplexityKey = prefs.getString("perplexity_key", "") ?: "",
            geminiKey = prefs.getString("gemini_key", "")?.takeIf { it.isNotEmpty() } ?: com.antigravity.voiceai.BuildConfig.GEMINI_KEY,
            deepgramKey = prefs.getString("deepgram_key", "")?.takeIf { it.isNotEmpty() } ?: com.antigravity.voiceai.BuildConfig.DEEPGRAM_KEY,
            selectedAgent = AgentType.valueOf(prefs.getString("agent", AgentType.OpenAI.name) ?: AgentType.OpenAI.name),
            selectedTheme = AppTheme.valueOf(prefs.getString("theme", AppTheme.Particles.name) ?: AppTheme.Particles.name),
            selectedModel = IaModel.valueOf(prefs.getString("model", IaModel.LLAMA_3_1_SONAR_SMALL.name) ?: IaModel.LLAMA_3_1_SONAR_SMALL.name)
        )
    }

    fun saveSettings(
        openAiKey: String,
        elevenLabsKey: String,
        perplexityKey: String,
        geminiKey: String,
        deepgramKey: String,
        agent: AgentType,
        theme: AppTheme,
        model: IaModel
    ) {
        prefs.edit()
            .putString("openai_key", openAiKey)
            .putString("elevenlabs_key", elevenLabsKey)
            .putString("perplexity_key", perplexityKey)
            .putString("gemini_key", geminiKey)
            .putString("deepgram_key", deepgramKey)
            .putString("agent", agent.name)
            .putString("theme", theme.name)
            .putString("model", model.name)
            .apply()
            
        _settings.value = loadSettings()
    }
}
