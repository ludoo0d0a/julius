package fr.geoking.julius.api.openvan

import fr.geoking.julius.poi.FuelPrice
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OpenVanFuelPricesResponse(
    val success: Boolean,
    val data: Map<String, OpenVanCountryData>? = null,
    val meta: OpenVanMeta? = null
)

@Serializable
data class OpenVanMeta(
    @SerialName("total_countries")
    val totalCountries: Int? = null
)

@Serializable
data class OpenVanCountryData(
    @SerialName("country_code")
    val countryCode: String? = null,
    @SerialName("country_name")
    val countryName: String? = null,
    val currency: String? = null,
    val prices: OpenVanPrices? = null,
    @SerialName("fetched_at")
    val fetchedAt: String? = null,
    val source: String? = null
) {
    fun toFuelPrices(): List<FuelPrice> {
        val p = prices ?: return emptyList()
        val out = mutableListOf<FuelPrice>()
        p.gasoline?.let { out += FuelPrice(fuelName = "SP95 E10", price = it) }
        p.diesel?.let { out += FuelPrice(fuelName = "Gazole", price = it) }
        p.lpg?.let { out += FuelPrice(fuelName = "GPLc", price = it) }
        p.gasolinePremium?.let { out += FuelPrice(fuelName = "SP98", price = it) }
        return out
    }
}

@Serializable
data class OpenVanPrices(
    val gasoline: Double? = null,
    val diesel: Double? = null,
    val lpg: Double? = null,
    val e85: Double? = null,
    val premium: Double? = null,
    @SerialName("gasoline_premium")
    val gasolinePremium: Double? = null
)

