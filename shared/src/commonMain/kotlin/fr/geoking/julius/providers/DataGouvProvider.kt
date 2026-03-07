package fr.geoking.julius.providers

import io.ktor.client.HttpClient

/**
 * [PoiProvider] implementation that fetches gas stations and fuel prices from the French
 * open data "Prix des carburants en France - Flux quotidien" (data.economie.gouv.fr),
 * dataset [prix-carburants-quotidien].
 *
 * Uses [DataGouvClient] for locations and prices. Data is updated daily (J-1).
 * No API key required. Returns [Poi] with [Poi.fuelPrices] populated.
 *
 * API: https://data.economie.gouv.fr/explore/dataset/prix-carburants-quotidien/api/
 */
class DataGouvProvider(
    private val client: HttpClient,
    private val radiusKm: Int = 10,
    private val limit: Int = 100
) : PoiProvider {

    private val dataGouvClient = DataGouvClient(client)

    override suspend fun getGasStations(latitude: Double, longitude: Double): List<Poi> {
        val stations = dataGouvClient.getStations(
            latitude = latitude,
            longitude = longitude,
            radiusKm = radiusKm,
            limit = limit
        )
        return stations.map { station ->
            Poi(
                id = station.id,
                name = station.name,
                address = station.address,
                latitude = station.latitude,
                longitude = station.longitude,
                brand = station.brand,
                fuelPrices = station.prices.map { p ->
                    FuelPrice(
                        fuelName = p.fuelName,
                        price = p.price,
                        updatedAt = p.updatedAt,
                        outOfStock = p.outOfStock
                    )
                }.ifEmpty { null }
            )
        }
    }
}
