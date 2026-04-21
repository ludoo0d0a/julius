package fr.geoking.julius.api.openchargemap

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class OpenChargeMapClientTest {

    @Test
    fun testPowerParsing() = runBlocking {
        val mockEngine = MockEngine { request ->
            respond(
                content = """
                    [
                        {
                            "ID": 1,
                            "AddressInfo": {
                                "Title": "Station 1",
                                "Latitude": 48.8566,
                                "Longitude": 2.3522
                            },
                            "Connections": [
                                { "PowerKW": 22.0 },
                                { "PowerKW": 7360.0 }
                            ]
                        }
                    ]
                """.trimIndent(),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val client = OpenChargeMapClient(HttpClient(mockEngine))
        val stations = client.getStations(48.8566, 2.3522)

        assertEquals(1, stations.size)
        val poi = stations[0]
        // Should be 22.0 or 7.36. Max should be 22.0 now.
        assertEquals(22.0, poi.powerKw)
    }

    @Test
    fun testPowerParsingConversion() = runBlocking {
        val mockEngine = MockEngine { request ->
            respond(
                content = """
                    [
                        {
                            "ID": 2,
                            "AddressInfo": { "Title": "Station 2", "Latitude": 0.0, "Longitude": 0.0 },
                            "Connections": [ { "PowerKW": 43000.0 } ]
                        }
                    ]
                """.trimIndent(),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val client = OpenChargeMapClient(HttpClient(mockEngine))
        val stations = client.getStations(0.0, 0.0)
        assertEquals(43.0, stations[0].powerKw)
    }
}
