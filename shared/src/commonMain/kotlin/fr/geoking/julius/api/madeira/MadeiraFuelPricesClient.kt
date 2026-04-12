package fr.geoking.julius.api.madeira

import fr.geoking.julius.poi.FuelPrice
import fr.geoking.julius.shared.network.NetworkException
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText

/**
 * Client for the official Madeira regional fuel price information.
 * Scrapes prices from the Madeira government portal.
 */
class MadeiraFuelPricesClient(
    private val client: HttpClient,
    private val baseUrl: String = DEFAULT_BASE_URL
) {
    private var cache: List<FuelPrice>? = null
    private var lastFetch: Long = 0
    private val cacheDurationMs = 3600_000 * 24 // 24 hours for regional max prices

    suspend fun getFuelPrices(): List<FuelPrice> {
        val now = currentTimeMillis()
        if (cache != null && now - lastFetch < cacheDurationMs) {
            return cache!!
        }

        val url = "$baseUrl/drec/Pesquisar/ctl/ReadInformcao/mid/15745/InformacaoId/219535/UnidadeOrganicaId/59/LiveSearch/Pre%C3%A7os"
        val response = client.get(url)
        val body = response.bodyAsText()
        if (response.status.value != 200) {
            throw NetworkException(response.status.value, "Madeira Fuel Prices API error")
        }
        val prices = parseFuelPrices(body)
        if (prices.isNotEmpty()) {
            cache = prices
            lastFetch = now
        }
        return prices
    }

    fun clearCache() {
        cache = null
        lastFetch = 0
    }

    private fun currentTimeMillis(): Long = io.ktor.util.date.getTimeMillis()

    private fun parseFuelPrices(html: String): List<FuelPrice> {
        val prices = mutableListOf<FuelPrice>()

        // Find all blocks with date ranges and prices
        // Example text from portal:
        // 13.01.2025 a 19.01.2025
        // Gasolina IO95
        // 1,589€
        // Gasóleo Rodoviário
        // 1,343€

        val blockRegex = """(\d{2}\.\d{2}\.\d{4})\s+a\s+(\d{2}\.\d{2}\.\d{4})[\s\S]+?Gasolina IO95\s+([\d,]+)€[\s\S]+?Gasóleo Rodoviário\s+([\d,]+)€""".toRegex()

        // Find the last match which should be the most recent prices
        val matches = blockRegex.findAll(html).toList()
        if (matches.isEmpty()) return emptyList()

        val latest = matches.last()
        val startDate = latest.groupValues[1]
        val endDate = latest.groupValues[2]
        val sp95Price = latest.groupValues[3].replace(",", ".").toDoubleOrNull()
        val dieselPrice = latest.groupValues[4].replace(",", ".").toDoubleOrNull()

        if (sp95Price != null) {
            prices.add(FuelPrice("SP95", sp95Price, updatedAt = "$startDate to $endDate"))
        }
        if (dieselPrice != null) {
            prices.add(FuelPrice("Gazole", dieselPrice, updatedAt = "$startDate to $endDate"))
        }

        return prices
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://www.madeira.gov.pt"
    }
}
