package fr.geoking.julius

import android.content.Context
import android.content.SharedPreferences
import fr.geoking.julius.VehicleType
import fr.geoking.julius.shared.SttEnginePreference
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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
    Llamatik,
    GeminiNano,
    RunAnywhere,
    MlcLlm,
    LlamaCpp,
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

enum class OpenAiModel(val modelName: String, val displayName: String) {
    GPT_4O("gpt-4o", "GPT-4o"),
    GPT_4O_MINI("gpt-4o-mini", "GPT-4o mini"),
    GPT_4_TURBO("gpt-4-turbo", "GPT-4 Turbo"),
    GPT_3_5_TURBO("gpt-3.5-turbo", "GPT-3.5 Turbo")
}

enum class GeminiModel(val modelName: String, val displayName: String) {
    GEMINI_2_0_FLASH("gemini-2.0-flash", "Gemini 2.0 Flash"),
    GEMINI_1_5_FLASH("gemini-1.5-flash", "Gemini 1.5 Flash"),
    GEMINI_1_5_PRO("gemini-1.5-pro", "Gemini 1.5 Pro")
}

/** Energy/fuel types for map POI filter (multi-select). Aligned with prix-carburants.gouv.fr. */
val DEFAULT_MAP_ENERGY_TYPES = setOf("gazole", "sp98", "sp95", "gplc", "e85")

/** Enseigne type for map filter, aligned with prix-carburants.gouv.fr. "all" = Toutes les enseignes. */
const val DEFAULT_MAP_ENSEIGNE_TYPE = "all"

/** Power buckets for IRVE filter (kW). Empty = all. */
val DEFAULT_MAP_POWER_LEVELS = emptySet<Int>()

/** IRVE operator filter. Empty = all. */
val DEFAULT_MAP_IRVE_OPERATORS = emptySet<String>()

/** Brand filter. Empty = all. */
val DEFAULT_MAP_BRANDS = emptySet<String>()

/** Default EV range in km for route planning. */
const val DEFAULT_EV_RANGE_KM = 300

enum class FuelCard { None, Routex }

