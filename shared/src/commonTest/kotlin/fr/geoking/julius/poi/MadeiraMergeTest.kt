package fr.geoking.julius.poi

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MadeiraMergeTest {

    @Test
    fun testMadeiraFueloAndOsmMerge() {
        val lat = 32.6496
        val lon = -16.9033 // Funchal

        // Fuelo station with specific name and prices
        val pFuelo = Poi(
            id = "fuelo_pt_123",
            name = "Galp Funchal",
            address = "Rua da Carreira",
            latitude = lat,
            longitude = lon,
            brand = "Galp",
            poiCategory = PoiCategory.Gas,
            fuelPrices = listOf(FuelPrice("Gazole", 1.45, isReference = false)),
            source = "Fuelo.net (PT-MA)"
        )

        // OSM station with generic name and reference prices
        val pOsm = Poi(
            id = "osm:fuel:456",
            name = "Gas station",
            address = "Rua da Carreira",
            latitude = lat + 0.0002, // ~22m away
            longitude = lon + 0.0002,
            brand = "Galp",
            poiCategory = PoiCategory.Gas,
            fuelPrices = listOf(FuelPrice("Gazole", 1.50, isReference = true)),
            source = "OpenStreetMap + OpenVan.camp (PT-MA official price)"
        )

        val merged = PoiMerger.mergePois(listOf(pFuelo, pOsm))
        assertEquals(1, merged.size, "Should merge Fuelo and OSM stations")

        val result = merged[0]
        assertEquals("Galp Funchal", result.name, "Should prefer specific name over generic 'Gas station'")
        assertEquals(1.45, result.fuelPrices?.first()?.price, "Should prefer station-specific price")
        assertTrue(result.source?.contains("Fuelo.net") == true)
        assertTrue(result.source?.contains("OpenVan.camp") == true)
    }

    @Test
    fun testAlvesBandeiraNormalization() {
        val lat = 32.7222
        val lon = -17.1234

        val pFuelo = Poi(
            id = "fuelo_pt_789",
            name = "Alves Bandeira Ribeira Brava",
            address = "Ribeira Brava",
            latitude = lat,
            longitude = lon,
            brand = "Alves-bandeira", // Common logo format
            source = "Fuelo.net (PT-MA)"
        )

        val pOsm = Poi(
            id = "osm:fuel:1011",
            name = "Alves Bandeira",
            address = "Ribeira Brava",
            latitude = lat + 0.0012, // ~133m away, beyond 100m
            longitude = lon + 0.0012,
            brand = "Alves Bandeira",
            source = "OpenStreetMap"
        )

        val merged = PoiMerger.mergePois(listOf(pFuelo, pOsm))
        assertEquals(1, merged.size, "Should merge Alves Bandeira despite dash and distance > 100m")
    }
}
