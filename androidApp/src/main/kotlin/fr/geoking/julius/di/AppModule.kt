package fr.geoking.julius.di

import fr.geoking.julius.AndroidVoiceManager
import fr.geoking.julius.AndroidActionExecutor
import fr.geoking.julius.AppSettings
import fr.geoking.julius.AndroidPermissionManager
import fr.geoking.julius.GoogleAuthManager
import fr.geoking.julius.SettingsManager
import fr.geoking.julius.AgentType
import fr.geoking.julius.agents.*
import fr.geoking.julius.shared.ConversationStore
import fr.geoking.julius.shared.LocalTranscriber
import fr.geoking.julius.shared.VoiceManager
import fr.geoking.julius.voice.VoskTranscriber
import fr.geoking.julius.shared.ActionExecutor
import fr.geoking.julius.shared.PermissionManager
import fr.geoking.julius.api.availability.BelibAvailabilityClient
import fr.geoking.julius.api.availability.BelibAvailabilityProvider
import fr.geoking.julius.api.availability.BorneAvailabilityProvider
import fr.geoking.julius.api.availability.BorneAvailabilityProviderFactory
import fr.geoking.julius.api.datagouv.DataGouvCampingClient
import fr.geoking.julius.api.datagouv.DataGouvCampingProvider
import fr.geoking.julius.api.datagouv.DataGouvElecProvider
import fr.geoking.julius.api.datagouv.DataGouvProvider
import fr.geoking.julius.api.etalab.EtalabProvider
import fr.geoking.julius.api.gas.GasApiProvider
import fr.geoking.julius.api.jules.JulesClient
import fr.geoking.julius.api.openchargemap.OpenChargeMapClient
import fr.geoking.julius.api.openchargemap.OpenChargeMapProvider
import fr.geoking.julius.api.overpass.OverpassClient
import fr.geoking.julius.api.overpass.OverpassProvider
import fr.geoking.julius.api.parking.ParkingProviderFactory
import fr.geoking.julius.api.routing.OsrmRoutingClient
import fr.geoking.julius.api.routing.RoutePlanner
import fr.geoking.julius.api.routing.RoutingClient
import fr.geoking.julius.api.routex.RoutexProvider
import fr.geoking.julius.api.transit.BelgiumTransitProvider
import fr.geoking.julius.api.transit.FranceTransitProvider
import fr.geoking.julius.api.transit.LuxembourgTransitProvider
import fr.geoking.julius.api.traffic.CitaTrafficClient
import fr.geoking.julius.api.traffic.CitaTrafficProvider
import fr.geoking.julius.api.traffic.GeographicRegion
import fr.geoking.julius.api.traffic.TrafficProviderFactory
import fr.geoking.julius.community.CommunityPoiRepository
import fr.geoking.julius.community.FavoritesRepository
import fr.geoking.julius.community.LocalCommunityPoiRepository
import fr.geoking.julius.community.LocalFavoritesRepository
import fr.geoking.julius.community.storage.CommunityPoiStorage
import fr.geoking.julius.community.storage.FavoritePoiStorage
import fr.geoking.julius.parking.ParkingAggregator
import fr.geoking.julius.poi.MergedPoiProvider
import fr.geoking.julius.poi.PoiProvider
import fr.geoking.julius.poi.SelectorPoiProvider
import fr.geoking.julius.api.toll.OpenTollDataParser
import fr.geoking.julius.transit.TransitAggregator
import fr.geoking.julius.transit.TransitApiSelector
import fr.geoking.julius.transit.TransitProvider
import fr.geoking.julius.toll.TollCalculator
import fr.geoking.julius.ui.OpenTollDataHelper
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

    single<JulesClient> { JulesClient(get()) }
    single<SettingsManager> { SettingsManager(androidContext()) }
    single<GoogleAuthManager> {
        GoogleAuthManager(androidContext(), get(), { get<ConversationStore>() })
    }
    
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
    single { OverpassClient(get()) }
    single<PoiProvider>(named("overpass")) {
        OverpassProvider(get(), radiusKm = 5, limit = 100)
    }
    single { DataGouvCampingClient(get()) }
    single<PoiProvider>(named("datagouvcamping")) {
        DataGouvCampingProvider(get(), radiusKm = 15, limit = 50)
    }
    single<PoiProvider>(named("selector")) {
        SelectorPoiProvider(
            routex = get(named("routex")),
            etalab = get(named("etalab")),
            gasApi = get(named("gasapi")),
            dataGouv = get(named("datagouv")),
            dataGouvElec = get(named("datagouvelec")),
            openChargeMap = get(named("openchargemap")),
            overpass = get(named("overpass")),
            dataGouvCamping = get(named("datagouvcamping")),
            settingsManager = get()
        )
    }
    single { CommunityPoiStorage(androidContext()) }
    single { FavoritePoiStorage(androidContext()) }
    single<CommunityPoiRepository> { LocalCommunityPoiRepository(get()) }
    single<FavoritesRepository> { LocalFavoritesRepository(get()) }
    single<PoiProvider> {
        MergedPoiProvider(base = get(named("selector")), communityRepo = get())
    }

    // Borne availability (e.g. Belib Paris): factory returns provider for current location.
    single { BelibAvailabilityClient(get()) }
    single<BorneAvailabilityProvider> { BelibAvailabilityProvider(get(), radiusKm = 10, limit = 200) }
    single<BorneAvailabilityProviderFactory> { BorneAvailabilityProviderFactory(get()) }

    // Traffic (e.g. Luxembourg CITA): factory returns provider for current location.
    single { CitaTrafficClient(get()) }
    single { CitaTrafficProvider(get()) }
    single<TrafficProviderFactory> {
        TrafficProviderFactory(
            listOf(
                GeographicRegion.Bbox(49.4, 5.7, 50.2, 6.6) to get<CitaTrafficProvider>()
            )
        )
    }

    single<RoutingClient> { OsrmRoutingClient(get()) }
    single<RoutePlanner> { RoutePlanner(get()) }

    // Transit (bus/tram): location-based provider selection (France, Luxembourg, Belgium).
    single<TransitProvider>(named("fr_ratp")) { FranceTransitProvider(get()) }
    single<TransitProvider>(named("lu_mobiliteit")) {
        val sm = get<SettingsManager>()
        val key = sm.settings.value.mobiliteitLuxembourgKey.ifBlank { fr.geoking.julius.BuildConfig.MOBILITEIT_LUXEMBOURG_KEY }
        LuxembourgTransitProvider(get(), key)
    }
    single<TransitProvider>(named("be_stib")) { BelgiumTransitProvider(get()) }
    single<List<TransitProvider>>(named("transitProviders")) {
        listOf(get(named("fr_ratp")), get(named("lu_mobiliteit")), get(named("be_stib")))
    }
    single { TransitApiSelector(get(named("transitProviders"))) }
    single { TransitAggregator(get(named("transitProviders")), get()) }

    // Parking POIs: LiveParking + ParkAPI + OSM, aggregated via factory
    single<ParkingProviderFactory> { ParkingProviderFactory(get(), get()) }
    single<ParkingAggregator> { get<ParkingProviderFactory>().createAggregator() }

    single { OpenTollDataHelper(androidContext()) }
    single<TollCalculator> {
        val settingsManager = get<SettingsManager>()
        TollCalculator(dataSource = {
            val path = settingsManager.settings.value.tollDataPath ?: return@TollCalculator null
            val file = java.io.File(path)
            if (!file.exists()) return@TollCalculator null
            OpenTollDataParser.parse(file.readText())
        })
    }

    single<ActionExecutor> {
        AndroidActionExecutor(androidContext(), get())
    }

    single { VoskTranscriber(androidContext(), modelDirPath = null) }
    single<LocalTranscriber> { get<VoskTranscriber>() }
    
    single<ConversationStore> {
        val settingsManager = get<SettingsManager>()
        ConversationStore(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
            agent = get(),
            voiceManager = get(),
            actionExecutor = get(),
            initialSpeechLanguageTag = resolveInitialSpeechLanguageTag(),
            localTranscriber = get(),
            sttPreference = { settingsManager.settings.value.sttEnginePreference }
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
