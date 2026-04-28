package fr.geoking.julius.auto

import android.util.Log
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

class AutoDashboardScreen(
    carContext: CarContext,
    private val settingsManager: SettingsManager,
    private val networkService: NetworkService,
    private val getMapDeps: () -> MapDeps?,
    private val store: ConversationStore,
    private val julesClient: JulesClient,
    private val julesRepository: JulesRepository
) : Screen(carContext) {

    override fun onGetTemplate(): Template {
        return try {
            val grid = ItemList.Builder()

            fun gridIcon(drawableId: Int) =
                CarIcon.Builder(IconCompat.createWithResource(carContext, drawableId)).build()

            // 1. Jules
            grid.addItem(
                GridItem.Builder()
                    .setTitle("Jules")
                    .setText("Coding sessions")
                    .setImage(gridIcon(R.drawable.ic_home))
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

            // 2. Map
            grid.addItem(
                GridItem.Builder()
                    .setTitle("Map")
                    .setText("Nearby stations")
                    .setImage(gridIcon(R.drawable.ic_map))
                    .setOnClickListener {
                        settingsManager.setUseVehicleFilter(false)
                        pushMapScreen()
                    }
                    .build()
            )

            // 3. Search
            grid.addItem(
                GridItem.Builder()
                    .setTitle("Search")
                    .setText("Name or brand")
                    .setImage(gridIcon(R.drawable.ic_search))
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

            // 4. Settings
            grid.addItem(
                GridItem.Builder()
                    .setTitle("Settings")
                    .setText("App preferences")
                    .setImage(gridIcon(R.drawable.ic_settings))
                    .setOnClickListener {
                        screenManager.push(AutoSettingsScreen(carContext, settingsManager))
                    }
                    .build()
            )

            GridTemplate.Builder()
                .setSingleList(grid.build())
                .setHeader(
                    Header.Builder()
                        .setTitle("Julius Dashboard")
                        .setStartHeaderAction(Action.APP_ICON)
                        .build()
                )
                .build()
        } catch (e: Exception) {
            Log.e("AutoDashboardScreen", "Failed to get template", e)
            MessageTemplate.Builder(e.message ?: "Unknown error")
                .setHeader(Header.Builder().setTitle("Julius Error").setStartHeaderAction(Action.APP_ICON).build())
                .build()
        }
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
