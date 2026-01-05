package com.antigravity.voiceai.auto

import android.content.Intent
import androidx.car.app.Screen
import androidx.car.app.Session
import com.antigravity.voiceai.shared.ConversationStore
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class VoiceSession : Session(), KoinComponent {

    private val store: ConversationStore by inject()

    override fun onCreateScreen(intent: Intent): Screen {
        return MainScreen(carContext, store)
    }
}
