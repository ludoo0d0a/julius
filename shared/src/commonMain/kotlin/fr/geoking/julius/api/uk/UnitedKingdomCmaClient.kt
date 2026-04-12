package fr.geoking.julius.api.uk

import fr.geoking.julius.shared.network.NetworkException
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.Serializable

/**
 * Client for United Kingdom fuel price data (CMA mandated).
 * Aggregates prices from multiple retailer feeds.
 */
class UnitedKingdomCmaClient(
    private val client: HttpClient
) {
    private val retailerFeeds = listOf(
        "https://www.asda.com/fuel-prices/fuel-prices.json",
        "https://www.bp.com/content/dam/bp/country-sites/en_gb/united-kingdom/home/fuel-prices/fuel-prices.json",
        "https://www.shell.co.uk/motorists/fuel-pricing/fuel-prices.json",
        "https://www.tesco.com/fuel-prices/fuel-prices.json",
        "https://app.morrisons.com/fuel-prices/fuel-prices.json",
        "https://fuelprices.sainsburys.co.uk/fuel-prices.json",
        "https://www.applegreenstores.com/fuel-prices.json",
        "https://www.jetlocal.co.uk/fuel-prices.json"
    )

    suspend fun getAllStations(): List<CmaStation> {
        val allStations = mutableListOf<CmaStation>()

        retailerFeeds.forEach { feedUrl ->
            try {
                val response = client.get(feedUrl)
                if (response.status.value == 200) {
                    val cmaResponse: CmaResponse = response.body()
                    allStations.addAll(cmaResponse.stations)
                }
            } catch (e: Exception) {
                // Skip failing feeds to maintain partial availability
                if (e is kotlinx.coroutines.CancellationException) throw e
            }
        }

        return allStations
    }
}

@Serializable
data class CmaResponse(
    val last_updated: String,
    val stations: List<CmaStation>
)

@Serializable
data class CmaStation(
    val site_id: String,
    val brand: String,
    val name: String,
    val address: String,
    val post_code: String,
    val location: CmaLocation,
    val prices: Map<String, Double>
)

@Serializable
data class CmaLocation(
    val latitude: Double,
    val longitude: Double
)
