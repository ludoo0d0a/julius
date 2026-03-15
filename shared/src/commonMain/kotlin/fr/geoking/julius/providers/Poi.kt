package fr.geoking.julius.providers

/** Source of gas station data shown on the map. */
enum class PoiProviderType {
    Routex,   // Wigeogis SiteFinder
    Etalab,   // data.economie.gouv.fr / donnees.roulez-eco.fr
    GasApi,  //  gas-api.ovh
    DataGouv, // data.gouv.fr (fuel)
    DataGouvElec, // data.gouv.fr IRVE (EV charging)
    OpenChargeMap // openchargemap.org (EV, Europe/world)
}

/**
 * IRVE-only details: connector types, tarification (free text), opening hours, payment, etc.
 * Used when [Poi.isElectric] and data comes from data.gouv.fr IRVE.
 */
data class IrveDetails(
    /** Connector type ids: "type_2", "combo_ccs", "chademo", "ef", "autre". */
    val connectorTypes: Set<String> = emptySet(),
    /** Free-text tarification; display as-is. */
    val tarification: String? = null,
    val gratuit: Boolean? = null,
    val openingHours: String? = null,
    val reservation: Boolean? = null,
    val paymentActe: Boolean? = null,
    val paymentCb: Boolean? = null,
    val paymentAutre: Boolean? = null,
    /** "Accès libre" / "Accès réservé". */
    val conditionAcces: String? = null
)

/**
 * Fuel type and price at a gas station (e.g. from data.gouv.fr / gas-api.ovh).
 */
data class FuelPrice(
    val fuelName: String,
    val price: Double,
    val updatedAt: String? = null,
    val outOfStock: Boolean = false
)

data class Poi(
    val id: String,
    val name: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val brand: String? = null,
    /** True for IRVE / EV charging stations (e.g. data.gouv.fr IRVE). */
    val isElectric: Boolean = false,
    /** Nominal power in kW (IRVE only). Used for min-power filter. */
    val powerKw: Double? = null,
    /** Operator name (IRVE only). Used for operator filter. */
    val operator: String? = null,
    /** True when station is on highway/autoroute (IRVE only). */
    val isOnHighway: Boolean = false,
    /** Number of charging points / points de charge (IRVE only). */
    val chargePointCount: Int? = null,
    /** When provided by the provider (e.g. DataGouv), lists fuel types and prices. */
    val fuelPrices: List<FuelPrice>? = null,
    /** Site name (e.g. Routex site_name) for title. */
    val siteName: String? = null,
    val postcode: String? = null,
    val addressLocal: String? = null,
    val countryLocal: String? = null,
    val townLocal: String? = null,
    /** Routex-only: amenities and opening hours for fullscreen details. */
    val routexDetails: RoutexSiteDetails? = null,
    /** IRVE-only: connector types, tarification, horaires, payment, etc. */
    val irveDetails: IrveDetails? = null
)

/**
 * Optional map viewport to scope the POI search to the visible area (e.g. for Routex API).
 * When provided, radius is derived from zoom and map size instead of a fixed default.
 */
data class MapViewport(
    val zoom: Float,
    val mapWidthPx: Int,
    val mapHeightPx: Int
)

/**
 * Maps API fuel names (from data.gouv.fr / Etalab / gas-api.ovh) to filter ids used in map settings.
 * Aligned with [prix-carburants.gouv.fr](https://www.prix-carburants.gouv.fr/) fuel list.
 */
object MapPoiFilter {
    /** Normalize API fuel name to a filter id (gazole, sp98, sp95, sp95_e10, gplc, e85). Returns null if unknown. */
    fun fuelNameToId(fuelName: String): String? {
        val n = fuelName.trim().lowercase()
        return when {
            n.contains("gazole") || n == "gasoil" || n == "diesel" -> "gazole"
            n.contains("sp98") || n == "sp 98" -> "sp98"
            n.contains("e10") || n.contains("sp95-e10") || n == "sp95 e10" -> "sp95_e10"
            n.contains("sp95") || n == "sp 95" -> "sp95"
            n.contains("gpl") || n == "gplc" || n == "lpg" -> "gplc"
            n.contains("e85") || n == "superéthanol" -> "e85"
            else -> null
        }
    }

    /**
     * Returns true if [poi] should be shown given [selectedEnergyIds].
     * When [selectedEnergyIds] is empty, returns true (show all).
     * Electric stations match when "electric" is selected; fuel stations match when they have at least one fuel in [selectedEnergyIds].
     */
    fun matchesEnergyFilter(poi: Poi, selectedEnergyIds: Set<String>): Boolean {
        if (selectedEnergyIds.isEmpty()) return true
        if (poi.isElectric) return "electric" in selectedEnergyIds
        val fuelIds = poi.fuelPrices?.mapNotNull { fuelNameToId(it.fuelName) }?.toSet() ?: emptySet()
        if (fuelIds.isEmpty()) return true // no price data: show anyway
        return fuelIds.any { it in selectedEnergyIds }
    }
}

interface PoiProvider {
    /**
     * Fetches gas stations near the given center.
     * When [viewport] is non-null, providers may use it to limit the search to the visible map
     * (e.g. Routex uses zoom + size to compute API radius from the visible diameter).
     */
    suspend fun getGasStations(
        latitude: Double,
        longitude: Double,
        viewport: MapViewport? = null
    ): List<Poi>
}

class MockPoiProvider : PoiProvider {
    override suspend fun getGasStations(
        latitude: Double,
        longitude: Double,
        viewport: MapViewport?
    ): List<Poi> {
        // Mock data around some common coordinates or relative to input
        return listOf(
            Poi("1", "BP Paris Sud", "123 Avenue du Maine, Paris", latitude + 0.01, longitude + 0.01, "BP"),
            Poi("2", "Aral Station", "45 Rue de Rivoli, Paris", latitude - 0.01, longitude + 0.02, "Aral"),
            Poi("3", "Eni Live", "88 Boulevard Haussmann, Paris", latitude + 0.02, longitude - 0.01, "Eni"),
            Poi("4", "Circle K", "10 Place de la Bastille, Paris", latitude - 0.02, longitude - 0.02, "Circle K"),
            Poi("5", "OMV Station", "22 Rue de la Paix, Paris", latitude + 0.005, longitude - 0.005, "OMV")
        )
    }
}
