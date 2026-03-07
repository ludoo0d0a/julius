package fr.geoking.julius.providers

import fr.geoking.julius.shared.NetworkException
import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.cos

/**
 * Client for the Routex (Wigeogis) SiteFinder API.
 * Fetches gas station results from https://app.wigeogis.com/kunden/routex-sitefinder/backend/getResults
 */
class RoutexClient(
    private val client: HttpClient,
    private val baseUrl: String = "https://app.wigeogis.com/kunden/routex-sitefinder/backend"
) {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Request body for getResults (siteFinder section).
     * Coordinates in locations.loc are [latitude, longitude].
     */
    @Serializable
    data class RoutexRequest(
        val zoom: Int = 12,
        val bounds: String,
        val title: String = "",
        val section: String = "siteFinder",
        val type: String = "",
        val routeDistance: Int = 5,
        val routeDistanceMin: Int = 1,
        val routeDistanceMax: Int = 5,
        val routeDistanceStep: Int = 1,
        val routeDistanceUnit: String = "km",
        val routeDistanceOverflow: Boolean = false,
        val avoidanceDistanceThreshold: Int = 500,
        val routeWkt: String? = null,
        val siteFilter: String = "radius",
        val radius: Int = 5,
        val radiusMin: Int = 1,
        val radiusMax: Int = 50,
        val radiusStep: Int = 1,
        val radiusUnit: String = "km",
        val width: Double = 2.6,
        val widthMin: Int = 2,
        val widthMax: Double = 2.6,
        val widthStep: Double = 0.1,
        val widthUnit: String = "m",
        val height: Int = 4,
        val heightMin: Int = 2,
        val heightMax: Int = 4,
        val heightStep: Double = 0.1,
        val heightUnit: String = "m",
        val length: Double = 18.75,
        val lengthMin: Int = 6,
        val lengthMax: Double = 18.75,
        val lengthStep: Double = 0.25,
        val lengthUnit: String = "m",
        val avoidMotorways: Boolean = false,
        val avoidTolls: Boolean = false,
        val avoidFerries: Boolean = false,
        val truckRestrictions: Boolean = false,
        val routeFilterChange: Boolean = false,
        val routingMaxWaypoints: Int = 8,
        val locations: RoutexLocations,
        val filters: List<Nothing> = emptyList(),
        val lng: String = "en"
    )

    @Serializable
    data class RoutexLocations(
        val loc: RoutexLoc,
        val routingLocations: List<RoutexRoutingLoc> = defaultRoutingLocations
    )

    @Serializable
    data class RoutexLoc(
        val id: String = "loc",
        val coordinates: List<Double>,
        val address: String = "Your current location",
        val radius: Int = 5
    )

    @Serializable
    data class RoutexRoutingLoc(
        val id: String,
        val coordinates: List<Double>? = null,
        val address: String = "",
        val visible: Boolean = true
    )

    companion object {
        private val defaultRoutingLocations = (0..7).map { i ->
            RoutexRoutingLoc(
                id = "wp$i",
                coordinates = null,
                address = "",
                visible = i < 2
            )
        }
    }

    /**
     * Build bounds string from center (lat, lng) and radius in km.
     * Format: "minLng, minLat, maxLng, maxLat"
     */
    fun boundsFromCenter(lat: Double, lng: Double, radiusKm: Int): String {
        // Approximate: 1° lat ≈ 111 km, 1° lng ≈ 111 * cos(lat) km
        val latDelta = radiusKm / 111.0
        val lngDelta = radiusKm / (111.0 * cos(Math.toRadians(lat)).coerceIn(0.01, 1.0))
        val minLng = lng - lngDelta
        val maxLng = lng + lngDelta
        val minLat = lat - latDelta
        val maxLat = lat + latDelta
        return "$minLng, $minLat, $maxLng, $maxLat"
    }

    /**
     * Call getResults and return raw JSON string (for debugging) or parsed gas station list.
     * Response structure is not fully documented; we parse flexibly from either
     * a "results"/"sites" array or GeoJSON-style "features".
     */
    suspend fun getResults(latitude: Double, longitude: Double, radiusKm: Int = 5): List<RoutexSite> {
        val bounds = boundsFromCenter(latitude, longitude, radiusKm)
        val request = RoutexRequest(
            bounds = bounds,
            locations = RoutexLocations(
                loc = RoutexLoc(
                    coordinates = listOf(latitude, longitude),
                    radius = radiusKm
                )
            ),
            radius = radiusKm
        )

        val response = client.post("$baseUrl/getResults") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }

        val body = response.bodyAsText()
        if (response.status.value != 200) {
            throw NetworkException(response.status.value, "Routex API error: $body")
        }

        return parseResults(body)
    }

    /**
     * Parses API response into a list of sites.
     * Tries: results[], sites[], or GeoJSON features[].
     */
    private fun parseResults(body: String): List<RoutexSite> {
        val element = json.parseToJsonElement(body)
        val obj = element.jsonObject

        // Try "results" or "sites" array
        val array = obj["results"]?.jsonArray
            ?: obj["sites"]?.jsonArray
            ?: obj["data"]?.jsonObject?.get("results")?.jsonArray
            ?: obj["data"]?.jsonObject?.get("sites")?.jsonArray
            ?: obj["features"]?.jsonArray

        if (array == null) return emptyList()

        return array.mapNotNull { element ->
            when (val item = element) {
                is JsonObject -> parseSiteFromObject(item)
                else -> null
            }
        }
    }

    private fun parseSiteFromObject(obj: JsonObject): RoutexSite? {
        // GeoJSON: geometry.coordinates [lng, lat], properties.name, properties.address
        val coords = obj["geometry"]?.jsonObject?.get("coordinates")?.jsonArray
        fun parseDouble(e: JsonElement): Double? =
            (e as? JsonPrimitive)?.content?.toDoubleOrNull()

        val (lat, lng) = if (coords != null && coords.size >= 2) {
            val a = parseDouble(coords[0]) ?: return null
            val b = parseDouble(coords[1]) ?: return null
            // GeoJSON is [lng, lat]
            Pair(b, a)
        } else {
            val latVal = obj["lat"]?.jsonPrimitive?.content?.toDoubleOrNull()
                ?: obj["latitude"]?.jsonPrimitive?.content?.toDoubleOrNull()
            val lngVal = obj["lng"]?.jsonPrimitive?.content?.toDoubleOrNull()
                ?: obj["longitude"]?.jsonPrimitive?.content?.toDoubleOrNull()
            if (latVal == null || lngVal == null) {
                val coordList = obj["coordinates"]?.jsonArray
                if (coordList != null && coordList.size >= 2) {
                    val first = parseDouble(coordList[0]) ?: return null
                    val second = parseDouble(coordList[1]) ?: return null
                    // Request uses [lat, lng]; assume response may use same
                    Pair(first, second)
                } else return null
            } else Pair(latVal, lngVal)
        }

        val props = obj["properties"]?.jsonObject ?: obj
        val name = props["name"]?.jsonPrimitive?.content
            ?: obj["name"]?.jsonPrimitive?.content
            ?: props["title"]?.jsonPrimitive?.content
            ?: ""
        val address = props["address"]?.jsonPrimitive?.content
            ?: obj["address"]?.jsonPrimitive?.content
            ?: ""
        val id = obj["id"]?.jsonPrimitive?.content
            ?: props["id"]?.jsonPrimitive?.content
            ?: "${lat}_${lng}"
        val brand = props["brand"]?.jsonPrimitive?.content
            ?: obj["brand"]?.jsonPrimitive?.content

        return RoutexSite(
            id = id,
            name = name.ifBlank { "Gas station" },
            address = address,
            latitude = lat,
            longitude = lng,
            brand = brand
        )
    }
}

/**
 * Parsed gas station / site from Routex API.
 */
@Serializable
data class RoutexSite(
    val id: String,
    val name: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val brand: String? = null
)
