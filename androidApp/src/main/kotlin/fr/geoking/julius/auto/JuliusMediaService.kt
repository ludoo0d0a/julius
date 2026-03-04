package fr.geoking.julius.auto

import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import fr.geoking.julius.AndroidVoiceManager
import fr.geoking.julius.shared.VoiceManager
import org.koin.android.ext.android.inject

@UnstableApi
class JuliusMediaService : MediaLibraryService() {

    private val voiceManager: VoiceManager by inject()

    override fun onCreate() {
        super.onCreate()
        if (voiceManager is AndroidVoiceManager) {
            (voiceManager as AndroidVoiceManager).initMediaSession(this)
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibraryService.MediaLibrarySession? {
        return if (voiceManager is AndroidVoiceManager) {
            (voiceManager as AndroidVoiceManager).mediaLibrarySession
        } else null
    }

    override fun onDestroy() {
        if (voiceManager is AndroidVoiceManager) {
            (voiceManager as AndroidVoiceManager).releaseMediaSession()
        }
        super.onDestroy()
    }
}
