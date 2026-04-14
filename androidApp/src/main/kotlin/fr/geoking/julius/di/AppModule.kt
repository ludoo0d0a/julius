package fr.geoking.julius.di

import android.content.Context
import fr.geoking.julius.feature.voice.AndroidVoiceManager
import fr.geoking.julius.AndroidActionExecutor
import fr.geoking.julius.feature.network.AndroidNetworkService
import fr.geoking.julius.feature.weather.AndroidWeatherLookup
import fr.geoking.julius.AppSettings
import fr.geoking.julius.feature.permission.AndroidPermissionManager
import fr.geoking.julius.feature.auth.GoogleAuthManager
import fr.geoking.julius.SettingsManager
import fr.geoking.julius.AgentType
import fr.geoking.julius.agents.*
import fr.geoking.julius.shared.location.BorderCrossingManager
import fr.geoking.julius.shared.conversation.ConversationStore
import fr.geoking.julius.shared.voice.LocalTranscriber
import fr.geoking.julius.shared.network.NetworkService
import fr.geoking.julius.shared.voice.VoiceManager
import fr.geoking.julius.feature.voice.VoskTranscriber
import fr.geoking.julius.shared.action.ActionExecutor
import fr.geoking.julius.shared.platform.PermissionManager
import fr.geoking.julius.shared.weather.WeatherLookup
import fr.geoking.julius.api.jules.JulesClient
import fr.geoking.julius.api.github.GitHubClient
import fr.geoking.julius.repository.JulesRepository
import fr.geoking.julius.shared.conversation.MessagePersistence
import fr.geoking.julius.shared.network.NetworkException
import fr.geoking.julius.persistence.AppDatabase
import fr.geoking.julius.persistence.RoomMessagePersistence
import fr.geoking.julius.persistence.JulesDao
import fr.geoking.julius.repository.FuelForecastRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import fr.geoking.julius.feature.settings.FirestoreSettingsSync
import androidx.room.Room
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.plugins.observer.ResponseObserver
import io.ktor.client.plugins.observer.ResponseObserverConfig
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.request
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.util.AttributeKey
import io.ktor.util.toMap
import fr.geoking.julius.shared.logging.DebugLogStore
import fr.geoking.julius.shared.logging.NetworkLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.Locale
import java.util.UUID
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
            ElevenLabsAgent(
                client,
                perplexityKey = settings.perplexityKey,
                elevenLabsKey = settings.elevenLabsKey,
                model = settings.selectedModel.modelName,
                scribe2 = settings.elevenLabsScribe2
            )
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

    override fun evaluateSetupIssue(input: AgentSetupInput): AgentSetupDescriptor? {
        return try {
            getOrCreateAgent(settingsManager.settings.value).evaluateSetupIssue(input)
        } catch (t: Throwable) {
            val mem = MemoryHelper.getMemoryReport(appContext)
            val msg = t.toString()
            android.util.Log.e("DynamicAgentWrapper", "evaluateSetupIssue error. $msg. $mem", t)
            AgentSetupDescriptor.MissingLlamatikModel("Setup error: $msg. $mem")
        }
    }

    private fun cacheKey(settings: AppSettings): String = buildString {
        append(settings.selectedAgent.name)
        append("|").append(settings.selectedModel.modelName)
        append("|").append(settings.openAiModel.modelName)
        append("|").append(settings.geminiModel.modelName)
        append("|").append(settings.extendedActionsEnabled)
        append("|").append(settings.openAiKey.take(8))
        append("|").append(settings.perplexityKey.take(8))
        append("|").append(settings.elevenLabsKey.take(8))
        append("|").append(settings.elevenLabsScribe2)
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
        return try {
            getOrCreateAgent(settings).process(input)
        } catch (e: NetworkException) {
            throw e
        } catch (t: Throwable) {
            val mem = MemoryHelper.getMemoryReport(appContext)
            val msg = t.toString()
            android.util.Log.e("DynamicAgentWrapper", "Agent process crash. $msg. $mem", t)
            throw NetworkException(null, "Agent error: $msg. $mem")
        }
    }
}

