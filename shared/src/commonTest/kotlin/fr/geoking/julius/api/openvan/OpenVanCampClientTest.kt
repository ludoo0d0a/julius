package fr.geoking.julius.api.openvan

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class OpenVanCampClientTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun decodeSample_includesLuxembourgPrices() {
        val body = """
            {
              "success": true,
              "data": {
                "LU": {
                  "country_code": "LU",
                  "country_name": "Luxembourg",
                  "currency": "EUR",
                  "prices": { "gasoline": 1.567, "diesel": 1.772, "lpg": 0.804, "e85": null, "premium": null },
                  "fetched_at": "2026-03-18T05:01:32+03:00",
                  "source": "EU Weekly Oil Bulletin"
                }
              },
              "meta": { "total_countries": 72 }
            }
        """.trimIndent()

        val root = json.decodeFromString<OpenVanFuelPricesResponse>(body)
        assertTrue(root.success)
        val lu = root.data?.get("LU")
        assertNotNull(lu)
        val fuels = lu.toFuelPrices()
        assertEquals(3, fuels.size)
        assertEquals("SP95 E10", fuels[0].fuelName)
        assertEquals(1.567, fuels[0].price)
        assertEquals("Gazole", fuels[1].fuelName)
        assertEquals(1.772, fuels[1].price)
        assertEquals("GPLc", fuels[2].fuelName)
        assertEquals(0.804, fuels[2].price)
    }
}
