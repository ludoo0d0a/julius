package fr.geoking.julius.api.datagouv

import fr.geoking.julius.poi.FuelPrice
import fr.geoking.julius.poi.MapViewport
import fr.geoking.julius.poi.Poi
import fr.geoking.julius.poi.PoiCategory
import fr.geoking.julius.api.gas.GasApiClient
import fr.geoking.julius.poi.PoiProvider
import io.ktor.client.HttpClient

/**
 * [PoiProvider] implementation that fetches gas stations and fuel prices from the French
 * open data "Prix des carburants en France - Flux quotidien" (data.economie.gouv.fr),
 * dataset [prix-carburants-quotidien].
 *
 * Uses [DataGouvClient] for locations and prices. Data is updated daily (J-1).
 * The quotidien export often omits enseigne fields; when [gasApiClient] is set, brands are
 * filled from [GasApiClient] by nearest-neighbour match (same source data as gas-api.ovh).
 * No API key required. Returns [Poi] with [Poi.fuelPrices] populated.
 *
 * API: https://data.economie.gouv.fr/explore/dataset/prix-carburants-quotidien/api/
 */
class DataGouvProvider(
    private val client: HttpClient,
    private val radiusKm: Int = 10,
    private val limit: Int = 100,
    private val gasApiClient: GasApiClient? = null
) : PoiProvider {

    private val dataGouvClient = DataGouvClient(client)

    override fun supportedCategories(): Set<PoiCategory> = setOf(PoiCategory.Gas)

    override suspend fun getGasStations(
        latitude: Double,
        longitude: Double,
        viewport: MapViewport?
    ): List<Poi> {
        var stations = dataGouvClient.getStations(
            latitude = latitude,
            longitude = longitude,
            radiusKm = radiusKm,
            limit = limit
        )
        if (gasApiClient != null && stations.isNotEmpty()) {
            try {
                val gas = gasApiClient.searchStations(
                    latitude = latitude,
                    longitude = longitude,
                    radiusKm = radiusKm.coerceIn(1, 100),
                    limit = 100
                )
                stations = DataGouvGasBrandMerge.mergeGasApiBrands(stations, gas)
            } catch (_: Exception) {
            }
        }
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
                }.ifEmpty { null },
                source = "DataGouv"
            )
        }
    }
}
