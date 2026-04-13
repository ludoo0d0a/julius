package fr.geoking.julius.api.ocpi

import fr.geoking.julius.poi.Poi
import fr.geoking.julius.poi.PoiCategory
import fr.geoking.julius.poi.PoiProvider
import fr.geoking.julius.poi.MapViewport
import fr.geoking.julius.poi.IrveDetails

/**
 * [PoiProvider] implementation for OCPI-compliant CPOs (e.g. Eco-Movement).
 */
class OcpiPoiProvider(
    private val client: OcpiClient,
    private val sourceName: String = "OCPI"
) : PoiProvider {

    override fun supportedCategories(): Set<PoiCategory> = setOf(PoiCategory.Irve)

    override suspend fun getGasStations(
        latitude: Double,
        longitude: Double,
        viewport: MapViewport?
    ): List<Poi> {
        // Radius and filtering logic is mostly handled by the client or after fetching.
        // For OCPI, we typically fetch locations around a center if the CPO supports it,
        // or the whole set for smaller providers.
        val locations = client.getLocations(latitude, longitude, radiusKm = 10)

        return locations.mapNotNull { loc ->
            val lat = loc.coordinates.latitude.toDoubleOrNull() ?: return@mapNotNull null
            val lon = loc.coordinates.longitude.toDoubleOrNull() ?: return@mapNotNull null

            val totalConnectors = loc.evses.sumOf { it.connectors.size }
            val availableConnectors = loc.evses.count { it.status == OcpiStatus.AVAILABLE }

            // Collect all unique connector types
            val connectorTypes = loc.evses.flatMap { evse ->
                evse.connectors.mapNotNull { conn -> mapOcpiConnectorType(conn.standard) }
            }.toSet()

            // Max power across all connectors (kW)
            val maxPower = loc.evses.flatMap { it.connectors }
                .mapNotNull { it.max_electric_power?.toDouble()?.div(1000.0) }
                .maxOrNull() ?: 0.0

            Poi(
                id = loc.id,
                name = loc.name ?: "EV Station",
                address = loc.address,
                latitude = lat,
                longitude = lon,
                brand = loc.operator?.name,
                isElectric = true,
                poiCategory = PoiCategory.Irve,
                powerKw = if (maxPower > 0.0) maxPower else null,
                operator = loc.operator?.name,
                chargePointCount = loc.evses.size,
                irveDetails = IrveDetails(
                    connectorTypes = connectorTypes,
                    availableConnectors = availableConnectors,
                    totalConnectors = totalConnectors
                ),
                source = sourceName
            )
        }
    }

    private fun mapOcpiConnectorType(standard: String): String? {
        return when (standard.uppercase()) {
            "IEC_62196_T2" -> "type_2"
            "IEC_62196_T2_COMBO" -> "combo_ccs"
            "CHADEMO" -> "chademo"
            "DOMESTIC_F" -> "ef"
            "DOMESTIC_E" -> "ef"
            else -> "autre"
        }
    }
}
