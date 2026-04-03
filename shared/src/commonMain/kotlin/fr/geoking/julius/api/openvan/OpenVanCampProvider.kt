package fr.geoking.julius.api.openvan

import fr.geoking.julius.api.overpass.OverpassClient
import fr.geoking.julius.api.routex.radiusKmFromMapViewport
import fr.geoking.julius.parking.ParkingRegion
import fr.geoking.julius.poi.FuelPriceRegistry
import fr.geoking.julius.poi.MapViewport
import fr.geoking.julius.poi.Poi
import fr.geoking.julius.poi.PoiCategory
import fr.geoking.julius.poi.PoiProvider
import fr.geoking.julius.shared.logging.log

/**
 * [PoiProvider] that fetches fuel stations from OpenStreetMap and enriches them with
 * national weekly retail averages from [OpenVanCampClient] (OpenVan.camp, CC BY 4.0).
 *
 * Enrichment only occurs in countries with regulated uniform fuel prices (see [FuelPriceRegistry.UNIFORM_PRICE_COUNTRIES]).
 */
class OpenVanCampProvider(
    private val openVanClient: OpenVanCampClient,
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
        val iso = region?.countryCode
        val isUniform = FuelPriceRegistry.isUniformPriceCountry(iso)

        log.d { "[OpenVanCampProvider] getGasStations lat=$latitude lon=$longitude iso=$iso uniform=$isUniform" }

        val effectiveRadiusKm = viewport
            ?.let {
                radiusKmFromMapViewport(latitude, longitude, it.zoom, it.mapWidthPx, it.mapHeightPx).coerceIn(1, 50)
            }
            ?: radiusKm

        val fuelPrices = if (iso != null && isUniform) {
            try {
                openVanClient.getReferenceFuelPrices(iso)?.takeIf { it.isNotEmpty() }
            } catch (e: Exception) {
                log.w(e) { "[OpenVanCampProvider] Failed to fetch prices for $iso" }
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
                id = "osm:fuel:${el.id}",
                name = name,
                address = address,
                latitude = el.lat,
                longitude = el.lon,
                brand = el.brand()?.takeIf { it.isNotBlank() },
                poiCategory = PoiCategory.Gas,
                fuelPrices = fuelPrices,
                source = if (fuelPrices != null) "OpenStreetMap + OpenVan.camp ($iso official price)" else "OpenStreetMap"
            )
        }
    }

    companion object {
        private const val PADDING_DEG = 0.12

        fun searchCenterMayIncludeLuxembourg(lat: Double, lon: Double): Boolean {
            val r = ParkingRegion.Luxembourg
            return lat in (r.latMin - PADDING_DEG)..(r.latMax + PADDING_DEG) &&
                lon in (r.lonMin - PADDING_DEG)..(r.lonMax + PADDING_DEG)
        }
    }
}
