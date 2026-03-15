package fr.geoking.julius.providers

/**
 * [PoiProvider] that fetches EV charging stations from Open Charge Map (open data, CC BY 4.0).
 * Requires [OpenChargeMapClient]; optional API key may be needed for production use.
 */
class OpenChargeMapProvider(
    private val client: OpenChargeMapClient,
    private val radiusKm: Int = 10,
    private val limit: Int = 50
) : PoiProvider {

    override suspend fun getGasStations(
        latitude: Double,
        longitude: Double,
        viewport: MapViewport?
    ): List<Poi> {
        val stations = client.getStations(
            latitude = latitude,
            longitude = longitude,
            distanceKm = radiusKm,
            maxResults = limit
        )
        return stations.map { s ->
            Poi(
                id = s.id,
                name = s.name,
                address = s.address,
                latitude = s.latitude,
                longitude = s.longitude,
                brand = null,
                isElectric = true,
                powerKw = s.powerKw,
                operator = s.operator,
                isOnHighway = false,
                chargePointCount = null,
                fuelPrices = null,
                irveDetails = IrveDetails(connectorTypes = s.connectorTypes)
            )
        }
    }
}
