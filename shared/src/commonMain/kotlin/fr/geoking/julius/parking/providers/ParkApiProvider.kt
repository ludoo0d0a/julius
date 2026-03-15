package fr.geoking.julius.parking.providers

import fr.geoking.julius.parking.ParkingPoi
import fr.geoking.julius.parking.ParkingProvider
import kotlin.math.sqrt

/**
 * City coverage: slug used in API + bbox so we only call when (lat,lon) is in that city area.
 */
data class ParkApiCityConfig(
    val citySlug: String,
    val latMin: Double,
    val latMax: Double,
    val lonMin: Double,
    val lonMax: Double
)

/**
 * Parking provider using [ParkApiClient] (parkendd.de). Covers configured cities;
 * when (lat, lon) falls in a city bbox, fetches that city's lots and filters by distance.
 */
class ParkApiProvider(
    private val api: ParkApiClient,
    private val cityConfigs: List<ParkApiCityConfig>
) : ParkingProvider {

    override val id: String = "parkapi"

    override fun covers(lat: Double, lon: Double): Boolean =
        cityConfigs.any { c ->
            lat in c.latMin..c.latMax && lon in c.lonMin..c.lonMax
        }

    override suspend fun getParkingNearby(lat: Double, lon: Double, radiusMeters: Int): List<ParkingPoi> {
        val config = cityConfigs.firstOrNull { c ->
            lat in c.latMin..c.latMax && lon in c.lonMin..c.lonMax
        } ?: return emptyList()
        val radiusKm = radiusMeters / 1000.0
        val lots = api.getLots(config.citySlug)
        return lots
            .filter { it.lat != null && it.lon != null }
            .filter { haversineKm(lat, lon, it.lat!!, it.lon!!) <= radiusKm }
            .map { lot ->
                ParkingPoi(
                    id = "parkapi_${lot.id}",
                    name = lot.name,
                    latitude = lot.lat!!,
                    longitude = lot.lon!!,
                    capacity = lot.total,
                    available = lot.free,
                    openingHours = null,
                    priceInfo = null,
                    providerId = id,
                    address = lot.address,
                    state = lot.state,
                    distanceKm = null
                )
            }
    }

    private fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
            Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
            Math.sin(dLon / 2) * Math.sin(dLon / 2)
        val c = 2 * Math.atan2(sqrt(a), sqrt(1.0 - a))
        return r * c
    }
}
