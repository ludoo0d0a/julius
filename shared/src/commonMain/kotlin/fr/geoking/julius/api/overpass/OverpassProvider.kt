package fr.geoking.julius.api.overpass

import fr.geoking.julius.providers.MapViewport
import fr.geoking.julius.providers.Poi
import fr.geoking.julius.providers.PoiCategory
import fr.geoking.julius.providers.PoiProvider
import fr.geoking.julius.providers.PoiSearchRequest
import fr.geoking.julius.providers.RestaurantDetails

/**
 * [PoiProvider] that fetches amenities (toilets, water, camping, caravan, picnic, etc.) from OpenStreetMap
 * via the [Overpass API](https://wiki.openstreetmap.org/wiki/Overpass_API).
 * No API key required. Data © OpenStreetMap contributors, ODbL.
 */
class OverpassProvider(
    private val client: OverpassClient,
    private val radiusKm: Int = 5,
    private val limit: Int = 100
) : PoiProvider {

    override fun supportedCategories(): Set<PoiCategory> = setOf(
        PoiCategory.Toilet,
        PoiCategory.DrinkingWater,
        PoiCategory.Camping,
        PoiCategory.CaravanSite,
        PoiCategory.PicnicSite,
        PoiCategory.TruckStop,
        PoiCategory.RestArea,
        PoiCategory.Restaurant,
        PoiCategory.FastFood
    )

    override suspend fun search(request: PoiSearchRequest): List<Poi> {
        val cat = request.categories.ifEmpty { supportedCategories() }
        val wanted = cat.filter { it in supportedCategories() }.toSet()
        if (wanted.isEmpty()) return emptyList()
        val amenityValues = wanted.mapNotNull { categoryToOsmAmenity(it) }.toSet()
        val tourismValues = wanted.mapNotNull { categoryToOsmTourism(it) }.toSet()
        val highwayValues = wanted.mapNotNull { categoryToOsmHighway(it) }.toSet()
        val tagFilters = buildList {
            if (amenityValues.isNotEmpty()) add("amenity" to amenityValues)
            if (tourismValues.isNotEmpty()) add("tourism" to tourismValues)
            if (highwayValues.isNotEmpty()) add("highway" to highwayValues)
        }
        if (tagFilters.isEmpty()) return emptyList()
        val needsWays = PoiCategory.TruckStop in wanted || PoiCategory.RestArea in wanted ||
            PoiCategory.Restaurant in wanted || PoiCategory.FastFood in wanted
        val elements = if (needsWays) {
            client.queryNodesAndWaysWithTagFilters(
                latitude = request.latitude,
                longitude = request.longitude,
                radiusKm = radiusKm,
                tagFilters = tagFilters,
                limit = limit
            )
        } else {
            client.queryNodesWithTagFilters(
                latitude = request.latitude,
                longitude = request.longitude,
                radiusKm = radiusKm,
                tagFilters = tagFilters,
                limit = limit
            )
        }
        return elements.mapNotNull { el ->
            val category = PoiCategory.fromOsmTags(el.tags) ?: return@mapNotNull null
            if (category !in wanted) return@mapNotNull null
            val restaurantDetails = when (category) {
                PoiCategory.Restaurant, PoiCategory.FastFood -> RestaurantDetails(
                    openingHours = el.openingHours()?.takeIf { it.isNotBlank() },
                    cuisine = el.cuisine()?.takeIf { it.isNotBlank() },
                    brand = el.brand()?.takeIf { it.isNotBlank() },
                    isFastFood = category == PoiCategory.FastFood
                )
                else -> null
            }
            Poi(
                id = "osm:${el.id}",
                name = el.name()?.takeIf { it.isNotBlank() } ?: categoryDisplayName(category),
                address = el.address() ?: "",
                latitude = el.lat,
                longitude = el.lon,
                poiCategory = category,
                brand = el.brand()?.takeIf { it.isNotBlank() },
                restaurantDetails = restaurantDetails
            )
        }
    }

    override suspend fun getGasStations(
        latitude: Double,
        longitude: Double,
        viewport: MapViewport?
    ): List<Poi> = emptyList()

    private fun categoryToOsmAmenity(c: PoiCategory): String? = when (c) {
        PoiCategory.Toilet -> "toilets"
        PoiCategory.DrinkingWater -> "drinking_water"
        PoiCategory.TruckStop -> "truck_stop"
        PoiCategory.Restaurant -> "restaurant"
        PoiCategory.FastFood -> "fast_food"
        else -> null
    }

    private fun categoryToOsmTourism(c: PoiCategory): String? = when (c) {
        PoiCategory.Camping -> "camp_site"
        PoiCategory.CaravanSite -> "caravan_site"
        PoiCategory.PicnicSite -> "picnic_site"
        else -> null
    }

    private fun categoryToOsmHighway(c: PoiCategory): String? = when (c) {
        PoiCategory.RestArea -> "rest_area"
        else -> null
    }

    private fun categoryDisplayName(c: PoiCategory): String = when (c) {
        PoiCategory.Toilet -> "Toilets"
        PoiCategory.DrinkingWater -> "Drinking water"
        PoiCategory.Camping -> "Camping"
        PoiCategory.CaravanSite -> "Aire camping-car"
        PoiCategory.PicnicSite -> "Picnic area"
        PoiCategory.TruckStop -> "Truck stop"
        PoiCategory.RestArea -> "Rest area"
        PoiCategory.Restaurant -> "Restaurant"
        PoiCategory.FastFood -> "Fast food"
        else -> c.name
    }
}
