package fr.geoking.julius.auto

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.*
import fr.geoking.julius.AgentType
import fr.geoking.julius.SettingsManager

class AutoAgentSelectionScreen(
    carContext: CarContext,
    private val settingsManager: SettingsManager
) : Screen(carContext) {

    override fun onGetTemplate(): Template {
        val settings = settingsManager.settings.value
        val listBuilder = ItemList.Builder()

        AgentType.entries.forEach { agentType ->
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

        return ListTemplate.Builder()
            .setSingleList(listBuilder.build())
            .setTitle("Select Agent")
            .setHeaderAction(Action.BACK)
            .build()
    }
}
