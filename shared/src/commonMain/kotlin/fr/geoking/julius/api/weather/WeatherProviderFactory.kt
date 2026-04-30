package fr.geoking.julius.api.weather

/**
 * Picks a [WeatherProvider].
 *
 * Vehicle/POI-related routing special-casing was removed; keep this simple and return the first provider.
 */
class WeatherProviderFactory(
    private val providers: List<WeatherProvider>
) {
    fun getProvider(latitude: Double, longitude: Double): WeatherProvider? =
        providers.firstOrNull()

    fun getProvidersForRoute(routePoints: List<Pair<Double, Double>>): List<WeatherProvider> {
        return providers
    }
}
