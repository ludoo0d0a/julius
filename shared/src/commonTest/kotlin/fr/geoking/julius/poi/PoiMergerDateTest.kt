package fr.geoking.julius.poi

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class PoiMergerDateTest {

    @Test
    fun mergePois_preservesMostRecentDate_evenIfPriceIsFromAnotherSource() {
        val lat = 49.6116
        val lon = 6.1319

        // Source 1: ANWB (Station-specific price, but NO date in the current implementation)
        val pANWB = Poi(
            id = "anwb:123",
            name = "Total Luxembourg",
            address = "Route d'Esch",
            latitude = lat,
            longitude = lon,
            fuelPrices = listOf(
                FuelPrice(fuelName = "Gazole", price = 1.50, updatedAt = null, isReference = false)
            ),
            source = "ANWB",
            updatedAt = null
        )

        // Source 2: OpenVanCamp (Reference price, WITH recent date)
        val pOpenVan = Poi(
            id = "osm:fuel:456",
            name = "Total",
            address = "Route d'Esch",
            latitude = lat + 0.0001,
            longitude = lon + 0.0001,
            fuelPrices = listOf(
                FuelPrice(fuelName = "Gazole", price = 1.55, updatedAt = "2023-11-20T10:00:00Z", isReference = true)
            ),
            source = "OpenStreetMap + OpenVan.camp",
            updatedAt = "2023-11-20T10:00:00Z"
        )

        val merged = PoiMerger.mergePois(listOf(pANWB, pOpenVan))
        assertEquals(1, merged.size)
        val mergedPoi = merged[0]
        val mergedGazole = mergedPoi.fuelPrices?.find { it.fuelName == "Gazole" }

        assertNotNull(mergedGazole)
        assertEquals(1.50, mergedGazole.price, "Should prefer station-specific price")

        // This is the expected fix: even if we pick the price from ANWB, we should keep the date from OpenVanCamp if it's more recent
        assertEquals("2023-11-20T10:00:00Z", mergedGazole.updatedAt, "Should preserve the most recent update date for fuel price")
        assertEquals("2023-11-20T10:00:00Z", mergedPoi.updatedAt, "Should preserve the most recent update date for POI")
    }
}
