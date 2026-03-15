package fr.geoking.julius.di

import fr.geoking.julius.AndroidVoiceManager
import fr.geoking.julius.AndroidActionExecutor
import fr.geoking.julius.AppSettings
import fr.geoking.julius.AndroidPermissionManager
import fr.geoking.julius.SettingsManager
import fr.geoking.julius.AgentType
import fr.geoking.julius.agents.*
import fr.geoking.julius.shared.ConversationStore
import fr.geoking.julius.shared.VoiceManager
import fr.geoking.julius.shared.ActionExecutor
import fr.geoking.julius.shared.PermissionManager
import fr.geoking.julius.providers.GasApiProvider
import fr.geoking.julius.providers.EtalabProvider
import fr.geoking.julius.providers.DataGouvProvider
import fr.geoking.julius.providers.DataGouvElecProvider
import fr.geoking.julius.providers.OpenChargeMapClient
import fr.geoking.julius.providers.OpenChargeMapProvider
import fr.geoking.julius.providers.PoiProvider
import fr.geoking.julius.providers.RoutexProvider
import fr.geoking.julius.providers.JulesClient
import fr.geoking.julius.providers.SelectorPoiProvider
import fr.geoking.julius.providers.availability.BelibAvailabilityClient
import fr.geoking.julius.providers.availability.BelibAvailabilityProvider
import fr.geoking.julius.providers.availability.BorneAvailabilityProviderFactory
import fr.geoking.julius.routing.OsrmRoutingClient
import fr.geoking.julius.routing.RoutePlanner
import fr.geoking.julius.routing.RoutingClient
import org.koin.core.qualifier.named
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
import java.util.Locale

// Wrapper to switch agents at runtime without Koin reload
class DynamicAgentWrapper(
    private val client: HttpClient,
    private val settingsManager: SettingsManager
) : ConversationalAgent {

    private var cachedAgent: ConversationalAgent? = null
    private var cachedKey: String? = null

    private fun cacheKey(settings: AppSettings): String = buildString {
        append(settings.selectedAgent.name)
        append("|").append(settings.selectedModel.modelName)
        append("|").append(settings.openAiModel.modelName)
        append("|").append(settings.geminiModel.modelName)
        append("|").append(settings.extendedActionsEnabled)
        append("|").append(settings.openAiKey.take(8))
        append("|").append(settings.perplexityKey.take(8))
        append("|").append(settings.elevenLabsKey.take(8))
        append("|").append(settings.geminiKey.take(8))
        append("|").append(settings.deepgramKey.take(8))
        append("|").append(settings.firebaseAiKey.take(8))
        append("|").append(settings.firebaseAiModel)
        append("|").append(settings.opencodeZenKey.take(8))
        append("|").append(settings.opencodeZenModel)
        append("|").append(settings.completionsMeKey.take(8))
        append("|").append(settings.completionsMeModel)
        append("|").append(settings.apifreellmKey.take(8))
        append("|").append(settings.localModelPath)
    }
    
    override suspend fun process(input: String): AgentResponse {
        val settings = settingsManager.settings.value
        val key = cacheKey(settings)
        val agent = if (cachedKey == key && cachedAgent != null) {
            cachedAgent!!
        } else {
            android.util.Log.d("DynamicAgentWrapper", "Creating agent: ${settings.selectedAgent.name}")
            val newAgent = when (settings.selectedAgent) {
            AgentType.OpenAI -> OpenAIAgent(client, apiKey = settings.openAiKey, model = settings.openAiModel.modelName, toolsEnabled = settings.extendedActionsEnabled)
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
            AgentType.Gemini -> GeminiAgent(client, apiKey = settings.geminiKey, model = settings.geminiModel.modelName, toolsEnabled = settings.extendedActionsEnabled)
            AgentType.FirebaseAI -> FirebaseAIAgent(client, apiKey = settings.firebaseAiKey, model = settings.firebaseAiModel)
            AgentType.OpenCodeZen -> OpenCodeZenAgent(client, apiKey = settings.opencodeZenKey, model = settings.opencodeZenModel)
            AgentType.CompletionsMe -> CompletionsMeAgent(client, apiKey = settings.completionsMeKey, model = settings.completionsMeModel)
            AgentType.ApiFreeLLM -> ApiFreeLLMAgent(client, apiKey = settings.apifreellmKey)
            AgentType.Local -> LocalAgent(modelPath = settings.localModelPath) // No API key needed - runs offline
            AgentType.Offline -> OfflineAgent() // Fully offline agent - math, counting, hangman, quotes
            }
            cachedAgent = newAgent
            cachedKey = key
            newAgent
        }
        return agent.process(input)
    }
}

