package fr.geoking.julius.api.uk

import fr.geoking.julius.poi.FuelPrice
import fr.geoking.julius.poi.MapViewport
import fr.geoking.julius.poi.Poi
import fr.geoking.julius.poi.PoiCategory
import fr.geoking.julius.poi.PoiProvider
import fr.geoking.julius.shared.location.haversineKm

/**
 * [PoiProvider] implementation for United Kingdom fuel prices (CMA).
 */
class UnitedKingdomCmaProvider(
    private val client: UnitedKingdomCmaClient,
    private val radiusKm: Int = 10
) : PoiProvider {

    override fun supportedCategories(): Set<PoiCategory> = setOf(PoiCategory.Gas)

    override suspend fun getGasStations(
        latitude: Double,
        longitude: Double,
        viewport: MapViewport?
    ): List<Poi> {
        val stations = client.getAllStations()

        return stations.filter { s ->
            haversineKm(latitude, longitude, s.location.latitude, s.location.longitude) <= radiusKm
        }.map { s ->
            Poi(
                id = s.site_id,
                name = s.name,
                address = "${s.address}, ${s.post_code}",
                latitude = s.location.latitude,
                longitude = s.location.longitude,
                brand = s.brand,
                poiCategory = PoiCategory.Gas,
                fuelPrices = s.prices.map { (fuel, price) ->
                    FuelPrice(
                        fuelName = mapFuelName(fuel),
                        price = price / 100.0 // Convert pence to pounds
                    )
                },
                source = "UK CMA (${s.brand})"
            )
        }
    }

    private fun mapFuelName(cmaFuel: String): String = when (cmaFuel.lowercase()) {
        "e5" -> "SP98"
        "e10" -> "SP95"
        "b7" -> "Gazole"
        "sdy" -> "Gazole"
        else -> cmaFuel
    }
}
