package fr.geoking.julius.shared

/**
 * Resolves a place (or current device position) and fetches live weather for [ActionType.GET_WEATHER].
 * Platform code loads map/network deps and calls regional [fr.geoking.julius.api.weather.WeatherProvider]s.
 */
fun interface WeatherLookup {
    /**
     * @param locationQuery place name to geocode, or null/blank for the user's current coordinates.
     */
    suspend fun getCurrentWeather(locationQuery: String?): ActionResult
}
