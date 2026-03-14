package fr.geoking.julius.auto

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.*
import fr.geoking.julius.GeminiModel
import fr.geoking.julius.SettingsManager

class AutoGeminiModelSelectionScreen(
    carContext: CarContext,
    private val settingsManager: SettingsManager
) : Screen(carContext) {

    override fun onGetTemplate(): Template {
        val settings = settingsManager.settings.value
        val listBuilder = ItemList.Builder()

        GeminiModel.entries.forEach { model ->
            listBuilder.addItem(
                Row.Builder()
                    .setTitle(model.displayName)
                    .setOnClickListener {
                        settingsManager.saveSettings(settings.copy(geminiModel = model))
                        screenManager.pop()
                    }
                    .build()
            )
        }

        return ListTemplate.Builder()
            .setSingleList(listBuilder.build())
            .setTitle("Select Gemini Model")
            .setHeaderAction(Action.BACK)
            .build()
    }
}
