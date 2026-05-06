package fr.geoking.julius.api.routex

import kotlinx.serialization.json.*
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow

const val ROUTEX_MAX_POIS: Int = 20

data class RoutexSite(
    val id: String,
    val name: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val brand: String?
)

object RoutexClient {
    private val json = Json { ignoreUnknownKeys = true }

    fun parseResults(body: String): List<RoutexSite> {
        val root = json.parseToJsonElement(body)

        val items: List<JsonObject> = when (root) {
            is JsonArray -> root.mapNotNull { it as? JsonObject }
            is JsonObject -> {
                val results = root["results"]
                when (results) {
                    is JsonArray -> results.mapNotNull { it as? JsonObject }
                    else -> {
                        val features = root["features"]
                        if (features is JsonArray) features.mapNotNull { it as? JsonObject } else emptyList()
                    }
                }
            }
            else -> emptyList()
        }

        return items.mapNotNull { obj ->
            // GeoJSON feature path
            val isFeature = obj["type"]?.jsonPrimitive?.content == "Feature"
            if (isFeature) {
                val geom = obj["geometry"] as? JsonObject
                val coords = geom?.get("coordinates") as? JsonArray
                val lon = coords?.getOrNull(0)?.jsonPrimitive?.content?.toDoubleOrNull() ?: return@mapNotNull null
                val lat = coords.getOrNull(1)?.jsonPrimitive?.content?.toDoubleOrNull() ?: return@mapNotNull null
                val props = obj["properties"] as? JsonObject ?: return@mapNotNull null
                val id = props["id"]?.jsonPrimitive?.content ?: return@mapNotNull null
                val name = props["name"]?.jsonPrimitive?.content ?: "Gas station"
                val address = props["address"]?.jsonPrimitive?.content ?: ""
                return@mapNotNull RoutexSite(id, name, address, lat, lon, brand = null)
            }

            val id = obj["id"]?.jsonPrimitive?.content ?: return@mapNotNull null
            val lon = obj["x"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: return@mapNotNull null
            val lat = obj["y"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: return@mapNotNull null
            val brand = obj["brand_id"]?.jsonPrimitive?.content
            RoutexSite(
                id = id,
                name = "Gas station",
                address = "",
                latitude = lat,
                longitude = lon,
                brand = brand
            )
        }
    }

    fun filterInBoundsAndLimit(
        sites: List<RoutexSite>,
        minLng: Double,
        minLat: Double,
        maxLng: Double,
        maxLat: Double,
        maxPois: Int
    ): List<RoutexSite> {
        return sites
            .asSequence()
            .filter { it.longitude in minLng..maxLng && it.latitude in minLat..maxLat }
            .take(maxPois)
            .toList()
    }
}

/**
 * Approximate radius (km) covered by a map viewport (Web Mercator).
 */
fun radiusKmFromMapViewport(
    centerLat: Double,
    centerLon: Double,
    zoom: Float,
    widthPx: Int,
    heightPx: Int
): Int {
    val z = zoom.toDouble()
    val earthCircumferenceM = 2.0 * PI * 6_378_137.0
    val metersPerPixelAtEquator = earthCircumferenceM / (256.0 * 2.0.pow(z))
    val metersPerPixel = metersPerPixelAtEquator * cos(centerLat * PI / 180.0)
    val halfMaxDimPx = (maxOf(widthPx, heightPx) / 2.0)
    val radiusM = halfMaxDimPx * metersPerPixel
    return (radiusM / 1000.0).toInt().coerceAtLeast(1)
}

