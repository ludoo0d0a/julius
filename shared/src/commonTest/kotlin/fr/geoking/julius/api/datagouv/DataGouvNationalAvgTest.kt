package fr.geoking.julius.api.datagouv

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DataGouvNationalAvgTest {

    @Test
    fun getNationalAverages_parsesCorrectly() = runBlocking {
        val mockEngine = MockEngine { request ->
            respond(
                content = """
                    {
                        "total_count": 3,
                        "results": [
                            {
                                "y": 2024,
                                "m": 1,
                                "d": 2,
                                "prix_nom": "Gazole",
                                "avg_price": 1.75
                            },
                            {
                                "y": 2024,
                                "m": 1,
                                "d": 1,
                                "prix_nom": "Gazole",
                                "avg_price": 1.74
                            },
                            {
                                "y": 2024,
                                "m": 1,
                                "d": 2,
                                "prix_nom": "E10",
                                "avg_price": 1.85
                            }
                        ]
                    }
                """.trimIndent(),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val client = DataGouvPrixCarburantClient(HttpClient(mockEngine))
        val averages = client.getNationalAverages(30)

        assertEquals(2, averages.size)
        assertTrue(averages.containsKey("Gazole"))
        assertTrue(averages.containsKey("E10"))

        val gazole = averages["Gazole"]!!
        assertEquals(2, gazole.size)
        assertEquals("2024-01-01", gazole[0].day)
        assertEquals(1.74, gazole[0].avgPrice)
        assertEquals("2024-01-02", gazole[1].day)
        assertEquals(1.75, gazole[1].avgPrice)
    }

    @Test
    fun getNationalAverages_parsesLegacyCorrectly() = runBlocking {
        val mockEngine = MockEngine { request ->
            respond(
                content = """
                    {
                        "total_count": 1,
                        "results": [
                            {
                                "year(prix_maj)": "2024",
                                "month(prix_maj)": "01",
                                "day(prix_maj)": "02",
                                "prix_nom": "Gazole",
                                "avg(prix_valeur)": 1.75
                            }
                        ]
                    }
                """.trimIndent(),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val client = DataGouvPrixCarburantClient(HttpClient(mockEngine))
        val averages = client.getNationalAverages(30)

        assertEquals(1, averages.size)
        val gazole = averages["Gazole"]!!
        assertEquals(1, gazole.size)
        assertEquals("2024-01-02", gazole[0].day)
        assertEquals(1.75, gazole[0].avgPrice)
    }
}
