package fr.geoking.julius.providers

/** Source of gas station data shown on the map. */
enum class PoiProviderType {
    Routex,   // Wigeogis SiteFinder
    Etalab,   // data.economie.gouv.fr / donnees.roulez-eco.fr
    DataGouv  // data.gouv.fr / gas-api.ovh
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
    val fuelPrices: List<FuelPrice>? = null
)

interface PoiProvider {
    suspend fun getGasStations(latitude: Double, longitude: Double): List<Poi>
}

class MockPoiProvider : PoiProvider {
    override suspend fun getGasStations(latitude: Double, longitude: Double): List<Poi> {
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
