package fr.geoking.julius.api.traffic

/**
 * Geographic region for which a [TrafficProvider] supplies data.
 * Used by [TrafficProviderFactory] to select the right provider for (lat, lon).
 */
sealed class GeographicRegion {
    /** Bounding box: lat in [latMin, latMax], lon in [lonMin, lonMax]. */
    data class Bbox(
        val latMin: Double,
        val lonMin: Double,
        val latMax: Double,
        val lonMax: Double
    ) : GeographicRegion() {
        override fun contains(latitude: Double, longitude: Double): Boolean =
            latitude in latMin..latMax && longitude in lonMin..lonMax
    }

    open fun contains(latitude: Double, longitude: Double): Boolean = when (this) {
        is Bbox -> this.contains(latitude, longitude)
    }
}
