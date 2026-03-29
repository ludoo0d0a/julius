package fr.geoking.julius.ui.map

import fr.geoking.julius.poi.FuelPrice
import fr.geoking.julius.poi.IrveDetails
import fr.geoking.julius.poi.Poi
import fr.geoking.julius.poi.PoiCategory

/**
 * Shared mock POIs for map marker and [PoiDetailCard] previews: mixed brands, fuels, IRVE power bands,
 * tariff, stock, and motorway — aligned with [fr.geoking.julius.ui.BrandHelper] keys.
 */
object PreviewPoiSamples {

    /** Same buckets as [fr.geoking.julius.ui.MAP_IRVE_POWER_OPTIONS] — all selected for marker previews. */
    val previewAllIrvePowerLevels: Set<Int> = setOf(0, 20, 50, 100, 200, 300)

    /**
     * Map-canvas preview: row 1 (higher lat) — six IRVE stations (11, 35, 75, 150, 250, 350 kW) for
     * every [ColorHelper.getPowerColor] tier; row 2 — four fuel brands.
     * Pass [previewAllIrvePowerLevels] as [effectivePowerLevels] so IRVE pills show power-range text like the map filter.
     */
    fun diverseMapPois(): List<Poi> = listOf(
        // —— Electric row (north / higher lat) — one sample per power colour tier ——
        Poi(
            id = "pv-ev-11",
            name = "11 kW",
            address = "Preview",
            latitude = 48.86800,
            longitude = 2.35170,
            brand = null,
            isElectric = true,
            poiCategory = PoiCategory.Irve,
            powerKw = 11.0,
            operator = "Preview",
            chargePointCount = 1,
            irveDetails = IrveDetails(connectorTypes = setOf("type_2"), tarification = "Gratuit", gratuit = true),
            source = "DataGouv"
        ),
        Poi(
            id = "pv-ev-35",
            name = "35 kW",
            address = "Preview",
            latitude = 48.86800,
            longitude = 2.35226,
            brand = "Tesla",
            isElectric = true,
            poiCategory = PoiCategory.Irve,
            powerKw = 35.0,
            operator = "Tesla",
            chargePointCount = 4,
            irveDetails = IrveDetails(connectorTypes = setOf("combo_ccs"), tarification = "Payant", gratuit = false),
            source = "DataGouv"
        ),
        Poi(
            id = "pv-ev-75",
            name = "75 kW",
            address = "Preview",
            latitude = 48.86800,
            longitude = 2.35282,
            brand = "Allego",
            isElectric = true,
            poiCategory = PoiCategory.Irve,
            powerKw = 75.0,
            operator = "Allego",
            chargePointCount = 2,
            irveDetails = IrveDetails(connectorTypes = setOf("combo_ccs"), tarification = "Payant", gratuit = false),
            source = "DataGouv"
        ),
        Poi(
            id = "pv-ev-150",
            name = "150 kW",
            address = "Preview",
            latitude = 48.86800,
            longitude = 2.35338,
            brand = "Lidl",
            isElectric = true,
            poiCategory = PoiCategory.Irve,
            powerKw = 150.0,
            operator = "Lidl",
            chargePointCount = 2,
            irveDetails = IrveDetails(connectorTypes = setOf("combo_ccs"), tarification = "Payant", gratuit = false),
            source = "DataGouv"
        ),
        Poi(
            id = "pv-ev-250",
            name = "250 kW",
            address = "Preview",
            latitude = 48.86800,
            longitude = 2.35394,
            brand = "Fastned",
            isElectric = true,
            poiCategory = PoiCategory.Irve,
            powerKw = 250.0,
            operator = "Fastned",
            chargePointCount = 6,
            irveDetails = IrveDetails(connectorTypes = setOf("combo_ccs"), tarification = "Payant", gratuit = false),
            source = "DataGouv"
        ),
        Poi(
            id = "pv-ev-350",
            name = "350 kW",
            address = "Preview",
            latitude = 48.86800,
            longitude = 2.35450,
            brand = "Ionity",
            isElectric = true,
            poiCategory = PoiCategory.Irve,
            powerKw = 350.0,
            operator = "Ionity",
            isOnHighway = true,
            chargePointCount = 8,
            irveDetails = IrveDetails(connectorTypes = setOf("combo_ccs"), tarification = "Payant", gratuit = false),
            siteName = "Ionity",
            source = "DataGouv"
        ),
        // —— Fuel row (south / lower lat) ——
        Poi(
            id = "pv-total-gazole",
            name = "Total Access",
            address = "1 Av. de la République, 75011 Paris",
            latitude = 48.86670,
            longitude = 2.35210,
            brand = "Total",
            poiCategory = PoiCategory.Gas,
            fuelPrices = listOf(
                FuelPrice("Gazole", 1.769, "2025-03-28"),
                FuelPrice("SP95", 1.899, "2025-03-28"),
                FuelPrice("E10", 1.859, "2025-03-28")
            ),
            siteName = "Total Access République",
            postcode = "75011",
            addressLocal = "1 Av. de la République",
            townLocal = "Paris",
            countryLocal = "France",
            source = "DataGouv"
        ),
        Poi(
            id = "pv-shell-sp98",
            name = "Shell",
            address = "45 Bd Voltaire, 75011 Paris",
            latitude = 48.86670,
            longitude = 2.35300,
            brand = "Shell",
            poiCategory = PoiCategory.Gas,
            fuelPrices = listOf(
                FuelPrice("Gazole", 1.799),
                FuelPrice("SP95", 1.919),
                FuelPrice("SP98", 2.049)
            ),
            siteName = "Shell Voltaire",
            postcode = "75011",
            addressLocal = "45 Bd Voltaire",
            townLocal = "Paris",
            countryLocal = "France",
            source = "DataGouv"
        ),
        Poi(
            id = "pv-esso-rupture",
            name = "Esso Express",
            address = "102 Rue de la Roquette, 75011 Paris",
            latitude = 48.86670,
            longitude = 2.35390,
            brand = "Esso",
            poiCategory = PoiCategory.Gas,
            fuelPrices = listOf(
                FuelPrice("Gazole", 1.789),
                FuelPrice("SP95", 1.889, outOfStock = true),
                FuelPrice("E10", 1.849)
            ),
            siteName = "Esso Roquette",
            postcode = "75011",
            addressLocal = "102 Rue de la Roquette",
            townLocal = "Paris",
            countryLocal = "France",
            source = "DataGouv"
        ),
        Poi(
            id = "pv-bp-e85",
            name = "BP",
            address = "200 Rue de Bercy, 75012 Paris",
            latitude = 48.86670,
            longitude = 2.35480,
            brand = "BP",
            poiCategory = PoiCategory.Gas,
            fuelPrices = listOf(
                FuelPrice("Gazole", 1.779),
                FuelPrice("SP95", 1.879),
                FuelPrice("E85", 1.129)
            ),
            siteName = "BP Bercy",
            postcode = "75012",
            addressLocal = "200 Rue de Bercy",
            townLocal = "Paris",
            countryLocal = "France",
            source = "DataGouv"
        ),
    )

