package fr.geoking.julius.auto

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.*
import fr.geoking.julius.AgentType
import fr.geoking.julius.SettingsManager
import fr.geoking.julius.shared.SttEnginePreference

class AutoSettingsScreen(
    carContext: CarContext,
    private val settingsManager: SettingsManager
) : Screen(carContext) {

    override fun onGetTemplate(): Template {
        val settings = settingsManager.settings.value

        val listBuilder = ItemList.Builder()

        listBuilder.addItem(
            Row.Builder()
                .setTitle("Agent")
                .addText(settings.selectedAgent.name)
                .setOnClickListener {
                    screenManager.push(AutoAgentSelectionScreen(carContext, settingsManager))
                }
                .build()
        )

        if (settings.selectedAgent == AgentType.Native || settings.selectedAgent == AgentType.ElevenLabs) {
            listBuilder.addItem(
                Row.Builder()
                    .setTitle("IA Model")
                    .addText(settings.selectedModel.displayName)
                    .setOnClickListener {
                        screenManager.push(AutoModelSelectionScreen(carContext, settingsManager))
                    }
                    .build()
            )
        }

        if (settings.selectedAgent == AgentType.OpenAI) {
            listBuilder.addItem(
                Row.Builder()
                    .setTitle("IA Model")
                    .addText(settings.openAiModel.displayName)
                    .setOnClickListener {
                        screenManager.push(AutoOpenAiModelSelectionScreen(carContext, settingsManager))
                    }
                    .build()
            )
        }

        if (settings.selectedAgent == AgentType.Gemini) {
            listBuilder.addItem(
                Row.Builder()
                    .setTitle("IA Model")
                    .addText(settings.geminiModel.displayName)
                    .setOnClickListener {
                        screenManager.push(AutoGeminiModelSelectionScreen(carContext, settingsManager))
                    }
                    .build()
            )
        }

        if (settings.selectedAgent == AgentType.Local) {
            listBuilder.addItem(
                Row.Builder()
                    .setTitle("Download model (Llamatik)")
                    .addText("Download GGUF model for offline use")
                    .setOnClickListener {
                        screenManager.push(AutoLocalModelScreen(carContext, settingsManager))
                    }
                    .build()
            )
        }

        listBuilder.addItem(
            Row.Builder()
                .setTitle("Download toll data (OpenTollData)")
                .addText("French highway toll estimation")
                .setOnClickListener {
                    screenManager.push(AutoTollDataScreen(carContext, settingsManager))
                }
                .build()
        )

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
                .addText("Play flavor only")
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

        return ListTemplate.Builder()
            .setSingleList(listBuilder.build())
            .setHeader(Header.Builder().setTitle("Settings").setStartHeaderAction(Action.BACK).build())
            .build()
    }
}

private fun sttEngineLabel(pref: SttEnginePreference): String = when (pref) {
    SttEnginePreference.LocalOnly -> "Local only (Vosk)"
    SttEnginePreference.LocalFirst -> "Local first (Vosk, then cloud)"
    SttEnginePreference.NativeOnly -> "Native only (cloud)"
}
