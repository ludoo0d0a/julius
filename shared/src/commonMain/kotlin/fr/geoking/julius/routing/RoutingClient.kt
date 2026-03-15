package fr.geoking.julius.routing

/**
 * Client for fetching a route between two points.
 * Implementations may use OSRM, GraphHopper, or other public routing APIs.
 */
interface RoutingClient {
    /**
     * Get a driving route from (originLon, originLat) to (destLon, destLat).
     * OSRM uses longitude,latitude order in the URL.
     * Returns null if the request fails or no route is found.
     */
    suspend fun getRoute(
        originLat: Double,
        originLon: Double,
        destLat: Double,
        destLon: Double
    ): RouteResult?
}
