package fr.geoking.julius.providers

import io.ktor.client.HttpClient

/**
 * [PoiProvider] implementation that fetches EV charging stations (IRVE) from the French
 * open data "Base nationale des IRVE" (data.gouv.fr), via ODRÉ Opendatasoft API.
 *
 * Uses [DataGouvElecClient] for locations. Data is consolidated from data.gouv.fr.
 * No API key required. Returns [Poi] with [Poi.fuelPrices] null (charging points, not fuel).
 *
 * Source: https://www.data.gouv.fr/datasets/base-nationale-des-irve-infrastructures-de-recharge-pour-vehicules-electriques
 * API: https://odre.opendatasoft.com/explore/dataset/bornes-irve/
 */
class DataGouvElecProvider(
    private val client: HttpClient,
    private val radiusKm: Int = 10,
    private val limit: Int = 100
) : PoiProvider {

    private val dataGouvElecClient = DataGouvElecClient(client)

    override fun supportedCategories(): Set<PoiCategory> = setOf(PoiCategory.Irve)

    override suspend fun getGasStations(
        latitude: Double,
        longitude: Double,
        viewport: MapViewport?
    ): List<Poi> {
        val stations = dataGouvElecClient.getStations(
            latitude = latitude,
            longitude = longitude,
            radiusKm = radiusKm,
            limit = limit
        )
        return stations.map { station ->
            val name = station.puissanceKw?.let { "${station.name} • $it kW" } ?: station.name
            Poi(
                id = station.id,
                name = name,
                address = station.address,
                latitude = station.latitude,
                longitude = station.longitude,
                brand = station.brand,
                isElectric = true,
                powerKw = station.puissanceKw,
                operator = station.operator,
                isOnHighway = station.isOnHighway,
                chargePointCount = station.nbrePdc,
                fuelPrices = null,
                irveDetails = station.irveDetails
            )
        }
    }
}
