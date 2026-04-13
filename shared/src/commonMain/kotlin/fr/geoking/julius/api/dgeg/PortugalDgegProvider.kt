package fr.geoking.julius.api.dgeg

import fr.geoking.julius.poi.FuelPrice
import fr.geoking.julius.poi.MapViewport
import fr.geoking.julius.poi.Poi
import fr.geoking.julius.poi.PoiCategory
import fr.geoking.julius.poi.PoiProvider
import fr.geoking.julius.shared.logging.log
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import kotlinx.serialization.Serializable

/**
 * [PoiProvider] for Portugal using the DGEG (Direção-Geral de Energia e Geologia) JSON API.
 * Handles station-specific real-time prices for mainland Portugal.
 */
class PortugalDgegProvider(
    private val httpClient: HttpClient
) : PoiProvider {

    private var cache: List<Poi>? = null
    private var lastFetch: Long = 0
    private val cacheDurationMs = 3600_000 // 1 hour

    override fun supportedCategories(): Set<PoiCategory> = setOf(PoiCategory.Gas)

    override suspend fun getGasStations(
        latitude: Double,
        longitude: Double,
        viewport: MapViewport?
    ): List<Poi> {
        val allStations = getAllStations()

        // Filtering by proximity (simple bounding box for performance)
        val radiusDeg = 0.2 // ~22km
        return allStations.filter {
            it.latitude in (latitude - radiusDeg)..(latitude + radiusDeg) &&
            it.longitude in (longitude - radiusDeg)..(longitude + radiusDeg)
        }
    }

    private suspend fun getAllStations(): List<Poi> {
        val now = currentTimeMillis()
        if (cache != null && now - lastFetch < cacheDurationMs) {
            return cache!!
        }

        return try {
            val fuelIds = listOf(2101, 2105, 3201, 3205, 3400, 3405, 1120).joinToString(",")
            val response: DgegEnvelope = httpClient.get("https://precoscombustiveis.dgeg.gov.pt/api/PrecoComb/PesquisarPostos") {
                parameter("idsTiposComb", fuelIds)
            }.body()

            val pois = response.resultado
                .groupBy { it.Id }
                .mapNotNull { (id, stations) ->
                    val first = stations.first()
                    if (first.Latitude == 0.0 || first.Longitude == 0.0) return@mapNotNull null

                    val prices = stations.mapNotNull { it.toFuelPrice() }

                    Poi(
                        id = "dgeg:$id",
                        name = first.Nome,
                        address = listOfNotNull(first.Morada, first.Localidade).joinToString(", "),
                        latitude = first.Latitude,
                        longitude = first.Longitude,
                        brand = first.Marca,
                        poiCategory = PoiCategory.Gas,
                        fuelPrices = prices.ifEmpty { null },
                        source = "DGEG (Portugal)"
                    )
                }
            cache = pois
            lastFetch = now
            pois
        } catch (e: Exception) {
            log.e(e) { "[PortugalDgegProvider] Failed to fetch stations" }
            cache ?: emptyList()
        }
    }

    override fun clearCache() {
        cache = null
        lastFetch = 0
    }

    private fun currentTimeMillis(): Long = io.ktor.util.date.getTimeMillis()

    @Serializable
    private data class DgegEnvelope(
        val status: Boolean,
        val resultado: List<DgegStation>
    )

    @Serializable
    private data class DgegStation(
        val Id: Int,
        val Nome: String,
        val Preco: String? = null,
        val Combustivel: String? = null,
        val Marca: String? = null,
        val Morada: String? = null,
        val Localidade: String? = null,
        val Latitude: Double,
        val Longitude: Double
    ) {
        fun toFuelPrice(): FuelPrice? {
            val priceString = Preco?.split(" ")?.firstOrNull() ?: return null
            val priceValue = priceString.replace(",", ".").toDoubleOrNull() ?: return null

            val fuelType = when {
                Combustivel?.contains("Gasóleo simples", ignoreCase = true) == true -> "Gazole"
                Combustivel?.contains("Gasóleo especial", ignoreCase = true) == true -> "Gazole"
                Combustivel?.contains("Gasolina simples 95", ignoreCase = true) == true -> "SP95"
                Combustivel?.contains("Gasolina especial 95", ignoreCase = true) == true -> "SP95"
                Combustivel?.contains("Gasolina 98", ignoreCase = true) == true -> "SP98"
                Combustivel?.contains("Gasolina especial 98", ignoreCase = true) == true -> "SP98"
                Combustivel?.contains("GPL Auto", ignoreCase = true) == true -> "GPLc"
                else -> return null
            }

            return FuelPrice(fuelType, priceValue)
        }
    }
}
