package fr.geoking.julius.api.routing

import fr.geoking.julius.poi.Poi
import fr.geoking.julius.poi.PoiProvider
import fr.geoking.julius.poi.PoiSearchRequest
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
    private val defaultSampleIntervalKm: Double = 30.0,
    private val defaultPoiRadiusMeters: Int = 5000
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
        poiProvider: PoiProvider,
        radiusMeters: Int = defaultPoiRadiusMeters
    ): Result<List<Poi>> {
        val route = routingClient.getRoute(originLat, originLon, destLat, destLon)
            ?: return Result.failure(Exception("No route found"))
        val points = route.points
        if (points.size < 2) return Result.success(emptyList())

        // Adjust sample interval based on radius to ensure coverage.
        // For a 500m radius, we want to sample more frequently (e.g. every 800m).
        val intervalMeters = if (radiusMeters < 5000) {
            (radiusMeters * 1.6).coerceAtLeast(150.0)
        } else {
            defaultSampleIntervalKm * 1000
        }

        val sampled = samplePointsByDistance(points, intervalMeters)
        val seenIds = mutableSetOf<String>()
        val result = mutableListOf<Poi>()
        val request = PoiSearchRequest(latitude = 0.0, longitude = 0.0, categories = emptySet())

        // Use a small fixed viewport if provider supports it, otherwise fallback to radius filtering
        for ((lat, lon) in sampled) {
            val pois = poiProvider.search(request.copy(latitude = lat, longitude = lon))
            for (poi in pois) {
                if (poi.id !in seenIds) {
                    // Manual distance check to the path segment might be better,
                    // but for now we rely on the provider's proximity search around sampled points.
                    // We filter out POIs that are further than radiusMeters from the sampled point.
                    val dist = haversineMeters(lat, lon, poi.latitude, poi.longitude)
                    if (dist <= radiusMeters) {
                        seenIds.add(poi.id)
                        result.add(poi)
                    }
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
