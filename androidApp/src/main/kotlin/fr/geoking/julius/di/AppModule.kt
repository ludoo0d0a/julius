package fr.geoking.julius.di

import android.content.Context
import fr.geoking.julius.AndroidVoiceManager
import fr.geoking.julius.AndroidActionExecutor
import fr.geoking.julius.AndroidNetworkService
import fr.geoking.julius.AndroidWeatherLookup
import fr.geoking.julius.AppSettings
import fr.geoking.julius.AndroidPermissionManager
import fr.geoking.julius.GoogleAuthManager
import fr.geoking.julius.SettingsManager
import fr.geoking.julius.AgentType
import fr.geoking.julius.agents.*
import fr.geoking.julius.shared.BorderCrossingManager
import fr.geoking.julius.shared.ConversationStore
import fr.geoking.julius.shared.LocalTranscriber
import fr.geoking.julius.shared.NetworkService
import fr.geoking.julius.shared.VoiceManager
import fr.geoking.julius.voice.VoskTranscriber
import fr.geoking.julius.shared.ActionExecutor
import fr.geoking.julius.shared.PermissionManager
import fr.geoking.julius.shared.WeatherLookup
import fr.geoking.julius.api.jules.JulesClient
import fr.geoking.julius.api.github.GitHubClient
import fr.geoking.julius.repository.JulesRepository
import fr.geoking.julius.shared.MessagePersistence
import fr.geoking.julius.persistence.AppDatabase
import fr.geoking.julius.persistence.RoomMessagePersistence
import fr.geoking.julius.persistence.NoOpJulesDao
import androidx.room.Room
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.Locale
import kotlin.random.Random

