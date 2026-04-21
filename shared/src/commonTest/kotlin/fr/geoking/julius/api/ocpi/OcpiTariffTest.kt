package fr.geoking.julius.api.ocpi

import io.ktor.util.date.GMTDate
import io.ktor.util.date.Month
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class OcpiTariffTest {

    @Test
    fun testSimpleTariffFormatting() {
        val tariff = OcpiTariff(
            id = "T1",
            currency = "EUR",
            elements = listOf(
                OcpiTariffElement(
                    price_components = listOf(
                        OcpiPriceComponent(type = OcpiPriceComponentType.ENERGY, price = 0.45, step_size = 1)
                    )
                )
            ),
            last_updated = "2024-03-20T10:00:00Z"
        )

        val formatted = OcpiTariffParser.formatTariff(tariff)
        assertEquals("0.45€ / kWh", formatted)
    }

    @Test
    fun testComplexTariffFormatting() {
        val tariff = OcpiTariff(
            id = "T2",
            currency = "EUR",
            elements = listOf(
                OcpiTariffElement(
                    price_components = listOf(
                        OcpiPriceComponent(type = OcpiPriceComponentType.FLAT, price = 1.0, step_size = 1),
                        OcpiPriceComponent(type = OcpiPriceComponentType.ENERGY, price = 0.50, step_size = 1),
                        OcpiPriceComponent(type = OcpiPriceComponentType.TIME, price = 2.0, step_size = 1)
                    )
                )
            ),
            last_updated = "2024-03-20T10:00:00Z"
        )

        val formatted = OcpiTariffParser.formatTariff(tariff)
        assertEquals("1€ / session + 0.5€ / kWh + 2€ / h", formatted)
    }

    @Test
    fun testTariffSelectionWithRestrictions() {
        // First element has a high price but requires min_kwh = 100
        // Second element is default and cheaper
        val tariff = OcpiTariff(
            id = "T3",
            currency = "EUR",
            elements = listOf(
                OcpiTariffElement(
                    price_components = listOf(
                        OcpiPriceComponent(type = OcpiPriceComponentType.ENERGY, price = 0.80, step_size = 1)
                    ),
                    restrictions = OcpiTariffRestrictions(min_kwh = 100.0)
                ),
                OcpiTariffElement(
                    price_components = listOf(
                        OcpiPriceComponent(type = OcpiPriceComponentType.ENERGY, price = 0.40, step_size = 1)
                    )
                )
            ),
            last_updated = "2024-03-20T10:00:00Z"
        )

        val components = OcpiTariffParser.getActiveComponents(tariff)
        assertEquals(0.40, components[OcpiPriceComponentType.ENERGY]?.price)

        val formatted = OcpiTariffParser.formatTariff(tariff)
        assertEquals("0.4€ / kWh", formatted)
    }

    @Test
    fun testMultipleDimensionsFirstMatch() {
        val tariff = OcpiTariff(
            id = "T4",
            currency = "EUR",
            elements = listOf(
                OcpiTariffElement(
                    price_components = listOf(
                        OcpiPriceComponent(type = OcpiPriceComponentType.FLAT, price = 1.50, step_size = 1)
                    )
                ),
                OcpiTariffElement(
                    price_components = listOf(
                        OcpiPriceComponent(type = OcpiPriceComponentType.ENERGY, price = 0.35, step_size = 1)
                    )
                )
            ),
            last_updated = "2024-03-20T10:00:00Z"
        )

        val components = OcpiTariffParser.getActiveComponents(tariff)
        assertEquals(1.50, components[OcpiPriceComponentType.FLAT]?.price)
        assertEquals(0.35, components[OcpiPriceComponentType.ENERGY]?.price)

        val formatted = OcpiTariffParser.formatTariff(tariff)
        assertEquals("1.5€ / session + 0.35€ / kWh", formatted)
    }

    @Test
    fun testTimeRestrictions() {
        val restrictions = OcpiTariffRestrictions(start_time = "08:00", end_time = "20:00")

        // 10:00 is within
        val morning = GMTDate(seconds = 0, minutes = 0, hours = 10, dayOfMonth = 1, month = Month.JANUARY, year = 2024)
        assertTrue(OcpiTariffParser.matchesRestrictions(restrictions, morning))

        // 22:00 is outside
        val night = GMTDate(seconds = 0, minutes = 0, hours = 22, dayOfMonth = 1, month = Month.JANUARY, year = 2024)
        assertFalse(OcpiTariffParser.matchesRestrictions(restrictions, night))
    }

    @Test
    fun testDayOfWeekRestrictions() {
        val restrictions = OcpiTariffRestrictions(day_of_week = listOf("MONDAY", "WEDNESDAY"))

        // Monday, 1st Jan 2024 is a Monday
        val monday = GMTDate(seconds = 0, minutes = 0, hours = 10, dayOfMonth = 1, month = Month.JANUARY, year = 2024)
        assertTrue(OcpiTariffParser.matchesRestrictions(restrictions, monday))

        // Tuesday, 2nd Jan 2024
        val tuesday = GMTDate(seconds = 0, minutes = 0, hours = 10, dayOfMonth = 2, month = Month.JANUARY, year = 2024)
        assertFalse(OcpiTariffParser.matchesRestrictions(restrictions, tuesday))
    }
}
