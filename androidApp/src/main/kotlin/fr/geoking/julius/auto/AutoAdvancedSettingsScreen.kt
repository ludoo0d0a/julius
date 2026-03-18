package fr.geoking.julius.auto

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.*
import fr.geoking.julius.SettingsManager
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
                .setTitle("Mute Radio")
                .addText("Mute media when Julius is active")
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
                .setTitle("Hey Julius (during speaking)")
                .addText("Say \"hey julius\" to interrupt and start listening")
                .setToggle(
                    Toggle.Builder { checked ->
                        val current = settingsManager.settings.value
                        settingsManager.saveSettings(current.copy(heyJuliusDuringSpeakingEnabled = checked))
                        invalidate()
                    }.setChecked(settings.heyJuliusDuringSpeakingEnabled).build()
                )
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
