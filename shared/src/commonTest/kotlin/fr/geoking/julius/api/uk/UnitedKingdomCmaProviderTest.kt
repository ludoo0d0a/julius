package fr.geoking.julius.api.uk

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class UnitedKingdomCmaProviderTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun testCmaMapping() = runBlocking {
        val mockEngine = MockEngine { request ->
            if (request.url.toString().contains("asda")) {
                respond(
                    content = """
                        {
                            "last_updated": "2024-03-20T10:00:00Z",
                            "stations": [
                                {
                                    "site_id": "SITE1",
                                    "brand": "Asda",
                                    "name": "Asda London",
                                    "address": "123 High St",
                                    "post_code": "SW1A 1AA",
                                    "location": {
                                        "latitude": 51.5074,
                                        "longitude": -0.1278
                                    },
                                    "prices": {
                                        "E10": 145.9,
                                        "B7": 152.9
                                    }
                                }
                            ]
                        }
                    """.trimIndent(),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            } else {
                respond(
                    content = "{}",
                    status = HttpStatusCode.NotFound
                )
            }
        }

        val httpClient = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(json)
            }
        }
        val provider = UnitedKingdomCmaProvider(httpClient, radiusKm = 10)

        val pois = provider.getGasStations(51.5, -0.1)

        assertEquals(1, pois.size)
        val poi = pois[0]
        assertEquals("uk_cma:SITE1", poi.id)
        assertEquals("Asda", poi.name)
        assertEquals(2, poi.fuelPrices?.size)

        val sp95 = poi.fuelPrices?.find { it.fuelName == "SP95" }
        assertEquals(1.459, sp95?.price)

        val gazole = poi.fuelPrices?.find { it.fuelName == "Gazole" }
        assertEquals(1.529, gazole?.price?.let { (it * 1000).toInt() / 1000.0 })
    }
}
