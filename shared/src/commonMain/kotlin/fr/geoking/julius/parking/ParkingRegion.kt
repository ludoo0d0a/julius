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
    val lonMax: Double,
    val countryCode: String
) {
    Luxembourg(
        latMin = 49.44,
        latMax = 50.18,
        lonMin = 5.73,
        lonMax = 6.53,
        countryCode = "LU"
    ),
    Belgium(
        latMin = 49.50,
        latMax = 51.51,
        lonMin = 2.54,
        lonMax = 6.41,
        countryCode = "BE"
    ),
    Switzerland(
        latMin = 45.82,
        latMax = 47.81,
        lonMin = 5.96,
        lonMax = 10.49,
        countryCode = "CH"
    ),
    Netherlands(
        latMin = 50.75,
        latMax = 53.56,
        lonMin = 3.36,
        lonMax = 7.23,
        countryCode = "NL"
    ),
    Denmark(
        latMin = 54.56,
        latMax = 57.75,
        lonMin = 8.08,
        lonMax = 15.16,
        countryCode = "DK"
    ),
    Austria(
        latMin = 46.37,
        latMax = 49.02,
        lonMin = 9.53,
        lonMax = 17.16,
        countryCode = "AT"
    ),
    Germany(
        latMin = 47.27,
        latMax = 55.06,
        lonMin = 5.87,
        lonMax = 15.04,
        countryCode = "DE"
    ),
    France(
        latMin = 41.33,
        latMax = 51.09,
        lonMin = -5.14,
        lonMax = 9.56,
        countryCode = "FR"
    ),
    UnitedKingdom(
        latMin = 49.86,
        latMax = 60.86,
        lonMin = -8.65,
        lonMax = 1.76,
        countryCode = "GB"
    ),
    Spain(
        latMin = 35.95,
        latMax = 43.79,
        lonMin = -9.30,
        lonMax = 4.33,
        countryCode = "ES"
    ),
    Italy(
        latMin = 36.65,
        latMax = 47.09,
        lonMin = 6.63,
        lonMax = 18.52,
        countryCode = "IT"
    ),
    Croatia(
        latMin = 42.39,
        latMax = 46.55,
        lonMin = 13.49,
        lonMax = 19.45,
        countryCode = "HR"
    ),
    Slovenia(
        latMin = 45.42,
        latMax = 46.88,
        lonMin = 13.38,
        lonMax = 16.61,
        countryCode = "SI"
    ),
    Montenegro(
        latMin = 41.85,
        latMax = 43.55,
        lonMin = 18.43,
        lonMax = 20.35,
        countryCode = "ME"
    ),
    NorthMacedonia(
        latMin = 40.85,
        latMax = 42.37,
        lonMin = 20.45,
        lonMax = 23.03,
        countryCode = "MK"
    ),
    Portugal(
        latMin = 36.94,
        latMax = 42.16,
        lonMin = -9.51,
        lonMax = -6.18,
        countryCode = "PT"
    ),
    Madeira(
        latMin = 32.35,
        latMax = 33.15,
        lonMin = -17.35,
        lonMax = -16.25,
        countryCode = "PT-MA"
    ),
    Azores(
        latMin = 36.90,
        latMax = 39.75,
        lonMin = -31.35,
        lonMax = -24.95,
        countryCode = "PT-AC"
    ),
    Norway(
        latMin = 57.90,
        latMax = 71.20,
        lonMin = 4.90,
        lonMax = 31.30,
        countryCode = "NO"
    ),
    Sweden(
        latMin = 55.30,
        latMax = 69.20,
        lonMin = 11.00,
        lonMax = 24.20,
        countryCode = "SE"
    ),
    Finland(
        latMin = 59.80,
        latMax = 70.20,
        lonMin = 19.10,
        lonMax = 31.60,
        countryCode = "FI"
    ),
    Poland(
        latMin = 49.00,
        latMax = 54.90,
        lonMin = 14.00,
        lonMax = 24.20,
        countryCode = "PL"
    ),
    Hungary(
        latMin = 45.70,
        latMax = 48.70,
        lonMin = 16.00,
        lonMax = 22.90,
        countryCode = "HU"
    ),
    Ireland(
        latMin = 51.40,
        latMax = 55.50,
        lonMin = -10.70,
        lonMax = -6.00,
        countryCode = "IE"
    ),
    Greece(
        latMin = 34.80,
        latMax = 41.80,
        lonMin = 19.30,
        lonMax = 28.30,
        countryCode = "GR"
    ),
    Romania(
        latMin = 43.60,
        latMax = 48.30,
        lonMin = 20.20,
        lonMax = 29.80,
        countryCode = "RO"
    ),
    Czechia(
        latMin = 48.50,
        latMax = 51.20,
        lonMin = 12.00,
        lonMax = 18.90,
        countryCode = "CZ"
    ),
    Slovakia(
        latMin = 47.70,
        latMax = 49.80,
        lonMin = 16.80,
        lonMax = 22.70,
        countryCode = "SK"
    ),
    Bulgaria(
        latMin = 41.20,
        latMax = 44.30,
        lonMin = 22.30,
        lonMax = 28.70,
        countryCode = "BG"
    );

    fun contains(lat: Double, lon: Double): Boolean =
        lat in latMin..latMax && lon in lonMin..lonMax

    fun intersects(latMin: Double, latMax: Double, lonMin: Double, lonMax: Double): Boolean {
        return !(latMax < this.latMin || latMin > this.latMax || lonMax < this.lonMin || lonMin > this.lonMax)
    }

    companion object {
        /** Order: smaller / more specific regions first so e.g. Luxembourg is chosen over Germany. */
        private val bySpecificity = listOf(
            Luxembourg, Montenegro, NorthMacedonia, Slovenia, Croatia,
            Madeira, Azores, Portugal,
            Belgium, Switzerland, Netherlands, Denmark, Austria,
            Norway, Sweden, Finland, Poland, Hungary, Ireland, Greece, Romania, Czechia, Slovakia, Bulgaria,
            Germany, France, UnitedKingdom, Spain, Italy
        )

        /** Returns the region containing (lat, lon), or null if none. */
        fun containing(lat: Double, lon: Double): ParkingRegion? =
            bySpecificity.firstOrNull { it.contains(lat, lon) }

        /** Returns all regions within [radiusKm] of ([lat], [lon]). Uses a simple bounding box for efficiency. */
        fun regionsInRadius(lat: Double, lon: Double, radiusKm: Double): List<ParkingRegion> {
            val latDelta = radiusKm / 111.0
            val lonDelta = radiusKm / (111.0 * kotlin.math.cos(lat * kotlin.math.PI / 180.0))

            val minLat = lat - latDelta
            val maxLat = lat + latDelta
            val minLon = lon - lonDelta
            val maxLon = lon + lonDelta

            return entries.filter { it.intersects(minLat, maxLat, minLon, maxLon) }
        }
    }
}
