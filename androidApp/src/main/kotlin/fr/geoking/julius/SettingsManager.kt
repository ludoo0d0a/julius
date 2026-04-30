package fr.geoking.julius

import android.content.Context
import android.content.SharedPreferences
import fr.geoking.julius.shared.voice.SttEnginePreference
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import fr.geoking.julius.api.geocoding.GeocodedPlace
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * @param enabled If false, hidden from agent pickers and next-agent cycling; [DynamicAgentWrapper] may still resolve
 *   the type if stored settings reference it until migrated.
 */
enum class AgentType(val enabled: Boolean = true) {
    OpenAI,
    /** Perplexity chat + ElevenLabs TTS; uses [AppSettings.perplexityKey]. */
    ElevenLabs,
    Deepgram,
    Gemini,
    FirebaseAI,
    OpenCodeZen,
    CompletionsMe,
    ApiFreeLLM,
    DeepSeek,
    Groq,
    OpenRouter,
    Llamatik,
    /** On-device GGUF via Llamatik; default download Gemma-class model. */
    GeminiNano,
    RunAnywhere,
    MlcLlm,
    LlamaCpp,
    /** On-device GGUF via Llamatik; suggested Phi-2–class model (MediaPipe-style UI label). */
    MediaPipe,
    AiEdge,
    PocketPal,
    Offline
}

val DEFAULT_AGENT = AgentType.Gemini

/** Agents shown in settings / Auto pickers and phone–car next-agent controls. */
fun enabledAgentTypes(): List<AgentType> = AgentType.entries.filter { it.enabled }

/** Next agent when cycling UI; if [current] is disabled or unknown, returns the first enabled agent. */
fun nextSelectableAgent(current: AgentType): AgentType {
    val agents = enabledAgentTypes()
    require(agents.isNotEmpty()) { "At least one AgentType must be enabled" }
    val i = agents.indexOf(current)
    if (i < 0) return agents.first()
    return agents[(i + 1) % agents.size]
}

enum class AppTheme { Particles, Sphere, Waves, Fractal, Micro }
enum class TextAnimation { None, Genie, Blur, Fade, Zoom, Falling }

/** Whether the mic stays active while Julius speaks (barge-in / full-duplex interrupt). */
enum class SpeakingInterruptMode {
    /** No recognition while assistant audio plays. */
    OFF,
    /** Only "hey julius" or "stop" stops playback and captures input. */
    WAKE_WORD,
    /** Any detected speech stops playback and is treated as the next user turn. */
    ANY_SPEECH
}
enum class FractalQuality { Low, Medium, High }
enum class FractalColorIntensity { Low, Medium, High }
enum class PerplexityModel(val modelName: String, val displayName: String) {
    LLAMA_3_1_SONAR_SMALL("llama-3.1-sonar-small-128k-online", "Sonar Small"),
    LLAMA_3_1_SONAR_LARGE("llama-3.1-sonar-large-128k-online", "Sonar Large"),
    LLAMA_3_1_8B_INSTRUCT("llama-3.1-8b-instruct", "Llama 3.1 Instruct"),
    LLAMA_3_1_70B_INSTRUCT("llama-3.1-70b-instruct", "Llama 3.1 70B Instruct"),
    GEMMA_2_9B_IT("gemma-2-9b-it", "Gemma 2 9B"),
    GEMMA_2_27B_IT("gemma-2-27b-it", "Gemma 2 27B")
}

enum class CarMapMode { Native, Custom }
enum class MapEngine { Google, MapLibre }
enum class MapTheme(val styleUrl: String) {
    Dark("https://tiles.openfreemap.org/styles/dark"),
    Modern("https://tiles.openfreemap.org/styles/bright"),
    Standard("https://tiles.openfreemap.org/styles/liberty")
}

