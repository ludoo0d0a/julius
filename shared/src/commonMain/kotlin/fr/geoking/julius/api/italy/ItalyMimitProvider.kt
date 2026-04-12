package fr.geoking.julius.api.italy

import fr.geoking.julius.poi.FuelPrice
import fr.geoking.julius.poi.MapViewport
import fr.geoking.julius.poi.Poi
import fr.geoking.julius.poi.PoiCategory
import fr.geoking.julius.poi.PoiProvider
import fr.geoking.julius.shared.location.haversineKm
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText

/**
 * [PoiProvider] implementation for Italy fuel prices (MIMIT official CSV).
 * Scrapes daily CSV exports from Ministero delle Imprese e del Made in Italy.
 */
class ItalyMimitProvider(
    private val client: HttpClient,
    private val radiusKm: Int = 10
) : PoiProvider {

    private val stationsUrl = "https://www.mise.gov.it/images/exportCSV/anagrafica_impianti_attivi.csv"
    private val pricesUrl = "https://www.mise.gov.it/images/exportCSV/prezzo_alle_vendite.csv"

    private var cache: List<Poi>? = null
    private var lastFetch: Long = 0
    private val cacheDurationMs = 3600_000 * 12 // 12 hours (daily export)

    override fun supportedCategories(): Set<PoiCategory> = setOf(PoiCategory.Gas)

    override suspend fun getGasStations(
        latitude: Double,
        longitude: Double,
        viewport: MapViewport?
    ): List<Poi> {
        val allStations = getAllStations()

        // Filtering by proximity (approx 0.2 deg for radiusKm default)
        val deg = radiusKm / 111.0
        return allStations.filter {
            it.latitude in (latitude - deg)..(latitude + deg) &&
            it.longitude in (longitude - deg)..(longitude + deg)
        }
    }

    private suspend fun getAllStations(): List<Poi> {
        val now = currentTimeMillis()
        if (cache != null && now - lastFetch < cacheDurationMs) {
            return cache!!
        }

        return try {
            val stationsCsv = client.get(stationsUrl).bodyAsText()
            val pricesCsv = client.get(pricesUrl).bodyAsText()

            val stations = parseStations(stationsCsv)
            val prices = parsePrices(pricesCsv)

            val pois = stations.map { s ->
                s.copy(fuelPrices = prices[s.id])
            }
            cache = pois
            lastFetch = now
            pois
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            cache ?: emptyList()
        }
    }

    private fun currentTimeMillis(): Long = io.ktor.util.date.getTimeMillis()

    override fun clearCache() {
        cache = null
        lastFetch = 0
    }

    private fun parseStations(csv: String): List<Poi> {
        return csv.lineSequence()
            .drop(2) // Skip header and possible metadata
            .mapNotNull { line ->
                val parts = line.split(";")
                if (parts.size < 10) return@mapNotNull null

                val id = parts[0]
                val name = parts[2]
                val address = parts[4]
                val sLat = parts[8].toDoubleOrNull() ?: 0.0
                val sLon = parts[9].toDoubleOrNull() ?: 0.0

                if (sLat == 0.0 || sLon == 0.0) return@mapNotNull null

                Poi(
                    id = id,
                    name = name,
                    address = address,
                    latitude = sLat,
                    longitude = sLon,
                    brand = parts[1],
                    poiCategory = PoiCategory.Gas,
                    source = "Italy MIMIT"
                )
            }.toList()
    }

    private fun parsePrices(csv: String): Map<String, List<FuelPrice>> {
        val prices = mutableMapOf<String, MutableList<FuelPrice>>()
        csv.lineSequence().drop(2).forEach { line ->
            val parts = line.split(";")
            if (parts.size < 4) return@forEach

            val stationId = parts[0]
            val fuelName = parts[1]
            val price = parts[2].toDoubleOrNull() ?: 0.0

            prices.getOrPut(stationId) { mutableListOf() }.add(
                FuelPrice(fuelName = mapFuelName(fuelName), price = price)
            )
        }
        return prices
    }

    private fun mapFuelName(name: String): String = when {
        name.contains("Benzina", ignoreCase = true) -> "SP95"
        name.contains("Gasolio", ignoreCase = true) -> "Gazole"
        name.contains("GPL", ignoreCase = true) -> "GPLC"
        name.contains("Metano", ignoreCase = true) -> "GNV"
        else -> name
    }
}
