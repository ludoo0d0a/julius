package fr.geoking.julius.poi

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PoiMergerMadeiraTest {

    @Test
    fun mergePois_mergesWithin200m() {
        val lat = 32.6500
        val lon = -16.9080

        // 0.0013 degrees latitude is ~144m
        val p1 = Poi("osm:1", "Galp", "Addr 1", lat, lon, brand = "Galp", source = "OpenStreetMap")
        val p2 = Poi("fuelo:1", "Galp Funchal", "Addr 2", lat + 0.0013, lon, brand = "Galp", source = "Fuelo.net")

        val merged = PoiMerger.mergePois(listOf(p1, p2))
        assertEquals(1, merged.size, "Should merge two stations within 200m regardless of brand match")
    }

    @Test
    fun mergePois_mergesSameBrandWithin500m() {
        val lat = 32.6500
        val lon = -16.9080

        // 0.0035 degrees latitude is ~388m
        val p1 = Poi("osm:1", "Galp Station", "Addr 1", lat, lon, brand = "Galp", source = "OpenStreetMap")
        val p2 = Poi("fuelo:1", "Galp", "Addr 2", lat + 0.0035, lon, brand = "GALP", source = "Fuelo.net")

        val merged = PoiMerger.mergePois(listOf(p1, p2))
        assertEquals(1, merged.size, "Should merge same brand stations up to 500m")
        assertTrue(merged[0].source?.contains("OpenStreetMap") == true)
        assertTrue(merged[0].source?.contains("Fuelo.net") == true)
    }

    @Test
    fun mergePois_doesNotMergeGenericNamesAtLargeDistance() {
        val lat = 32.6500
        val lon = -16.9080

        // 0.003 degrees is ~333m
        val p1 = Poi("osm:1", "Gas station", "Addr 1", lat, lon, brand = "Galp", source = "OpenStreetMap")
        val p2 = Poi("fuelo:1", "Repsol Station", "Addr 2", lat + 0.003, lon, brand = "Repsol", source = "Fuelo.net")

        val merged = PoiMerger.mergePois(listOf(p1, p2))
        assertEquals(2, merged.size, "Should NOT merge different brand stations with generic 'station' in name at 300m")
    }

    @Test
    fun mergePois_doesNotMergeDifferentBrandsAtLargeDistance() {
        val lat = 32.6500
        val lon = -16.9080

        // 0.003 degrees is ~333m
        val p1 = Poi("osm:1", "Galp", "Addr 1", lat, lon, brand = "Galp", source = "OpenStreetMap")
        val p2 = Poi("fuelo:1", "Repsol", "Addr 2", lat + 0.003, lon, brand = "Repsol", source = "Fuelo.net")

        val merged = PoiMerger.mergePois(listOf(p1, p2))
        assertEquals(2, merged.size, "Should NOT merge different brands at 300m")
    }

    @Test
    fun mergePois_mergesSimilarBrandsWithin500m() {
         val lat = 32.6500
         val lon = -16.9080

         // ~388m
         // 'TotalEnergies' contains 'Total'
         val p1 = Poi("osm:1", "Total", "Addr 1", lat, lon, brand = "Total", source = "OpenStreetMap")
         val p2 = Poi("fuelo:1", "TotalEnergies", "Addr 2", lat + 0.0035, lon, brand = "TotalEnergies", source = "Fuelo.net")

         val merged = PoiMerger.mergePois(listOf(p1, p2))
         assertEquals(1, merged.size, "Should merge similar brands (Total vs TotalEnergies) up to 500m")
    }
}
