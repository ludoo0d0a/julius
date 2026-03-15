package fr.geoking.julius.parking

/**
 * Geographic region (country-level) for parking APIs. Used by [ParkingApiSelector] to choose
 * which providers to call based on user location. Smaller regions are checked first so e.g.
 * Luxembourg matches Luxembourg, not Germany or France.
 */
enum class ParkingRegion(
    val latMin: Double,
    val latMax: Double,
    val lonMin: Double,
    val lonMax: Double
) {
    Luxembourg(
        latMin = 49.44,
        latMax = 50.18,
        lonMin = 5.73,
        lonMax = 6.53
    ),
    Belgium(
        latMin = 49.50,
        latMax = 51.51,
        lonMin = 2.54,
        lonMax = 6.41
    ),
    Switzerland(
        latMin = 45.82,
        latMax = 47.81,
        lonMin = 5.96,
        lonMax = 10.49
    ),
    Netherlands(
        latMin = 50.75,
        latMax = 53.56,
        lonMin = 3.36,
        lonMax = 7.23
    ),
    Denmark(
        latMin = 54.56,
        latMax = 57.75,
        lonMin = 8.08,
        lonMax = 15.16
    ),
    Austria(
        latMin = 46.37,
        latMax = 49.02,
        lonMin = 9.53,
        lonMax = 17.16
    ),
    Germany(
        latMin = 47.27,
        latMax = 55.06,
        lonMin = 5.87,
        lonMax = 15.04
    ),
    France(
        latMin = 41.33,
        latMax = 51.09,
        lonMin = -5.14,
        lonMax = 9.56
    ),
    UnitedKingdom(
        latMin = 49.86,
        latMax = 60.86,
        lonMin = -8.65,
        lonMax = 1.76
    ),
    Spain(
        latMin = 35.95,
        latMax = 43.79,
        lonMin = -9.30,
        lonMax = 4.33
    ),
    Italy(
        latMin = 36.65,
        latMax = 47.09,
        lonMin = 6.63,
        lonMax = 18.52
    );

    fun contains(lat: Double, lon: Double): Boolean =
        lat in latMin..latMax && lon in lonMin..lonMax

    companion object {
        /** Order: smaller / more specific regions first so e.g. Luxembourg is chosen over Germany. */
        private val bySpecificity = listOf(
            Luxembourg, Belgium, Switzerland, Netherlands, Denmark, Austria,
            Germany, France, UnitedKingdom, Spain, Italy
        )

        /** Returns the region containing (lat, lon), or null if none. */
        fun containing(lat: Double, lon: Double): ParkingRegion? =
            bySpecificity.firstOrNull { it.contains(lat, lon) }
    }
}