enum class OpenAiModel(val modelName: String, val displayName: String) {
    GPT_4O("gpt-4o", "GPT-4o"),
    GPT_4O_MINI("gpt-4o-mini", "GPT-4o mini"),
    GPT_4_TURBO("gpt-4-turbo", "GPT-4 Turbo"),
    GPT_3_5_TURBO("gpt-3.5-turbo", "GPT-3.5 Turbo")
}

enum class GeminiModel(val modelName: String, val displayName: String) {
    GEMMA_4_31B("gemma-4-31b-it", "Gemma 4 31B"),
    GEMMA_4_26B("gemma-4-26b-it", "Gemma 4 26B"),
    GEMINI_2_0_FLASH("gemini-2.0-flash", "Gemini 2.0 Flash"),
    GEMINI_1_5_FLASH("gemini-1.5-flash", "Gemini 1.5 Flash"),
    GEMINI_1_5_PRO("gemini-1.5-pro", "Gemini 1.5 Pro")
}

data class AppSettings(
    /** Show Google traffic layer on the map (green / yellow / red). */
    val mapTrafficEnabled: Boolean = false,
    /** Whether to log network requests/responses for debugging on the map screen. */
    val debugLoggingEnabled: Boolean = false,
    /** Map engine to use on the phone (Google or MapLibre). */
    val phoneMapEngine: MapEngine = MapEngine.Google,
    /** Map theme for the MapLibre engine (Dark, Modern, Standard). */
    val mapTheme: MapTheme = MapTheme.Dark,
    val carMapMode: CarMapMode = CarMapMode.Native,
    val openAiKey: String = "",
    val openAiModel: OpenAiModel = OpenAiModel.GPT_4O,
    val elevenLabsKey: String = "",
    val elevenLabsScribe2: Boolean = true,
    val perplexityKey: String = "",
    val geminiKey: String = "",
    val geminiModel: GeminiModel = GeminiModel.GEMMA_4_31B,
    val deepgramKey: String = "",
    val firebaseAiKey: String = "",
    val firebaseAiModel: String = "gemini-1.5-flash-latest",
    val opencodeZenKey: String = "",
    val opencodeZenModel: String = "minimax-m2.5-free",
    val completionsMeKey: String = "",
    val completionsMeModel: String = "claude-sonnet-4.5",
    val apifreellmKey: String = "",
    val deepSeekKey: String = "",
    val deepSeekModel: String = "deepseek-chat",
    val groqKey: String = "",
    val groqModel: String = "llama-3.3-70b-versatile",
    val openRouterKey: String = "",
    val openRouterModel: String = "openrouter/auto",
    val julesKeys: List<String> = emptyList(),
    /** Personal access token for GitHub (merge/close PRs, comments from the Jules screen). */
    val githubApiKey: String = "",
    val selectedAgent: AgentType = DEFAULT_AGENT,
    val selectedTheme: AppTheme = AppTheme.Particles,
    val selectedModel: PerplexityModel = PerplexityModel.LLAMA_3_1_SONAR_SMALL,
    val fractalQuality: FractalQuality = FractalQuality.Medium,
    val fractalColorIntensity: FractalColorIntensity = FractalColorIntensity.Medium,
    val extendedActionsEnabled: Boolean = true,
    val wakeWordEnabled: Boolean = false,
    /** Mic during assistant speech: off, wake phrase only, or any speech (see migration from legacy boolean). */
    val speakingInterruptMode: SpeakingInterruptMode = SpeakingInterruptMode.ANY_SPEECH,
    val useCarMic: Boolean = false,
    val muteMediaOnCar: Boolean = false,
    /** STT engine for car mic path: LocalOnly (Vosk only), LocalFirst (Vosk then agent), NativeOnly (agent only). */
    val sttEnginePreference: SttEnginePreference = SttEnginePreference.LocalFirst,
    val textAnimation: TextAnimation = TextAnimation.Fade,
    /** Path to Llamatik GGUF model: asset-relative (e.g. "models/phi-2.Q4_0.gguf") or absolute path after download. */
    val llamatikModelPath: String = "models/phi-2.Q4_0.gguf",
    /** Selected Llamatik model variant for download UI; must match [fr.geoking.julius.agents.LlamatikModelVariant].name (e.g. Phi2Gguf). */
    val selectedLlamatikModelVariant: String = "Gemma4_E4B_Gguf",
    val lastJulesRepoId: String = "",
    val lastJulesRepoName: String = "",
    val googleUserName: String? = null,
    val isLoggedIn: Boolean = false,
    /** Optional API key for Luxembourg mobiliteit.lu (request from opendata-api@atp.etat.lu). */
    val mobiliteitLuxembourgKey: String = "",
    /** Last 10 unique destinations. */
    val routeHistory: List<GeocodedPlace> = emptyList(),
    val lastCountryCode: String? = null
)

