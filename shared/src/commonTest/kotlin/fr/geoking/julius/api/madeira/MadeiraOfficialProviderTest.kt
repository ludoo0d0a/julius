package fr.geoking.julius.api.madeira

import fr.geoking.julius.api.overpass.OverpassClient
import fr.geoking.julius.api.overpass.OverpassElement
import fr.geoking.julius.poi.FuelPrice
import fr.geoking.julius.shared.network.NetworkException
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MadeiraOfficialProviderTest {

    private val mockHtml = """
        ...
        13.01.2025 a 19.01.2025
        Gasolina IO95
        1,589€
        Gasóleo Rodoviário
        1,343€
        ...
    """.trimIndent()

    @Test
    fun testMadeiraFuelPricesClient_Parsing() = runBlocking {
        val mockEngine = MockEngine { _ ->
            respond(
                content = mockHtml,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Text.Html.toString())
            )
        }
        val client = MadeiraFuelPricesClient(HttpClient(mockEngine))
        val prices = client.getFuelPrices()

        assertEquals(2, prices.size)
        assertEquals(1.589, prices.find { it.fuelName == "SP95" }?.price)
        assertEquals(1.343, prices.find { it.fuelName == "Gazole" }?.price)
        assertTrue(prices.all { it.updatedAt == "13.01.2025 to 19.01.2025" })
    }
}
