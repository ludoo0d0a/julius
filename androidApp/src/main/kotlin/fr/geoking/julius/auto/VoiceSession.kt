package fr.geoking.julius.auto

import android.content.Intent
import android.util.Log
import androidx.car.app.Screen
import androidx.car.app.Session
import fr.geoking.julius.SettingsManager
import fr.geoking.julius.shared.ConversationStore
import fr.geoking.julius.community.CommunityPoiRepository
import fr.geoking.julius.community.FavoritesRepository
import fr.geoking.julius.providers.PoiProvider
import fr.geoking.julius.providers.availability.BorneAvailabilityProviderFactory
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class VoiceSession : Session(), KoinComponent {

    private val store: ConversationStore by inject()
    private val settingsManager: SettingsManager by inject()
    private val poiProvider: PoiProvider by inject()
    private val availabilityProviderFactory: BorneAvailabilityProviderFactory by inject()
    private val communityRepo: CommunityPoiRepository by inject()
    private val favoritesRepo: FavoritesRepository by inject()
    private val voiceManager: fr.geoking.julius.shared.VoiceManager by inject()

    override fun onCreateScreen(intent: Intent): Screen {
        (voiceManager as? fr.geoking.julius.AndroidVoiceManager)?.setCarContext(carContext)
        return try {
            MainScreen(carContext, store, settingsManager, poiProvider, availabilityProviderFactory, communityRepo, favoritesRepo)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create MainScreen", e)
            ErrorScreen(
                carContext,
                errorMessage = e.message ?: e.toString(),
                errorDetail = e.stackTraceToString().take(300)
            )
        }
    }

    companion object {
        private const val TAG = "VoiceSession"
    }
}
