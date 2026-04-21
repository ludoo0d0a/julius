package fr.geoking.julius.api.openchargemap

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import fr.geoking.julius.poi.BrandRegistry
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OpenChargeMapProviderTest {

    @Test
    fun testBrandExtraction() = runBlocking {
        val mockEngine = MockEngine { _ ->
            respond(
                content = """
                    [
                        {
                            "ID": 1,
                            "AddressInfo": {
                                "Title": "Tesla Supercharger Paris",
                                "Latitude": 48.8566,
                                "Longitude": 2.3522
                            },
                            "OperatorInfo": {
                                "Title": "Tesla"
                            },
                            "Connections": []
                        },
                        {
                            "ID": 2,
                            "AddressInfo": {
                                "Title": "Ionity Station A",
                                "Latitude": 48.8567,
                                "Longitude": 2.3523
                            },
                            "Connections": []
                        }
                    ]
                """.trimIndent(),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val client = OpenChargeMapClient(HttpClient(mockEngine))
        val provider = OpenChargeMapProvider(client)
        val pois = provider.getGasStations(48.8566, 2.3522)

        assertEquals(2, pois.size)

        // Case 1: Operator is present
        assertEquals("Tesla", pois[0].brand)
        assertEquals("Tesla", pois[0].operator)
        assertTrue(BrandRegistry.hasIcon(pois[0].brand), "Tesla should have an icon")

        // Case 2: Operator is missing, fallback to first word of name
        assertEquals("Ionity", pois[1].brand)
        assertEquals(null, pois[1].operator)
        assertTrue(BrandRegistry.hasIcon(pois[1].brand), "Ionity should have an icon")
    }
}
