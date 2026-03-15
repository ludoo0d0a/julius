package fr.geoking.julius.auto

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.*
import fr.geoking.julius.AgentType
import fr.geoking.julius.SettingsManager

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
