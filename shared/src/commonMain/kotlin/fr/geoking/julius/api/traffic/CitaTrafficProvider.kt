package fr.geoking.julius.api.traffic

/**
 * [TrafficProvider] for Luxembourg motorways (CITA DATEX II).
 * Fetches all roads (A1, A3, A4, A6, A7, A13, B40) and maps sensor data to [TrafficEvent].
 */
class CitaTrafficProvider(
    private val client: CitaTrafficClient
) : TrafficProvider {

    /** Luxembourg bounding box (approximate). */
    private val luxBbox = GeographicRegion.Bbox(
        latMin = 49.4,
        lonMin = 5.7,
        latMax = 50.2,
        lonMax = 6.6
    )

    override suspend fun getTraffic(request: TrafficRequest): TrafficInfo? {
        when (request) {
            is TrafficRequest.Bbox -> if (!luxBbox.contains((request.latMin + request.latMax) / 2, (request.lonMin + request.lonMax) / 2)) return null
            is TrafficRequest.Route -> if (request.points.isEmpty()) return null
                else {
                    val (lat, lon) = request.points[request.points.size / 2]
                    if (!luxBbox.contains(lat, lon)) return null
                }
        }
        val xmlByRoad = client.fetchAllRoads()
        if (xmlByRoad.isEmpty()) return null
        val events = mutableListOf<TrafficEvent>()
        for ((roadId, xml) in xmlByRoad) {
            val rawSites = CitaDatexParser.parseToRawSites(xml)
            for (raw in rawSites) {
                val severity = raw.speedKmh?.let { speedToSeverity(it) } ?: TrafficSeverity.Unknown
                val message = raw.speedKmh?.let { "${kotlin.math.round(it)} km/h" } ?: "—"
                events.add(
                    TrafficEvent(
                        roadRef = raw.roadNumber,
                        direction = raw.direction,
                        severity = severity,
                        message = message,
                        travelTimeSeconds = null,
                        bbox = Bbox(raw.latitude, raw.longitude, raw.latitude, raw.longitude),
                        sourceId = raw.siteId,
                        updatedAt = CitaDatexParser.parseMeasurementTime(raw.measurementTime)
                    )
                )
            }
        }
        return TrafficInfo(events = events, providerId = PROVIDER_ID)
    }

    private fun speedToSeverity(speedKmh: Double): TrafficSeverity = when {
        speedKmh <= 0 -> TrafficSeverity.Closure
        speedKmh < 30 -> TrafficSeverity.Congestion
        speedKmh < 60 -> TrafficSeverity.Normal
        else -> TrafficSeverity.Normal
    }

    companion object {
        const val PROVIDER_ID = "cita"
    }
}
