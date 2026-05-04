package fr.geoking.julius.auto

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.*
import androidx.core.graphics.drawable.IconCompat
import fr.geoking.julius.R
import fr.geoking.julius.SettingsManager
import fr.geoking.julius.api.jules.JulesClient
import fr.geoking.julius.repository.JulesRepository
import fr.geoking.julius.shared.conversation.ConversationStore

/**
 * Main dashboard screen for Android Auto.
 */
class AutoHomeScreen(
    carContext: CarContext,
    private val store: ConversationStore,
    private val settingsManager: SettingsManager,
    private val julesClient: JulesClient,
    private val julesRepository: JulesRepository
) : Screen(carContext) {

    override fun onGetTemplate(): Template = safeCarTemplate(carContext, TAG) {
        val listBuilder = ItemList.Builder()

        listBuilder.addItem(
            Row.Builder()
                .setTitle("Talk to Julius")
                .addText("Voice or keyboard via search bar")
                .setImage(
                    CarIcon.Builder(
                        IconCompat.createWithResource(carContext, R.drawable.ic_speaker)
                    ).build()
                )
                .setOnClickListener {
                    screenManager.push(AutoJuliusConversationScreen(carContext, store))
                }
                .build()
        )

        listBuilder.addItem(
            Row.Builder()
                .setTitle("Jules")
                .addText("View and manage your tasks")
                .setImage(
                    CarIcon.Builder(
                        IconCompat.createWithResource(carContext, R.drawable.ic_home)
                    ).build()
                )
                .setOnClickListener {
                    screenManager.push(
                        AutoJulesSourceScreen(
                            carContext,
                            store,
                            settingsManager,
                            julesClient,
                            julesRepository
                        )
                    )
                }
                .build()
        )

        listBuilder.addItem(
            Row.Builder()
                .setTitle("Settings")
                .addText("Configure the application")
                .setImage(
                    CarIcon.Builder(
                        IconCompat.createWithResource(carContext, R.drawable.ic_settings)
                    ).build()
                )
                .setOnClickListener {
                    screenManager.push(AutoAdvancedSettingsScreen(carContext, settingsManager))
                }
                .build()
        )

        ListTemplate.Builder()
            .setSingleList(listBuilder.build())
            .setHeader(
                Header.Builder()
                    .setTitle("Julius")
                    .setStartHeaderAction(Action.APP_ICON)
                    .build()
            )
            .build()
    }

    companion object {
        private const val TAG = "AutoHomeScreen"
    }
}