val appModule = module {
    single<HttpClient> {
        val settingsManager = get<SettingsManager>()
        HttpClient(OkHttp) {
            val requestBodyKey = AttributeKey<String>("DebugRequestBody")

            install(ResponseObserver) {
                onResponse { response ->
                    if (settingsManager.settings.value.debugLoggingEnabled) {
                        val request = response.request
                        val reqBody = request.attributes.getOrNull(requestBodyKey)

                        // Capture logs in a background task to avoid blocking.
                        // Note: bodyAsText() on duplicated body from ResponseObserver is safe.
                        // Using GlobalScope for this side-effect logging task as it's short-lived and doesn't need to be cancelled with any specific UI.
                        @OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
                        kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.Default) {
                            val respBody = try {
                                response.bodyAsText()
                            } catch (e: Exception) {
                                "[Unreadable body: ${e.message}]"
                            }

                            DebugLogStore.addLog(
                                NetworkLog(
                                    id = UUID.randomUUID().toString(),
                                    url = request.url.toString(),
                                    host = request.url.host,
                                    method = request.method.value,
                                    requestHeaders = request.headers.toMap(),
                                    requestBody = reqBody,
                                    responseHeaders = response.headers.toMap(),
                                    responseBody = respBody,
                                    statusCode = response.status.value,
                                    durationMs = response.responseTime.timestamp - response.requestTime.timestamp,
                                    timestamp = System.currentTimeMillis()
                                )
                            )
                        }
                    }
                }
            }

            install(createClientPlugin("NetworkDebugLog") {
                on(io.ktor.client.plugins.api.Send) { request ->
                    if (settingsManager.settings.value.debugLoggingEnabled) {
                        val content = request.body
                        if (content is io.ktor.http.content.TextContent) {
                            request.attributes.put(requestBodyKey, content.text)
                        } else if (content is io.ktor.client.utils.EmptyContent) {
                            request.attributes.put(requestBodyKey, "")
                        }
                    }
                    proceed(request)
                }
            })

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
    single { FirebaseAuth.getInstance() }
    single { FirebaseFirestore.getInstance() }
    single { FirestoreSettingsSync(get(), get()) }
    single<SettingsManager> { SettingsManager(androidContext(), get()) }
    single<GoogleAuthManager> {
        GoogleAuthManager(androidContext(), get(), { get<ConversationStore>() }, get())
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

    single { FuelForecastRepository(http = get(), db = get()) }

    single<ActionExecutor> {
        AndroidActionExecutor(androidContext(), get(), get(), get())
    }

    single { VoskTranscriber(androidContext(), modelDirPath = null) }
    single<LocalTranscriber> { get<VoskTranscriber>() }
    
    single<AppDatabase> {
        fun buildAndValidate(builder: androidx.room.RoomDatabase.Builder<AppDatabase>): AppDatabase {
            val db = builder.fallbackToDestructiveMigration(dropAllTables = true).build()
            // Force database open and schema validation early to catch crashes at startup
            db.openHelper.writableDatabase.query("SELECT 1").close()
            return db
        }

        try {
            android.util.Log.d("AppModule", "Building persistent Room database...")
            buildAndValidate(
                Room.databaseBuilder(androidContext(), AppDatabase::class.java, "julius-db")
                    .addMigrations(
                        AppDatabase.MIGRATION_1_2,
                        AppDatabase.MIGRATION_2_3,
                        AppDatabase.MIGRATION_3_4,
                        AppDatabase.MIGRATION_4_5,
                        AppDatabase.MIGRATION_5_6,
                        AppDatabase.MIGRATION_6_7,
                        AppDatabase.MIGRATION_7_8,
                        AppDatabase.MIGRATION_8_9
                    )
            )
        } catch (e: Throwable) {
            android.util.Log.e("AppModule", "Persistent DB failed. Falling back to in-memory. Error: ${e.stackTraceToString()}", e)
            try {
                android.util.Log.d("AppModule", "Building in-memory Room database...")
                buildAndValidate(
                    Room.inMemoryDatabaseBuilder(androidContext(), AppDatabase::class.java)
                )
            } catch (inner: Throwable) {
                android.util.Log.e("AppModule", "In-memory DB also failed. Error: ${inner.stackTraceToString()}", inner)
                throw inner
            }
        }
    }

    single<JulesDao> { get<AppDatabase>().julesDao() }

    single { JulesRepository(androidContext(), get(), get(), get(), get(), get()) }

    single<ConversationStore> {
        val settingsManager = get<SettingsManager>()
        val db = get<AppDatabase>()

        val persistence = try {
            RoomMessagePersistence(db.chatMessageDao())
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
