package com.antigravity.voiceai

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AgentType { OpenAI, ElevenLabs, Deepgram, Native }
enum class AppTheme { Particles, Sphere, Waves }

data class AppSettings(
    val openAiKey: String = "",
    val elevenLabsKey: String = "",
    val perplexityKey: String = "",
    val selectedAgent: AgentType = AgentType.OpenAI,
    val selectedTheme: AppTheme = AppTheme.Particles
)

class SettingsManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("voice_ai_prefs", Context.MODE_PRIVATE)
    
    // Simple state flow to observe changes
    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private fun loadSettings(): AppSettings {
        return AppSettings(
            openAiKey = prefs.getString("openai_key", "") ?: "",
            elevenLabsKey = prefs.getString("elevenlabs_key", "") ?: "",
            perplexityKey = prefs.getString("perplexity_key", "") ?: "",
            selectedAgent = AgentType.valueOf(prefs.getString("agent", AgentType.OpenAI.name) ?: AgentType.OpenAI.name),
            selectedTheme = AppTheme.valueOf(prefs.getString("theme", AppTheme.Particles.name) ?: AppTheme.Particles.name)
        )
    }

    fun saveSettings(
        openAiKey: String,
        elevenLabsKey: String,
        perplexityKey: String,
        agent: AgentType,
        theme: AppTheme
    ) {
        prefs.edit()
            .putString("openai_key", openAiKey)
            .putString("elevenlabs_key", elevenLabsKey)
            .putString("perplexity_key", perplexityKey)
            .putString("agent", agent.name)
            .putString("theme", theme.name)
            .apply()
            
        _settings.value = loadSettings()
    }
}
