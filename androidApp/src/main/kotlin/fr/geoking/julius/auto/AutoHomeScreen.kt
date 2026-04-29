package fr.geoking.julius.auto

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.*
import androidx.core.graphics.drawable.IconCompat
import fr.geoking.julius.CarMapMode
import fr.geoking.julius.R
import fr.geoking.julius.SettingsManager
import fr.geoking.julius.api.jules.JulesClient
import fr.geoking.julius.di.MapDeps
import fr.geoking.julius.repository.JulesRepository
import fr.geoking.julius.shared.conversation.ConversationStore
import fr.geoking.julius.shared.network.NetworkService

class AutoHomeScreen(
    carContext: CarContext,
    private val settingsManager: SettingsManager,
    private val getMapDeps: () -> MapDeps?,
    private val store: ConversationStore,
    private val julesClient: JulesClient,
    private val julesRepository: JulesRepository
) : Screen(carContext) {

    override fun onGetTemplate(): Template = safeCarTemplate(carContext, "AutoHomeScreen") {
        val listBuilder = ItemList.Builder()

        fun icon(drawableId: Int) =
            CarIcon.Builder(IconCompat.createWithResource(carContext, drawableId)).build()

        // 1. Talk to Julius
        listBuilder.addItem(
            Row.Builder()
                .setTitle("Talk to Julius")
                .addText("Voice assistant")
                .setImage(icon(R.drawable.ic_speaker))
                .setOnClickListener {
                    store.startListening()
                }
                .build()
        )

        // 2. Map
        listBuilder.addItem(
            Row.Builder()
                .setTitle("Map")
                .addText("Nearby stations")
                .setImage(icon(R.drawable.ic_map))
                .setOnClickListener {
                    settingsManager.setUseVehicleFilter(false)
                    pushMapScreen()
                }
                .build()
        )

        // 3. Search
        listBuilder.addItem(
            Row.Builder()
                .setTitle("Search")
                .addText("Name or brand")
                .setImage(icon(R.drawable.ic_search))
                .setOnClickListener {
                    val mapDeps = getMapDeps()
                    if (mapDeps != null) {
                        screenManager.push(
                            AutoPoiSearchScreen(
                                carContext = carContext,
                                poiProvider = mapDeps.poiProvider,
                                settingsManager = settingsManager,
                                availabilityProviderFactory = mapDeps.availabilityProviderFactory,
                                favoritesRepo = mapDeps.favoritesRepo
                            )
                        )
                    }
                }
                .build()
        )

        // 4. Jules
        listBuilder.addItem(
            Row.Builder()
                .setTitle("Jules")
                .addText("Coding sessions")
                .setImage(icon(R.drawable.ic_jules))
                .setOnClickListener {
                    screenManager.push(
                        AutoJulesSourceScreen(
                            carContext = carContext,
                            store = store,
                            settingsManager = settingsManager,
                            julesClient = julesClient,
                            julesRepository = julesRepository
                        )
                    )
                }
                .build()
        )

        // 5. Settings
        listBuilder.addItem(
            Row.Builder()
                .setTitle("Settings")
                .addText("App preferences")
                .setImage(icon(R.drawable.ic_settings))
                .setOnClickListener {
                    screenManager.push(AutoSettingsScreen(carContext, settingsManager))
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

    private fun pushMapScreen() {
        val mapDeps = getMapDeps()
        if (mapDeps != null) {
            val screen = if (settingsManager.settings.value.carMapMode == CarMapMode.Native) {
                NativeMapPoiScreen(
                    carContext = carContext,
                    poiProvider = mapDeps.poiProvider,
                    availabilityProviderFactory = mapDeps.availabilityProviderFactory,
                    settingsManager = settingsManager,
                    communityRepo = mapDeps.communityRepo,
                    favoritesRepo = mapDeps.favoritesRepo
                )
            } else {
                CustomMapPoiScreen(
                    carContext = carContext,
                    poiProvider = mapDeps.poiProvider,
                    availabilityProviderFactory = mapDeps.availabilityProviderFactory,
                    settingsManager = settingsManager,
                    routePlanner = mapDeps.routePlanner,
                    routingClient = mapDeps.routingClient,
                    tollCalculator = mapDeps.tollCalculator,
                    trafficProviderFactory = mapDeps.trafficProviderFactory,
                    geocodingClient = mapDeps.geocodingClient,
                    communityRepo = mapDeps.communityRepo,
                    favoritesRepo = mapDeps.favoritesRepo
                )
            }
            screenManager.push(screen)
        }
    }
}
