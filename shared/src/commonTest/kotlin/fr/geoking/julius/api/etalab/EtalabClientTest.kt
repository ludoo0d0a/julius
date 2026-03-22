package fr.geoking.julius.api.etalab

import io.ktor.client.HttpClient
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class EtalabClientTest {

    private val client = EtalabClient(HttpClient())

    @Test
    fun parseRecords_parsesStation() {
        val body = """
            {
                "results": [
                    {
                        "id": "1",
                        "adresse": "123 Rue de Paris",
                        "ville": "Paris",
                        "cp": "75001",
                        "nom": "Station Paris",
                        "latitude": 48.8566,
                        "longitude": 2.3522,
                        "prix": [
                            {"nom": "Gazole", "valeur": 1.8}
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
        assertEquals(48.8566, station.latitude)
        assertEquals(2.3522, station.longitude)
        assertEquals(1, station.fuels.size)
        assertEquals("Gazole", station.fuels[0].name)
        assertEquals(1.8, station.fuels[0].priceEur)
    }

    @Test
    fun parseRecords_handlesResultsAsSingleObject() {
        // API may return "results" as a single object instead of array
        val body = """
            {
                "results": {
                    "id": "2",
                    "adresse": "10 Route de Lyon",
                    "ville": "Lyon",
                    "cp": "69001",
                    "nom": "Station Lyon",
                    "latitude": 45.7640,
                    "longitude": 4.8357,
                    "prix": [
                        {"nom": "SP98", "valeur": 1.95}
                    ]
                }
            }
        """.trimIndent()

        val stations = client.parseRecords(body)
        assertEquals(1, stations.size)
        assertEquals("2", stations[0].id)
        assertEquals("Station Lyon", stations[0].name)
        assertEquals(1, stations[0].fuels.size)
        assertEquals("SP98", stations[0].fuels[0].name)
        assertEquals(1.95, stations[0].fuels[0].priceEur)
    }

    @Test
    fun parseRecords_scaledLatLon_andAtNomPrix() {
        val body = """
            {
                "results": [
                    {
                        "id": "93170002",
                        "adresse": "44 AVENUE",
                        "ville": "Bagnolet",
                        "cp": "93170",
                        "latitude": "4886205",
                        "longitude": "241650",
                        "pop": "R",
                        "prix": "[{\"@nom\": \"Gazole\", \"@valeur\": \"2.090\"}, {\"@nom\": \"E10\", \"@valeur\": \"1.990\"}]"
                    }
                ]
            }
        """.trimIndent()

        val stations = client.parseRecords(body)
        assertEquals(1, stations.size)
        val station = stations[0]
        assertEquals(48.86205, station.latitude, 1e-4)
        assertEquals(2.4165, station.longitude, 1e-4)
        assertEquals(2, station.fuels.size)
        assertEquals("Gazole", station.fuels[0].name)
        assertEquals(2.09, station.fuels[0].priceEur, 1e-6)
        assertEquals("E10", station.fuels[1].name)
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
