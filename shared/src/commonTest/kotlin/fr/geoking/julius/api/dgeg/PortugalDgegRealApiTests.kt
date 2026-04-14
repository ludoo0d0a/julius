package fr.geoking.julius.api.dgeg

import fr.geoking.julius.poi.PoiCategory
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertTrue

class PortugalDgegRealApiTests {

    @Test
    fun testRealApiReturnsData() = runBlocking {
        val client = HttpClient {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    coerceInputValues = true
                })
            }
        }
        val provider = PortugalDgegProvider(client)

        // Lisbon coordinates
        val stations = provider.getGasStations(38.7223, -9.1393, null)

        assertTrue(stations.isNotEmpty(), "Should return at least one station in Lisbon")
        val stationWithPrices = stations.find { it.fuelPrices?.isNotEmpty() == true }
        assertTrue(stationWithPrices != null, "Should return at least one station with fuel prices")

        val firstPrice = stationWithPrices?.fuelPrices?.first()
        assertTrue(firstPrice != null && firstPrice.price > 0.0, "Fuel price should be greater than 0")
    }
}
