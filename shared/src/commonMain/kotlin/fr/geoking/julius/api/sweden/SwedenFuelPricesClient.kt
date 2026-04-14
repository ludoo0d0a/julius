package fr.geoking.julius.api.sweden

import fr.geoking.julius.poi.FuelPrice
import fr.geoking.julius.shared.network.NetworkException
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText

/**
 * Client for official Swedish fuel list prices from Circle K.
 * Scrapes prices from https://www.circlek.se/foretag/drivmedel/priser
 */
class SwedenFuelPricesClient(
    private val client: HttpClient,
    private val baseUrl: String = DEFAULT_BASE_URL
) {
    suspend fun getFuelPrices(): List<FuelPrice> {
        val url = "$baseUrl/foretag/drivmedel/priser"
        val response = client.get(url)
        val body = response.bodyAsText()
        if (response.status.value != 200) {
            throw NetworkException(response.status.value, "Sweden Fuel Prices API error")
        }
        return parseFuelPrices(body)
    }

    private fun parseFuelPrices(html: String): List<FuelPrice> {
        val prices = mutableListOf<FuelPrice>()

        // Use a robust regex that captures the content between the span and the end of the cell.
        val regex = """Produktnamn:</span>\s*([^<]+)</td>[\s\S]*?Pris:</span>\s*([^<]+)</td>[\s\S]*?<time[^>]*>([^<]+)</time>""".toRegex()

        regex.findAll(html).forEach { match ->
            val name = match.groupValues[1].trim()
            val priceStr = match.groupValues[2].trim().replace(',', '.')
            val price = priceStr.toDoubleOrNull() ?: return@forEach
            val updatedAt = match.groupValues[3].trim()

            val normalizedName = when {
                name.contains("miles 95", ignoreCase = true) -> "SP95 E10"
                name.contains("miles 98", ignoreCase = true) || name.contains("miles+ 98", ignoreCase = true) -> "SP98"
                name.contains("miles diesel", ignoreCase = true) || name.contains("miles+ diesel", ignoreCase = true) -> "Gazole"
                name.equals("E85", ignoreCase = true) -> "E85"
                else -> null
            }

            if (normalizedName != null) {
                if (prices.none { it.fuelName == normalizedName }) {
                    prices.add(
                        FuelPrice(
                            fuelName = normalizedName,
                            price = price,
                            updatedAt = updatedAt
                        )
                    )
                }
            }
        }

        return prices
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://www.circlek.se"
    }
}