    /**
     * Six POIs for [PoiDetailCard] previews — merged [Poi.source] so expanded fuel/IRVE rows appear.
     */
    fun diverseCardPois(): List<Poi> = listOf(
        previewCardTotal(),
        previewCardShell(),
        previewCardIonity(),
        previewCardUrbanEv(),
        previewCardEssoRupture(),
        previewCardBpE85()
    )

    private fun previewCardTotal(): Poi = Poi(
        id = "card-total",
        name = "Station",
        address = "1 Av. de la République, 75011 Paris",
        latitude = 48.8676,
        longitude = 2.3635,
        brand = "Total",
        poiCategory = PoiCategory.Gas,
        fuelPrices = listOf(
            FuelPrice("Gazole", 1.769, "2025-03-28"),
            FuelPrice("SP95", 1.899, "2025-03-28"),
            FuelPrice("SP98", 2.019, "2025-03-28"),
            FuelPrice("E10", 1.859, "2025-03-28")
        ),
        siteName = "Total Access République",
        postcode = "75011",
        addressLocal = "1 Av. de la République",
        townLocal = "Paris",
        countryLocal = "France",
        source = "DataGouv + GasApi"
    )

    private fun previewCardShell(): Poi = Poi(
        id = "card-shell",
        name = "Station",
        address = "45 Bd Voltaire, 75011 Paris",
        latitude = 48.858,
        longitude = 2.37,
        brand = "Shell",
        poiCategory = PoiCategory.Gas,
        fuelPrices = listOf(
            FuelPrice("Gazole", 1.799),
            FuelPrice("SP95", 1.919),
            FuelPrice("SP98", 2.049)
        ),
        siteName = "Shell Voltaire",
        postcode = "75011",
        addressLocal = "45 Bd Voltaire",
        townLocal = "Paris",
        countryLocal = "France",
        source = "DataGouv + GasApi"
    )

