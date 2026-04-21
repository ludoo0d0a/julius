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

class FastnedOcpiPoiProviderTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun testGetLocationsAndTariffsMapping() = runBlocking {
        val mockEngine = MockEngine { request ->
            val url = request.url.encodedPath
            when {
                url.endsWith("/locations") -> respond(
                    content = """
                        {
                            "data": [
                                {
                                    "id": "LOC1",
                                    "name": "Fastned London",
                                    "address": "123 High St",
                                    "city": "London",
                                    "country": "GBR",
                                    "coordinates": {
                                        "latitude": "51.5074",
                                        "longitude": "0.1278"
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
                                                    "max_electric_power": 300000,
                                                    "tariff_ids": ["TARIFF1"],
                                                    "last_updated": "2024-03-20T10:00:00Z"
                                                }
                                            ],
                                            "last_updated": "2024-03-20T10:00:00Z"
                                        }
                                    ],
                                    "last_updated": "2024-03-20T10:00:00Z"
                                }
                            ],
                            "status_code": 1000
                        }
                    """.trimIndent(),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
                url.endsWith("/tariffs") -> respond(
                    content = """
                        {
                            "data": [
                                {
                                    "id": "TARIFF1",
                                    "currency": "GBP",
                                    "elements": [
                                        {
                                            "price_components": [
                                                {
                                                    "type": "ENERGY",
                                                    "price": 0.69,
                                                    "step_size": 1
                                                }
                                            ]
                                        }
                                    ],
                                    "last_updated": "2024-03-20T10:00:00Z"
                                }
                            ],
                            "status_code": 1000
                        }
                    """.trimIndent(),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
                else -> respond(
                    content = "Error",
                    status = HttpStatusCode.BadRequest
                )
            }
        }

        val httpClient = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(json)
            }
        }
        val ocpiClient = OcpiClient(httpClient, "https://uk-public.api.fastned.nl/uk-public/ocpi/cpo/2.2.1", "")
        val provider = FastnedOcpiPoiProvider(ocpiClient)

        val pois = provider.getGasStations(51.5, 0.1)

        assertEquals(1, pois.size)
        val poi = pois[0]
        assertEquals("LOC1", poi.id)
        assertEquals("Fastned London", poi.name)
        assertTrue(poi.isElectric)
        assertEquals(300.0, poi.powerKw)
        assertEquals("0.69£ / kWh", poi.irveDetails?.tarification)
    }
}
