package fr.geoking.julius.parking

/**
 * Selects which [ParkingProvider]s to use for a given [ParkingQueryContext].
 * Only providers that cover at least one of the context points are returned.
 */
class ParkingApiSelector(
    private val providers: List<ParkingProvider>
) {
    /**
     * Returns providers that cover at least one point in [context].
     * If context has no points, returns empty list (no API calls).
     */
    fun select(context: ParkingQueryContext): List<ParkingProvider> {
        val points = context.points()
        if (points.isEmpty()) return emptyList()
        return providers.filter { provider ->
            points.any { (lat, lon) -> provider.covers(lat, lon) }
        }
    }
}
