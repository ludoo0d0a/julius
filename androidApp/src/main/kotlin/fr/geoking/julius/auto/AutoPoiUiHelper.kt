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
import fr.geoking.julius.api.availability.StationAvailabilitySummary
import fr.geoking.julius.poi.Poi
import fr.geoking.julius.poi.PoiCategory

/**
 * Shared logic for mapping POIs to car UI components (rows, markers, icons).
 */
object AutoPoiUiHelper {

    fun getIconResId(poi: Poi): Int = when (poi.poiCategory) {
        PoiCategory.Toilet -> R.drawable.ic_poi_toilet
        PoiCategory.DrinkingWater -> R.drawable.ic_poi_water
        PoiCategory.Camping -> R.drawable.ic_poi_camping
        PoiCategory.CaravanSite -> R.drawable.ic_poi_caravan
        PoiCategory.PicnicSite -> R.drawable.ic_poi_picnic
        PoiCategory.Radar -> R.drawable.ic_poi_radar
        else -> if (poi.isElectric) R.drawable.ic_poi_electric else R.drawable.ic_poi_gas
    }

    fun buildPlace(carContext: CarContext, poi: Poi): Place {
        return Place.Builder(CarLocation.create(poi.latitude, poi.longitude))
            .setMarker(
                PlaceMarker.Builder()
                    .setIcon(
                        CarIcon.Builder(IconCompat.createWithResource(carContext, getIconResId(poi))).build(),
                        PlaceMarker.TYPE_ICON
                    )
                    .setLabel(if (poi.poiCategory == PoiCategory.Radar) "VMA" else "POI")
                    .build()
            )
            .build()
    }

    fun buildPoiRow(
        carContext: CarContext,
        poi: Poi,
        availability: StationAvailabilitySummary?,
        onClick: () -> Unit
    ): Row {
        val title = poi.siteName?.takeIf { it.isNotBlank() } ?: poi.name.ifBlank { "POI" }
        val address = poi.addressLocal?.takeIf { it.isNotBlank() } ?: poi.address.ifBlank { "${poi.latitude}, ${poi.longitude}" }
        val source = "[Source: ${poi.source ?: "Unknown"}]"
        val place = buildPlace(carContext, poi)

        return Row.Builder()
            .setTitle(title)
            .addText("$address $source")
            .setMetadata(Metadata.Builder().setPlace(place).build())
            .setBrowsable(true)
            .setOnClickListener(onClick)
            .build()
    }
}
