package fr.geoking.julius.auto

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.*
import fr.geoking.julius.SettingsManager
import fr.geoking.julius.SpeakingInterruptMode
import fr.geoking.julius.shared.SttEnginePreference

class AutoAdvancedSettingsScreen(
    carContext: CarContext,
    private val settingsManager: SettingsManager
) : Screen(carContext) {

    override fun onGetTemplate(): Template {
        val settings = settingsManager.settings.value
        val listBuilder = ItemList.Builder()

        listBuilder.addItem(
            Row.Builder()
                .setTitle("Extended Actions")
                .addText("Allow AI to access sensors")
                .setToggle(
                    Toggle.Builder { checked ->
                        val current = settingsManager.settings.value
                        settingsManager.saveSettings(current.copy(extendedActionsEnabled = checked))
                        invalidate()
                    }.setChecked(settings.extendedActionsEnabled).build()
                )
                .build()
        )

        listBuilder.addItem(
            Row.Builder()
                .setTitle("Mute Media")
                .addText("Pause other audio when Julius is active")
                .setToggle(
                    Toggle.Builder { checked ->
                        val current = settingsManager.settings.value
                        settingsManager.saveSettings(current.copy(muteMediaOnCar = checked))
                        invalidate()
                    }.setChecked(settings.muteMediaOnCar).build()
                )
                .build()
        )

        listBuilder.addItem(
            Row.Builder()
                .setTitle("Use Car Microphone")
                .addText("Android Auto session only")
                .setToggle(
                    Toggle.Builder { checked ->
                        val current = settingsManager.settings.value
                        settingsManager.saveSettings(current.copy(useCarMic = checked))
                        invalidate()
                    }.setChecked(settings.useCarMic).build()
                )
                .build()
        )

        listBuilder.addItem(
            Row.Builder()
                .setTitle("STT engine (car)")
                .addText(sttEngineLabel(settings.sttEnginePreference))
                .setOnClickListener {
                    screenManager.push(AutoSttEngineSelectionScreen(carContext, settingsManager))
                }
                .build()
        )

        listBuilder.addItem(
            Row.Builder()
                .setTitle("Hands-free Wake Word")
                .addText("Say 'Julius' to start")
                .setToggle(
                    Toggle.Builder { checked ->
                        val current = settingsManager.settings.value
                        settingsManager.saveSettings(current.copy(wakeWordEnabled = checked))
                        invalidate()
                    }.setChecked(settings.wakeWordEnabled).build()
                )
                .build()
        )

        listBuilder.addItem(
            Row.Builder()
                .setTitle("Interrupt while speaking")
                .addText(autoSpeakingInterruptSummary(settings.speakingInterruptMode))
                .setOnClickListener {
                    screenManager.push(AutoSpeakingInterruptSelectionScreen(carContext, settingsManager))
                }
                .build()
        )

        return ListTemplate.Builder()
            .setSingleList(listBuilder.build())
            .setHeader(Header.Builder().setTitle("Voice & Advanced Settings").setStartHeaderAction(Action.BACK).build())
            .build()
    }

    private fun sttEngineLabel(pref: SttEnginePreference): String = when (pref) {
        SttEnginePreference.LocalOnly -> "Local only (Vosk)"
        SttEnginePreference.LocalFirst -> "Local first (Vosk, then cloud)"
        SttEnginePreference.NativeOnly -> "Native only (cloud)"
    }
}

private fun autoSpeakingInterruptSummary(mode: SpeakingInterruptMode): String = when (mode) {
    SpeakingInterruptMode.OFF -> "Off — no mic while speaking"
    SpeakingInterruptMode.WAKE_WORD -> "Hey Julius only"
    SpeakingInterruptMode.ANY_SPEECH -> "Any speech"
}
