package fr.geoking.julius.api.geocoding

data class GeocodedPlace(
    val label: String,
    val latitude: Double,
    val longitude: Double
)

interface GeocodingClient {
    suspend fun geocode(query: String, limit: Int = 1): List<GeocodedPlace>
}

