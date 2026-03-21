package fr.geoking.julius.ui

import fr.geoking.julius.R
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class BrandHelperTest {

    @Test
    fun testTeslaMatching() {
        val info = BrandHelper.getBrandInfo("Tesla Supercharger")
        assertNotNull(info)
        assertEquals("Tesla", info.displayName)
        assertEquals(R.drawable.ic_brand_tesla, info.iconResId)
    }

    @Test
    fun testIonityMatching() {
        val info = BrandHelper.getBrandInfo("IONITY Paris")
        assertNotNull(info)
        assertEquals("Ionity", info.displayName)
    }

    @Test
    fun testLidlMatching() {
        val info = BrandHelper.getBrandInfo("Lidl Charging")
        assertNotNull(info)
        assertEquals("Lidl", info.displayName)
    }

    @Test
    fun testChargyMatching() {
        val info = BrandHelper.getBrandInfo("Chargy Ok")
        assertNotNull(info)
        assertEquals("Chargy", info.displayName)
    }

    @Test
    fun testUnknownBrandReturnsNull() {
        val info = BrandHelper.getBrandInfo("Some Unknown Brand")
        assertNull(info)
    }
}
