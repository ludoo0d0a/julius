package fr.geoking.julius.providers

/** Source of gas station data shown on the map. */
enum class PoiProviderType {
    Routex,   // Wigeogis SiteFinder
    Etalab,   // data.economie.gouv.fr / donnees.roulez-eco.fr
    GasApi,  //  gas-api.ovh
    DataGouv, // data.gouv.fr (fuel)
    DataGouvElec // data.gouv.fr IRVE (EV charging)
}

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
    /** When provided by the provider (e.g. DataGouv), lists fuel types and prices. */
    val fuelPrices: List<FuelPrice>? = null,
    /** Site name (e.g. Routex site_name) for title. */
    val siteName: String? = null,
    val postcode: String? = null,
    val addressLocal: String? = null,
    val countryLocal: String? = null,
    val townLocal: String? = null,
    /** Routex-only: amenities and opening hours for fullscreen details. */
    val routexDetails: RoutexSiteDetails? = null
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
