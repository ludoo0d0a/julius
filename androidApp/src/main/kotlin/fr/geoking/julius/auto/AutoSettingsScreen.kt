package fr.geoking.julius.auto

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.*
import fr.geoking.julius.AgentType
import fr.geoking.julius.SettingsManager
import fr.geoking.julius.api.jules.JulesClient
import fr.geoking.julius.shared.ConversationStore
import fr.geoking.julius.shared.SttEnginePreference

class AutoSettingsScreen(
    carContext: CarContext,
    private val settingsManager: SettingsManager,
    private val store: ConversationStore,
    private val julesClient: JulesClient
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
                .setTitle("Jules")
                .addText("Open Jules code assistant")
                .setOnClickListener {
                    screenManager.push(AutoJulesScreen(carContext, store, settingsManager, julesClient))
                }
                .build()
        )

        listBuilder.addItem(
            Row.Builder()
                .setTitle("Voice & Advanced Settings")
                .addText("Wake word, STT engine, Mute radio...")
                .setOnClickListener {
                    screenManager.push(AutoAdvancedSettingsScreen(carContext, settingsManager))
                }
                .build()
        )

        return ListTemplate.Builder()
            .setSingleList(listBuilder.build())
            .setHeader(Header.Builder().setTitle("Settings").setStartHeaderAction(Action.BACK).build())
            .build()
    }
}
