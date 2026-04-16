package fr.geoking.julius.poi

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PoiMergerTest {

    @Test
    fun mergePois_mergesClosePois() {
        // Paris coordinates
        val lat = 48.8566
        val lon = 2.3522

        // 0.0001 lat/lon is roughly 11 meters
        val p1 = Poi("1", "Station A", "Address 1", lat, lon, brand = "Generic")
        val p2 = Poi("2", "Station A", "Address 1", lat + 0.0002, lon + 0.0002, brand = "Generic") // ~30m away

        val merged = PoiMerger.mergePois(listOf(p1, p2))
        assertEquals(1, merged.size, "Should merge two close POIs with similar names")
    }

    @Test
    fun mergePois_respects100mThreshold() {
        val lat = 48.8566
        val lon = 2.3522

        // 0.001 degrees latitude is ~111m
        val p1 = Poi("1", "Station A", "Address 1", lat, lon, brand = "Generic")
        val p2 = Poi("2", "Station A", "Address 1", lat + 0.001, lon, brand = "Generic") // ~111m away

        val merged = PoiMerger.mergePois(listOf(p1, p2))
        assertEquals(2, merged.size, "Should NOT merge POIs more than 100m away")
    }

    @Test
    fun mergePois_prioritizesBrandWithIcon() {
        val lat = 48.8566
        val lon = 2.3522

        // "Total" has an icon, "GenericBrand" does not
        val p1 = Poi("1", "Total Paris", "Address 1", lat, lon, brand = "GenericBrand")
        val p2 = Poi("2", "Total Paris", "Address 1", lat + 0.0001, lon, brand = "Total")

        val merged = PoiMerger.mergePois(listOf(p1, p2))
        assertEquals(1, merged.size)
        assertEquals("Total", merged[0].brand, "Should pick the brand with an icon")

        // Reverse order
        val merged2 = PoiMerger.mergePois(listOf(p2, p1))
        assertEquals(1, merged2.size)
        assertEquals("Total", merged2[0].brand, "Should pick the brand with an icon regardless of order")
    }

    @Test
    fun mergePois_picksLatestPrice() {
        val lat = 48.8566
        val lon = 2.3522

        val p1 = Poi("1", "Station", "Address", lat, lon, fuelPrices = listOf(
            FuelPrice("Gazole", 1.50, updatedAt = "2023-10-01T10:00:00Z")
        ))
        val p2 = Poi("2", "Station", "Address", lat + 0.0001, lon, fuelPrices = listOf(
            FuelPrice("Gazole", 1.60, updatedAt = "2023-10-01T12:00:00Z")
        ))

        val merged = PoiMerger.mergePois(listOf(p1, p2))
        assertEquals(1, merged.size)
        assertEquals(1.60, merged[0].fuelPrices?.first()?.price, "Should pick the latest price")

        // Reverse order
        val merged2 = PoiMerger.mergePois(listOf(p2, p1))
        assertEquals(1, merged2.size)
        assertEquals(1.60, merged2[0].fuelPrices?.first()?.price, "Should pick the latest price regardless of order")
    }

    @Test
    fun mergePois_picksPriceWithTimestampOverNull() {
        val lat = 48.8566
        val lon = 2.3522

        val p1 = Poi("1", "Station", "Address", lat, lon, fuelPrices = listOf(
            FuelPrice("Gazole", 1.50, updatedAt = null)
        ))
        val p2 = Poi("2", "Station", "Address", lat + 0.0001, lon, fuelPrices = listOf(
            FuelPrice("Gazole", 1.60, updatedAt = "2023-10-01T12:00:00Z")
        ))

        val merged = PoiMerger.mergePois(listOf(p1, p2))
        assertEquals(1, merged.size)
        assertEquals(1.60, merged[0].fuelPrices?.first()?.price, "Should pick price with timestamp over null")
    }

    @Test
    fun mergePois_saintJulienLesMetz_mergesDataGouvAndGasApi() {
        // Relais des 4 chemins - Total Access in Saint-Julien-lès-Metz
        val lat = 49.1332
        val lon = 6.2001

        val pDataGouv = Poi(
            id = "57070001",
            name = "relais des 4 chemins  total express",
            address = "rue de l'abattoir",
            latitude = lat,
            longitude = lon,
            brand = "TotalEnergies",
            townLocal = "Saint-Julien-lès-Metz",
            fuelPrices = listOf(FuelPrice("Gazole", 1.85)),
            source = "DataGouv"
        )

        val pGasApi = Poi(
            id = "gas_57070001",
            name = "TOTAL ACCESS",
            address = "Rue de l'Abattoir",
            latitude = lat + 0.0005, // ~55m away
            longitude = lon + 0.0005,
            brand = "Total",
            fuelPrices = listOf(FuelPrice("Gazole", 1.86)),
            source = "GasAPI"
        )

        val merged = PoiMerger.mergePois(listOf(pDataGouv, pGasApi))
        assertEquals(1, merged.size, "Should merge the two sources for the same station")
        assertTrue(merged[0].source?.contains("DataGouv") == true)
        assertTrue(merged[0].source?.contains("GasAPI") == true)
    }
}
