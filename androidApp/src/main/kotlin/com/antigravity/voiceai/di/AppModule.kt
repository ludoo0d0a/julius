package com.antigravity.voiceai.di

import com.antigravity.voiceai.AndroidVoiceManager
import com.antigravity.voiceai.AndroidActionExecutor
import com.antigravity.voiceai.SettingsManager
import com.antigravity.voiceai.AgentType
import com.antigravity.voiceai.agents.*
import com.antigravity.voiceai.shared.ConversationStore
import com.antigravity.voiceai.shared.VoiceManager
import com.antigravity.voiceai.shared.ActionExecutor
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

// Wrapper to switch agents at runtime without Koin reload
class DynamicAgentWrapper(
    private val client: HttpClient,
    private val settingsManager: SettingsManager
) : ConversationalAgent {
    
    override suspend fun process(input: String): AgentResponse {
        val settings = settingsManager.settings.value
        val agent = when (settings.selectedAgent) {
            AgentType.OpenAI -> OpenAIAgent(client, apiKey = settings.openAiKey)
            AgentType.ElevenLabs -> ElevenLabsAgent(client, perplexityKey = settings.perplexityKey, elevenLabsKey = settings.elevenLabsKey)
            AgentType.Deepgram -> DeepgramAgent(client, deepgramKey = settings.deepgramKey)
            AgentType.Native -> NativeAgent(client, apiKey = settings.perplexityKey)
            AgentType.Gemini -> GeminiAgent(client, apiKey = settings.geminiKey)
        }
        return agent.process(input)
    }
}

val appModule = module {
    single<HttpClient> {
        HttpClient(OkHttp) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }
    }

    single { SettingsManager(androidContext()) }
    
    // Use the dynamic wrapper instead of a static agent
    single<ConversationalAgent> {
        DynamicAgentWrapper(get(), get())
    }
    
    single<VoiceManager> {
        AndroidVoiceManager(androidContext())
    }
    
    single<ActionExecutor> {
        AndroidActionExecutor(androidContext())
    }
    
    single {
        ConversationStore(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
            agent = get(),
            voiceManager = get(),
            actionExecutor = get()
        )
    }
}
