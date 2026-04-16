package fr.geoking.julius.api.uk

import fr.geoking.julius.api.routex.radiusKmFromMapViewport
import fr.geoking.julius.poi.FuelPrice
import fr.geoking.julius.poi.MapViewport
import fr.geoking.julius.poi.Poi
import fr.geoking.julius.poi.PoiCategory
import fr.geoking.julius.poi.PoiProvider
import fr.geoking.julius.shared.location.haversineKm
import io.ktor.client.HttpClient
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * [PoiProvider] for United Kingdom using CMA Open Data feeds.
 * Aggregates results from multiple retailer feeds and filters by proximity.
 */
class UnitedKingdomCmaProvider(
    private val httpClient: HttpClient,
    private val radiusKm: Int = 15,
    private val limit: Int = 100
) : PoiProvider {

    private val client = UnitedKingdomCmaClient(httpClient)
    private val mutex = Mutex()
    private var cachedStations: List<Poi>? = null
    private var lastFetch: Long = 0
    private val cacheDurationMs = 3600_000 // 1 hour

    override fun supportedCategories(): Set<PoiCategory> = setOf(PoiCategory.Gas)

    override suspend fun getGasStations(
        latitude: Double,
        longitude: Double,
        viewport: MapViewport?
    ): List<Poi> {
        val effectiveRadiusKm = viewport
            ?.let {
                radiusKmFromMapViewport(latitude, longitude, it.zoom, it.mapWidthPx, it.mapHeightPx).coerceIn(1, 50)
            }
            ?: radiusKm

        val allStations = getOrFetchAllStations()

        return allStations.asSequence()
            .map { it to haversineKm(latitude, longitude, it.latitude, it.longitude) }
            .filter { (_, dist) -> dist <= effectiveRadiusKm }
            .sortedBy { (_, dist) -> dist }
            .take(limit)
            .map { it.first }
            .toList()
    }

    private suspend fun getOrFetchAllStations(): List<Poi> = mutex.withLock {
        val now = currentTimeMillis()
        if (cachedStations != null && now - lastFetch < cacheDurationMs) {
            return cachedStations!!
        }

        val pois = coroutineScope {
            client.retailers.map { retailer ->
                async {
                    val data = client.fetchRetailerData(retailer)
                    data?.stations?.mapNotNull { it.toPoi(retailer.name) } ?: emptyList()
                }
            }.awaitAll().flatten()
        }

        if (pois.isNotEmpty()) {
            cachedStations = pois
            lastFetch = now
        }

        return pois
    }

    private fun CmaStation.toPoi(retailerName: String): Poi? {
        val lat = location?.latitude ?: return null
        val lng = location.longitude ?: return null
        if (lat == 0.0 || lng == 0.0) return null

        val fuelPrices = prices.mapNotNull { (type, price) ->
            // Normalizing prices: most CMA feeds use pence (e.g. 152.9).
            // We want Euros or a consistent format, but for now we keep the numeric value
            // (usually pence per liter in UK) and rely on UI to format or convert if needed.
            // Converting pence to "pounds" (or decimal equivalent) is standard (e.g. 1.529).
            val normalizedPrice = if (price > 10.0) price / 100.0 else price

            val fuelName = when (type.uppercase()) {
                "E10" -> "SP95"
                "E5" -> "SP98"
                "B7" -> "Gazole"
                "SDV" -> "Premium Diesel"
                "LPG" -> "GPLc"
                else -> type
            }
            FuelPrice(fuelName, normalizedPrice)
        }

        return Poi(
            id = "uk_cma:${site_id ?: "${brand}_${lat}_${lng}"}",
            name = brand ?: retailerName,
            address = listOfNotNull(address, postcode).joinToString(", "),
            latitude = lat,
            longitude = lng,
            brand = brand ?: retailerName,
            poiCategory = PoiCategory.Gas,
            fuelPrices = fuelPrices.ifEmpty { null },
            source = "CMA Open Data ($retailerName)"
        )
    }

    override fun clearCache() {
        cachedStations = null
        lastFetch = 0
    }

    private fun currentTimeMillis(): Long = io.ktor.util.date.getTimeMillis()
}