    private fun previewCardIonity(): Poi = Poi(
        id = "card-ionity",
        name = "Ionity • 350 kW",
        address = "A1 Aire de Régny",
        latitude = 46.0,
        longitude = 4.0,
        brand = "Ionity",
        isElectric = true,
        poiCategory = PoiCategory.Irve,
        powerKw = 350.0,
        operator = "Ionity",
        isOnHighway = true,
        chargePointCount = 8,
        irveDetails = IrveDetails(
            connectorTypes = setOf("combo_ccs"),
            tarification = "Payant",
            gratuit = false
        ),
        siteName = "Ionity A1",
        postcode = "42370",
        addressLocal = "Aire de Régny",
        townLocal = "Régny",
        countryLocal = "France",
        source = "DataGouv + OpenChargeMap"
    )

    private fun previewCardUrbanEv(): Poi = Poi(
        id = "card-urban-ev",
        name = "Borne • 22 kW",
        address = "8 Rue du Faubourg Poissonnière, 75010 Paris",
        latitude = 48.87,
        longitude = 2.35,
        brand = null,
        isElectric = true,
        poiCategory = PoiCategory.Irve,
        powerKw = 22.0,
        operator = "Paris",
        isOnHighway = false,
        chargePointCount = 2,
        irveDetails = IrveDetails(
            connectorTypes = setOf("type_2"),
            tarification = "Gratuit",
            gratuit = true,
            openingHours = "24/7"
        ),
        siteName = "IRVE — Faubourg Poissonnière",
        postcode = "75010",
        addressLocal = "8 Rue du Faubourg Poissonnière",
        townLocal = "Paris",
        countryLocal = "France",
        source = "DataGouv + OpenChargeMap"
    )

    private fun previewCardEssoRupture(): Poi = Poi(
        id = "card-esso",
        name = "Station",
        address = "102 Rue de la Roquette, 75011 Paris",
        latitude = 48.855,
        longitude = 2.38,
        brand = "Esso",
        poiCategory = PoiCategory.Gas,
        fuelPrices = listOf(
            FuelPrice("Gazole", 1.789),
            FuelPrice("SP95", 1.889, outOfStock = true),
            FuelPrice("E10", 1.849)
        ),
        siteName = "Esso Roquette",
        postcode = "75011",
        addressLocal = "102 Rue de la Roquette",
        townLocal = "Paris",
        countryLocal = "France",
        source = "DataGouv + GasApi"
    )

    private fun previewCardBpE85(): Poi = Poi(
        id = "card-bp",
        name = "Station",
        address = "200 Rue de Bercy, 75012 Paris",
        latitude = 48.84,
        longitude = 2.38,
        brand = "BP",
        poiCategory = PoiCategory.Gas,
        fuelPrices = listOf(
            FuelPrice("Gazole", 1.779),
            FuelPrice("SP95", 1.879),
            FuelPrice("E85", 1.129)
        ),
        siteName = "BP Bercy",
        postcode = "75012",
        addressLocal = "200 Rue de Bercy",
        townLocal = "Paris",
        countryLocal = "France",
        source = "DataGouv + GasApi"
    )
}
