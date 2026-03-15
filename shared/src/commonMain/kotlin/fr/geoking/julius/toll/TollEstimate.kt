package fr.geoking.julius.toll

/**
 * Result of highway toll estimation for a route.
 */
data class TollEstimate(
    val amountEur: Double,
    val currency: String = "EUR"
)
