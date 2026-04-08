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

/**
 * Android Auto: only options that are safe and relevant without the in-car voice assistant.
 * Agent, Jules, and voice/STT are configured in the phone app.
 */
class AutoSettingsScreen(
    carContext: CarContext,
    private val settingsManager: SettingsManager
) : Screen(carContext) {

    override fun onGetTemplate(): Template {
        val listBuilder = ItemList.Builder()

        listBuilder.addItem(
            Row.Builder()
                .setTitle("AI & voice")
                .addText("Configure agents, Jules, wake word, and STT in the Julius app on your phone.")
                .build()
        )

        listBuilder.addItem(
            Row.Builder()
                .setTitle("Download toll data (OpenTollData)")
                .addText("French highway toll estimation")
                .setOnClickListener {
                    screenManager.push(AutoTollDataScreen(carContext, settingsManager))
                }
                .build()
        )

        return ListTemplate.Builder()
            .setSingleList(listBuilder.build())
            .setHeader(Header.Builder().setTitle("Settings").setStartHeaderAction(Action.BACK).build())
            .build()
    }
}
