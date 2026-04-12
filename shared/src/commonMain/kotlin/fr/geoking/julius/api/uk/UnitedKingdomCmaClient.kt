package fr.geoking.julius.api.uk

import fr.geoking.julius.shared.network.NetworkException
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Client for fetching UK fuel price data following the CMA (Competition and Markets Authority)
 * open data scheme. Retailers provide a JSON file with station information and prices.
 */
class UnitedKingdomCmaClient(
    private val client: HttpClient
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /**
     * Common retailers participating in the CMA scheme.
     */
    val retailers = listOf(
        Retailer("Asda", "https://storelocator.asda.com/fuel_prices_data.json"),
        Retailer("BP", "https://www.bp.com/en_gb/united-kingdom/home/fuelprices/fuel_prices_data.json"),
        Retailer("Shell", "https://www.shell.co.uk/fuel-prices-data.html"), // Note: Shell often redirects or uses a wrapper
        Retailer("Esso/Tesco Alliance", "https://fuelprices.esso.co.uk/latestdata.json"),
        Retailer("Morrisons", "https://www.morrisons.com/fuel-prices/fuel.json"),
        Retailer("Sainsbury's", "https://api.sainsburys.co.uk/v1/exports/latest/fuel_prices_data.json"),
        Retailer("Applegreen", "https://applegreenstores.com/fuel-prices/data.json"),
        Retailer("MFG", "https://fuel.motorfuelgroup.com/fuel_prices_data.json"),
        Retailer("Rontec", "https://www.rontec-servicestations.co.uk/fuel-prices/data/fuel_prices_data.json"),
        Retailer("SGN", "https://www.sgnretail.uk/files/data/SGN_daily_fuel_prices.json")
    )

    suspend fun fetchRetailerData(retailer: Retailer): CmaResponse? {
        return try {
            val response = client.get(retailer.url)
            val body = response.bodyAsText()
            if (response.status.value != 200) return null

            // Clean up common issues (e.g. leading/trailing whitespace)
            val cleanBody = body.trim()
            if (!cleanBody.startsWith("{")) return null

            json.decodeFromString<CmaResponse>(cleanBody)
        } catch (e: Exception) {
            null
        }
    }

    data class Retailer(val name: String, val url: String)
}

@Serializable
data class CmaResponse(
    val last_updated: String? = null,
    val stations: List<CmaStation> = emptyList()
)

@Serializable
data class CmaStation(
    val site_id: String? = null,
    val brand: String? = null,
    val address: String? = null,
    val postcode: String? = null,
    val location: CmaLocation? = null,
    val prices: Map<String, Double> = emptyMap()
)

@Serializable
data class CmaLocation(
    val latitude: Double? = null,
    val longitude: Double? = null
)
