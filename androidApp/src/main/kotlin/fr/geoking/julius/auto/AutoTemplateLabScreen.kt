package fr.geoking.julius.auto

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.CarIcon
import androidx.car.app.model.Header
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.core.graphics.drawable.IconCompat
import fr.geoking.julius.CarMapMode
import fr.geoking.julius.R
import fr.geoking.julius.SettingsManager
import fr.geoking.julius.di.MapDeps
import fr.geoking.julius.poi.Poi
import fr.geoking.julius.poi.PoiCategory

/**
 * Developer / QA hub to open different Android Auto templates side by side.
 * Useful when comparing **POI** vs **navigation**-style UIs during review.
 */
class AutoTemplateLabScreen(
    carContext: CarContext,
    private val settingsManager: SettingsManager,
    private val getMapDeps: () -> MapDeps?
) : Screen(carContext) {

    private val sampleDestination: Poi = Poi(
        id = "lab:destination",
        name = "Sample charging stop",
        address = "Template lab",
        latitude = 48.8566,
        longitude = 2.3522,
        poiCategory = PoiCategory.Irve,
        isElectric = true
    )

    /** Map module load can fail (logged as VoiceSession); explain instead of a no-op tap. */
    private fun withMapDeps(block: (MapDeps) -> Unit) {
        val mapDeps = getMapDeps()
        if (mapDeps == null) {
            screenManager.push(
                object : Screen(carContext) {
                    override fun onGetTemplate(): Template =
                        MessageTemplate.Builder(
                            "Map dependencies failed to load. Check log tag VoiceSession. " +
                                "Opening the map on the phone once usually loads the map module; then retry."
                        )
                            .setHeader(
                                Header.Builder()
                                    .setTitle("Map unavailable")
                                    .setStartHeaderAction(Action.BACK)
                                    .build()
                            )
                            .addAction(
                                Action.Builder()
                                    .setTitle("OK")
                                    .setOnClickListener { screenManager.pop() }
                                    .build()
                            )
                            .build()
                }
            )
            return
        }
        block(mapDeps)
    }

    override fun onGetTemplate(): Template {
        val listBuilder = ItemList.Builder()

        listBuilder.addItem(
            Row.Builder()
                .setTitle("MessageTemplate")
                .addText("Simple text + OK action")
                .setOnClickListener {
                    screenManager.push(
                        object : Screen(carContext) {
                            override fun onGetTemplate(): Template = MessageTemplate.Builder(
                                "MessageTemplate sample — use for errors or short notices."
                            )
                                .setHeader(
                                    Header.Builder()
                                        .setTitle("Lab: message")
                                        .setStartHeaderAction(Action.BACK)
                                        .build()
                                )
                                .addAction(
                                    Action.Builder()
                                        .setTitle("Close")
                                        .setOnClickListener { screenManager.pop() }
                                        .build()
                                )
                                .build()
                        }
                    )
                }
                .build()
        )

        listBuilder.addItem(
            Row.Builder()
                .setTitle("NavigationTemplate (sample)")
                .addText("Placeholder routing UI (navigation category apps)")
                .setImage(CarIcon.Builder(IconCompat.createWithResource(carContext, R.drawable.ic_map)).build())
                .setOnClickListener {
                    screenManager.push(GuidanceScreen(carContext, sampleDestination))
                }
                .build()
        )

        listBuilder.addItem(
            Row.Builder()
                .setTitle("Native map POI")
                .addText("MapWithContent / place list map")
                .setOnClickListener {
                    withMapDeps { mapDeps ->
                        screenManager.push(
                            NativeMapPoiScreen(
                                carContext = carContext,
                                poiProvider = mapDeps.poiProvider,
                                availabilityProviderFactory = mapDeps.availabilityProviderFactory,
                                settingsManager = settingsManager,
                                communityRepo = mapDeps.communityRepo,
                                favoritesRepo = mapDeps.favoritesRepo
                            )
                        )
                    }
                }
                .build()
        )

        listBuilder.addItem(
            Row.Builder()
                .setTitle("Custom map (pan)")
                .addText("Surface / custom tiles")
                .setOnClickListener {
                    withMapDeps { mapDeps ->
                        screenManager.push(
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
                        )
                    }
                }
                .build()
        )

        listBuilder.addItem(
            Row.Builder()
                .setTitle("Route planning")
                .addText("Same as dashboard Routes")
                .setOnClickListener {
                    withMapDeps { mapDeps ->
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

        listBuilder.addItem(
            Row.Builder()
                .setTitle("Toggle map mode")
                .addText("Current: ${settingsManager.settings.value.carMapMode.name} → switch")
                .setOnClickListener {
                    val next =
                        if (settingsManager.settings.value.carMapMode == CarMapMode.Native) CarMapMode.Custom
                        else CarMapMode.Native
                    settingsManager.setCarMapMode(next)
                    invalidate()
                }
                .build()
        )

        return ListTemplate.Builder()
            .setSingleList(listBuilder.build())
            .setHeader(
                Header.Builder()
                    .setTitle("Template lab")
                    .setStartHeaderAction(Action.BACK)
                    .build()
            )
            .build()
    }
}
