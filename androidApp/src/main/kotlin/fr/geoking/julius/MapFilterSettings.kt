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

        // Do not filter out stations from the map based on energy, power, operator or connector filters.
        // Instead, we show all stations and adjust their markers (label/color) based on these filters.
        // Brand filter remains active as it's often used to find specific networks or for fuel card compatibility.
        val filterBrands = settings.effectiveFuelBrandFilterIds()
        if (filterBrands.isNotEmpty()) {
            val brandIds = filterBrands.map { it.lowercase() }.toSet()
            result = result.filter { poi ->
                val brand = poi.brand
                poi.isElectric ||
                    brand == null || // Don't filter out unknown brands (e.g. from OpenVanCamp / OSM)
                    brandIds.any { id -> brand.lowercase().contains(id) }
            }
        }

        return result
    }

}
