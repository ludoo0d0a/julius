package fr.geoking.julius.api.ocpi

import fr.geoking.julius.poi.PoiCategory
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
import kotlin.test.assertTrue

class OcpiPoiProviderTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun testGetLocationsMapping() = runBlocking {
        val mockEngine = MockEngine { request ->
            respond(
                content = """
                    {
                        "data": [
                            {
                                "id": "LOC1",
                                "name": "Ionity Paris",
                                "address": "A1 Autoroute",
                                "city": "Paris",
                                "country": "FRA",
                                "coordinates": {
                                    "latitude": "48.8566",
                                    "longitude": "2.3522"
                                },
                                "evses": [
                                    {
                                        "uid": "EVSE1",
                                        "status": "AVAILABLE",
                                        "connectors": [
                                            {
                                                "id": "CON1",
                                                "standard": "IEC_62196_T2_COMBO",
                                                "format": "CABLE",
                                                "power_type": "DC",
                                                "max_voltage": 1000,
                                                "max_amperage": 350,
                                                "max_electric_power": 350000,
                                                "last_updated": "2024-03-20T10:00:00Z"
                                            }
                                        ],
                                        "last_updated": "2024-03-20T10:00:00Z"
                                    }
                                ],
                                "last_updated": "2024-03-20T10:00:00Z"
                            }
                        ],
                        "status_code": 1000,
                        "status_message": "Success",
                        "timestamp": "2024-03-20T10:00:00Z"
                    }
                """.trimIndent(),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val httpClient = HttpClient(mockEngine) {
            install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
                json(json)
            }
        }
        val ocpiClient = OcpiClient(httpClient, "https://api.ionity.eu", "token")
        val provider = OcpiPoiProvider(ocpiClient, "Ionity")

        val pois = provider.getGasStations(48.8, 2.3)

        assertEquals(1, pois.size)
        val poi = pois[0]
        assertEquals("LOC1", poi.id)
        assertEquals("Ionity Paris", poi.name)
        assertTrue(poi.isElectric)
        assertEquals(350.0, poi.powerKw)
        assertEquals(1, poi.chargePointCount)
        assertEquals(setOf("combo_ccs"), poi.irveDetails?.connectorTypes)
        assertEquals(1, poi.irveDetails?.availableConnectors)
    }

    @Test
    fun testGetLocationsNotFoundGraceful() = runBlocking {
        val mockEngine = MockEngine { request ->
            respond(
                content = """{"timestamp":"2026-04-15T18:25:03.099Z","path":"/ocpi/2.2.1/locations","status":404,"error":"Not Found"}""",
                status = HttpStatusCode.NotFound,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val httpClient = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(json)
            }
        }
        val ocpiClient = OcpiClient(httpClient, "https://api.ionity.eu/ocpi/2.2.1", "token")
        val provider = OcpiPoiProvider(ocpiClient, "Ionity")

        val pois = provider.getGasStations(48.8, 2.3)

        assertTrue(pois.isEmpty(), "Should return empty list on 404 instead of throwing exception")
    }

    @Test
    fun testTokenPrefixing() = runBlocking {
        var capturedHeader: String? = null
        val mockEngine = MockEngine { request ->
            capturedHeader = request.headers[HttpHeaders.Authorization]
            respond(
                content = """{"data": [], "status_code": 1000}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val httpClient = HttpClient(mockEngine) {
            install(ContentNegotiation) { json(json) }
        }

        // Case 1: Token without prefix
        OcpiClient(httpClient, "https://api.example.com", "abc").getLocations()
        assertEquals("Token abc", capturedHeader)

        // Case 2: Token with prefix
        OcpiClient(httpClient, "https://api.example.com", "Token abc").getLocations()
        assertEquals("Token abc", capturedHeader)

        // Case 3: Token with prefix (case insensitive) - should normalize to "Token "
        OcpiClient(httpClient, "https://api.example.com", "token abc").getLocations()
        assertEquals("Token abc", capturedHeader)

        // Case 4: No prefix requested
        OcpiClient(httpClient, "https://api.example.com", "abc", useTokenPrefix = false).getLocations()
        assertEquals("abc", capturedHeader)
    }
}
