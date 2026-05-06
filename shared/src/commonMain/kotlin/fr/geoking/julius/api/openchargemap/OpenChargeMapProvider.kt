package fr.geoking.julius.api.openchargemap

import fr.geoking.julius.poi.Poi

class OpenChargeMapProvider(
    private val client: OpenChargeMapClient
) {
    suspend fun getGasStations(latitude: Double, longitude: Double): List<Poi> {
        val stations = client.getStations(latitude, longitude)
        return stations.map { poi ->
            val operator = poi.operator
            val brand = operator ?: poi.name.split(" ").firstOrNull()
            poi.copy(
                brand = brand,
                operator = operator,
                isElectric = true
            )
        }
    }
}

