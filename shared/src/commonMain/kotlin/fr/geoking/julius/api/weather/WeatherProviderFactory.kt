package fr.geoking.julius.api.weather

/**
 * Picks a [WeatherProvider].
 *
 * Vehicle/POI-related routing special-casing was removed; keep this simple and return the first provider.
 */
class WeatherProviderFactory(
    private val providers: List<Pair<fr.geoking.julius.api.traffic.GeographicRegion, WeatherProvider>>
) {
    fun getProvider(latitude: Double, longitude: Double): WeatherProvider? {
        return providers.firstOrNull { (region, _) -> region.contains(latitude, longitude) }?.second
    }

    fun getProvidersForRoute(routePoints: List<Pair<Double, Double>>): List<WeatherProvider> {
        return routePoints.mapNotNull { (lat, lon) -> getProvider(lat, lon) }.distinct()
    }
}
