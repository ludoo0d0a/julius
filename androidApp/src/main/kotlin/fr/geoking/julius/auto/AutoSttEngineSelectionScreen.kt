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
import fr.geoking.julius.shared.voice.SttEnginePreference

class AutoSttEngineSelectionScreen(
    carContext: CarContext,
    private val settingsManager: SettingsManager
) : Screen(carContext) {

    override fun onGetTemplate(): Template {
        val settings = settingsManager.settings.value
        val listBuilder = ItemList.Builder()
        SttEnginePreference.entries.forEach { pref ->
            val isSelected = settings.sttEnginePreference == pref
            listBuilder.addItem(
                Row.Builder()
                    .setTitle(sttEngineLabel(pref))
                    .addText(if (isSelected) "Selected" else "")
                    .setOnClickListener {
                        settingsManager.saveSettings(settings.copy(sttEnginePreference = pref))
                        screenManager.pop()
                    }
                    .build()
            )
        }
        return ListTemplate.Builder()
            .setSingleList(listBuilder.build())
            .setHeader(
                Header.Builder()
                    .setTitle("STT engine (car mic)")
                    .setStartHeaderAction(Action.BACK)
                    .build()
            )
            .build()
    }
}

private fun sttEngineLabel(pref: SttEnginePreference): String = when (pref) {
    SttEnginePreference.LocalOnly -> "Local only (Vosk)"
    SttEnginePreference.LocalFirst -> "Local first (Vosk, then cloud)"
    SttEnginePreference.NativeOnly -> "Native only (cloud)"
}
