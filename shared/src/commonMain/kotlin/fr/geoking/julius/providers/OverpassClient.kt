package fr.geoking.julius.providers

import fr.geoking.julius.shared.NetworkException
import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.cos
import kotlin.math.PI

/**
 * Client for the [Overpass API](https://wiki.openstreetmap.org/wiki/Overpass_API) (OpenStreetMap).
 * Queries nodes/ways by OSM tags (e.g. amenity=toilets, amenity=drinking_water).
 * No API key required. Use responsibly (rate limit ~2 req/s on public instances).
 */
class OverpassClient(
    private val client: HttpClient,
    private val baseUrl: String = "https://overpass-api.de/api/interpreter"
) {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Fetch POI nodes matching the given OSM amenity tag values in the bounding box.
     * @param latitude Center latitude
     * @param longitude Center longitude
     * @param radiusKm Search radius in km
     * @param amenityValues OSM "amenity" tag values, e.g. "toilets", "drinking_water"
     * @param limit Max elements per amenity type (Overpass may return more; we trim)
     */
    suspend fun queryNodes(
        latitude: Double,
        longitude: Double,
        radiusKm: Int = 5,
        amenityValues: Set<String>,
        limit: Int = 100
    ): List<OverpassElement> {
        if (amenityValues.isEmpty()) return emptyList()
        val deltaLat = radiusKm / 111.0
        val deltaLng = radiusKm / (111.0 * cos(latitude * PI / 180)).coerceAtLeast(0.01)
        val south = latitude - deltaLat
        val north = latitude + deltaLat
        val west = longitude - deltaLng
        val east = longitude + deltaLng
        val bbox = "$south,$west,$north,$east"
        val unionParts = amenityValues.joinToString("\n") { amenity ->
            """  node["amenity"="$amenity"]($bbox);"""
        }
        val query = """
            [out:json][timeout:25];
            (
            $unionParts
            );
            out body ${limit.coerceIn(1, 500)};
        """.trimIndent()

        val response = client.post(baseUrl) { setBody(query) }
        val body = response.bodyAsText()
        if (response.status.value != 200) {
            throw NetworkException(response.status.value, "Overpass API error: ${body.take(500)}")
        }
        return parseElements(body)
    }

    private fun parseElements(body: String): List<OverpassElement> {
        val root = json.parseToJsonElement(body).jsonObject
        val elements = root["elements"] ?: return emptyList()
        val arr = elements.jsonArray
        return arr.mapNotNull { el ->
            val obj = el.jsonObject
            val type = obj["type"]?.jsonPrimitive?.content ?: return@mapNotNull null
            if (type != "node") return@mapNotNull null
            val id = obj["id"]?.jsonPrimitive?.content?.toLongOrNull() ?: return@mapNotNull null
            val lat = obj["lat"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: return@mapNotNull null
            val lon = obj["lon"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: return@mapNotNull null
            val tags = (obj["tags"] as? JsonObject)?.mapValues { (_, v) -> (v as? kotlinx.serialization.json.JsonPrimitive)?.content ?: "" } ?: emptyMap()
            OverpassElement(id = id, lat = lat, lon = lon, tags = tags)
        }
    }
}

data class OverpassElement(
    val id: Long,
    val lat: Double,
    val lon: Double,
    val tags: Map<String, String>
) {
    fun amenity(): String? = tags["amenity"]
    fun name(): String? = tags["name"]
    fun address(): String? = tags["addr:street"]?.let { street ->
        val house = tags["addr:housenumber"]
        val city = tags["addr:city"] ?: tags["addr:place"]
        listOfNotNull(house?.let { "$it $street" } ?: street, city).joinToString(", ")
    } ?: tags["address"]
}
