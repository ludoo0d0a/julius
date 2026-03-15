package fr.geoking.julius.providers

/**
 * [PoiProvider] that fetches amenities (toilets, drinking water, etc.) from OpenStreetMap
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
        PoiCategory.DrinkingWater
    )

    override suspend fun search(request: PoiSearchRequest): List<Poi> {
        val cat = request.categories.ifEmpty { supportedCategories() }
        val wanted = cat.filter { it in supportedCategories() }.toSet()
        if (wanted.isEmpty()) return emptyList()
        val amenityValues = wanted.mapNotNull { categoryToOsmAmenity(it) }.toSet()
        if (amenityValues.isEmpty()) return emptyList()
        val elements = client.queryNodes(
            latitude = request.latitude,
            longitude = request.longitude,
            radiusKm = radiusKm,
            amenityValues = amenityValues,
            limit = limit
        )
        return elements.mapNotNull { el ->
            val category = PoiCategory.fromOsmAmenity(el.amenity() ?: return@mapNotNull null)
                ?: return@mapNotNull null
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
        else -> null
    }

    private fun categoryDisplayName(c: PoiCategory): String = when (c) {
        PoiCategory.Toilet -> "Toilets"
        PoiCategory.DrinkingWater -> "Drinking water"
        else -> c.name
    }
}
