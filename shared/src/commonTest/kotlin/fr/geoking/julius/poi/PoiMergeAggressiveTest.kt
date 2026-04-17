package fr.geoking.julius.poi

import kotlin.test.Test
import kotlin.test.assertEquals

class PoiMergeAggressiveTest {

    @Test
    fun testDifferentBrandsShouldNotMergeEvenIfClose() {
        val lat = 48.8566
        val lon = 2.3522

        val p1 = Poi("1", "BP Paris", "Address 1", lat, lon, brand = "BP", source = "SourceA")
        val p2 = Poi("2", "Total Paris", "Address 1", lat + 0.0002, lon + 0.0002, brand = "Total", source = "SourceB") // ~30m

        val merged = PoiMerger.mergePois(listOf(p1, p2))
        assertEquals(2, merged.size, "Should NOT merge different brands even if they are close (< 100m)")
    }
}
