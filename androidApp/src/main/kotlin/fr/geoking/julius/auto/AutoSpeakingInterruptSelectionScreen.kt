package fr.geoking.julius.auto

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.Header
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import fr.geoking.julius.SettingsManager
import fr.geoking.julius.SpeakingInterruptMode

class AutoSpeakingInterruptSelectionScreen(
    carContext: CarContext,
    private val settingsManager: SettingsManager
) : Screen(carContext) {

    override fun onGetTemplate(): Template {
        val settings = settingsManager.settings.value
        val listBuilder = ItemList.Builder()
        SpeakingInterruptMode.entries.forEach { mode ->
            val isSelected = settings.speakingInterruptMode == mode
            listBuilder.addItem(
                Row.Builder()
                    .setTitle(speakingInterruptTitle(mode))
                    .addText(
                        buildString {
                            append(speakingInterruptSubtitle(mode))
                            if (isSelected) append(" · Selected")
                        }
                    )
                    .setOnClickListener {
                        settingsManager.saveSettings(settings.copy(speakingInterruptMode = mode))
                        screenManager.pop()
                    }
                    .build()
            )
        }
        return ListTemplate.Builder()
            .setSingleList(listBuilder.build())
            .setHeader(
                Header.Builder()
                    .setTitle("Interrupt while speaking")
                    .setStartHeaderAction(Action.BACK)
                    .build()
            )
            .build()
    }
}

private fun speakingInterruptTitle(mode: SpeakingInterruptMode): String = when (mode) {
    SpeakingInterruptMode.OFF -> "Off"
    SpeakingInterruptMode.WAKE_WORD -> "Hey Julius only"
    SpeakingInterruptMode.ANY_SPEECH -> "Any speech"
}

private fun speakingInterruptSubtitle(mode: SpeakingInterruptMode): String = when (mode) {
    SpeakingInterruptMode.OFF -> "No mic while Julius speaks"
    SpeakingInterruptMode.WAKE_WORD -> "Say \"hey julius\" or \"stop\""
    SpeakingInterruptMode.ANY_SPEECH -> "Talking stops playback (may react to echo)"
}