val appModule = module {
    single<HttpClient> {
        HttpClient(OkHttp) {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    encodeDefaults = true
                })
            }
        }
    }

    single { JulesClient(get()) }
    single { SettingsManager(androidContext()) }
    single { fr.geoking.julius.GoogleAuthManager(androidContext(), get(), get()) }
    
    // Use the dynamic wrapper instead of a static agent
    single<ConversationalAgent> {
        DynamicAgentWrapper(get(), get())
    }
    
    single<VoiceManager> {
        AndroidVoiceManager(androidContext(), get())
    }

    single<PermissionManager> {
        AndroidPermissionManager(androidContext())
    }

    // Map data source: Routex (default), Etalab, or DataGouv; selected in map screen.
    single<PoiProvider>(named("routex")) {
        RoutexProvider(get(), radiusKm = 5)
    }
    single<PoiProvider>(named("etalab")) {
        EtalabProvider(get(), radiusKm = 10, limit = 100)
    }
    single<PoiProvider>(named("gasapi")) {
        GasApiProvider(get(), radiusKm = 10, limit = 20)
    }
    single<PoiProvider>(named("datagouv")) {
        DataGouvProvider(get(), radiusKm = 10, limit = 100)
    }
    single<PoiProvider>(named("datagouvelec")) {
        DataGouvElecProvider(get(), radiusKm = 10, limit = 100)
    }
    single<OpenChargeMapClient> {
        OpenChargeMapClient(get(), apiKey = get<SettingsManager>().settings.value.openChargeMapKey.ifBlank { null })
    }
    single<PoiProvider>(named("openchargemap")) {
        OpenChargeMapProvider(get(), radiusKm = 10, limit = 50)
    }
    single<PoiProvider> {
        SelectorPoiProvider(
            routex = get(named("routex")),
            etalab = get(named("etalab")),
            gasApi = get(named("gasapi")),
            dataGouv = get(named("datagouv")),
            dataGouvElec = get(named("datagouvelec")),
            openChargeMap = get(named("openchargemap")),
            settingsManager = get()
        )
    }

    // Borne availability (e.g. Belib Paris): factory returns provider for current location.
    single { BelibAvailabilityClient(get()) }
    single { BelibAvailabilityProvider(get(), radiusKm = 10, limit = 200) }
    single { BorneAvailabilityProviderFactory(get()) }

    single<RoutingClient> { OsrmRoutingClient(get()) }
    single<RoutePlanner> { RoutePlanner(get()) }

    single<ActionExecutor> {
        AndroidActionExecutor(androidContext(), get())
    }
    
    single {
        ConversationStore(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
            agent = get(),
            voiceManager = get(),
            actionExecutor = get(),
            initialSpeechLanguageTag = resolveInitialSpeechLanguageTag()
        )
    }
}

private fun resolveInitialSpeechLanguageTag(): String {
    val locale = Locale.getDefault()
    val lang = locale.language.lowercase(Locale.ROOT)
    return when (lang) {
        "en",
        "fr",
        "es",
        "de",
        "it",
        "pt",
        "ar",
        "ja",
        "ko",
        "zh",
        "ru",
        "hi" -> lang
        else -> "en"
    }
}