data class AppSettings(
    val vehicleBrand: String = "",
    val vehicleModel: String = "",
    val vehicleEnergy: String = "gas", // gas, electric, hybrid
    val vehicleGasTypes: Set<String> = DEFAULT_MAP_ENERGY_TYPES,
    val vehiclePowerLevels: Set<Int> = DEFAULT_MAP_POWER_LEVELS,
    val fuelCard: FuelCard = FuelCard.None,
    val useVehicleFilter: Boolean = false,
    val selectedPoiProviders: Set<fr.geoking.julius.poi.PoiProviderType> = setOf(fr.geoking.julius.poi.PoiProviderType.Routex),
    /** Selected energy types to show on map (e.g. sp95, sp98, gazole, e85, electric). Empty = show all. */
    val selectedMapEnergyTypes: Set<String> = DEFAULT_MAP_ENERGY_TYPES,
    /** Type d'enseigne: "all", "major", "gms", "independant". Filter applied when provider supplies data. */
    val mapEnseigneType: String = DEFAULT_MAP_ENSEIGNE_TYPE,
    /** Filter by brand ids (lowercase), empty = show all brands. */
    val mapBrands: Set<String> = DEFAULT_MAP_BRANDS,
    /** Selected service ids for map filter (e.g. bornes_electriques, automate_cb). Applied when provider supplies data. */
    val selectedMapServices: Set<String> = emptySet(),
    /** Power buckets in kW for IRVE stations (empty = all). Applied when provider is DataGouvElec. */
    val mapPowerLevels: Set<Int> = DEFAULT_MAP_POWER_LEVELS,
    /** IRVE operator filter: empty = all. Applied when provider is DataGouvElec. */
    val mapIrveOperators: Set<String> = DEFAULT_MAP_IRVE_OPERATORS,
    /** Selected connector types for IRVE (type_2, combo_ccs, chademo, ef, autre). Empty = show all. Applied when provider is DataGouvElec. */
    val selectedMapConnectorTypes: Set<String> = emptySet(),
    /** Show Google traffic layer on the map (green / yellow / red). */
    val mapTrafficEnabled: Boolean = false,
    /** EV range in km for route planning. */
    val evRangeKm: Int = DEFAULT_EV_RANGE_KM,
    /** Optional consumption in kWh/100 km; null = use range only. */
    val evConsumptionKwhPer100km: Float? = null,
    /** Optional API key for Open Charge Map (api.openchargemap.io). */
    val openChargeMapKey: String = "",
    /** When POI provider is Overpass: which amenity types to show (toilets, drinking_water, truck_stop, rest_area). */
    val selectedOverpassAmenityTypes: Set<String> = setOf("toilets", "drinking_water"),
    /** Vehicle type for POI categories and optional routing profile (Car, Truck, Motorcycle, Motorhome). */
    val vehicleType: VehicleType = VehicleType.Car,
    val carMapMode: CarMapMode = CarMapMode.Native,
    val openAiKey: String = "",
    val openAiModel: OpenAiModel = OpenAiModel.GPT_4O,
    val elevenLabsKey: String = "",
    val perplexityKey: String = "",
    val geminiKey: String = "",
    val geminiModel: GeminiModel = GeminiModel.GEMINI_2_0_FLASH,
    val deepgramKey: String = "",
    val firebaseAiKey: String = "",
    val firebaseAiModel: String = "gemini-1.5-flash-latest",
    val opencodeZenKey: String = "",
    val opencodeZenModel: String = "minimax-m2.5-free",
    val completionsMeKey: String = "",
    val completionsMeModel: String = "claude-sonnet-4.5",
    val apifreellmKey: String = "",
    val julesKey: String = "",
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
    /** Selected Llamatik model variant for download UI; must match [fr.geoking.julius.ui.LlamatikModelVariant].name (e.g. Phi2Q4_0). */
    val selectedLlamatikModelVariant: String = "Phi2Q4_0",
    val lastJulesRepoId: String = "",
    val lastJulesRepoName: String = "",
    val googleUserName: String? = null,
    val isLoggedIn: Boolean = false,
    /** Path to downloaded OpenTollData JSON for highway toll estimation; null until user downloads. */
    val tollDataPath: String? = null,
    /** Optional API key for Luxembourg mobiliteit.lu (request from opendata-api@atp.etat.lu). */
    val mobiliteitLuxembourgKey: String = ""
)

