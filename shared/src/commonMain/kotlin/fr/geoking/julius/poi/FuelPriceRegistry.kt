package fr.geoking.julius.poi

/**
 * Registry of countries with regulated uniform fuel prices and available free price sources.
 */
object FuelPriceRegistry {
    /**
     * Countries where fuel prices are strictly regulated and identical at all stations (or nearly all).
     * For these countries, applying a national average (e.g. from OpenVanCamp) to all stations is valid.
     *
     * - LU: Luxembourg (strictly regulated maximum prices)
     * - ME: Montenegro (regulated by government)
     * - MK: North Macedonia (regulated by government)
     * - HR: Croatia (regulated prices, updated every 14 days)
     * - SI: Slovenia (regulated prices outside motorways)
     */
    val UNIFORM_PRICE_COUNTRIES = setOf("LU", "ME", "MK", "HR", "SI")

    /**
     * Map of country ISO codes to their specific free/open price sources.
     */
    val COUNTRY_SPECIFIC_PROVIDERS = mapOf(
        "FR" to listOf(PoiProviderType.DataGouv),
         "ES" to listOf(PoiProviderType.SpainMinetur),
    )

    /**
     * Returns true if the given country code has uniform fuel prices.
     */
    fun isUniformPriceCountry(countryCode: String?): Boolean =
        countryCode?.uppercase() in UNIFORM_PRICE_COUNTRIES
}
