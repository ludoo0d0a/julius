package fr.geoking.julius.auto

import android.util.Log
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarIcon
import androidx.car.app.model.Header
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.core.graphics.drawable.IconCompat
import fr.geoking.julius.CarMapMode
import fr.geoking.julius.R
import fr.geoking.julius.SettingsManager
import fr.geoking.julius.agents.ConversationalAgent
import fr.geoking.julius.api.jules.JulesClient
import fr.geoking.julius.di.MapDeps
import fr.geoking.julius.repository.JulesRepository
import fr.geoking.julius.shared.conversation.ConversationStore
import fr.geoking.julius.shared.network.NetworkService

class AutoDashboardScreen(
    carContext: CarContext,
    private val store: ConversationStore,
    private val settingsManager: SettingsManager,
    private val julesClient: JulesClient,
    private val julesRepository: JulesRepository,
    private val networkService: NetworkService,
    private val conversationalAgent: ConversationalAgent,
    private val getMapDeps: () -> MapDeps?
) : Screen(carContext) {

    init {
        val screenNames = listOf(
            "AutoDashboardScreen",
            "MainScreen (Assistant)",
            "AutoJulesSourceScreen",
            "NativeMapPoiScreen",
            "CustomMapPoiScreen",
            "AutoRoutePlanningScreen",
            "AutoNetworkLocationInfoScreen",
            "AutoSettingsScreen",
            "AutoHistoryScreen (via MainScreen)"
        )
        Log.d("JuliusNavigation", "Android Auto Screens: ${screenNames.joinToString(", ")}")
    }

    override fun onGetTemplate(): Template {
        val listBuilder = ItemList.Builder()

        // 1. Assistant
        listBuilder.addItem(
            Row.Builder()
                .setTitle("Assistant")
                .addText("Voice interaction and help")
                .setImage(CarIcon.Builder(IconCompat.createWithResource(carContext, R.drawable.ic_speaker)).build())
                .setOnClickListener {
                    screenManager.push(MainScreen(carContext, store, settingsManager, julesClient, getMapDeps, conversationalAgent))
                }
                .build()
        )

        // 2. Jules Chat
        listBuilder.addItem(
            Row.Builder()
                .setTitle("Jules Chat")
                .addText("Open Jules code assistant")
                .setImage(CarIcon.Builder(IconCompat.createWithResource(carContext, R.drawable.ic_jules)).build())
                .setOnClickListener {
                    screenManager.push(AutoJulesSourceScreen(carContext, store, settingsManager, julesClient, julesRepository))
                }
                .build()
        )

        // 3. Map (Native or Custom)
        listBuilder.addItem(
            Row.Builder()
                .setTitle("Map")
                .addText("Search nearby gas/EV stations")
                .setImage(CarIcon.Builder(IconCompat.createWithResource(carContext, R.drawable.ic_map)).build())
                .setOnClickListener {
                    settingsManager.setUseVehicleFilter(false)
                    pushMapScreen()
                }
                .build()
        )

        // 4. POI Map (Vehicle filtered)
        listBuilder.addItem(
            Row.Builder()
                .setTitle("POI Map")
                .addText("Search filtered by vehicle settings")
                .setImage(CarIcon.Builder(IconCompat.createWithResource(carContext, R.drawable.ic_map)).build())
                .setOnClickListener {
                    settingsManager.setUseVehicleFilter(true)
                    pushMapScreen()
                }
                .build()
        )

        // 5. Routes
        listBuilder.addItem(
            Row.Builder()
                .setTitle("Routes")
                .addText("Plan your journey")
                .setImage(CarIcon.Builder(IconCompat.createWithResource(carContext, R.drawable.ic_swap_horiz)).build())
                .setOnClickListener {
                    val mapDeps = getMapDeps()
                    if (mapDeps != null) {
                        screenManager.push(
                            AutoRoutePlanningScreen(
                                carContext = carContext,
                                routePlanner = mapDeps.routePlanner,
                                routingClient = mapDeps.routingClient,
                                poiProvider = mapDeps.poiProvider,
                                geocodingClient = mapDeps.geocodingClient,
                                settingsManager = settingsManager
                            )
                        )
                    }
                }
                .build()
        )

        // 6. Network/Location Info
        listBuilder.addItem(
            Row.Builder()
                .setTitle("Network & Location Info")
                .addText("Check cellular and GPS status")
                .setImage(CarIcon.Builder(IconCompat.createWithResource(carContext, R.drawable.ic_speaker)).build())
                .setOnClickListener {
                    screenManager.push(AutoNetworkLocationInfoScreen(carContext, networkService))
                }
                .build()
        )

        // 7. Settings
        listBuilder.addItem(
            Row.Builder()
                .setTitle("Settings")
                .addText("App and agent configuration")
                .setImage(CarIcon.Builder(IconCompat.createWithResource(carContext, R.drawable.ic_settings)).build())
                .setOnClickListener {
                    screenManager.push(AutoSettingsScreen(carContext, settingsManager, store, julesClient))
                }
                .build()
        )

        return ListTemplate.Builder()
            .setSingleList(listBuilder.build())
            .setHeader(
                Header.Builder()
                    .setTitle("Julius Dashboard")
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
