package fr.geoking.julius.auto

import android.content.Intent
import android.util.Log
import androidx.car.app.Screen
import androidx.car.app.Session
import fr.geoking.julius.SettingsManager
import fr.geoking.julius.shared.ConversationStore
import fr.geoking.julius.shared.PoiProvider
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class VoiceSession : Session(), KoinComponent {

    private val store: ConversationStore by inject()
    private val settingsManager: SettingsManager by inject()
    private val poiProvider: PoiProvider by inject()

    override fun onCreateScreen(intent: Intent): Screen {
        return try {
            MainScreen(carContext, store, settingsManager, poiProvider)
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
