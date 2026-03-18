package fr.geoking.julius.api

import fr.geoking.julius.agents.createTestHttpClientEngine
import fr.geoking.julius.api.chargy.ChargyProvider
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertTrue

class ChargyProviderRealApiTests {

    private fun createHttpClient(): HttpClient {
        return HttpClient(createTestHttpClientEngine()) {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                })
            }
        }
    }

    private val luxCityLat = 49.6116
    private val luxCityLon = 6.1319

    @Test
    fun testChargyProvider() = runBlocking {
        val client = createHttpClient()
        try {
            val provider = ChargyProvider(client)
            val pois = provider.getGasStations(luxCityLat, luxCityLon)
            println("Chargy returned ${pois.size} POIs")
            assertTrue(pois.isNotEmpty(), "Chargy should return some EV stations in Luxembourg City")

            val first = pois.first()
            assertTrue(first.isElectric, "Should be an electric station")
            assertTrue(first.name.contains("/") || first.name.contains("FULL"), "Name should contain availability info")
            assertTrue(first.irveDetails?.totalConnectors != null, "Should have total connectors info")
        } finally {
            client.close()
        }
    }
}
