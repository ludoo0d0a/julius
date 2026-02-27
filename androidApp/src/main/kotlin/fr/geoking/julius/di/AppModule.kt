package fr.geoking.julius.di

import fr.geoking.julius.AndroidVoiceManager
import fr.geoking.julius.AndroidActionExecutor
import fr.geoking.julius.AndroidPermissionManager
import fr.geoking.julius.SettingsManager
import fr.geoking.julius.AgentType
import fr.geoking.julius.agents.*
import fr.geoking.julius.shared.ConversationStore
import fr.geoking.julius.shared.VoiceManager
import fr.geoking.julius.shared.ActionExecutor
import fr.geoking.julius.shared.PermissionManager
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
        // Always read fresh settings value to ensure we use the latest agent selection
        val settings = settingsManager.settings.value
        
        // Debug logging to help identify state management issues
        android.util.Log.d("DynamicAgentWrapper", "Processing with agent: ${settings.selectedAgent.name}")
        
        val agent = when (settings.selectedAgent) {
            AgentType.OpenAI -> OpenAIAgent(client, apiKey = settings.openAiKey, toolsEnabled = settings.extendedActionsEnabled)
            AgentType.ElevenLabs -> {
                // Ensure required keys are present for ElevenLabs
                if (settings.perplexityKey.isBlank() || settings.elevenLabsKey.isBlank()) {
                    android.util.Log.w("DynamicAgentWrapper", "ElevenLabs selected but missing keys (perplexity: ${settings.perplexityKey.isNotBlank()}, elevenlabs: ${settings.elevenLabsKey.isNotBlank()})")
                }
                ElevenLabsAgent(client, perplexityKey = settings.perplexityKey, elevenLabsKey = settings.elevenLabsKey, model = settings.selectedModel.modelName)
            }
            AgentType.Deepgram -> {
                android.util.Log.d("DynamicAgentWrapper", "Creating Deepgram agent (this should not happen if ElevenLabs is selected)")
                DeepgramAgent(client, deepgramKey = settings.deepgramKey)
            }
            AgentType.Native -> PerplexityAgent(client, apiKey = settings.perplexityKey, model = settings.selectedModel.modelName)
            AgentType.Gemini -> GeminiAgent(client, apiKey = settings.geminiKey, toolsEnabled = settings.extendedActionsEnabled)
            AgentType.Genkit -> GenkitAgent(client, endpoint = settings.genkitEndpoint, apiKey = settings.genkitApiKey)
            AgentType.FirebaseAI -> FirebaseAIAgent(client, apiKey = settings.firebaseAiKey, model = settings.firebaseAiModel)
            AgentType.Embedded -> EmbeddedAgent() // No API key needed - runs offline
        }
        
        android.util.Log.d("DynamicAgentWrapper", "Agent created: ${agent::class.simpleName}, processing input...")
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

    single<PermissionManager> {
        AndroidPermissionManager(androidContext())
    }
    
    single<ActionExecutor> {
        AndroidActionExecutor(androidContext(), get())
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
