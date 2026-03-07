package fr.geoking.julius.shared

import io.ktor.client.HttpClient

/**
 * [PoiProvider] implementation that fetches gas stations from the Routex (Wigeogis) SiteFinder API.
 */
class RoutexProvider(
    private val client: HttpClient,
    private val radiusKm: Int = 5
) : PoiProvider {

    private val routexClient = RoutexClient(client)

    override suspend fun getGasStations(latitude: Double, longitude: Double): List<Poi> {
        val sites = routexClient.getResults(latitude, longitude, radiusKm)
        return sites.map { site ->
            Poi(
                id = site.id,
                name = site.name,
                address = site.address,
                latitude = site.latitude,
                longitude = site.longitude,
                brand = site.brand
            )
        }
    }
}