open class SettingsManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("voice_ai_prefs", Context.MODE_PRIVATE)
    
    // Simple state flow to observe changes
    private val _settings = MutableStateFlow(loadSettings())
    open val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private fun loadSettings(): AppSettings {
        val openAiKey = prefs.getString("openai_key", "")?.takeIf { it.isNotEmpty() } ?: fr.geoking.julius.BuildConfig.OPENAI_KEY
        val elevenLabsKey = prefs.getString("elevenlabs_key", "")?.takeIf { it.isNotEmpty() } ?: fr.geoking.julius.BuildConfig.ELEVENLABS_KEY
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
        val julesKey = prefs.getString("jules_key", "")?.takeIf { it.isNotEmpty() } ?: fr.geoking.julius.BuildConfig.JULES_KEY
        val githubApiKey = prefs.getString("github_api_key", "")?.takeIf { it.isNotEmpty() } ?: fr.geoking.julius.BuildConfig.GITHUB_TOKEN
        val lastJulesRepoId = prefs.getString("last_jules_repo_id", "") ?: ""
        val lastJulesRepoName = prefs.getString("last_jules_repo_name", "") ?: ""
        val googleUserName = prefs.getString("google_user_name", null)
        val isLoggedIn = prefs.getBoolean("is_logged_in", false)

        // Persist build-time keys (from env/local.properties) when prefs were empty so they show in settings and are reused
        persistBuildTimeKeysIfUsed(
            openAiKey, elevenLabsKey, perplexityKey, geminiKey, deepgramKey,
            firebaseAiKey, firebaseAiModel, opencodeZenKey, opencodeZenModel,
            completionsMeKey, completionsMeModel, apifreellmKey, julesKey, githubApiKey
        )

        val speakingInterruptMode = loadSpeakingInterruptMode()

        val energyTypesStr = prefs.getString("map_energy_types", null)
        val selectedMapEnergyTypes = if (!energyTypesStr.isNullOrBlank()) {
            energyTypesStr.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                .map { if (it == "sp95_e10") "sp95" else it }
                .toSet()
        } else DEFAULT_MAP_ENERGY_TYPES
        val mapEnseigneType = prefs.getString("map_enseigne_type", DEFAULT_MAP_ENSEIGNE_TYPE) ?: DEFAULT_MAP_ENSEIGNE_TYPE
        val mapServicesStr = prefs.getString("map_services", null)
        val selectedMapServices = if (!mapServicesStr.isNullOrBlank()) {
            mapServicesStr.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        } else emptySet()
        val mapPowerLevelsStr = prefs.getString("map_power_levels", null)
        val mapPowerLevels = if (!mapPowerLevelsStr.isNullOrBlank()) {
            mapPowerLevelsStr.split(",").mapNotNull { it.trim().toIntOrNull() }.toSet()
        } else DEFAULT_MAP_POWER_LEVELS
        val mapIrveOperatorsStr = prefs.getString("map_irve_operators", null)
        val mapIrveOperators = if (!mapIrveOperatorsStr.isNullOrBlank()) {
            mapIrveOperatorsStr.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                .map { if (it == "totalenergies") "total" else it }
                .toSet()
        } else DEFAULT_MAP_IRVE_OPERATORS
        val mapBrandsStr = prefs.getString("map_brands", null)
        val mapBrands = if (!mapBrandsStr.isNullOrBlank()) {
            mapBrandsStr.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                .map {
                    when (it) {
                        "totalenergies" -> "total"
                        "esso express" -> "esso"
                        "e.leclerc" -> "leclerc"
                        else -> it
                    }
                }
                .toSet()
        } else DEFAULT_MAP_BRANDS
        val mapConnectorTypesStr = prefs.getString("map_connector_types", null)
        val selectedMapConnectorTypes = if (!mapConnectorTypesStr.isNullOrBlank()) {
            mapConnectorTypesStr.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        } else emptySet()
        val mapTrafficEnabled = prefs.getBoolean("map_traffic_enabled", false)
        val evRangeKm = prefs.getInt("ev_range_km", DEFAULT_EV_RANGE_KM).coerceIn(50, 1000)
        val evConsumptionKwhPer100km = if (prefs.contains("ev_consumption_kwh_100")) {
            prefs.getFloat("ev_consumption_kwh_100", 18f).takeIf { it > 0f }
        } else null
        val openChargeMapKey = prefs.getString("openchargemap_key", "") ?: ""
        val mobiliteitLuxembourgKey = prefs.getString("mobiliteit_luxembourg_key", "")?.takeIf { it.isNotEmpty() }
            ?: fr.geoking.julius.BuildConfig.MOBILITEIT_LUXEMBOURG_KEY
        val overpassAmenityStr = prefs.getString("overpass_amenity_types", "toilets,drinking_water") ?: "toilets,drinking_water"
        val selectedOverpassAmenityTypes = overpassAmenityStr.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
            .ifEmpty { setOf("toilets", "drinking_water") }
        val vehicleType = try {
            VehicleType.valueOf(prefs.getString("vehicle_type", VehicleType.Car.name) ?: VehicleType.Car.name)
        } catch (e: IllegalArgumentException) {
            VehicleType.Car
        }
        val carMapMode = try {
            CarMapMode.valueOf(prefs.getString("car_map_mode", CarMapMode.Native.name) ?: CarMapMode.Native.name)
        } catch (e: IllegalArgumentException) {
            CarMapMode.Native
        }

        val vehicleBrand = prefs.getString("vehicle_brand", "") ?: ""
        val vehicleModel = prefs.getString("vehicle_model", "") ?: ""
        val vehicleEnergy = prefs.getString("vehicle_energy", "gas") ?: "gas"
        val vehicleGasTypesStr = prefs.getString("vehicle_gas_types", null)
        val vehicleGasTypes = if (!vehicleGasTypesStr.isNullOrBlank()) {
            vehicleGasTypesStr.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                .map { if (it == "sp95_e10") "sp95" else it }
                .toSet()
        } else DEFAULT_MAP_ENERGY_TYPES
        val vehiclePowerLevelsStr = prefs.getString("vehicle_power_levels", null)
        val vehiclePowerLevels = if (!vehiclePowerLevelsStr.isNullOrBlank()) {
            vehiclePowerLevelsStr.split(",").mapNotNull { it.trim().toIntOrNull() }.toSet()
        } else DEFAULT_MAP_POWER_LEVELS
        val fuelCard = try {
            FuelCard.valueOf(prefs.getString("fuel_card", FuelCard.None.name) ?: FuelCard.None.name)
        } catch (e: IllegalArgumentException) {
            FuelCard.None
        }
        val useVehicleFilter = prefs.getBoolean("use_vehicle_filter", false)

        val selectedPoiProviders = run {
            val providersStr = prefs.getString("selected_poi_providers", null)
            if (!providersStr.isNullOrBlank()) {
                providersStr.split(",").mapNotNull {
                    try { fr.geoking.julius.poi.PoiProviderType.valueOf(it.trim()) } catch (e: Exception) { null }
                }.toSet()
            } else {
                // Migration
                val legacy = prefs.getString("poi_provider", null)
                if (legacy != null) {
                    try {
                        setOf(fr.geoking.julius.poi.PoiProviderType.valueOf(legacy))
                    } catch (e: Exception) {
                        setOf(fr.geoking.julius.poi.PoiProviderType.Routex)
                    }
                } else {
                    setOf(fr.geoking.julius.poi.PoiProviderType.Routex)
                }
            }
        }

        return AppSettings(
            vehicleBrand = vehicleBrand,
            vehicleModel = vehicleModel,
            vehicleEnergy = vehicleEnergy,
            vehicleGasTypes = vehicleGasTypes,
            vehiclePowerLevels = vehiclePowerLevels,
            fuelCard = fuelCard,
            useVehicleFilter = useVehicleFilter,
            selectedPoiProviders = selectedPoiProviders,
            selectedMapEnergyTypes = selectedMapEnergyTypes,
            mapEnseigneType = mapEnseigneType,
            selectedMapServices = selectedMapServices,
            mapPowerLevels = mapPowerLevels,
            mapIrveOperators = mapIrveOperators,
            mapBrands = mapBrands,
            selectedMapConnectorTypes = selectedMapConnectorTypes,
            mapTrafficEnabled = mapTrafficEnabled,
            evRangeKm = evRangeKm,
            evConsumptionKwhPer100km = evConsumptionKwhPer100km,
            openChargeMapKey = openChargeMapKey,
            selectedOverpassAmenityTypes = selectedOverpassAmenityTypes,
            vehicleType = vehicleType,
            carMapMode = carMapMode,
            openAiKey = openAiKey,
            openAiModel = try {
                OpenAiModel.valueOf(prefs.getString("openai_model", OpenAiModel.GPT_4O.name) ?: OpenAiModel.GPT_4O.name)
            } catch (e: IllegalArgumentException) { OpenAiModel.GPT_4O },
            elevenLabsKey = elevenLabsKey,
            perplexityKey = perplexityKey,
            geminiKey = geminiKey,
            geminiModel = try {
                GeminiModel.valueOf(prefs.getString("gemini_model", GeminiModel.GEMINI_2_0_FLASH.name) ?: GeminiModel.GEMINI_2_0_FLASH.name)
            } catch (e: IllegalArgumentException) { GeminiModel.GEMINI_2_0_FLASH },
            deepgramKey = deepgramKey,
            firebaseAiKey = firebaseAiKey,
            firebaseAiModel = firebaseAiModel,
            opencodeZenKey = opencodeZenKey,
            opencodeZenModel = opencodeZenModel,
            completionsMeKey = completionsMeKey,
            completionsMeModel = completionsMeModel,
            apifreellmKey = apifreellmKey,
            julesKey = julesKey,
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
            selectedLlamatikModelVariant = prefs.getString("selected_llamatik_model_variant", "Phi2Q4_0") ?: "Phi2Q4_0",
            tollDataPath = prefs.getString("toll_data_path", null),
            mobiliteitLuxembourgKey = mobiliteitLuxembourgKey,
            lastJulesRepoId = lastJulesRepoId,
            lastJulesRepoName = lastJulesRepoName,
            googleUserName = googleUserName,
            isLoggedIn = isLoggedIn
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
        julesKey: String,
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
        if (prefs.getString("jules_key", "")?.isEmpty() != false && julesKey.isNotEmpty()) edit.putString("jules_key", julesKey)
        if (prefs.getString("github_api_key", "")?.isEmpty() != false && githubApiKey.isNotEmpty()) edit.putString("github_api_key", githubApiKey)
        edit.apply()
    }

    open fun setPoiProviderTypes(types: Set<fr.geoking.julius.poi.PoiProviderType>) {
        prefs.edit().putString("selected_poi_providers", types.joinToString(",") { it.name }).apply()
        _settings.value = _settings.value.copy(selectedPoiProviders = types)
    }

    open fun togglePoiProviderType(type: fr.geoking.julius.poi.PoiProviderType) {
        val current = _settings.value.selectedPoiProviders
        val next = if (current.contains(type)) current - type else current + type
        setPoiProviderTypes(next)
    }

    open fun setMapEnergyTypes(types: Set<String>) {
        prefs.edit().putString("map_energy_types", types.joinToString(",")).apply()
        _settings.value = _settings.value.copy(selectedMapEnergyTypes = types)
    }

    open fun setMapEnseigneType(type: String) {
        prefs.edit().putString("map_enseigne_type", type).apply()
        _settings.value = _settings.value.copy(mapEnseigneType = type)
    }

    open fun setMapServices(services: Set<String>) {
        prefs.edit().putString("map_services", services.joinToString(",")).apply()
        _settings.value = _settings.value.copy(selectedMapServices = services)
    }

    open fun setMapPowerLevels(levels: Set<Int>) {
        prefs.edit().putString("map_power_levels", levels.joinToString(",")).apply()
        _settings.value = _settings.value.copy(mapPowerLevels = levels)
    }

    open fun setMapIrveOperators(operators: Set<String>) {
        prefs.edit().putString("map_irve_operators", operators.joinToString(",")).apply()
        _settings.value = _settings.value.copy(mapIrveOperators = operators)
    }

    open fun setMapBrands(brands: Set<String>) {
        prefs.edit().putString("map_brands", brands.joinToString(",")).apply()
        _settings.value = _settings.value.copy(mapBrands = brands)
    }

    open fun setMapConnectorTypes(types: Set<String>) {
        prefs.edit().putString("map_connector_types", types.joinToString(",")).apply()
        _settings.value = _settings.value.copy(selectedMapConnectorTypes = types)
    }

    open fun setMapTrafficEnabled(value: Boolean) {
        prefs.edit().putBoolean("map_traffic_enabled", value).apply()
        _settings.value = _settings.value.copy(mapTrafficEnabled = value)
    }

    open fun setOverpassAmenityTypes(types: Set<String>) {
        val value = types.ifEmpty { setOf("toilets", "drinking_water") }
        prefs.edit().putString("overpass_amenity_types", value.joinToString(",")).apply()
        _settings.value = _settings.value.copy(selectedOverpassAmenityTypes = value)
    }

    open fun setSttEnginePreference(preference: SttEnginePreference) {
        prefs.edit().putString("stt_engine_preference", preference.name).apply()
        _settings.value = _settings.value.copy(sttEnginePreference = preference)
    }

    open fun setVehicleType(type: VehicleType) {
        prefs.edit().putString("vehicle_type", type.name).apply()
        _settings.value = _settings.value.copy(vehicleType = type)
    }

    open fun setCarMapMode(mode: CarMapMode) {
        prefs.edit().putString("car_map_mode", mode.name).apply()
        _settings.value = _settings.value.copy(carMapMode = mode)
    }

    open fun setVehicleBrand(brand: String) {
        prefs.edit().putString("vehicle_brand", brand).apply()
        _settings.value = _settings.value.copy(vehicleBrand = brand)
    }

    open fun setVehicleModel(model: String) {
        prefs.edit().putString("vehicle_model", model).apply()
        _settings.value = _settings.value.copy(vehicleModel = model)
    }

    open fun setVehicleEnergy(energy: String) {
        prefs.edit().putString("vehicle_energy", energy).apply()
        _settings.value = _settings.value.copy(vehicleEnergy = energy)
    }

    open fun setVehicleGasTypes(types: Set<String>) {
        prefs.edit().putString("vehicle_gas_types", types.joinToString(",")).apply()
        _settings.value = _settings.value.copy(vehicleGasTypes = types)
    }

    open fun setVehiclePowerLevels(levels: Set<Int>) {
        prefs.edit().putString("vehicle_power_levels", levels.joinToString(",")).apply()
        _settings.value = _settings.value.copy(vehiclePowerLevels = levels)
    }

    open fun setFuelCard(card: FuelCard) {
        prefs.edit().putString("fuel_card", card.name).apply()
        _settings.value = _settings.value.copy(fuelCard = card)
    }

    open fun setUseVehicleFilter(use: Boolean) {
        prefs.edit().putBoolean("use_vehicle_filter", use).apply()
        _settings.value = _settings.value.copy(useVehicleFilter = use)
    }

    open fun setEvRangeKm(km: Int) {
        val value = km.coerceIn(50, 1000)
        prefs.edit().putInt("ev_range_km", value).apply()
        _settings.value = _settings.value.copy(evRangeKm = value)
    }

    open fun setEvConsumptionKwhPer100km(consumption: Float?) {
        if (consumption != null && consumption > 0f) {
            prefs.edit().putFloat("ev_consumption_kwh_100", consumption).apply()
            _settings.value = _settings.value.copy(evConsumptionKwhPer100km = consumption)
        } else {
            prefs.edit().remove("ev_consumption_kwh_100").apply()
            _settings.value = _settings.value.copy(evConsumptionKwhPer100km = null)
        }
    }

    /** Local rating for a POI (1–5). No backend; stored in SharedPreferences. */
    open fun getPoiRating(poiId: String): Int? {
        val v = prefs.getInt("poi_rating_$poiId", -1)
        return if (v in 1..5) v else null
    }

    /** Set local rating for a POI (1–5). */
    open fun setPoiRating(poiId: String, value: Int) {
        val v = value.coerceIn(1, 5)
        prefs.edit().putInt("poi_rating_$poiId", v).apply()
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

    private fun saveSettingsInternal(settings: AppSettings) {
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
            .putString("vehicle_brand", settings.vehicleBrand)
            .putString("vehicle_model", settings.vehicleModel)
            .putString("vehicle_energy", settings.vehicleEnergy)
            .putString("vehicle_gas_types", settings.vehicleGasTypes.joinToString(","))
            .putString("vehicle_power_levels", settings.vehiclePowerLevels.joinToString(","))
            .putString("fuel_card", settings.fuelCard.name)
            .putBoolean("use_vehicle_filter", settings.useVehicleFilter)
            .putString("selected_poi_providers", settings.selectedPoiProviders.joinToString(",") { it.name })
            .putString("map_energy_types", settings.selectedMapEnergyTypes.joinToString(","))
            .putString("map_enseigne_type", settings.mapEnseigneType)
            .putString("map_services", settings.selectedMapServices.joinToString(","))
            .putString("map_power_levels", settings.mapPowerLevels.joinToString(","))
            .putString("map_irve_operators", settings.mapIrveOperators.joinToString(","))
            .putString("map_brands", settings.mapBrands.joinToString(","))
            .putString("map_connector_types", settings.selectedMapConnectorTypes.joinToString(","))
            .putBoolean("map_traffic_enabled", settings.mapTrafficEnabled)
            .putInt("ev_range_km", settings.evRangeKm.coerceIn(50, 1000))
            .apply { settings.evConsumptionKwhPer100km?.let { putFloat("ev_consumption_kwh_100", it) } ?: remove("ev_consumption_kwh_100") }
            .putString("openchargemap_key", settings.openChargeMapKey)
            .putString("mobiliteit_luxembourg_key", settings.mobiliteitLuxembourgKey)
            .putString("overpass_amenity_types", settings.selectedOverpassAmenityTypes.joinToString(","))
            .putString("vehicle_type", settings.vehicleType.name)
            .putString("car_map_mode", settings.carMapMode.name)
            .putString("openai_key", settings.openAiKey)
            .putString("openai_model", settings.openAiModel.name)
            .putString("elevenlabs_key", settings.elevenLabsKey)
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
            .putString("jules_key", settings.julesKey)
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
            .apply { settings.tollDataPath?.let { putString("toll_data_path", it) } ?: remove("toll_data_path") }
            .putString("last_jules_repo_id", settings.lastJulesRepoId)
            .putString("last_jules_repo_name", settings.lastJulesRepoName)
            .putString("google_user_name", settings.googleUserName)
            .putBoolean("is_logged_in", settings.isLoggedIn)
            .apply()

        // Update StateFlow immediately with the new values to ensure UI and agent switching update right away
        _settings.value = settings
    }

    open fun saveSettings(
        openAiKey: String,
        openAiModel: OpenAiModel = _settings.value.openAiModel,
        elevenLabsKey: String,
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
        julesKey: String = "",
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
            vehicleBrand = _settings.value.vehicleBrand,
            vehicleModel = _settings.value.vehicleModel,
            vehicleEnergy = _settings.value.vehicleEnergy,
            vehicleGasTypes = _settings.value.vehicleGasTypes,
            vehiclePowerLevels = _settings.value.vehiclePowerLevels,
            fuelCard = _settings.value.fuelCard,
            useVehicleFilter = _settings.value.useVehicleFilter,
            selectedPoiProviders = _settings.value.selectedPoiProviders,
            selectedMapEnergyTypes = _settings.value.selectedMapEnergyTypes,
            mapEnseigneType = _settings.value.mapEnseigneType,
            selectedMapServices = _settings.value.selectedMapServices,
            mapPowerLevels = _settings.value.mapPowerLevels,
            mapIrveOperators = _settings.value.mapIrveOperators,
            mapBrands = _settings.value.mapBrands,
            selectedMapConnectorTypes = _settings.value.selectedMapConnectorTypes,
            mapTrafficEnabled = _settings.value.mapTrafficEnabled,
            evRangeKm = _settings.value.evRangeKm,
            evConsumptionKwhPer100km = _settings.value.evConsumptionKwhPer100km,
            openChargeMapKey = _settings.value.openChargeMapKey,
            selectedOverpassAmenityTypes = _settings.value.selectedOverpassAmenityTypes,
            vehicleType = _settings.value.vehicleType,
            carMapMode = _settings.value.carMapMode,
            openAiKey = openAiKey,
            openAiModel = openAiModel,
            elevenLabsKey = elevenLabsKey,
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
            julesKey = julesKey.ifBlank { _settings.value.julesKey },
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
            tollDataPath = _settings.value.tollDataPath,
            lastJulesRepoId = _settings.value.lastJulesRepoId,
            lastJulesRepoName = _settings.value.lastJulesRepoName,
            googleUserName = _settings.value.googleUserName,
            isLoggedIn = _settings.value.isLoggedIn
        )
        saveSettings(newSettings)
    }
}
