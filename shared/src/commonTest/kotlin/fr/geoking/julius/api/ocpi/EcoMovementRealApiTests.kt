package fr.geoking.julius.api.ocpi

import fr.geoking.julius.agents.RealApiTestBase
import fr.geoking.julius.agents.TestPropertyReader
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertTrue

class EcoMovementRealApiTests : RealApiTestBase() {

    @Test
    fun testEcoMovementLocations() = runBlocking {
        val url = TestPropertyReader.getProperty("ECO_MOVEMENT_URL")
        val token = TestPropertyReader.getProperty("ECO_MOVEMENT_TOKEN")

        if (url.isNullOrBlank() || token.isNullOrBlank()) {
            println("⚠️ Skipping Eco-Movement test - ECO_MOVEMENT_URL or ECO_MOVEMENT_TOKEN missing")
            return@runBlocking
        }

        withHttpClient { httpClient ->
            val client = OcpiClient(httpClient, baseUrl = url, token = token)
            val provider = OcpiPoiProvider(client, providerName = "Eco-Movement")

            // Test around Paris
            val pois = provider.getGasStations(48.8566, 2.3522)
            println("Eco-Movement returned ${pois.size} POIs")

            assertTrue(pois.isNotEmpty(), "Eco-Movement should return some EV stations")

            val first = pois.first()
            println("First POI: ${first.name} at ${first.address}, ${first.latitude}, ${first.longitude}")
            assertTrue(first.isElectric, "POI should be electric")
        }
    }
}
