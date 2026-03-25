package fr.geoking.julius

import fr.geoking.julius.poi.MapPoiFilter
import fr.geoking.julius.poi.Poi
import fr.geoking.julius.poi.PoiProviderType

fun AppSettings.effectiveMapEnergyFilterIds(): Set<String> =
    if (useVehicleFilter) {
        when (vehicleEnergy) {
            "electric" -> setOf("electric")
            "hybrid" -> vehicleGasTypes + "electric"
            else -> vehicleGasTypes
        }
    } else {
        selectedMapEnergyTypes
    }

fun AppSettings.effectiveFuelBrandFilterIds(): Set<String> =
    if (useVehicleFilter) {
        if (fuelCard == FuelCard.Routex && (vehicleEnergy == "gas" || vehicleEnergy == "hybrid")) {
            setOf("esso", "eni", "total", "shell", "aral", "totalenergies")
        } else {
            emptySet()
        }
    } else {
        mapBrands
    }

fun AppSettings.effectiveIrvePowerLevels(): Set<Int> =
    if (useVehicleFilter && (vehicleEnergy == "electric" || vehicleEnergy == "hybrid")) {
        vehiclePowerLevels
    } else {
        mapPowerLevels
    }

fun AppSettings.effectiveIrveOperatorFilter(): Set<String> =
    if (useVehicleFilter && (vehicleEnergy == "electric" || vehicleEnergy == "hybrid")) {
        emptySet()
    } else {
        mapIrveOperators
    }

fun Set<PoiProviderType>.isOnlyOverpass(): Boolean =
    isNotEmpty() && all { it == PoiProviderType.Overpass }

/**
 * Energy / brand / IRVE filters for station POIs. When [skipWhenOnlyOverpass] is true and
 * [providers] is only Overpass, returns [pois] unchanged (OSM amenity results).
 */
object StationMapFilters {

    fun apply(
        settings: AppSettings,
        pois: List<Poi>,
        providers: Set<PoiProviderType>,
        skipWhenOnlyOverpass: Boolean,
    ): List<Poi> {
        if (skipWhenOnlyOverpass && providers.isOnlyOverpass()) return pois

        var result = pois

        val selectedEnergies = settings.effectiveMapEnergyFilterIds()
        if (selectedEnergies.isNotEmpty()) {
            result = result.filter { MapPoiFilter.matchesEnergyFilter(it, selectedEnergies) }
        }

        val filterBrands = settings.effectiveFuelBrandFilterIds()
        if (filterBrands.isNotEmpty()) {
            val brandIds = filterBrands.map { it.lowercase() }.toSet()
            result = result.filter { poi ->
                poi.isElectric ||
                    (poi.brand?.lowercase()?.let { brand -> brandIds.any { id -> brand.contains(id) } } ?: false)
            }
        }

        val filterPowerLevels = settings.effectiveIrvePowerLevels()
        if (filterPowerLevels.isNotEmpty()) {
            result = result.filter { poi ->
                !poi.isElectric ||
                    poi.powerKw == null ||
                    powerMatchesAnyLevel(poi.powerKw!!, filterPowerLevels)
            }
        }

        val filterIrveOperators = settings.effectiveIrveOperatorFilter()
        if (filterIrveOperators.isNotEmpty()) {
            val operators = filterIrveOperators.map { it.trim().lowercase() }
            result = result.filter { poi ->
                !poi.isElectric ||
                    operators.any { op -> poi.operator?.trim()?.lowercase()?.contains(op) == true }
            }
        }

        if (settings.selectedMapConnectorTypes.isNotEmpty()) {
            val connectorSet = settings.selectedMapConnectorTypes
            result = result.filter { poi ->
                !poi.isElectric || poi.irveDetails?.connectorTypes?.any { it in connectorSet } == true
            }
        }

        return result
    }

    private fun powerMatchesAnyLevel(powerKw: Double, levels: Set<Int>): Boolean =
        levels.any { level ->
            when (level) {
                0 -> true
                20 -> powerKw in 20.0..49.9
                50 -> powerKw in 50.0..99.9
                100 -> powerKw in 100.0..199.9
                200 -> powerKw in 200.0..299.9
                300 -> powerKw >= 300.0
                else -> powerKw >= level
            }
        }
}
