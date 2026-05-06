package fr.geoking.julius.poi

/**
 * Generic fuel price model reused across providers.
 */
data class FuelPrice(
    val fuelName: String,
    val price: Double,
    val updatedAt: String? = null
)

