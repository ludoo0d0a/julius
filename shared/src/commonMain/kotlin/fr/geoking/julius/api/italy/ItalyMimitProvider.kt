package fr.geoking.julius.api.italy

import fr.geoking.julius.api.routex.radiusKmFromMapViewport
import fr.geoking.julius.poi.FuelPrice
import fr.geoking.julius.poi.MapViewport
import fr.geoking.julius.poi.Poi
import fr.geoking.julius.poi.PoiCategory
import fr.geoking.julius.poi.PoiProvider
import fr.geoking.julius.shared.location.haversineKm
import fr.geoking.julius.shared.logging.log
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * [PoiProvider] for Italy using the official MIMIT (Ministero delle Imprese e del Made in Italy)
 * station-by-station open data.
 * Fetches and parses CSV feeds:
 * - Stations: https://www.mimit.gov.it/images/exportCSV/anagrafica_impianti_attivi.csv
 * - Prices: https://www.mimit.gov.it/images/exportCSV/prezzo_alle_8.csv
 */
class ItalyMimitProvider(
    private val client: HttpClient,
    private val radiusKm: Int = 15,
    private val limit: Int = 50
) : PoiProvider {

    private val mutex = Mutex()
    private var cachedStations: List<Poi>? = null
    private var lastFetch: Long = 0
    private val cacheDurationMs = 3600_000 * 6 // 6 hours, as it's a large file

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

        return try {
            val stationsCsv = client.get("https://www.mimit.gov.it/images/exportCSV/anagrafica_impianti_attivi.csv").bodyAsText()
            val pricesCsv = client.get("https://www.mimit.gov.it/images/exportCSV/prezzo_alle_8.csv").bodyAsText()

            val pois = withContext(Dispatchers.Default) {
                parseMimitData(stationsCsv, pricesCsv)
            }

            if (pois.isNotEmpty()) {
                cachedStations = pois
                lastFetch = now
            }
            pois
        } catch (e: Exception) {
            log.e(e) { "[ItalyMimitProvider] Failed to fetch data" }
            cachedStations ?: emptyList()
        }
    }

    private fun parseMimitData(stationsCsv: String, pricesCsv: String): List<Poi> {
        val priceMap = mutableMapOf<String, MutableList<FuelPrice>>()

        // Parse prices first
        // format: idImpianto|descCarburante|prezzo|isSelf|dtComu
        pricesCsv.lineSequence().drop(2).forEach { line ->
            val cols = line.split("|")
            if (cols.size >= 3) {
                val id = cols[0]
                val name = cols[1]
                val price = cols[2].toDoubleOrNull()
                val isSelf = cols.getOrNull(3) == "1"
                val updatedAt = cols.getOrNull(4)

                if (price != null) {
                    val fuelName = when {
                        name.contains("Benzina", true) -> if (isSelf) "SP95 (Self)" else "SP95"
                        name.contains("Gasolio", true) -> if (isSelf) "Gazole (Self)" else "Gazole"
                        name.contains("GPL", true) -> "GPLc"
                        name.contains("Metano", true) -> "CNG"
                        else -> name
                    }
                    priceMap.getOrPut(id) { mutableListOf() }.add(FuelPrice(fuelName, price, updatedAt))
                }
            }
        }

        // Parse stations and merge
        // format: idImpianto|Gestore|Bandiera|Tipo Impianto|Nome Impianto|Indirizzo|Comune|Provincia|Latitudine|Longitudine
        return stationsCsv.lineSequence().drop(2).mapNotNull { line ->
            val cols = line.split("|")
            if (cols.size >= 10) {
                val id = cols[0]
                val brand = cols[2]
                val name = cols[4]
                val address = cols[5]
                val city = cols[6]
                val lat = cols[8].toDoubleOrNull()
                val lon = cols[9].toDoubleOrNull()

                if (lat != null && lon != null && (lat != 0.0 || lon != 0.0)) {
                    Poi(
                        id = "italy_mimit:$id",
                        name = name.takeIf { it.isNotBlank() } ?: "Gas Station",
                        address = listOfNotNull(address, city).joinToString(", "),
                        latitude = lat,
                        longitude = lon,
                        brand = brand,
                        poiCategory = PoiCategory.Gas,
                        fuelPrices = priceMap[id]?.ifEmpty { null },
                        source = "MIMIT (Italy)"
                    )
                } else null
            } else null
        }.toList()
    }

    override fun clearCache() {
        cachedStations = null
        lastFetch = 0
    }

    private fun currentTimeMillis(): Long = io.ktor.util.date.getTimeMillis()
}
