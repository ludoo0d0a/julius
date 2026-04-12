package fr.geoking.julius.poi

/**
 * Registry of countries with regulated uniform fuel prices and available free price sources.
 */
object FuelPriceRegistry {
    /**
     * Countries where fuel prices are strictly regulated and identical at all stations (or nearly all),
     * or countries where we provide a national reference price from official sources.
     * For these countries, applying a national average (e.g. from OpenVanCamp) to all stations is valid as a fallback.
     *
     * - LU: Luxembourg (strictly regulated maximum prices)
     * - ME: Montenegro (regulated by government)
     * - MK: North Macedonia (regulated by government)
     * - HR: Croatia (regulated prices, updated every 14 days)
     * - SI: Slovenia (regulated prices outside motorways)
     * - BE: Belgium (regulated maximum prices)
     * - PT: Portugal (official reference prices)
     * - PT-MA: Madeira (regional reference prices)
     * - PT-AC: Azores (regional reference prices)
     * - IT: Italy (official reference prices)
     * - SE: Sweden (official reference prices)
     * - DK: Denmark (official reference prices)
     * - FI: Finland (official reference prices)
     * - NO: Norway (official reference prices)
     * - PL: Poland (official reference prices)
     * - HU: Hungary (official reference prices)
     * - IE: Ireland (official reference prices)
     * - GR: Greece (official reference prices)
     * - RO: Romania (official reference prices)
     * - CZ: Czechia (official reference prices)
     * - SK: Slovakia (official reference prices)
     * - BG: Bulgaria (official reference prices)
     */
    val REFERENCE_PRICE_COUNTRIES = setOf(
        "LU", "ME", "MK", "HR", "SI", "BE", "PT", "PT-MA", "PT-AC", "IT", "SE", "DK", "FI",
        "NO", "PL", "HU", "IE", "GR", "RO", "CZ", "SK", "BG", "GB"
    )

    /**
     * Returns true if the given country code has a reference fuel price available.
     */
    fun hasReferencePrice(countryCode: String?): Boolean =
        countryCode?.uppercase() in REFERENCE_PRICE_COUNTRIES
}
