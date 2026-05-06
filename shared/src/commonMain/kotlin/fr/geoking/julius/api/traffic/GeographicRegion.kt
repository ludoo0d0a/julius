package fr.geoking.julius.api.traffic

sealed interface GeographicRegion {
    fun contains(latitude: Double, longitude: Double): Boolean

    data class Bbox(
        val minLat: Double,
        val minLon: Double,
        val maxLat: Double,
        val maxLon: Double
    ) : GeographicRegion {
        override fun contains(latitude: Double, longitude: Double): Boolean =
            latitude in minLat..maxLat && longitude in minLon..maxLon
    }

    data object Everywhere : GeographicRegion {
        override fun contains(latitude: Double, longitude: Double): Boolean = true
    }
}

