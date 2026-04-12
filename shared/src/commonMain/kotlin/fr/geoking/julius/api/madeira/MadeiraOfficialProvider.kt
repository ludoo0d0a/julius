package fr.geoking.julius.api.madeira

import fr.geoking.julius.api.overpass.OverpassClient
import fr.geoking.julius.api.routex.radiusKmFromMapViewport
import fr.geoking.julius.parking.ParkingRegion
import fr.geoking.julius.poi.MapViewport
import fr.geoking.julius.poi.Poi
import fr.geoking.julius.poi.PoiCategory
import fr.geoking.julius.poi.PoiProvider
import fr.geoking.julius.shared.logging.log

/**
 * [PoiProvider] that fetches fuel stations from OpenStreetMap and enriches them with
 * official regional maximum prices for Madeira via [MadeiraFuelPricesClient].
 */
class MadeiraOfficialProvider(
    private val madeiraClient: MadeiraFuelPricesClient,
    private val overpassClient: OverpassClient,
    private val radiusKm: Int = 10,
    private val limit: Int = 100
) : PoiProvider {

    override fun supportedCategories(): Set<PoiCategory> = setOf(PoiCategory.Gas)

    override suspend fun getGasStations(
        latitude: Double,
        longitude: Double,
        viewport: MapViewport?
    ): List<Poi> {
        val region = ParkingRegion.containing(latitude, longitude)
        val isMadeira = region == ParkingRegion.Madeira || region?.countryCode == "PT-MA"

        val effectiveRadiusKm = viewport
            ?.let {
                radiusKmFromMapViewport(latitude, longitude, it.zoom, it.mapWidthPx, it.mapHeightPx).coerceIn(1, 50)
            }
            ?: radiusKm

        val fuelPrices = if (isMadeira) {
            try {
                madeiraClient.getFuelPrices().takeIf { it.isNotEmpty() }
            } catch (e: Exception) {
                log.w(e) { "[MadeiraOfficialProvider] Failed to fetch prices" }
                null
            }
        } else {
            null
        }

        val elements = try {
            overpassClient.queryNodesAndWaysWithTagFilters(
                latitude = latitude,
                longitude = longitude,
                radiusKm = effectiveRadiusKm,
                tagFilters = listOf("amenity" to setOf("fuel")),
                limit = limit
            )
        } catch (_: Exception) {
            emptyList()
        }

        return elements.map { el ->
            val name = el.name()?.takeIf { it.isNotBlank() } ?: "Gas station"
            val address = el.address().orEmpty()
            Poi(
                id = "osm:pt_ma_fuel:${el.id}",
                name = name,
                address = address,
                latitude = el.lat,
                longitude = el.lon,
                brand = el.brand()?.takeIf { it.isNotBlank() },
                poiCategory = PoiCategory.Gas,
                fuelPrices = fuelPrices,
                source = if (fuelPrices != null) "OpenStreetMap + Madeira (official max price)" else "OpenStreetMap"
            )
        }
    }

    override fun clearCache() {
        madeiraClient.clearCache()
    }
}
