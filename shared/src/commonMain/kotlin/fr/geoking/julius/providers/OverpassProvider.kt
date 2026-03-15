package fr.geoking.julius.providers

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
        PoiCategory.RestArea
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
        val needsWays = PoiCategory.TruckStop in wanted || PoiCategory.RestArea in wanted
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
            Poi(
                id = "osm:${el.id}",
                name = el.name()?.takeIf { it.isNotBlank() } ?: categoryDisplayName(category),
                address = el.address() ?: "",
                latitude = el.lat,
                longitude = el.lon,
                poiCategory = category
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
        else -> c.name
    }
}
