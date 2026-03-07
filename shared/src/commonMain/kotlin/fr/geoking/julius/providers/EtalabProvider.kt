package fr.geoking.julius.providers

import io.ktor.client.HttpClient

/**
 * [PoiProvider] implementation that fetches gas stations from the French open data
 * "Prix des carburants en France" (Etalab / donnees.roulez-eco.fr), via the
 * data.economie.gouv.fr Explore API.
 *
 * Data is updated every ~10 minutes. No API key required (open data).
 */
class EtalabProvider(
    private val client: HttpClient,
    private val radiusKm: Int = 10,
    private val limit: Int = 100
) : PoiProvider {

    private val etalabClient = EtalabClient(client)

    override suspend fun getGasStations(latitude: Double, longitude: Double): List<Poi> {
        val stations = etalabClient.getStations(latitude, longitude, radiusKm, limit)
        return stations.map { station ->
            Poi(
                id = station.id,
                name = station.name,
                address = station.address,
                latitude = station.latitude,
                longitude = station.longitude,
                brand = station.brand
            )
        }
    }
}
