package fr.geoking.julius.auto

import android.util.Log
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarLocation
import androidx.car.app.model.Header
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.car.app.navigation.model.NavigationTemplate
import fr.geoking.julius.R
import fr.geoking.julius.api.routing.Route
import fr.geoking.julius.api.routing.RoutePlanner
import fr.geoking.julius.api.routing.RoutingClient
import fr.geoking.julius.poi.Poi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Simple route preview screen:
 * - Fetches 1+ routes using RoutingClient / RoutePlanner
 * - Shows them in a list
 * - Starts guidance by switching to a minimal NavigationTemplate when user presses Start.
 */
class RoutePreviewScreen(
    carContext: CarContext,
    private val destination: Poi,
    private val routePlanner: RoutePlanner,
    private val routingClient: RoutingClient
) : Screen(carContext) {

    private val scope = CoroutineScope(Dispatchers.IO + Job())

    private var isLoading: Boolean = true
    private var routes: List<Route> = emptyList()
    private var activeRoute: Route? = null
    private var inNavigation: Boolean = false

    init {
        loadRoutes()
    }

    private fun loadRoutes() {
        scope.launch {
            try {
                val result = routingClient.getRoutesTo(
                    latitude = destination.latitude,
                    longitude = destination.longitude
                )
                routes = result.routes
                activeRoute = routes.firstOrNull()
            } catch (e: Exception) {
                Log.e("RoutePreviewScreen", "Failed to load routes", e)
                routes = emptyList()
                activeRoute = null
            } finally {
                isLoading = false
                invalidate()
            }
        }
    }

    override fun onGetTemplate(): Template {
        return try {
            if (inNavigation && activeRoute != null) {
                buildNavigationTemplate()
            } else if (isLoading) {
                MessageTemplate.Builder("Calculating route...")
                    .setHeader(
                        Header.Builder()
                            .setTitle(destination.name.ifBlank { "Destination" })
                            .setStartHeaderAction(Action.BACK)
                            .build()
                    )
                    .setLoading(true)
                    .build()
            } else if (routes.isEmpty()) {
                MessageTemplate.Builder("No routes found")
                    .setHeader(
                        Header.Builder()
                            .setTitle(destination.name.ifBlank { "Destination" })
                            .setStartHeaderAction(Action.BACK)
                            .build()
                    )
                    .build()
            } else {
                buildPreviewTemplate()
            }
        } catch (e: Exception) {
            Log.e("RoutePreviewScreen", "Error building template", e)
            MessageTemplate.Builder("Route preview error: ${e.message}")
                .setHeader(
                    Header.Builder()
                        .setTitle("Error")
                        .setStartHeaderAction(Action.BACK)
                        .build()
                )
                .build()
        }
    }

    private fun buildPreviewTemplate(): Template {
        val listBuilder = ItemList.Builder()

        routes.forEachIndexed { index, route ->
            val title = "Route ${index + 1}"
            val row = Row.Builder()
                .setTitle(title)
                .addText(route.summary ?: "")
                .addText(route.etaDescription ?: "")
                .setOnClickListener {
                    activeRoute = route
                    invalidate()
                }
                .build()
            listBuilder.addItem(row)
        }

        val header = Header.Builder()
            .setTitle(destination.name.ifBlank { "Destination" })
            .setStartHeaderAction(Action.BACK)
            .build()

        val actionStrip = ActionStrip.Builder()
            .addAction(
                Action.Builder()
                    .setTitle("Start")
                    .setOnClickListener {
                        if (activeRoute != null) {
                            inNavigation = true
                            invalidate()
                        }
                    }
                    .build()
            )
            .build()

        return ListTemplate.Builder()
            .setSingleList(listBuilder.build())
            .setHeader(header)
            .setActionStrip(actionStrip)
            .build()
    }

    private fun buildNavigationTemplate(): Template {
        // Minimal NavigationTemplate – actual turn-by-turn instructions and updates
        // are expected to be provided by your routing / navigation engine on the
        // surface (via AutoSurfaceRenderer) and by updating travel estimates here.
        val actionStrip = ActionStrip.Builder()
            .addAction(
                Action.Builder()
                    .setTitle("Stop")
                    .setOnClickListener {
                        inNavigation = false
                        finish()
                    }
                    .build()
            )
            .build()

        return NavigationTemplate.Builder()
            .setActionStrip(actionStrip)
            .setNavigateAction(
                Action.Builder()
                    .setTitle(carContext.getString(R.string.app_name))
                    .build()
            )
            .setDestination(
                CarLocation.create(destination.latitude, destination.longitude)
            )
            .build()
    }
}

