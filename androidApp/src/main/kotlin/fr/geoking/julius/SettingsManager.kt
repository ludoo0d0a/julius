package fr.geoking.julius

import android.content.Context
import android.content.SharedPreferences
import fr.geoking.julius.VehicleType
import fr.geoking.julius.shared.SttEnginePreference
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AgentType { OpenAI, ElevenLabs, Deepgram, Native, Gemini, FirebaseAI, OpenCodeZen, CompletionsMe, ApiFreeLLM, Local, Offline }

val DEFAULT_AGENT = AgentType.Gemini

enum class AppTheme { Particles, Sphere, Waves, Fractal, Micro }
enum class TextAnimation { None, Genie, Blur, Fade, Zoom, Falling }
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
val DEFAULT_MAP_ENERGY_TYPES = setOf("gazole", "sp98", "sp95_e10", "sp95", "gplc", "e85", "electric")

/** Enseigne type for map filter, aligned with prix-carburants.gouv.fr. "all" = Toutes les enseignes. */
const val DEFAULT_MAP_ENSEIGNE_TYPE = "all"

/** Min power filter for IRVE (kW). 0 = no filter. Aligned with LibreChargeMap. */
const val DEFAULT_MAP_MIN_POWER_KW = 0

/** IRVE operator filter. "all" = Tous les opérateurs. */
const val DEFAULT_MAP_IRVE_OPERATOR = "all"

/** Default EV range in km for route planning. */
const val DEFAULT_EV_RANGE_KM = 300