// Wrapper to switch agents at runtime without Koin reload
class DynamicAgentWrapper(
    private val client: HttpClient,
    private val settingsManager: SettingsManager,
    private val llamaBackend: LlamaBackend,
    private val appContext: Context,
) : ConversationalAgent {

    private var cachedAgent: ConversationalAgent? = null
    private var cachedKey: String? = null

    private fun llamatik(settings: AppSettings) = LlamatikAgent(
        modelPath = settings.llamatikModelPath,
        backend = llamaBackend,
        extendedActionsEnabled = settings.extendedActionsEnabled,
    )

    /** Prefer Google AI Edge LiteRT-LM for `.litertlm`; fall back to Llamatik (GGUF). */
    private fun liteRtOrLlamatik(settings: AppSettings): ConversationalAgent {
        val p = settings.llamatikModelPath.trim()
        return if (p.endsWith(".litertlm", ignoreCase = true)) {
            LiteRtLmOnDeviceAgent(
                appContext.applicationContext,
                p,
                extendedActionsEnabled = settings.extendedActionsEnabled,
            )
        } else {
            llamatik(settings)
        }
    }

    private fun createAgent(settings: AppSettings): ConversationalAgent {
        android.util.Log.d("DynamicAgentWrapper", "Creating agent: ${settings.selectedAgent.name}")
        return when (settings.selectedAgent) {
        AgentType.OpenAI -> OpenAIAgent(client, apiKey = settings.openAiKey, model = settings.openAiModel.modelName, toolsEnabled = settings.extendedActionsEnabled)
        AgentType.ElevenLabs -> {
            if (settings.perplexityKey.isBlank() || settings.elevenLabsKey.isBlank()) {
                android.util.Log.w("DynamicAgentWrapper", "ElevenLabs selected but missing keys (perplexity: ${settings.perplexityKey.isNotBlank()}, elevenlabs: ${settings.elevenLabsKey.isNotBlank()})")
            }
            ElevenLabsAgent(client, perplexityKey = settings.perplexityKey, elevenLabsKey = settings.elevenLabsKey, model = settings.selectedModel.modelName)
        }
        AgentType.Deepgram -> {
            android.util.Log.d("DynamicAgentWrapper", "Creating Deepgram agent (this should not happen if ElevenLabs is selected)")
            DeepgramAgent(client, deepgramKey = settings.deepgramKey)
        }
        AgentType.Gemini -> GeminiAgent(client, apiKey = settings.geminiKey, model = settings.geminiModel.modelName, toolsEnabled = settings.extendedActionsEnabled)
        AgentType.FirebaseAI -> FirebaseAIAgent(client, apiKey = settings.firebaseAiKey, model = settings.firebaseAiModel)
        AgentType.OpenCodeZen -> OpenCodeZenAgent(client, apiKey = settings.opencodeZenKey, model = settings.opencodeZenModel)
        AgentType.CompletionsMe -> CompletionsMeAgent(client, apiKey = settings.completionsMeKey, model = settings.completionsMeModel)
        AgentType.ApiFreeLLM -> ApiFreeLLMAgent(client, apiKey = settings.apifreellmKey)
        AgentType.DeepSeek -> DeepSeekAgent(client, apiKey = settings.deepSeekKey, model = settings.deepSeekModel)
        AgentType.Groq -> GroqAgent(client, apiKey = settings.groqKey, model = settings.groqModel)
        AgentType.OpenRouter -> OpenRouterAgent(client, apiKey = settings.openRouterKey, model = settings.openRouterModel)
        AgentType.Llamatik -> llamatik(settings)
        // GenAI priority: LiteRT-LM (.litertlm) when path matches; else GGUF via Llamatik.
        AgentType.GeminiNano -> liteRtOrLlamatik(settings)
        AgentType.MediaPipe -> liteRtOrLlamatik(settings)
        AgentType.AiEdge -> liteRtOrLlamatik(settings)
        AgentType.RunAnywhere -> llamatik(settings)
        AgentType.MlcLlm -> llamatik(settings)
        AgentType.LlamaCpp -> llamatik(settings)
        AgentType.PocketPal -> llamatik(settings)
        AgentType.Offline -> OfflineAgent(extendedActionsEnabled = settings.extendedActionsEnabled)
        }
    }

    private fun getOrCreateAgent(settings: AppSettings): ConversationalAgent {
        val key = cacheKey(settings)
        if (cachedKey == key && cachedAgent != null) return cachedAgent!!
        releaseCachedAgentResources()
        val newAgent = createAgent(settings)
        cachedAgent = newAgent
        cachedKey = key
        return newAgent
    }

    private fun releaseCachedAgentResources() {
        when (val old = cachedAgent) {
            is LlamatikAgent -> old.shutdown()
            is LiteRtLmOnDeviceAgent -> old.close()
            else -> {}
        }
    }

    override fun evaluateSetupIssue(input: AgentSetupInput): AgentSetupDescriptor? =
        getOrCreateAgent(settingsManager.settings.value).evaluateSetupIssue(input)

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
        append("|").append(settings.deepSeekKey.take(8))
        append("|").append(settings.deepSeekModel)
        append("|").append(settings.groqKey.take(8))
        append("|").append(settings.groqModel)
        append("|").append(settings.openRouterKey.take(8))
        append("|").append(settings.openRouterModel)
        append("|").append(settings.llamatikModelPath)
    }
    
    override suspend fun process(input: String): AgentResponse {
        val settings = settingsManager.settings.value
        return getOrCreateAgent(settings).process(input)
    }
}

