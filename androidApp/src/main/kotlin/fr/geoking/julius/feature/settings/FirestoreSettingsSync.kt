package fr.geoking.julius.feature.settings

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import fr.geoking.julius.AppSettings
import fr.geoking.julius.SettingsManager
import fr.geoking.julius.AgentType
import fr.geoking.julius.AppTheme
import fr.geoking.julius.CarMapMode
import fr.geoking.julius.FractalColorIntensity
import fr.geoking.julius.FractalQuality
import fr.geoking.julius.FuelCard
import fr.geoking.julius.GeminiModel
import fr.geoking.julius.OpenAiModel
import fr.geoking.julius.PerplexityModel
import fr.geoking.julius.SpeakingInterruptMode
import fr.geoking.julius.TextAnimation
import fr.geoking.julius.VehicleType
import fr.geoking.julius.poi.PoiProviderType
import fr.geoking.julius.shared.voice.SttEnginePreference
import kotlinx.coroutines.tasks.await

private const val TAG = "FirestoreSettingsSync"
private const val COLLECTION_SETTINGS = "user_settings"
private const val DOCUMENT_ID = "app_settings"

class FirestoreSettingsSync(
    private val firestore: FirebaseFirestore,
    private val firebaseAuth: FirebaseAuth
) {
    suspend fun uploadSettings(settings: AppSettings) {
        val userId = firebaseAuth.currentUser?.uid ?: return
        try {
            val data = settingsToMap(settings)
            firestore.collection(COLLECTION_SETTINGS)
                .document(userId)
                .set(data, SetOptions.merge())
                .await()
            Log.d(TAG, "uploadSettings: success")
        } catch (e: Exception) {
            Log.e(TAG, "uploadSettings: failed", e)
        }
    }

    suspend fun downloadAndMerge(localSettings: AppSettings): AppSettings? {
        val userId = firebaseAuth.currentUser?.uid ?: return null
        try {
            val doc = firestore.collection(COLLECTION_SETTINGS)
                .document(userId)
                .get()
                .await()

            if (!doc.exists()) {
                Log.d(TAG, "downloadAndMerge: no remote settings found")
                return null
            }

            val remoteData = doc.data ?: return null
            return mergeSettings(localSettings, remoteData)
        } catch (e: Exception) {
            Log.e(TAG, "downloadAndMerge: failed", e)
            return null
        }
    }

    private fun settingsToMap(s: AppSettings): Map<String, Any?> {
        return mapOf(
            "vehicleBrand" to s.vehicleBrand,
            "vehicleModel" to s.vehicleModel,
            "vehicleEnergy" to s.vehicleEnergy,
            "vehicleGasTypes" to s.vehicleGasTypes.toList(),
            "vehiclePowerLevels" to s.vehiclePowerLevels.toList(),
            "fuelCard" to s.fuelCard.name,
            "useVehicleFilter" to s.useVehicleFilter,
            "selectedPoiProviders" to s.selectedPoiProviders.map { it.name },
            "selectedMapEnergyTypes" to s.selectedMapEnergyTypes.toList(),
            "mapEnseigneType" to s.mapEnseigneType,
            "mapBrands" to s.mapBrands.toList(),
            "selectedMapServices" to s.selectedMapServices.toList(),
            "mapPowerLevels" to s.mapPowerLevels.toList(),
            "mapIrveOperators" to s.mapIrveOperators.toList(),
            "selectedMapConnectorTypes" to s.selectedMapConnectorTypes.toList(),
            "mapTrafficEnabled" to s.mapTrafficEnabled,
            "evRangeKm" to s.evRangeKm,
            "evConsumptionKwhPer100km" to s.evConsumptionKwhPer100km,
            "openChargeMapKey" to s.openChargeMapKey,
            "selectedOverpassAmenityTypes" to s.selectedOverpassAmenityTypes.toList(),
            "vehicleType" to s.vehicleType.name,
            "carMapMode" to s.carMapMode.name,
            "openAiKey" to s.openAiKey,
            "openAiModel" to s.openAiModel.name,
            "elevenLabsKey" to s.elevenLabsKey,
            "elevenLabsScribe2" to s.elevenLabsScribe2,
            "perplexityKey" to s.perplexityKey,
            "geminiKey" to s.geminiKey,
            "geminiModel" to s.geminiModel.name,
            "deepgramKey" to s.deepgramKey,
            "firebaseAiKey" to s.firebaseAiKey,
            "firebaseAiModel" to s.firebaseAiModel,
            "opencodeZenKey" to s.opencodeZenKey,
            "opencodeZenModel" to s.opencodeZenModel,
            "completionsMeKey" to s.completionsMeKey,
            "completionsMeModel" to s.completionsMeModel,
            "apifreellmKey" to s.apifreellmKey,
            "deepSeekKey" to s.deepSeekKey,
            "deepSeekModel" to s.deepSeekModel,
            "groqKey" to s.groqKey,
            "groqModel" to s.groqModel,
            "openRouterKey" to s.openRouterKey,
            "openRouterModel" to s.openRouterModel,
            "julesKey" to s.julesKey,
            "githubApiKey" to s.githubApiKey,
            "selectedAgent" to s.selectedAgent.name,
            "selectedTheme" to s.selectedTheme.name,
            "selectedModel" to s.selectedModel.name,
            "fractalQuality" to s.fractalQuality.name,
            "fractalColorIntensity" to s.fractalColorIntensity.name,
            "extendedActionsEnabled" to s.extendedActionsEnabled,
            "wakeWordEnabled" to s.wakeWordEnabled,
            "speakingInterruptMode" to s.speakingInterruptMode.name,
            "useCarMic" to s.useCarMic,
            "muteMediaOnCar" to s.muteMediaOnCar,
            "sttEnginePreference" to s.sttEnginePreference.name,
            "textAnimation" to s.textAnimation.name,
            "mobiliteitLuxembourgKey" to s.mobiliteitLuxembourgKey
        )
    }

    private fun mergeSettings(local: AppSettings, remote: Map<String, Any?>): AppSettings {
        // "Merge: local first. If local data, use local first"
        // Interpretation: if the local value is NOT the default value (i.e. user modified it), keep local.
        // Otherwise, take from remote.

        val default = AppSettings()

        fun <T> pick(current: T, remoteVal: Any?, defaultVal: T, parser: (Any) -> T): T {
            return if (current != defaultVal) {
                current
            } else {
                remoteVal?.let { try { parser(it) } catch(e: Exception) { defaultVal } } ?: defaultVal
            }
        }

        fun parseString(v: Any) = v as String
        fun parseInt(v: Any) = (v as Long).toInt()
        fun parseFloat(v: Any) = (v as Double).toFloat()
        fun parseBoolean(v: Any) = v as Boolean
        fun <E : Enum<E>> parseEnum(v: Any, enumClass: Class<E>): E = java.lang.Enum.valueOf(enumClass, v as String)
        fun parseStringSet(v: Any) = (v as List<*>).filterIsInstance<String>().toSet()
        fun parseIntSet(v: Any) = (v as List<*>).filterIsInstance<Long>().map { it.toInt() }.toSet()
        fun parsePoiProviderSet(v: Any) = (v as List<*>).filterIsInstance<String>().mapNotNull {
            try { PoiProviderType.valueOf(it) } catch(e: Exception) { null }
        }.toSet()

        return local.copy(
            vehicleBrand = pick(local.vehicleBrand, remote["vehicleBrand"], default.vehicleBrand, ::parseString),
            vehicleModel = pick(local.vehicleModel, remote["vehicleModel"], default.vehicleModel, ::parseString),
            vehicleEnergy = pick(local.vehicleEnergy, remote["vehicleEnergy"], default.vehicleEnergy, ::parseString),
            vehicleGasTypes = pick(local.vehicleGasTypes, remote["vehicleGasTypes"], default.vehicleGasTypes, ::parseStringSet),
            vehiclePowerLevels = pick(local.vehiclePowerLevels, remote["vehiclePowerLevels"], default.vehiclePowerLevels, ::parseIntSet),
            fuelCard = pick(local.fuelCard, remote["fuelCard"], default.fuelCard) { parseEnum(it, FuelCard::class.java) },
            useVehicleFilter = pick(local.useVehicleFilter, remote["useVehicleFilter"], default.useVehicleFilter, ::parseBoolean),
            selectedPoiProviders = pick(local.selectedPoiProviders, remote["selectedPoiProviders"], default.selectedPoiProviders, ::parsePoiProviderSet),
            selectedMapEnergyTypes = pick(local.selectedMapEnergyTypes, remote["selectedMapEnergyTypes"], default.selectedMapEnergyTypes, ::parseStringSet),
            mapEnseigneType = pick(local.mapEnseigneType, remote["mapEnseigneType"], default.mapEnseigneType, ::parseString),
            mapBrands = pick(local.mapBrands, remote["mapBrands"], default.mapBrands, ::parseStringSet),
            selectedMapServices = pick(local.selectedMapServices, remote["selectedMapServices"], default.selectedMapServices, ::parseStringSet),
            mapPowerLevels = pick(local.mapPowerLevels, remote["mapPowerLevels"], default.mapPowerLevels, ::parseIntSet),
            mapIrveOperators = pick(local.mapIrveOperators, remote["mapIrveOperators"], default.mapIrveOperators, ::parseStringSet),
            selectedMapConnectorTypes = pick(local.selectedMapConnectorTypes, remote["selectedMapConnectorTypes"], default.selectedMapConnectorTypes, ::parseStringSet),
            mapTrafficEnabled = pick(local.mapTrafficEnabled, remote["mapTrafficEnabled"], default.mapTrafficEnabled, ::parseBoolean),
            evRangeKm = pick(local.evRangeKm, remote["evRangeKm"], default.evRangeKm, ::parseInt),
            evConsumptionKwhPer100km = pick(local.evConsumptionKwhPer100km, remote["evConsumptionKwhPer100km"], default.evConsumptionKwhPer100km) { (it as Double).toFloat() },
            openChargeMapKey = pick(local.openChargeMapKey, remote["openChargeMapKey"], default.openChargeMapKey, ::parseString),
            selectedOverpassAmenityTypes = pick(local.selectedOverpassAmenityTypes, remote["selectedOverpassAmenityTypes"], default.selectedOverpassAmenityTypes, ::parseStringSet),
            vehicleType = pick(local.vehicleType, remote["vehicleType"], default.vehicleType) { parseEnum(it, VehicleType::class.java) },
            carMapMode = pick(local.carMapMode, remote["carMapMode"], default.carMapMode) { parseEnum(it, CarMapMode::class.java) },
            openAiKey = pick(local.openAiKey, remote["openAiKey"], default.openAiKey, ::parseString),
            openAiModel = pick(local.openAiModel, remote["openAiModel"], default.openAiModel) { parseEnum(it, OpenAiModel::class.java) },
            elevenLabsKey = pick(local.elevenLabsKey, remote["elevenLabsKey"], default.elevenLabsKey, ::parseString),
            elevenLabsScribe2 = pick(local.elevenLabsScribe2, remote["elevenLabsScribe2"], default.elevenLabsScribe2, ::parseBoolean),
            perplexityKey = pick(local.perplexityKey, remote["perplexityKey"], default.perplexityKey, ::parseString),
            geminiKey = pick(local.geminiKey, remote["geminiKey"], default.geminiKey, ::parseString),
            geminiModel = pick(local.geminiModel, remote["geminiModel"], default.geminiModel) { parseEnum(it, GeminiModel::class.java) },
            deepgramKey = pick(local.deepgramKey, remote["deepgramKey"], default.deepgramKey, ::parseString),
            firebaseAiKey = pick(local.firebaseAiKey, remote["firebaseAiKey"], default.firebaseAiKey, ::parseString),
            firebaseAiModel = pick(local.firebaseAiModel, remote["firebaseAiModel"], default.firebaseAiModel, ::parseString),
            opencodeZenKey = pick(local.opencodeZenKey, remote["opencodeZenKey"], default.opencodeZenKey, ::parseString),
            opencodeZenModel = pick(local.opencodeZenModel, remote["opencodeZenModel"], default.opencodeZenModel, ::parseString),
            completionsMeKey = pick(local.completionsMeKey, remote["completionsMeKey"], default.completionsMeKey, ::parseString),
            completionsMeModel = pick(local.completionsMeModel, remote["completionsMeModel"], default.completionsMeModel, ::parseString),
            apifreellmKey = pick(local.apifreellmKey, remote["apifreellmKey"], default.apifreellmKey, ::parseString),
            deepSeekKey = pick(local.deepSeekKey, remote["deepSeekKey"], default.deepSeekKey, ::parseString),
            deepSeekModel = pick(local.deepSeekModel, remote["deepSeekModel"], default.deepSeekModel, ::parseString),
            groqKey = pick(local.groqKey, remote["groqKey"], default.groqKey, ::parseString),
            groqModel = pick(local.groqModel, remote["groqModel"], default.groqModel, ::parseString),
            openRouterKey = pick(local.openRouterKey, remote["openRouterKey"], default.openRouterKey, ::parseString),
            openRouterModel = pick(local.openRouterModel, remote["openRouterModel"], default.openRouterModel, ::parseString),
            julesKey = pick(local.julesKey, remote["julesKey"], default.julesKey, ::parseString),
            githubApiKey = pick(local.githubApiKey, remote["githubApiKey"], default.githubApiKey, ::parseString),
            selectedAgent = pick(local.selectedAgent, remote["selectedAgent"], default.selectedAgent) { parseEnum(it, AgentType::class.java) },
            selectedTheme = pick(local.selectedTheme, remote["selectedTheme"], default.selectedTheme) { parseEnum(it, AppTheme::class.java) },
            selectedModel = pick(local.selectedModel, remote["selectedModel"], default.selectedModel) { parseEnum(it, PerplexityModel::class.java) },
            fractalQuality = pick(local.fractalQuality, remote["fractalQuality"], default.fractalQuality) { parseEnum(it, FractalQuality::class.java) },
            fractalColorIntensity = pick(local.fractalColorIntensity, remote["fractalColorIntensity"], default.fractalColorIntensity) { parseEnum(it, FractalColorIntensity::class.java) },
            extendedActionsEnabled = pick(local.extendedActionsEnabled, remote["extendedActionsEnabled"], default.extendedActionsEnabled, ::parseBoolean),
            wakeWordEnabled = pick(local.wakeWordEnabled, remote["wakeWordEnabled"], default.wakeWordEnabled, ::parseBoolean),
            speakingInterruptMode = pick(local.speakingInterruptMode, remote["speakingInterruptMode"], default.speakingInterruptMode) { parseEnum(it, SpeakingInterruptMode::class.java) },
            useCarMic = pick(local.useCarMic, remote["useCarMic"], default.useCarMic, ::parseBoolean),
            muteMediaOnCar = pick(local.muteMediaOnCar, remote["muteMediaOnCar"], default.muteMediaOnCar, ::parseBoolean),
            sttEnginePreference = pick(local.sttEnginePreference, remote["sttEnginePreference"], default.sttEnginePreference) { parseEnum(it, SttEnginePreference::class.java) },
            textAnimation = pick(local.textAnimation, remote["textAnimation"], default.textAnimation) { parseEnum(it, TextAnimation::class.java) },
            mobiliteitLuxembourgKey = pick(local.mobiliteitLuxembourgKey, remote["mobiliteitLuxembourgKey"], default.mobiliteitLuxembourgKey, ::parseString)
        )
    }
}
