package fr.geoking.julius.api.ocpi

import fr.geoking.julius.shared.network.NetworkException
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json

/**
 * Generic OCPI client for fetching locations and status.
 * Supports Token-based authentication as required by OCPI 2.2.1.
 */
class OcpiClient(
    private val client: HttpClient,
    private val baseUrl: String,
    private val token: String
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /**
     * Fetches locations from the OCPI /locations endpoint.
     * Optional bounding box or radius filtering depends on CPO implementation.
     */
    suspend fun getLocations(
        latitude: Double? = null,
        longitude: Double? = null,
        radiusKm: Int? = null
    ): List<OcpiLocation> {
        if (baseUrl.isBlank()) return emptyList()

        // OCPI /locations typically supports date_from, date_to for delta.
        // Some implementations might support lat/lon/dist as query params (extension).
        val url = if (latitude != null && longitude != null && radiusKm != null) {
            // Note: This is an example, actual OCPI 2.2.1 /locations is a list.
            // Filtering is often done via date or by fetching the whole set.
            "$baseUrl/locations"
        } else {
            "$baseUrl/locations"
        }

        val response = client.get(url) {
            header("Authorization", "Token $token")
        }

        if (response.status.value != 200) {
            throw NetworkException(response.status.value, "OCPI Error: ${response.bodyAsText()}")
        }

        val ocpiResponse: OcpiResponse<List<OcpiLocation>> = response.body()
        return ocpiResponse.data ?: emptyList()
    }
}
