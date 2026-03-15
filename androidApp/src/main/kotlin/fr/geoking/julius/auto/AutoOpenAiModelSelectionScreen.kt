package fr.geoking.julius.auto

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.*
import fr.geoking.julius.OpenAiModel
import fr.geoking.julius.SettingsManager

class AutoOpenAiModelSelectionScreen(
    carContext: CarContext,
    private val settingsManager: SettingsManager
) : Screen(carContext) {

    override fun onGetTemplate(): Template {
        val settings = settingsManager.settings.value
        val listBuilder = ItemList.Builder()

        OpenAiModel.entries.forEach { model ->
            listBuilder.addItem(
                Row.Builder()
                    .setTitle(model.displayName)
                    .setOnClickListener {
                        settingsManager.saveSettings(settings.copy(openAiModel = model))
                        screenManager.pop()
                    }
                    .build()
            )
        }

        return ListTemplate.Builder()
            .setSingleList(listBuilder.build())
            .setHeader(Header.Builder().setTitle("Select OpenAI Model").setStartHeaderAction(Action.BACK).build())
            .build()
    }
}
