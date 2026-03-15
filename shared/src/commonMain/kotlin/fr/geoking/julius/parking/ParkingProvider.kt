package fr.geoking.julius.parking

/**
 * Per-source parking API. Implementations fetch from LiveParking, ParkAPI, OSM, etc.
 * [ParkingApiSelector] chooses which providers to call based on location.
 */
interface ParkingProvider {
    val id: String

    /** True if this provider can return data for the given point (e.g. within coverage bbox). */
    fun covers(lat: Double, lon: Double): Boolean

    /**
     * Parking POIs near (lat, lon) within [radiusMeters].
     * Return empty list on error or unsupported; callers merge from multiple providers.
     */
    suspend fun getParkingNearby(lat: Double, lon: Double, radiusMeters: Int): List<ParkingPoi>
}
