package fr.geoking.julius.auto

import androidx.car.app.CarContext
import androidx.car.app.model.CarIcon
import androidx.car.app.model.CarLocation
import androidx.car.app.model.Metadata
import androidx.car.app.model.Place
import androidx.car.app.model.PlaceMarker
import androidx.car.app.model.Row
import androidx.core.graphics.drawable.IconCompat
import fr.geoking.julius.R
import fr.geoking.julius.api.belib.StationAvailabilitySummary
import fr.geoking.julius.poi.Poi
import fr.geoking.julius.ui.map.PoiMarkerHelper

/**
 * Shared logic for mapping POIs to car UI components (rows, markers, icons).
 */
object AutoPoiUiHelper {

    fun buildPlace(carContext: CarContext, poi: Poi): Place {
        val markerBitmap = PoiMarkerHelper.getMarkerBitmap(
            context = carContext,
            poi = poi,
            selectedEnergyTypes = emptySet(), // No easy access to settings here, using default
            useVehicleFilter = false,
            vehicleEnergy = "",
            vehicleGasTypes = emptySet(),
            sizePx = 72,
            availability = null,
            style = PoiMarkerHelper.MarkerStyle.Circle
        )
        return Place.Builder(CarLocation.create(poi.latitude, poi.longitude))
            .setMarker(
                PlaceMarker.Builder()
                    .setIcon(
                        CarIcon.Builder(IconCompat.createWithBitmap(markerBitmap)).build(),
                        PlaceMarker.TYPE_ICON
                    )
                    .build()
            )
            .build()
    }

    fun buildPoiRow(
        carContext: CarContext,
        poi: Poi,
        availability: StationAvailabilitySummary?,
        selectedEnergyTypes: Set<String> = emptySet(),
        useVehicleFilter: Boolean = false,
        vehicleEnergy: String = "",
        vehicleGasTypes: Set<String> = emptySet(),
        onClick: () -> Unit
    ): Row {
        val title = poi.siteName?.takeIf { it.isNotBlank() } ?: poi.name.ifBlank { "POI" }
        val address = poi.addressLocal?.takeIf { it.isNotBlank() } ?: poi.address.ifBlank { "${poi.latitude}, ${poi.longitude}" }
        val source = "[Source: ${poi.source ?: "Unknown"}]"
        val markerBitmap = PoiMarkerHelper.getMarkerBitmap(
            context = carContext,
            poi = poi,
            selectedEnergyTypes = selectedEnergyTypes,
            useVehicleFilter = useVehicleFilter,
            vehicleEnergy = vehicleEnergy,
            vehicleGasTypes = vehicleGasTypes,
            sizePx = 72,
            availability = availability,
            style = PoiMarkerHelper.MarkerStyle.Circle
        )
        val place = Place.Builder(CarLocation.create(poi.latitude, poi.longitude))
            .setMarker(
                PlaceMarker.Builder()
                    .setIcon(
                        CarIcon.Builder(IconCompat.createWithBitmap(markerBitmap)).build(),
                        PlaceMarker.TYPE_ICON
                    )
                    .build()
            )
            .build()

        return Row.Builder()
            .setTitle(title)
            .addText("$address $source")
            .setMetadata(Metadata.Builder().setPlace(place).build())
            .setBrowsable(true)
            .setOnClickListener(onClick)
            .build()
    }
}
