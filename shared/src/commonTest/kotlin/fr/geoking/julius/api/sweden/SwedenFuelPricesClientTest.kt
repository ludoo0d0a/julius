package fr.geoking.julius.api.sweden

import fr.geoking.julius.agents.createTestHttpClientEngine
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SwedenFuelPricesClientTest {

    @Test
    fun testParseFuelPrices() = runBlocking {
        val mockHtml = """
            <h3>Aktuella listpriser företagskund</h3>
            <table class="cols-6">
                <tbody>
                    <tr>
                        <td headers="view-field-product-label-table-column" class="views-field views-field-field-product-label"><span class="uk-hidden@m">Produktnamn:</span> miles 95 </td>
                        <td headers="view-price-gross-table-column" class="views-field views-field-price-gross"><span class="uk-hidden@m">Pris:</span> 18,74 </td>
                        <td headers="view-field-price-date-table-column" class="views-field views-field-field-price-date"><span class="uk-hidden@m">Ändringsdatum:</span> <time datetime="2026-04-10T12:00:00Z">2026-04-10</time></td>
                    </tr>
                    <tr>
                        <td headers="view-field-product-label-table-column" class="views-field views-field-field-product-label"><span class="uk-hidden@m">Produktnamn:</span> miles diesel </td>
                        <td headers="view-price-gross-table-column" class="views-field views-field-price-gross"><span class="uk-hidden@m">Pris:</span> 22,84 </td>
                        <td headers="view-field-price-date-table-column" class="views-field views-field-field-price-date"><span class="uk-hidden@m">Ändringsdatum:</span> <time datetime="2026-04-13T12:00:00Z">2026-04-13</time></td>
                    </tr>
                </tbody>
            </table>
        """.trimIndent()

        val engine = MockEngine { _ ->
            respond(
                content = mockHtml,
                status = HttpStatusCode.OK,
                headers = headersOf("Content-Type", "text/html")
            )
        }
        val client = HttpClient(engine)
        val swedenClient = SwedenFuelPricesClient(client)

        val prices = swedenClient.getFuelPrices()

        assertEquals(2, prices.size, "Should have parsed 2 fuel prices")

        val sp95 = prices.find { it.fuelName == "SP95 E10" }
        assertTrue(sp95 != null, "SP95 E10 not found")
        assertEquals(18.74, sp95.price)
        assertEquals("2026-04-10", sp95.updatedAt)

        val diesel = prices.find { it.fuelName == "Gazole" }
        assertTrue(diesel != null, "Gazole not found")
        assertEquals(22.84, diesel.price)
        assertEquals("2026-04-13", diesel.updatedAt)
    }

    @Test
    fun testSwedenFuelPricesRealApi() = runBlocking {
        val client = HttpClient(createTestHttpClientEngine())
        try {
            val swedenClient = SwedenFuelPricesClient(client)
            val prices = swedenClient.getFuelPrices()
            println("Sweden Fuel Prices returned ${prices.size} items")
            assertTrue(prices.isNotEmpty(), "Sweden client should return some fuel prices")

            val names = prices.map { it.fuelName }
            assertTrue(names.contains("SP95 E10"), "Should contain SP95 E10. Found: $names")
            assertTrue(names.contains("Gazole"), "Should contain Gazole. Found: $names")

            prices.forEach {
                assertTrue(it.price > 10.0, "Price ${it.price} for ${it.fuelName} seems too low")
                assertTrue(it.price < 50.0, "Price ${it.price} for ${it.fuelName} seems too high")
                assertTrue(it.updatedAt?.isNotEmpty() == true, "UpdatedAt should be present for ${it.fuelName}")
            }
        } finally {
            client.close()
        }
    }
}
