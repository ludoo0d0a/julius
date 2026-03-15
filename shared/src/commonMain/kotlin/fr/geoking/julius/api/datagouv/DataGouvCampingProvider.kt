package fr.geoking.julius.api.datagouv

import fr.geoking.julius.providers.MapViewport
import fr.geoking.julius.providers.Poi
import fr.geoking.julius.providers.PoiCategory
import fr.geoking.julius.providers.PoiProvider
import fr.geoking.julius.providers.PoiSearchRequest

/**
 * [PoiProvider] that fetches aires de camping-car from data.gouv.fr–linked Opendatasoft APIs
 * (e.g. Hérault Data). Complements Overpass OSM data with official regional aires.
 * No API key. Licence: Licence Ouverte 2.0 (Etalab).
 */
class DataGouvCampingProvider(
    private val client: DataGouvCampingClient,
    private val radiusKm: Int = 15,
    private val limit: Int = 50
) : PoiProvider {

    override fun supportedCategories(): Set<PoiCategory> = setOf(PoiCategory.CaravanSite)

    override suspend fun search(request: PoiSearchRequest): List<Poi> {
        val wanted = request.categories.ifEmpty { supportedCategories() }
        if (PoiCategory.CaravanSite !in wanted) return emptyList()
        val aires = client.getAires(
            latitude = request.latitude,
            longitude = request.longitude,
            radiusKm = radiusKm,
            limit = limit
        )
        return aires.map { r ->
            Poi(
                id = "dgouv:${r.id}",
                name = r.name,
                address = r.address,
                latitude = r.latitude,
                longitude = r.longitude,
                poiCategory = PoiCategory.CaravanSite,
                siteName = r.typeAire
            )
        }
    }

    override suspend fun getGasStations(
        latitude: Double,
        longitude: Double,
        viewport: MapViewport?
    ): List<Poi> = emptyList()
}
