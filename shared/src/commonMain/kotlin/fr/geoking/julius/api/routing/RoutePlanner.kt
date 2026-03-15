package fr.geoking.julius.api.routing

import fr.geoking.julius.providers.Poi
import fr.geoking.julius.providers.PoiProvider
import fr.geoking.julius.providers.PoiSearchRequest
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Plans a route and returns charging stations (POIs) along the route.
 * Samples points every [sampleIntervalKm] km, fetches nearby POIs, and deduplicates.
 */
class RoutePlanner(
    private val routingClient: RoutingClient,
    private val sampleIntervalKm: Double = 30.0,
    private val poiRadiusKm: Int = 5
) {

    /**
     * Returns POIs (gas, IRVE, truck stops, rest areas, etc.) along the route from origin to destination.
     * Uses [poiProvider] to fetch POIs near sampled points; categories are vehicle-aware when the provider
     * uses settings (e.g. SelectorPoiProvider). No range simulation; just "POIs along route".
     */
    suspend fun getStationsAlongRoute(
        originLat: Double,
        originLon: Double,
        destLat: Double,
        destLon: Double,
        poiProvider: PoiProvider
    ): Result<List<Poi>> {
        val route = routingClient.getRoute(originLat, originLon, destLat, destLon)
            ?: return Result.failure(Exception("No route found"))
        val points = route.points
        if (points.size < 2) return Result.success(emptyList())

        val sampled = samplePointsByDistance(points, sampleIntervalKm * 1000)
        val seenIds = mutableSetOf<String>()
        val result = mutableListOf<Poi>()
        val request = PoiSearchRequest(latitude = 0.0, longitude = 0.0, categories = emptySet())
        for ((lat, lon) in sampled) {
            val pois = poiProvider.search(request.copy(latitude = lat, longitude = lon))
            for (poi in pois) {
                if (poi.id !in seenIds) {
                    seenIds.add(poi.id)
                    result.add(poi)
                }
            }
        }
        return Result.success(result)
    }

    private fun samplePointsByDistance(points: List<Pair<Double, Double>>, intervalMeters: Double): List<Pair<Double, Double>> {
        if (points.isEmpty()) return emptyList()
        if (points.size == 1) return points
        val sampled = mutableListOf<Pair<Double, Double>>()
        sampled.add(points.first())
        var acc = 0.0
        for (i in 1 until points.size) {
            val (lat0, lon0) = points[i - 1]
            val (lat1, lon1) = points[i]
            val segLen = haversineMeters(lat0, lon0, lat1, lon1)
            acc += segLen
            if (acc >= intervalMeters) {
                sampled.add(lat1 to lon1)
                acc = 0.0
            }
        }
        if (sampled.last() != points.last()) {
            sampled.add(points.last())
        }
        return sampled
    }

    private fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6_371_000.0
        val rad = PI / 180.0
        val dLat = (lat2 - lat1) * rad
        val dLon = (lon2 - lon1) * rad
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(lat1 * rad) * cos(lat2 * rad) * sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1.0 - a))
        return r * c
    }
}
