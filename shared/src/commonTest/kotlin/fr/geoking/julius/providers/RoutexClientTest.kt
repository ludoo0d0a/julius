package fr.geoking.julius.providers

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Unit tests for RoutexClient response parsing.
 * API returns items with keys: id, x (longitude), y (latitude), brand_id.
 */
class RoutexClientTest {

    @Test
    fun parseResults_rootArrayWithIdXyBrandId_parsesSites() {
        // Routex API format: id, x, y, brand_id (x=lng, y=lat)
        val body = """
            [
                {"id":"site-1","x":2.3522,"y":48.8566,"brand_id":"42"},
                {"id":"site-2","x":2.36,"y":48.86,"brand_id":"BP"}
            ]
        """.trimIndent()
        val sites = RoutexClient.parseResults(body)
        assertEquals(2, sites.size)

        val first = sites[0]
        assertEquals("site-1", first.id)
        assertEquals(48.8566, first.latitude)
        assertEquals(2.3522, first.longitude)
        assertEquals("42", first.brand)
        assertEquals("Gas station", first.name)

        val second = sites[1]
        assertEquals("site-2", second.id)
        assertEquals(48.86, second.latitude)
        assertEquals(2.36, second.longitude)
        assertEquals("BP", second.brand)
    }

    @Test
    fun parseResults_brandIdAsNumber_parsesAsString() {
        val body = """[{"id":"1","x":2.35,"y":48.85,"brand_id":123}]"""
        val sites = RoutexClient.parseResults(body)
        assertEquals(1, sites.size)
        assertEquals("123", sites[0].brand)
    }

    @Test
    fun parseResults_geoJsonFeatures_parsesSites() {
        val body = """
            {
                "features": [
                    {
                        "type": "Feature",
                        "geometry": {"type": "Point", "coordinates": [2.35, 48.85]},
                        "properties": {"name": "Station A", "address": "1 Rue X", "id": "geo-1"}
                    }
                ]
            }
        """.trimIndent()
        val sites = RoutexClient.parseResults(body)
        assertEquals(1, sites.size)
        assertEquals("geo-1", sites[0].id)
        assertEquals(48.85, sites[0].latitude)
        assertEquals(2.35, sites[0].longitude)
        assertEquals("Station A", sites[0].name)
        assertEquals("1 Rue X", sites[0].address)
    }

    @Test
    fun parseResults_emptyArray_returnsEmpty() {
        val sites = RoutexClient.parseResults("[]")
        assertTrue(sites.isEmpty())
    }

    @Test
    fun parseResults_objectWithResultsArray_parsesSites() {
        val body = """
            {"results": [{"id":"r1","x":1.0,"y":2.0,"brand_id":"B1"}]}
        """.trimIndent()
        val sites = RoutexClient.parseResults(body)
        assertEquals(1, sites.size)
        assertEquals("r1", sites[0].id)
        assertEquals(2.0, sites[0].latitude)
        assertEquals(1.0, sites[0].longitude)
    }

    @Test
    fun filterInBoundsAndLimit_keepsOnlySitesInBounds() {
        val sites = listOf(
            RoutexSite("1", "A", "", 48.0, 2.0, null),
            RoutexSite("2", "B", "", 49.0, 3.0, null),
            RoutexSite("3", "C", "", 50.0, 4.0, null) // outside
        )
        // Bounds: lng 1..3, lat 47.5..49.5
        val result = RoutexClient.filterInBoundsAndLimit(sites, 1.0, 47.5, 3.0, 49.5, 10)
        assertEquals(2, result.size)
        assertEquals(listOf("1", "2"), result.map { it.id })
    }

    @Test
    fun filterInBoundsAndLimit_respectsMaxPois() {
        val sites = List(50) { i ->
            RoutexSite("id-$i", "S", "", 48.0 + i * 0.001, 2.0, null)
        }
        val result = RoutexClient.filterInBoundsAndLimit(sites, 1.0, 47.0, 3.0, 49.0, ROUTEX_MAX_POIS)
        assertEquals(ROUTEX_MAX_POIS, result.size)
    }

    @Test
    fun routexMaxPoisConstant_is20() {
        assertEquals(20, ROUTEX_MAX_POIS)
    }
}
