package fr.geoking.julius.api.dgeg

import fr.geoking.julius.poi.FuelPrice
import fr.geoking.julius.poi.MapViewport
import fr.geoking.julius.poi.Poi
import fr.geoking.julius.poi.PoiCategory
import fr.geoking.julius.poi.PoiProvider
import fr.geoking.julius.shared.logging.log
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
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
            val response: List<DgegStation> = httpClient.post("https://precoscombustiveis.dgeg.gov.pt/api/PrecoComb/PesquisarPostos") {
                contentType(ContentType.Application.Json)
                setBody(DgegRequest())
            }.body()

            val pois = response.mapNotNull { it.toPoi() }
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
    private data class DgegRequest(
        val idCombustivel: String? = null,
        val idTipoPosto: String? = null,
        val idMunicipio: String? = null,
        val idDistrito: String? = null,
        val idMarca: String? = null
    )

    @Serializable
    private data class DgegStation(
        val Id: Int,
        val Nome: String,
        val Morada: String? = null,
        val Localidade: String? = null,
        val Latitude: Double,
        val Longitude: Double,
        val Marca: String? = null,
        val Preco: String? = null, // Sometimes used in specific searches, but we want all fuels
        val Combustiveis: List<DgegFuel>? = null
    ) {
        fun toPoi(): Poi? {
            if (Latitude == 0.0 || Longitude == 0.0) return null

            val prices = Combustiveis?.mapNotNull { it.toFuelPrice() } ?: emptyList()

            return Poi(
                id = "dgeg:$Id",
                name = Nome,
                address = listOfNotNull(Morada, Localidade).joinToString(", "),
                latitude = Latitude,
                longitude = Longitude,
                brand = Marca,
                poiCategory = PoiCategory.Gas,
                fuelPrices = prices.ifEmpty { null },
                source = "DGEG (Portugal)"
            )
        }
    }

    @Serializable
    private data class DgegFuel(
        val IdCombustivel: Int,
        val Descritivo: String,
        val Preco: String? = null
    ) {
        fun toFuelPrice(): FuelPrice? {
            val priceValue = Preco?.replace(",", ".")?.toDoubleOrNull() ?: return null

            // Fuel IDs mapping based on DGEG API observation:
            // 2101: Gasóleo rodoviário (Diesel)
            // 2105: Gasóleo especial (Premium Diesel)
            // 3201: Gasolina IO95 (SP95)
            // 3205: Gasolina especial IO95 (Premium SP95)
            // 3400: Gasolina IO98 (SP98)
            // 3405: Gasolina especial IO98 (Premium SP98)
            // 1120: GPL Auto (LPG)

            val fuelType = when (IdCombustivel) {
                2101, 2105 -> "Gazole"
                3201, 3205 -> "SP95"
                3400, 3405 -> "SP98"
                1120 -> "GPLc"
                else -> return null
            }

            return FuelPrice(fuelType, priceValue)
        }
    }
}