open class SettingsManager(
    context: Context,
    private val firestoreSync: Any? = null
) {
    private val prefs: SharedPreferences = context.getSharedPreferences("voice_ai_prefs", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    // Simple state flow to observe changes
    private val _settings = MutableStateFlow(loadSettings())
    open val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    fun triggerPullAndMerge() {
        // Firestore settings sync removed.
    }

    private fun loadSettings(): AppSettings {
        val openAiKey = prefs.getString("openai_key", "")?.takeIf { it.isNotEmpty() } ?: fr.geoking.julius.BuildConfig.OPENAI_KEY
        val elevenLabsKey = prefs.getString("elevenlabs_key", "")?.takeIf { it.isNotEmpty() } ?: fr.geoking.julius.BuildConfig.ELEVENLABS_KEY
        val elevenLabsScribe2 = prefs.getBoolean("elevenlabs_scribe2", true)
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
        val deepSeekKey = prefs.getString("deepseek_key", "")?.takeIf { it.isNotEmpty() } ?: fr.geoking.julius.BuildConfig.DEEPSEEK_KEY
        val deepSeekModel = prefs.getString("deepseek_model", "deepseek-chat") ?: "deepseek-chat"
        val groqKey = prefs.getString("groq_key", "")?.takeIf { it.isNotEmpty() } ?: fr.geoking.julius.BuildConfig.GROQ_KEY
        val groqModel = prefs.getString("groq_model", "llama-3.3-70b-versatile") ?: "llama-3.3-70b-versatile"
        val openRouterKey = prefs.getString("openrouter_key", "")?.takeIf { it.isNotEmpty() } ?: fr.geoking.julius.BuildConfig.OPENROUTER_KEY
        val openRouterModel = prefs.getString("openrouter_model", "openrouter/auto") ?: "openrouter/auto"
        val julesKeyLegacy = prefs.getString("jules_key", "")?.takeIf { it.isNotEmpty() }
        val julesKeysJson = prefs.getString("jules_keys", null)
        val julesKeys = when {
            !julesKeysJson.isNullOrBlank() -> try {
                Json.decodeFromString<List<String>>(julesKeysJson)
            } catch (e: Exception) {
                emptyList()
            }
            julesKeyLegacy != null -> listOf(julesKeyLegacy)
            fr.geoking.julius.BuildConfig.JULES_KEY.isNotEmpty() -> listOf(fr.geoking.julius.BuildConfig.JULES_KEY)
            else -> emptyList()
        }
        val githubApiKey = prefs.getString("github_api_key", "")?.takeIf { it.isNotEmpty() } ?: fr.geoking.julius.BuildConfig.GITHUB_TOKEN
        val lastJulesRepoId = prefs.getString("last_jules_repo_id", "") ?: ""
        val lastJulesRepoName = prefs.getString("last_jules_repo_name", "") ?: ""
        val googleUserName = prefs.getString("google_user_name", null)
        val isLoggedIn = prefs.getBoolean("is_logged_in", false)
        val routeHistoryJson = prefs.getString("route_history", "[]") ?: "[]"
        val routeHistory = try {
            Json.decodeFromString<List<GeocodedPlace>>(routeHistoryJson)
        } catch (e: Exception) {
            emptyList()
        }
        val lastCountryCode = prefs.getString("last_country_code", null)

        // Persist build-time keys (from env/local.properties) when prefs were empty so they show in settings and are reused
        persistBuildTimeKeysIfUsed(
            openAiKey, elevenLabsKey, perplexityKey, geminiKey, deepgramKey,
            firebaseAiKey, firebaseAiModel, opencodeZenKey, opencodeZenModel,
            completionsMeKey, completionsMeModel, apifreellmKey,
            deepSeekKey, deepSeekModel, groqKey, groqModel,
            openRouterKey, openRouterModel,
            julesKeys, githubApiKey
        )

        val speakingInterruptMode = loadSpeakingInterruptMode()

        val mapTrafficEnabled = prefs.getBoolean("map_traffic_enabled", false)
        val debugLoggingEnabled = prefs.getBoolean("debug_logging_enabled", false)
        val mobiliteitLuxembourgKey = prefs.getString("mobiliteit_luxembourg_key", "")?.takeIf { it.isNotEmpty() }
            ?: fr.geoking.julius.BuildConfig.MOBILITEIT_LUXEMBOURG_KEY
        val phoneMapEngine = try {
            MapEngine.valueOf(prefs.getString("phone_map_engine", MapEngine.Google.name) ?: MapEngine.Google.name)
        } catch (e: IllegalArgumentException) {
            MapEngine.Google
        }
        val mapTheme = try {
            MapTheme.valueOf(prefs.getString("map_theme", MapTheme.Dark.name) ?: MapTheme.Dark.name)
        } catch (e: IllegalArgumentException) {
            MapTheme.Dark
        }
        val carMapMode = try {
            CarMapMode.valueOf(prefs.getString("car_map_mode", CarMapMode.Native.name) ?: CarMapMode.Native.name)
        } catch (e: IllegalArgumentException) {
            CarMapMode.Native
        }

        return AppSettings(
            mapTrafficEnabled = mapTrafficEnabled,
            debugLoggingEnabled = debugLoggingEnabled,
            phoneMapEngine = phoneMapEngine,
            mapTheme = mapTheme,
            carMapMode = carMapMode,
            openAiKey = openAiKey,
            openAiModel = try {
                OpenAiModel.valueOf(prefs.getString("openai_model", OpenAiModel.GPT_4O.name) ?: OpenAiModel.GPT_4O.name)
            } catch (e: IllegalArgumentException) { OpenAiModel.GPT_4O },
            elevenLabsKey = elevenLabsKey,
            elevenLabsScribe2 = elevenLabsScribe2,
            perplexityKey = perplexityKey,
            geminiKey = geminiKey,
            geminiModel = try {
                GeminiModel.valueOf(prefs.getString("gemini_model", GeminiModel.GEMMA_4_31B.name) ?: GeminiModel.GEMMA_4_31B.name)
            } catch (e: IllegalArgumentException) { GeminiModel.GEMMA_4_31B },
            deepgramKey = deepgramKey,
            firebaseAiKey = firebaseAiKey,
            firebaseAiModel = firebaseAiModel,
            opencodeZenKey = opencodeZenKey,
            opencodeZenModel = opencodeZenModel,
            completionsMeKey = completionsMeKey,
            completionsMeModel = completionsMeModel,
            apifreellmKey = apifreellmKey,
            deepSeekKey = deepSeekKey,
            deepSeekModel = deepSeekModel,
            groqKey = groqKey,
            groqModel = groqModel,
            openRouterKey = openRouterKey,
            openRouterModel = openRouterModel,
            julesKeys = julesKeys,
            githubApiKey = githubApiKey,
            selectedAgent = run {
                val rawAgent = prefs.getString("agent", null)
                if (rawAgent == "Native") {
                    android.util.Log.i(
                        "SettingsManager",
                        "Migrated deprecated agent Native to ${DEFAULT_AGENT.name}; use ElevenLabs for Perplexity + TTS."
                    )
                    prefs.edit().putString("agent", DEFAULT_AGENT.name).apply()
                }
                val loaded = try {
                    val agentName = prefs.getString("agent", null)
                    if (agentName != null) AgentType.valueOf(agentName)
                    else DEFAULT_AGENT
                } catch (e: IllegalArgumentException) {
                    android.util.Log.w("SettingsManager", "Invalid agent name in preferences, using default: ${e.message}")
                    DEFAULT_AGENT
                }
                if (!loaded.enabled) {
                    android.util.Log.w(
                        "SettingsManager",
                        "Selected agent ${loaded.name} is disabled; using $DEFAULT_AGENT"
                    )
                    prefs.edit().putString("agent", DEFAULT_AGENT.name).apply()
                    DEFAULT_AGENT
                } else {
                    loaded
                }
            },
            selectedTheme = try {
                AppTheme.valueOf(prefs.getString("theme", AppTheme.Micro.name) ?: AppTheme.Micro.name)
            } catch (e: IllegalArgumentException) {
                AppTheme.Micro
            },
            selectedModel = try {
                PerplexityModel.valueOf(prefs.getString("model", PerplexityModel.LLAMA_3_1_SONAR_SMALL.name) ?: PerplexityModel.LLAMA_3_1_SONAR_SMALL.name)
            } catch (e: IllegalArgumentException) { PerplexityModel.LLAMA_3_1_SONAR_SMALL },
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
            extendedActionsEnabled = prefs.getBoolean("extended_actions_enabled", true),
            wakeWordEnabled = prefs.getBoolean("wake_word_enabled", false),
            speakingInterruptMode = speakingInterruptMode,
            useCarMic = prefs.getBoolean("use_car_mic", false),
            muteMediaOnCar = prefs.getBoolean("mute_media_on_car", false),
            sttEnginePreference = try {
                SttEnginePreference.valueOf(prefs.getString("stt_engine_preference", SttEnginePreference.LocalFirst.name) ?: SttEnginePreference.LocalFirst.name)
            } catch (e: IllegalArgumentException) {
                SttEnginePreference.LocalFirst
            },
            textAnimation = try {
                TextAnimation.valueOf(prefs.getString("text_animation", TextAnimation.Fade.name) ?: TextAnimation.Fade.name)
            } catch (e: IllegalArgumentException) {
                TextAnimation.Fade
            },
            llamatikModelPath = prefs.getString("llamatik_model_path", "models/phi-2.Q4_0.gguf") ?: "models/phi-2.Q4_0.gguf",
            selectedLlamatikModelVariant = prefs.getString("selected_llamatik_model_variant", "Gemma4_E4B_Gguf") ?: "Gemma4_E4B_Gguf",
            mobiliteitLuxembourgKey = mobiliteitLuxembourgKey,
            lastJulesRepoId = lastJulesRepoId,
            lastJulesRepoName = lastJulesRepoName,
            googleUserName = googleUserName,
            isLoggedIn = isLoggedIn,
            routeHistory = routeHistory,
            lastCountryCode = lastCountryCode
        )
    }

    private fun loadSpeakingInterruptMode(): SpeakingInterruptMode {
        val stored = prefs.getString("speaking_interrupt_mode", null)
        if (stored != null) {
            return try {
                SpeakingInterruptMode.valueOf(stored)
            } catch (_: IllegalArgumentException) {
                SpeakingInterruptMode.ANY_SPEECH
            }
        }
        val legacyHeyJulius = prefs.getBoolean("hey_julius_during_speaking_enabled", false)
        return if (legacyHeyJulius) SpeakingInterruptMode.WAKE_WORD else SpeakingInterruptMode.ANY_SPEECH
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
        deepSeekKey: String,
        deepSeekModel: String,
        groqKey: String,
        groqModel: String,
        openRouterKey: String,
        openRouterModel: String,
        julesKeys: List<String>,
        githubApiKey: String
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
        if (prefs.getString("deepseek_key", "")?.isEmpty() != false && deepSeekKey.isNotEmpty()) edit.putString("deepseek_key", deepSeekKey)
        if (prefs.getString("deepseek_model", "")?.isEmpty() != false && deepSeekModel.isNotEmpty()) edit.putString("deepseek_model", deepSeekModel)
        if (prefs.getString("groq_key", "")?.isEmpty() != false && groqKey.isNotEmpty()) edit.putString("groq_key", groqKey)
        if (prefs.getString("groq_model", "")?.isEmpty() != false && groqModel.isNotEmpty()) edit.putString("groq_model", groqModel)
        if (prefs.getString("openrouter_key", "")?.isEmpty() != false && openRouterKey.isNotEmpty()) edit.putString("openrouter_key", openRouterKey)
        if (prefs.getString("openrouter_model", "")?.isEmpty() != false && openRouterModel.isNotEmpty()) edit.putString("openrouter_model", openRouterModel)
        if (prefs.getString("jules_keys", "")?.isEmpty() != false && julesKeys.isNotEmpty()) edit.putString("jules_keys", Json.encodeToString(julesKeys))
        if (prefs.getString("github_api_key", "")?.isEmpty() != false && githubApiKey.isNotEmpty()) edit.putString("github_api_key", githubApiKey)
        edit.apply()
    }

    open fun setMapTrafficEnabled(value: Boolean) {
        prefs.edit().putBoolean("map_traffic_enabled", value).apply()
        _settings.value = _settings.value.copy(mapTrafficEnabled = value)
    }

    open fun setDebugLoggingEnabled(value: Boolean) {
        prefs.edit().putBoolean("debug_logging_enabled", value).apply()
        _settings.value = _settings.value.copy(debugLoggingEnabled = value)
    }

    open fun setSttEnginePreference(preference: SttEnginePreference) {
        prefs.edit().putString("stt_engine_preference", preference.name).apply()
        _settings.value = _settings.value.copy(sttEnginePreference = preference)
    }

    open fun setCarMapMode(mode: CarMapMode) {
        prefs.edit().putString("car_map_mode", mode.name).apply()
        _settings.value = _settings.value.copy(carMapMode = mode)
    }

    open fun setPhoneMapEngine(engine: MapEngine) {
        prefs.edit().putString("phone_map_engine", engine.name).apply()
        _settings.value = _settings.value.copy(phoneMapEngine = engine)
    }

    open fun setMapTheme(theme: MapTheme) {
        prefs.edit().putString("map_theme", theme.name).apply()
        _settings.value = _settings.value.copy(mapTheme = theme)
    }

    open fun addRouteHistory(place: GeocodedPlace) {
        val current = _settings.value.routeHistory
        val filtered = current.filter { it.label != place.label || it.latitude != place.latitude || it.longitude != place.longitude }
        val updated = (listOf(place) + filtered).take(10)
        saveSettings(_settings.value.copy(routeHistory = updated))
    }

    open fun saveSettings(settings: AppSettings) {
        saveSettingsInternal(settings)
    }

    open fun saveSettingsWithThemeCheck(settings: AppSettings) {
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

    private fun saveSettingsInternal(settings: AppSettings, upload: Boolean = true) {
        val settings = if (!settings.selectedAgent.enabled) {
            android.util.Log.w(
                "SettingsManager",
                "Refusing to persist disabled agent ${settings.selectedAgent.name}; using $DEFAULT_AGENT"
            )
            settings.copy(selectedAgent = DEFAULT_AGENT)
        } else {
            settings
        }
        prefs.edit()
            .putBoolean("map_traffic_enabled", settings.mapTrafficEnabled)
            .putBoolean("debug_logging_enabled", settings.debugLoggingEnabled)
            .putString("mobiliteit_luxembourg_key", settings.mobiliteitLuxembourgKey)
            .putString("car_map_mode", settings.carMapMode.name)
            .putString("phone_map_engine", settings.phoneMapEngine.name)
            .putString("map_theme", settings.mapTheme.name)
            .putString("openai_key", settings.openAiKey)
            .putString("openai_model", settings.openAiModel.name)
            .putString("elevenlabs_key", settings.elevenLabsKey)
            .putBoolean("elevenlabs_scribe2", settings.elevenLabsScribe2)
            .putString("perplexity_key", settings.perplexityKey)
            .putString("gemini_key", settings.geminiKey)
            .putString("gemini_model", settings.geminiModel.name)
            .putString("deepgram_key", settings.deepgramKey)
            .putString("firebase_ai_key", settings.firebaseAiKey)
            .putString("firebase_ai_model", settings.firebaseAiModel)
            .putString("opencode_zen_key", settings.opencodeZenKey)
            .putString("opencode_zen_model", settings.opencodeZenModel)
            .putString("completions_me_key", settings.completionsMeKey)
            .putString("completions_me_model", settings.completionsMeModel)
            .putString("apifreellm_key", settings.apifreellmKey)
            .putString("deepseek_key", settings.deepSeekKey)
            .putString("deepseek_model", settings.deepSeekModel)
            .putString("groq_key", settings.groqKey)
            .putString("groq_model", settings.groqModel)
            .putString("openrouter_key", settings.openRouterKey)
            .putString("openrouter_model", settings.openRouterModel)
            .putString("jules_keys", Json.encodeToString(settings.julesKeys))
            .remove("jules_key")
            .putString("github_api_key", settings.githubApiKey)
            .putString("agent", settings.selectedAgent.name)
            .putString("theme", settings.selectedTheme.name)
            .putString("model", settings.selectedModel.name)
            .putString("fractal_quality", settings.fractalQuality.name)
            .putString("fractal_color_intensity", settings.fractalColorIntensity.name)
            .putBoolean("extended_actions_enabled", settings.extendedActionsEnabled)
            .putBoolean("wake_word_enabled", settings.wakeWordEnabled)
            .putString("speaking_interrupt_mode", settings.speakingInterruptMode.name)
            .remove("hey_julius_during_speaking_enabled")
            .putBoolean("use_car_mic", settings.useCarMic)
            .putBoolean("mute_media_on_car", settings.muteMediaOnCar)
            .putString("stt_engine_preference", settings.sttEnginePreference.name)
            .putString("text_animation", settings.textAnimation.name)
            .putString("llamatik_model_path", settings.llamatikModelPath)
            .putString("selected_llamatik_model_variant", settings.selectedLlamatikModelVariant)
            .putString("last_jules_repo_id", settings.lastJulesRepoId)
            .putString("last_jules_repo_name", settings.lastJulesRepoName)
            .putString("google_user_name", settings.googleUserName)
            .putBoolean("is_logged_in", settings.isLoggedIn)
            .putString("route_history", Json.encodeToString(settings.routeHistory))
            .apply { settings.lastCountryCode?.let { putString("last_country_code", it) } ?: remove("last_country_code") }
            .apply()

        // Update StateFlow immediately with the new values to ensure UI and agent switching update right away
        _settings.value = settings

        // Firestore settings sync removed.
    }

    open fun saveSettings(
        openAiKey: String,
        openAiModel: OpenAiModel = _settings.value.openAiModel,
        elevenLabsKey: String,
        elevenLabsScribe2: Boolean = _settings.value.elevenLabsScribe2,
        perplexityKey: String,
        geminiKey: String,
        geminiModel: GeminiModel = _settings.value.geminiModel,
        deepgramKey: String,
        firebaseAiKey: String,
        firebaseAiModel: String,
        opencodeZenKey: String = "",
        opencodeZenModel: String = "minimax-m2.5-free",
        completionsMeKey: String = "",
        completionsMeModel: String = "claude-sonnet-4.5",
        apifreellmKey: String = "",
        deepSeekKey: String = "",
        deepSeekModel: String = "deepseek-chat",
        groqKey: String = "",
        groqModel: String = "llama-3.3-70b-versatile",
        openRouterKey: String = "",
        openRouterModel: String = "openrouter/auto",
        julesKeys: List<String> = emptyList(),
        agent: AgentType,
        theme: AppTheme,
        model: PerplexityModel,
        fractalQuality: FractalQuality = FractalQuality.Medium,
        fractalColorIntensity: FractalColorIntensity = FractalColorIntensity.Medium,
        extendedActionsEnabled: Boolean = true,
        wakeWordEnabled: Boolean = false,
        speakingInterruptMode: SpeakingInterruptMode = _settings.value.speakingInterruptMode,
        useCarMic: Boolean = false,
        muteMediaOnCar: Boolean = false,
        sttEnginePreference: SttEnginePreference = _settings.value.sttEnginePreference,
        llamatikModelPath: String = _settings.value.llamatikModelPath,
        selectedLlamatikModelVariant: String = _settings.value.selectedLlamatikModelVariant
    ) {
        val newSettings = AppSettings(
            mapTrafficEnabled = _settings.value.mapTrafficEnabled,
            debugLoggingEnabled = _settings.value.debugLoggingEnabled,
            phoneMapEngine = _settings.value.phoneMapEngine,
            mapTheme = _settings.value.mapTheme,
            carMapMode = _settings.value.carMapMode,
            openAiKey = openAiKey,
            openAiModel = openAiModel,
            elevenLabsKey = elevenLabsKey,
            elevenLabsScribe2 = elevenLabsScribe2,
            perplexityKey = perplexityKey,
            geminiKey = geminiKey,
            geminiModel = geminiModel,
            deepgramKey = deepgramKey,
            firebaseAiKey = firebaseAiKey,
            firebaseAiModel = firebaseAiModel,
            opencodeZenKey = opencodeZenKey,
            opencodeZenModel = opencodeZenModel,
            completionsMeKey = completionsMeKey,
            completionsMeModel = completionsMeModel,
            apifreellmKey = apifreellmKey,
            deepSeekKey = deepSeekKey,
            deepSeekModel = deepSeekModel,
            groqKey = groqKey,
            groqModel = groqModel,
            openRouterKey = openRouterKey,
            openRouterModel = openRouterModel,
            julesKeys = julesKeys,
            githubApiKey = _settings.value.githubApiKey,
            selectedAgent = agent,
            selectedTheme = theme,
            selectedModel = model,
            fractalQuality = fractalQuality,
            fractalColorIntensity = fractalColorIntensity,
            extendedActionsEnabled = extendedActionsEnabled,
            wakeWordEnabled = wakeWordEnabled,
            speakingInterruptMode = speakingInterruptMode,
            useCarMic = useCarMic,
            muteMediaOnCar = muteMediaOnCar,
            sttEnginePreference = sttEnginePreference,
            textAnimation = _settings.value.textAnimation,
            llamatikModelPath = llamatikModelPath,
            selectedLlamatikModelVariant = selectedLlamatikModelVariant,
            lastJulesRepoId = _settings.value.lastJulesRepoId,
            lastJulesRepoName = _settings.value.lastJulesRepoName,
            googleUserName = _settings.value.googleUserName,
            isLoggedIn = _settings.value.isLoggedIn,
            routeHistory = _settings.value.routeHistory,
            lastCountryCode = _settings.value.lastCountryCode
        )
        saveSettings(newSettings)
    }
}
