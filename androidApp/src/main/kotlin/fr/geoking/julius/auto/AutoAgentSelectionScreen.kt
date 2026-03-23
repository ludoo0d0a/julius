package fr.geoking.julius.auto

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.*
import fr.geoking.julius.AgentType
import fr.geoking.julius.SettingsManager
import fr.geoking.julius.enabledAgentTypes

class AutoAgentSelectionScreen(
    carContext: CarContext,
    private val settingsManager: SettingsManager
) : Screen(carContext) {

    override fun onGetTemplate(): Template {
        val settings = settingsManager.settings.value
        val listBuilder = ItemList.Builder()

        val llamatikPathAgentTypes = listOf(
            AgentType.Llamatik, AgentType.GeminiNano, AgentType.RunAnywhere,
            AgentType.MlcLlm, AgentType.LlamaCpp, AgentType.MediaPipe,
            AgentType.AiEdge, AgentType.PocketPal, AgentType.Offline
        )
        val llamatikPathAgents = llamatikPathAgentTypes.filter { it.enabled }
        val remoteAgents = enabledAgentTypes().filter { it !in llamatikPathAgentTypes }

        remoteAgents.forEach { agentType ->
            listBuilder.addItem(
                Row.Builder()
                    .setTitle(agentType.name)
                    .setOnClickListener {
                        settingsManager.saveSettings(settings.copy(selectedAgent = agentType))
                        screenManager.pop()
                    }
                    .build()
            )
        }

        llamatikPathAgents.forEach { agentType ->
            val label = if (agentType != AgentType.Offline) "${agentType.name} (on-device)" else agentType.name
            listBuilder.addItem(
                Row.Builder()
                    .setTitle(label)
                    .setOnClickListener {
                        settingsManager.saveSettings(settings.copy(selectedAgent = agentType))
                        screenManager.pop()
                    }
                    .build()
            )
        }

        return ListTemplate.Builder()
            .setSingleList(listBuilder.build())
            .setHeader(Header.Builder().setTitle("Select Agent").setStartHeaderAction(Action.BACK).build())
            .build()
    }
}
