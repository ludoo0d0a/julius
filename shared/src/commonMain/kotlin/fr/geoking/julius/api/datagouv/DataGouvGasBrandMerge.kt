package fr.geoking.julius.api.datagouv

import fr.geoking.julius.api.gas.GasApiStation
import kotlin.math.*

object DataGouvGasBrandMerge {
    private const val MAX_DISTANCE_M = 100.0

    fun mergeGasApiBrands(dgStations: List<DataGouvStation>, gasStations: List<GasApiStation>): List<DataGouvStation> {
        if (gasStations.isEmpty()) return dgStations

        return dgStations.map { dg ->
            if (dg.brand != null) return@map dg

            val nearest = gasStations.minByOrNull { gas ->
                haversineM(dg.latitude, dg.longitude, gas.latitude, gas.longitude)
            }

            val distanceM = nearest?.let { haversineM(dg.latitude, dg.longitude, it.latitude, it.longitude) }
            if (nearest != null && distanceM != null && distanceM <= MAX_DISTANCE_M) {
                dg.copy(brand = nearest.brand)
            } else {
                dg
            }
        }
    }

    fun haversineM(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0
        val dLat = (lat2 - lat1) * PI / 180.0
        val dLon = (lon2 - lon1) * PI / 180.0
        val a =
            sin(dLat / 2).pow(2) + cos(lat1 * PI / 180.0) * cos(lat2 * PI / 180.0) * sin(dLon / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }
}