val appModule = module {
    single<HttpClient> {
        HttpClient(OkHttp) {
            install(HttpRequestRetry) {
                // Keep this conservative: we mostly want to smooth out flaky networks/timeouts
                // without turning transient issues into long hangs or repeated side effects.
                maxRetries = 2

                retryIf { request, response ->
                    // Only retry on status codes for idempotent methods, to avoid repeating side-effects.
                    val method = request.method
                    val idempotent =
                        method == HttpMethod.Get ||
                            method == HttpMethod.Head ||
                            method == HttpMethod.Options

                    if (!idempotent) return@retryIf false

                    val status = response.status
                    status == HttpStatusCode.TooManyRequests || status.value in 500..599
                }

                retryOnExceptionIf { _, cause ->
                    when (cause) {
                        is SocketTimeoutException -> true
                        is HttpRequestTimeoutException -> true
                        is ConnectException -> true
                        is UnknownHostException -> true
                        is IOException -> {
                            // Typical transient socket failures on mobile networks.
                            val msg = cause.message?.lowercase() ?: ""
                            msg.contains("connection reset") ||
                                msg.contains("broken pipe") ||
                                msg.contains("software caused connection abort") ||
                                msg.contains("unexpected end of stream")
                        }
                        else -> false
                    }
                }

                // Exponential backoff with a little jitter.
                delayMillis { retry ->
                    val base = 300L
                    val max = 3_000L
                    val exp = (base shl retry.coerceAtMost(10)).coerceAtMost(max)
                    val jitter = (exp * (0.15 + Random.nextDouble() * 0.25)).toLong()
                    exp + jitter
                }
            }
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    encodeDefaults = true
                })
            }
        }
    }

    single<JulesClient> { JulesClient(get()) }
    single<GitHubClient> { GitHubClient(get()) }
    single<SettingsManager> { SettingsManager(androidContext()) }
    single<GoogleAuthManager> {
        GoogleAuthManager(androidContext(), get(), { get<ConversationStore>() })
    }
    
    single<LlamaBackend> { AndroidLlamaBackend(androidContext()) }

    // Use the dynamic wrapper instead of a static agent
    single<ConversationalAgent> {
        DynamicAgentWrapper(get(), get(), get(), androidContext())
    }
    
    single<VoiceManager> {
        AndroidVoiceManager(androidContext(), get())
    }

    single<PermissionManager> {
        AndroidPermissionManager(androidContext())
    }

    // Map/route/POI/transit/traffic/toll are in mapModule; load via MapModuleLoader when opening map.

    single<WeatherLookup> {
        AndroidWeatherLookup(androidContext(), get())
    }

    single<NetworkService> {
        AndroidNetworkService(
            androidContext(),
            CoroutineScope(SupervisorJob() + Dispatchers.IO),
            get()
        )
    }

    single<ActionExecutor> {
        AndroidActionExecutor(androidContext(), get(), get(), get())
    }

    single { VoskTranscriber(androidContext(), modelDirPath = null) }
    single<LocalTranscriber> { get<VoskTranscriber>() }
    
    single<AppDatabase?> {
        try {
            android.util.Log.d("AppModule", "Building Room database...")
            val db = Room.databaseBuilder(
                androidContext(),
                AppDatabase::class.java, "julius-db"
            )
                .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3)
                .fallbackToDestructiveMigration()
                .build()
            android.util.Log.d("AppModule", "Room database built successfully")
            db
        } catch (e: Throwable) {
            android.util.Log.e("AppModule", "Failed to build Room database. App will run without persistence.", e)
            null
        }
    }

    single<JulesDao> { 
        val db = get<AppDatabase?>()
        if (db != null) {
            try {
                db.julesDao()
            } catch (e: Throwable) {
                android.util.Log.e("AppModule", "Failed to get julesDao, using null-safe fallback", e)
                NoOpJulesDao()
            }
        } else {
            NoOpJulesDao()
        }
    }

    single { JulesRepository(get(), get(), get()) }

    single<ConversationStore> {
        val settingsManager = get<SettingsManager>()

        val persistence = try {
            val db = get<AppDatabase?>()
            if (db != null) {
                RoomMessagePersistence(db.chatMessageDao())
            } else {
                android.util.Log.w("AppModule", "Database is null, skipping persistence")
                null
            }
        } catch (e: Throwable) {
            android.util.Log.e("AppModule", "Failed to initialize Room persistence", e)
            null
        }

        val store = ConversationStore(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
            agent = get(),
            voiceManager = get(),
            actionExecutor = get(),
            initialSpeechLanguageTag = resolveInitialSpeechLanguageTag(),
            localTranscriber = get(),
            sttPreference = { settingsManager.settings.value.sttEnginePreference },
            persistence = persistence
        )

        // Initialize BorderCrossingManager here to ensure it starts with the store
        BorderCrossingManager(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
            networkService = get(),
            conversationStore = store
        )

        store
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
