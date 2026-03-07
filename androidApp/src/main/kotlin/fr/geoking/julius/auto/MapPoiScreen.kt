package fr.geoking.julius.auto

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.util.Log
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.*
import androidx.car.app.model.Distance
import androidx.car.app.model.Place
import androidx.lifecycle.lifecycleScope
import fr.geoking.julius.shared.Poi
import fr.geoking.julius.shared.PoiProvider
import kotlinx.coroutines.launch

class MapPoiScreen(
    carContext: CarContext,
    private val poiProvider: PoiProvider
) : Screen(carContext) {

    private var pois: List<Poi> = emptyList()
    private var isLoading = true

    init {
        loadPois()
    }

    private fun loadPois() {
        lifecycleScope.launch {
            isLoading = true
            invalidate()

            var lat = 48.8566
            var lon = 2.3522

            if (carContext.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                carContext.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                try {
                    val locationManager = carContext.getSystemService(android.content.Context.LOCATION_SERVICE) as LocationManager
                    val location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                        ?: locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                    if (location != null) {
                        lat = location.latitude
                        lon = location.longitude
                    }
                } catch (e: Exception) {
                    Log.e("MapPoiScreen", "Failed to get location", e)
                }
            }

            pois = poiProvider.getGasStations(lat, lon)
            isLoading = false
            invalidate()
        }
    }

    override fun onGetTemplate(): Template {
        return try {
            val builder = PlaceListMapTemplate.Builder()
                .setTitle("Gas Stations")
                .setHeaderAction(Action.BACK)

            if (isLoading) {
                builder.setLoading(true)
            } else {
                val listBuilder = ItemList.Builder()
                    .setNoItemsMessage("No gas stations found")

                for (poi in pois) {
                    val metadata = Metadata.Builder()
                        .setPlace(
                            Place.Builder(CarLocation.create(poi.latitude, poi.longitude))
                                .setMarker(PlaceMarker.Builder().build())
                                .build()
                        )
                        .build()

                    listBuilder.addItem(
                        Row.Builder()
                            .setTitle(poi.name)
                            .addText(poi.address)
                            .setMetadata(metadata)
                            .setOnClickListener {
                                val intent = Intent(CarContext.ACTION_NAVIGATE, Uri.parse("geo:${poi.latitude},${poi.longitude}?q=${poi.name}"))
                                carContext.startCarApp(intent)
                            }
                            .build()
                    )
                }
                builder.setItemList(listBuilder.build())
            }

            builder.setActionStrip(
                ActionStrip.Builder()
                    .addAction(
                        Action.Builder()
                            .setTitle("Refresh")
                            .setIcon(CarIcon.Builder(androidx.core.graphics.drawable.IconCompat.createWithResource(carContext, fr.geoking.julius.R.drawable.ic_map)).build())
                            .setOnClickListener { loadPois() }
                            .build()
                    )
                    .build()
            )

            builder.build()
        } catch (e: Exception) {
            Log.e("MapPoiScreen", "Error building template", e)
            MessageTemplate.Builder("Failed to load map: ${e.message}")
                .setTitle("Error")
                .setHeaderAction(Action.BACK)
                .build()
        }
    }
}
