package fr.geoking.julius.api.ocpi

import kotlinx.serialization.Serializable

/**
 * OCPI 2.2.1 Location module models.
 * Simplified for availability and price display.
 * https://github.com/ocpi/ocpi/blob/master/mod_locations.asciidoc
 */

@Serializable
data class OcpiResponse<T>(
    val data: T?,
    val status_code: Int,
    val status_message: String? = null,
    val timestamp: String? = null
)

@Serializable
data class OcpiLocation(
    val id: String,
    val name: String? = null,
    val address: String,
    val city: String,
    val postal_code: String? = null,
    val country: String,
    val coordinates: OcpiCoordinates,
    val evses: List<OcpiEvse> = emptyList(),
    val operator: OcpiOperator? = null,
    val last_updated: String
)

@Serializable
data class OcpiCoordinates(
    val latitude: String,
    val longitude: String
)

@Serializable
data class OcpiEvse(
    val uid: String,
    val evse_id: String? = null,
    val status: OcpiStatus,
    val connectors: List<OcpiConnector> = emptyList(),
    val last_updated: String
)

@Serializable
data class OcpiConnector(
    val id: String,
    val standard: String, // e.g. "IEC_62196_T2", "TESLA_S"
    val format: String,   // "SOCKET", "CABLE"
    val power_type: String, // "AC_3_PHASE", "DC"
    val max_voltage: Int,
    val max_amperage: Int,
    val max_electric_power: Int? = null,
    val tariff_ids: List<String> = emptyList(),
    val last_updated: String
)

@Serializable
data class OcpiOperator(
    val name: String,
    val website: String? = null
)

@Serializable
data class OcpiTariff(
    val id: String,
    val currency: String,
    val elements: List<OcpiTariffElement> = emptyList(),
    val last_updated: String
)

@Serializable
data class OcpiTariffElement(
    val price_components: List<OcpiPriceComponent> = emptyList()
)

@Serializable
data class OcpiPriceComponent(
    val type: OcpiPriceComponentType,
    val price: Double,
    val step_size: Int
)

@Serializable
enum class OcpiPriceComponentType {
    ENERGY,
    FLAT,
    TIME,
    PARKING_TIME
}

@Serializable
enum class OcpiStatus {
    AVAILABLE,
    BLOCKED,
    CHARGING,
    INOPERATIVE,
    OUTOFORDER,
    PLANNED,
    REMOVED,
    RESERVED,
    UNKNOWN
}
