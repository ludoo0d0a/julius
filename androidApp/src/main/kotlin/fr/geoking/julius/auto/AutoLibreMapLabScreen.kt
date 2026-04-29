package fr.geoking.julius.auto

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
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
import androidx.lifecycle.lifecycleScope
import fr.geoking.julius.R
import fr.geoking.julius.feature.location.LocationHelper
import kotlinx.coroutines.launch

/**
 * Android Auto template lab: map surface + zoom controls for exercising the raster pipeline.
 * Phone VectorMapScreen uses MapLibre + OpenFreeMap; "Open MapLibre on phone" starts the host app with
 * `julius://map/libremap` (switches engine to MapLibre and opens the map).
 */
class AutoLibreMapLabScreen(carContext: CarContext) : Screen(carContext) {

    private var searchLat = 48.8566
    private var searchLon = 2.3522
    private var zoom = 12
    private var isLoading = true
    private var isDarkMode = false

    init {
        refreshLocation()
    }

    private fun refreshLocation() {
        lifecycleScope.launch {
            isLoading = true
            invalidate()
            var lat = 48.8566
            var lon = 2.3522
            if (carContext.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                carContext.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
            ) {
                LocationHelper.getCurrentLocation(carContext)?.let {
                    lat = it.latitude
                    lon = it.longitude
                }
            }
            searchLat = lat
            searchLon = lon
            isLoading = false
            invalidate()
        }
    }

    private fun bumpZoom(delta: Int) {
        zoom = (zoom + delta).coerceIn(4, 18)
        invalidate()
    }

    override fun onGetTemplate(): Template = safeCarTemplate(carContext, "AutoLibreMapLabScreen") {
        val currentDarkMode = (carContext.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        if (currentDarkMode != isDarkMode) {
            isDarkMode = currentDarkMode
        }

        val actionStrip = ActionStrip.Builder()
            .addAction(
                Action.Builder()
                    .setTitle("Home")
                    .setIcon(CarIcon.Builder(IconCompat.createWithResource(carContext, R.drawable.ic_home)).build())
                    .setOnClickListener { screenManager.popToRoot() }
                    .build()
            )
            .build()

        val title = "MapLibre (lab)"

        if (isLoading) {
            return@safeCarTemplate ListTemplate.Builder()
                .setLoading(true)
                .setHeader(
                    Header.Builder()
                        .setTitle(title)
                        .setStartHeaderAction(Action.BACK)
                        .build()
                )
                .setActionStrip(actionStrip)
                .build()
        }

        val list = ItemList.Builder()
            .addItem(
                Row.Builder()
                    .setTitle("Recenter")
                    .setImage(CarIcon.Builder(IconCompat.createWithResource(carContext, R.drawable.ic_map)).build())
                    .setOnClickListener { refreshLocation() }
                    .build()
            )
            .addItem(
                Row.Builder()
                    .setTitle("Zoom in")
                    .addText("Level $zoom")
                    .setOnClickListener { bumpZoom(1) }
                    .build()
            )
            .addItem(
                Row.Builder()
                    .setTitle("Zoom out")
                    .addText("Level $zoom")
                    .setOnClickListener { bumpZoom(-1) }
                    .build()
            )
            .addItem(
                Row.Builder()
                    .setTitle("Open MapLibre on phone")
                    .addText("Vector map (OpenFreeMap)")
                    .setOnClickListener {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("julius://map/libremap")).apply {
                            setPackage(carContext.packageName)
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        }
                        try {
                            carContext.startCarApp(intent)
                        } catch (e: Exception) {
                            Log.e("AutoLibreMapLabScreen", "startCarApp failed", e)
                        }
                    }
                    .build()
            )
            .build()

        ListTemplate.Builder()
            .setHeader(
                Header.Builder()
                    .setTitle(title)
                    .setStartHeaderAction(Action.BACK)
                    .build()
            )
            .setSingleList(list)
            .setActionStrip(actionStrip)
            .build()
    }
}
