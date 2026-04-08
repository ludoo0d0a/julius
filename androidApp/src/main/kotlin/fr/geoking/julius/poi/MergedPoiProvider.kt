package fr.geoking.julius.poi

import fr.geoking.julius.api.routex.radiusKmFromMapViewport
import fr.geoking.julius.community.CommunityPoiRepository

/**
 * Wraps a base [PoiProvider] and merges in community POIs from [CommunityPoiRepository],
 * and filters out hidden official POIs.
 */
class MergedPoiProvider(
    private val base: PoiProvider,
    private val communityRepo: CommunityPoiRepository
) : PoiProvider {

    override suspend fun search(request: PoiSearchRequest): List<Poi> {
        val basePois = base.search(request)
        val hiddenIds = communityRepo.getHiddenOfficialIds()
        val radiusKm = request.viewport
            ?.let { v ->
                radiusKmFromMapViewport(
                    centerLat = request.latitude,
                    centerLng = request.longitude,
                    zoom = v.zoom,
                    mapWidthPx = v.mapWidthPx,
                    mapHeightPx = v.mapHeightPx
                ).coerceIn(5, 50).toDouble()
            } ?: 50.0
        val communityPois = communityRepo.getCommunityPoisInArea(request.latitude, request.longitude, radiusKm)
        val linkedOfficialIds = communityRepo.getCommunityLinkedOfficialIdsInArea(request.latitude, request.longitude, radiusKm)
        val filteredBase = basePois
            .filter { it.id !in hiddenIds }
            .filter { it.id !in linkedOfficialIds }
        return filteredBase + communityPois
    }

    override suspend fun getGasStations(
        latitude: Double,
        longitude: Double,
        viewport: MapViewport?
    ): List<Poi> {
        return search(PoiSearchRequest(latitude, longitude, viewport, emptySet()))
    }

    override fun clearCache() {
        base.clearCache()
    }
}
