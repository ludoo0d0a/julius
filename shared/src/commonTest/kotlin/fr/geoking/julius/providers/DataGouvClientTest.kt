package fr.geoking.julius.providers

import io.ktor.client.HttpClient
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class DataGouvClientTest {

    private val client = DataGouvClient(HttpClient())

    @Test
    fun parseRecords_withFields_parsesStation() {
        // ODS API v2.1 /records returns fields at top level (results is array of objects)
        val body = """
            {
                "results": [
                    {
                        "id": "1",
                        "adresse": "123 Rue de Paris",
                        "ville": "Paris",
                        "cp": "75001",
                        "nom": "Station Paris",
                        "marque": "Total",
                        "latitude": 48.8566,
                        "longitude": 2.3522,
                        "prix": [
                            {"nom": "Gazole", "valeur": 1800, "maj": "2023-10-27T12:00:00Z"}
                        ]
                    }
                ]
            }
        """.trimIndent()

        val stations = client.parseRecords(body)
        assertEquals(1, stations.size)
        val station = stations[0]
        assertEquals("1", station.id)
        assertEquals("Station Paris", station.name)
        assertEquals("123 Rue de Paris, 75001, Paris", station.address)
        assertEquals(48.8566, station.latitude)
        assertEquals(2.3522, station.longitude)
        assertEquals("Total", station.brand)
        assertEquals(1, station.prices.size)
        assertEquals("Gazole", station.prices[0].fuelName)
        assertEquals(1.8, station.prices[0].price)
    }

    @Test
    fun parseGeo_prefersGeom() {
        val json = Json { ignoreUnknownKeys = true }
        val record = json.parseToJsonElement("""
            {
                "geom": {
                    "type": "Point",
                    "coordinates": [2.3522, 48.8566]
                },
                "geolocation": {
                    "type": "Point",
                    "coordinates": [0.0, 0.0]
                }
            }
        """).jsonObject

        val coords = client.parseGeo(record)
        assertNotNull(coords)
        assertEquals(48.8566, coords.first)
        assertEquals(2.3522, coords.second)
    }
}