data class AppSettings(
    val selectedPoiProvider: fr.geoking.julius.providers.PoiProviderType = fr.geoking.julius.providers.PoiProviderType.Routex,
    /** Selected energy types to show on map (e.g. sp95, sp98, gazole, e85, electric). Empty = show all. */
    val selectedMapEnergyTypes: Set<String> = DEFAULT_MAP_ENERGY_TYPES,
    /** Type d'enseigne: "all", "major", "gms", "independant". Filter applied when provider supplies data. */
    val mapEnseigneType: String = DEFAULT_MAP_ENSEIGNE_TYPE,
    /** Selected service ids for map filter (e.g. bornes_electriques, automate_cb). Applied when provider supplies data. */
    val selectedMapServices: Set<String> = emptySet(),
    /** Min power in kW for IRVE stations (0 = no filter). Applied when provider is DataGouvElec. */
    val mapMinPowerKw: Int = DEFAULT_MAP_MIN_POWER_KW,
    /** IRVE operator filter: "all", "atlante", "avia", "zunder", "ionity", "fastned", "tesla". Applied when provider is DataGouvElec. */
    val mapIrveOperator: String = DEFAULT_MAP_IRVE_OPERATOR,
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
    val selectedAgent: AgentType = DEFAULT_AGENT,
    val selectedTheme: AppTheme = AppTheme.Particles,
    val selectedModel: PerplexityModel = PerplexityModel.LLAMA_3_1_SONAR_SMALL,
    val fractalQuality: FractalQuality = FractalQuality.Medium,
    val fractalColorIntensity: FractalColorIntensity = FractalColorIntensity.Medium,
    val extendedActionsEnabled: Boolean = false,
    val wakeWordEnabled: Boolean = false,
    val useCarMic: Boolean = false,
    /** STT engine for car mic path: LocalOnly (Vosk only), LocalFirst (Vosk then agent), NativeOnly (agent only). */
    val sttEnginePreference: SttEnginePreference = SttEnginePreference.LocalFirst,
    val textAnimation: TextAnimation = TextAnimation.Fade,
    /** Path to local GGUF model: asset-relative (e.g. "models/phi-2.Q4_0.gguf") or absolute path after download. */
    val localModelPath: String = "models/phi-2.Q4_0.gguf",
    /** Selected local model variant for download UI; must match [fr.geoking.julius.ui.LocalModelVariant].name (e.g. Phi2Q4_0). */
    val selectedLocalModelVariant: String = "Phi2Q4_0",
    val lastJulesRepoId: String = "",
    val lastJulesRepoName: String = "",
    val googleUserName: String? = null,
    val isLoggedIn: Boolean = false,
    /** Path to downloaded OpenTollData JSON for highway toll estimation; null until user downloads. */
    val tollDataPath: String? = null
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
        val lastJulesRepoId = prefs.getString("last_jules_repo_id", "") ?: ""
        val lastJulesRepoName = prefs.getString("last_jules_repo_name", "") ?: ""
        val googleUserName = prefs.getString("google_user_name", null)
        val isLoggedIn = prefs.getBoolean("is_logged_in", false)

        // Persist build-time keys (from env/local.properties) when prefs were empty so they show in settings and are reused
        persistBuildTimeKeysIfUsed(
            openAiKey, elevenLabsKey, perplexityKey, geminiKey, deepgramKey,
            firebaseAiKey, firebaseAiModel, opencodeZenKey, opencodeZenModel,
            completionsMeKey, completionsMeModel, apifreellmKey, julesKey
        )

        val energyTypesStr = prefs.getString("map_energy_types", null)
        val selectedMapEnergyTypes = if (!energyTypesStr.isNullOrBlank()) {
            energyTypesStr.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        } else DEFAULT_MAP_ENERGY_TYPES
        val mapEnseigneType = prefs.getString("map_enseigne_type", DEFAULT_MAP_ENSEIGNE_TYPE) ?: DEFAULT_MAP_ENSEIGNE_TYPE
        val mapServicesStr = prefs.getString("map_services", null)
        val selectedMapServices = if (!mapServicesStr.isNullOrBlank()) {
            mapServicesStr.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        } else emptySet()
        val mapMinPowerKw = prefs.getInt("map_min_power_kw", DEFAULT_MAP_MIN_POWER_KW)
            .coerceIn(0, 300)
        val mapIrveOperator = prefs.getString("map_irve_operator", DEFAULT_MAP_IRVE_OPERATOR) ?: DEFAULT_MAP_IRVE_OPERATOR
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
        val overpassAmenityStr = prefs.getString("overpass_amenity_types", "toilets,drinking_water") ?: "toilets,drinking_water"
        val selectedOverpassAmenityTypes = overpassAmenityStr.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
            .ifEmpty { setOf("toilets", "drinking_water") }
        val vehicleType = try {
            VehicleType.valueOf(prefs.getString("vehicle_type", VehicleType.Car.name) ?: VehicleType.Car.name)
        } catch (e: IllegalArgumentException) {
            VehicleType.Car
        }

        return AppSettings(
            selectedPoiProvider = try {
                fr.geoking.julius.providers.PoiProviderType.valueOf(
                    prefs.getString("poi_provider", fr.geoking.julius.providers.PoiProviderType.Routex.name) ?: fr.geoking.julius.providers.PoiProviderType.Routex.name
                )
            } catch (e: IllegalArgumentException) {
                fr.geoking.julius.providers.PoiProviderType.Routex
            },
            selectedMapEnergyTypes = selectedMapEnergyTypes,
            mapEnseigneType = mapEnseigneType,
            selectedMapServices = selectedMapServices,
            mapMinPowerKw = mapMinPowerKw,
            mapIrveOperator = mapIrveOperator,
            selectedMapConnectorTypes = selectedMapConnectorTypes,
            mapTrafficEnabled = mapTrafficEnabled,
            evRangeKm = evRangeKm,
            evConsumptionKwhPer100km = evConsumptionKwhPer100km,
            openChargeMapKey = openChargeMapKey,
            selectedOverpassAmenityTypes = selectedOverpassAmenityTypes,
            vehicleType = vehicleType,
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
            selectedAgent = try {
                val agentName = prefs.getString("agent", null)
                if (agentName != null) AgentType.valueOf(agentName)
                else DEFAULT_AGENT
            } catch (e: IllegalArgumentException) {
                android.util.Log.w("SettingsManager", "Invalid agent name in preferences, using default: ${e.message}")
                DEFAULT_AGENT
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
            extendedActionsEnabled = prefs.getBoolean("extended_actions_enabled", false),
            wakeWordEnabled = prefs.getBoolean("wake_word_enabled", false),
            useCarMic = prefs.getBoolean("use_car_mic", false),
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
            localModelPath = prefs.getString("local_model_path", "models/phi-2.Q4_0.gguf") ?: "models/phi-2.Q4_0.gguf",
            selectedLocalModelVariant = prefs.getString("selected_local_model_variant", "Phi2Q4_0") ?: "Phi2Q4_0",
            tollDataPath = prefs.getString("toll_data_path", null),
            lastJulesRepoId = lastJulesRepoId,
            lastJulesRepoName = lastJulesRepoName,
            googleUserName = googleUserName,
            isLoggedIn = isLoggedIn
        )
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
        julesKey: String
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
        edit.apply()
    }

    open fun setPoiProviderType(type: fr.geoking.julius.providers.PoiProviderType) {
        prefs.edit().putString("poi_provider", type.name).apply()
        _settings.value = _settings.value.copy(selectedPoiProvider = type)
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

    open fun setMapMinPowerKw(kw: Int) {
        val value = kw.coerceIn(0, 300)
        prefs.edit().putInt("map_min_power_kw", value).apply()
        _settings.value = _settings.value.copy(mapMinPowerKw = value)
    }

    open fun setMapIrveOperator(operator: String) {
        prefs.edit().putString("map_irve_operator", operator).apply()
        _settings.value = _settings.value.copy(mapIrveOperator = operator)
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
        prefs.edit()
            .putString("poi_provider", settings.selectedPoiProvider.name)
            .putString("map_energy_types", settings.selectedMapEnergyTypes.joinToString(","))
            .putString("map_enseigne_type", settings.mapEnseigneType)
            .putString("map_services", settings.selectedMapServices.joinToString(","))
            .putInt("map_min_power_kw", settings.mapMinPowerKw)
            .putString("map_irve_operator", settings.mapIrveOperator)
            .putString("map_connector_types", settings.selectedMapConnectorTypes.joinToString(","))
            .putBoolean("map_traffic_enabled", settings.mapTrafficEnabled)
            .putInt("ev_range_km", settings.evRangeKm.coerceIn(50, 1000))
            .apply { settings.evConsumptionKwhPer100km?.let { putFloat("ev_consumption_kwh_100", it) } ?: remove("ev_consumption_kwh_100") }
            .putString("openchargemap_key", settings.openChargeMapKey)
            .putString("overpass_amenity_types", settings.selectedOverpassAmenityTypes.joinToString(","))
            .putString("vehicle_type", settings.vehicleType.name)
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
            .putString("agent", settings.selectedAgent.name)
            .putString("theme", settings.selectedTheme.name)
            .putString("model", settings.selectedModel.name)
            .putString("fractal_quality", settings.fractalQuality.name)
            .putString("fractal_color_intensity", settings.fractalColorIntensity.name)
            .putBoolean("extended_actions_enabled", settings.extendedActionsEnabled)
            .putBoolean("wake_word_enabled", settings.wakeWordEnabled)
            .putBoolean("use_car_mic", settings.useCarMic)
            .putString("stt_engine_preference", settings.sttEnginePreference.name)
            .putString("text_animation", settings.textAnimation.name)
            .putString("local_model_path", settings.localModelPath)
            .putString("selected_local_model_variant", settings.selectedLocalModelVariant)
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
        extendedActionsEnabled: Boolean = false,
        wakeWordEnabled: Boolean = false,
        useCarMic: Boolean = false,
        sttEnginePreference: SttEnginePreference = _settings.value.sttEnginePreference,
        localModelPath: String = _settings.value.localModelPath,
        selectedLocalModelVariant: String = _settings.value.selectedLocalModelVariant
    ) {
        val newSettings = AppSettings(
            selectedPoiProvider = _settings.value.selectedPoiProvider,
            selectedMapEnergyTypes = _settings.value.selectedMapEnergyTypes,
            mapEnseigneType = _settings.value.mapEnseigneType,
            selectedMapServices = _settings.value.selectedMapServices,
            mapMinPowerKw = _settings.value.mapMinPowerKw,
            mapIrveOperator = _settings.value.mapIrveOperator,
            selectedMapConnectorTypes = _settings.value.selectedMapConnectorTypes,
            mapTrafficEnabled = _settings.value.mapTrafficEnabled,
            evRangeKm = _settings.value.evRangeKm,
            evConsumptionKwhPer100km = _settings.value.evConsumptionKwhPer100km,
            openChargeMapKey = _settings.value.openChargeMapKey,
            selectedOverpassAmenityTypes = _settings.value.selectedOverpassAmenityTypes,
            vehicleType = _settings.value.vehicleType,
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
            selectedAgent = agent,
            selectedTheme = theme,
            selectedModel = model,
            fractalQuality = fractalQuality,
            fractalColorIntensity = fractalColorIntensity,
            extendedActionsEnabled = extendedActionsEnabled,
            wakeWordEnabled = wakeWordEnabled,
            useCarMic = useCarMic,
            sttEnginePreference = sttEnginePreference,
            textAnimation = _settings.value.textAnimation,
            localModelPath = localModelPath,
            selectedLocalModelVariant = selectedLocalModelVariant,
            tollDataPath = _settings.value.tollDataPath,
            lastJulesRepoId = _settings.value.lastJulesRepoId,
            lastJulesRepoName = _settings.value.lastJulesRepoName,
            googleUserName = _settings.value.googleUserName,
            isLoggedIn = _settings.value.isLoggedIn
        )
        saveSettings(newSettings)
    }
}
