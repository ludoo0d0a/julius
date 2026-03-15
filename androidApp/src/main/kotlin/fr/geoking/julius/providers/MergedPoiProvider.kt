package fr.geoking.julius.providers

import fr.geoking.julius.community.CommunityPoiRepository

/**
 * Wraps a base [PoiProvider] and merges in community POIs from [CommunityPoiRepository],
 * and filters out hidden official POIs.
 */
class MergedPoiProvider(
    private val base: PoiProvider,
    private val communityRepo: CommunityPoiRepository
) : PoiProvider {

    override suspend fun getGasStations(
        latitude: Double,
        longitude: Double,
        viewport: MapViewport?
    ): List<Poi> {
        val basePois = base.getGasStations(latitude, longitude, viewport)
        val hiddenIds = communityRepo.getHiddenOfficialIds()
        val radiusKm = viewport?.let { v ->
            val zoom = v.zoom
            (10.0 * (zoom / 12.0)).coerceIn(5.0, 100.0)
        } ?: 50.0
        val communityPois = communityRepo.getCommunityPoisInArea(latitude, longitude, radiusKm)
        val linkedOfficialIds = communityRepo.getCommunityLinkedOfficialIdsInArea(latitude, longitude, radiusKm)
        val filteredBase = basePois
            .filter { it.id !in hiddenIds }
            .filter { it.id !in linkedOfficialIds }
        return filteredBase + communityPois
    }
}
