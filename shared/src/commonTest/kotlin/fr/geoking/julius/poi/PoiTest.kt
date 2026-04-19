package fr.geoking.julius.poi

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PoiTest {

    @Test
    fun sanitizeUserPoiProviderSelection_keepsOnlySelectableProviders() {
        // When all providers are enabled (POI_DATA_SOURCES_DISABLED_FOR_USER_SELECTION is empty)
        val inSet = setOf(PoiProviderType.Etalab, PoiProviderType.GasApi, PoiProviderType.Routex)
        assertEquals(
            setOf(PoiProviderType.Etalab, PoiProviderType.GasApi, PoiProviderType.Routex),
            inSet.sanitizeUserPoiProviderSelection()
        )
    }

    @Test
    fun testMockPoiProvider() = runBlocking {
        val provider = MockPoiProvider()
        val pois = provider.getGasStations(48.8566, 2.3522)

        assertEquals(5, pois.size, "Should return 5 mock gas stations")

        val brands = pois.map { it.brand }.toSet()
        assertTrue(brands.contains("BP"), "Should contain BP")
        assertTrue(brands.contains("Aral"), "Should contain Aral")
        assertTrue(brands.contains("Eni"), "Should contain Eni")
        assertTrue(brands.contains("Circle K"), "Should contain Circle K")
        assertTrue(brands.contains("OMV"), "Should contain OMV")

        val names = pois.map { it.name }
        assertTrue(names.any { it.contains("BP") }, "One name should contain BP")
    }

    @Test
    fun testFuelNameToId() {
        assertEquals("gazole", MapPoiFilter.fuelNameToId("Gazole"))
        assertEquals("gazole", MapPoiFilter.fuelNameToId("Diesel"))
        assertEquals("gazole", MapPoiFilter.fuelNameToId("Gasoil"))
        assertEquals("gazole_plus", MapPoiFilter.fuelNameToId("Gazole Premium"))
        assertEquals("gazole_plus", MapPoiFilter.fuelNameToId("Diesel Ultimate"))
        assertEquals("gazole_plus", MapPoiFilter.fuelNameToId("Gazole Excellium"))
        assertEquals("gazole_plus", MapPoiFilter.fuelNameToId("Diesel Supreme"))
        assertEquals("gazole_plus", MapPoiFilter.fuelNameToId("Diesel V-Power"))
        assertEquals("gazole_plus", MapPoiFilter.fuelNameToId("Gasóleo Especial"))
        assertEquals("gazole_plus", MapPoiFilter.fuelNameToId("Diesel Plus"))
        assertEquals("gazole_plus", MapPoiFilter.fuelNameToId("Gazole Extra"))
        assertEquals("gazole_plus", MapPoiFilter.fuelNameToId("Star Diesel"))

        assertEquals("sp95", MapPoiFilter.fuelNameToId("SP95"))
        assertEquals("sp95_e10", MapPoiFilter.fuelNameToId("Sans Plomb 95 E10"))
        assertEquals("sp98", MapPoiFilter.fuelNameToId("SP98"))
        assertEquals("e85", MapPoiFilter.fuelNameToId("E85"))
        assertEquals("gplc", MapPoiFilter.fuelNameToId("GPL"))
    }

    @Test
    fun testGetDisplayGroup() {
        assertEquals("🌍 International", PoiProviderType.Routex.getDisplayGroup())
        assertEquals("🇫🇷 France", PoiProviderType.Etalab.getDisplayGroup())
        assertEquals("🇫🇷 France", PoiProviderType.GasApi.getDisplayGroup())
        assertEquals("🌍 Global", PoiProviderType.OpenChargeMap.getDisplayGroup())
        assertEquals("🌍 Global", PoiProviderType.EcoMovement.getDisplayGroup())
        assertEquals("🇪🇺 Europe (Reference)", PoiProviderType.OpenVanCamp.getDisplayGroup())
        assertEquals("🌍 General", PoiProviderType.Overpass.getDisplayGroup())
        assertEquals("🇪🇸 Spain", PoiProviderType.SpainMinetur.getDisplayGroup())
        assertEquals("🇩🇪 Germany", PoiProviderType.GermanyTankerkoenig.getDisplayGroup())
        assertEquals("🇦🇹 Austria", PoiProviderType.AustriaEControl.getDisplayGroup())
        assertEquals("🇧🇪 Belgium", PoiProviderType.BelgiumOfficial.getDisplayGroup())
        assertEquals("🇵🇹 Portugal", PoiProviderType.PortugalDgeg.getDisplayGroup())
        assertEquals("🇬🇧 United Kingdom", PoiProviderType.UnitedKingdomCma.getDisplayGroup())
        assertEquals("🇮🇹 Italy", PoiProviderType.ItalyMimit.getDisplayGroup())
        assertEquals("🇪🇺 Multi-country", PoiProviderType.Fuelo.getDisplayGroup())
        assertEquals("🇪🇺 Multi-country", PoiProviderType.DrivstoffAppen.getDisplayGroup())
        assertEquals("🇦🇺 Australia", PoiProviderType.AustraliaFuel.getDisplayGroup())
        assertEquals("🇫🇷 France", PoiProviderType.Hybrid.getDisplayGroup())
    }
}
