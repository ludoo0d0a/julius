package fr.geoking.julius.community

import fr.geoking.julius.community.db.FavoritePoiEntity
import fr.geoking.julius.community.storage.FavoritePoiStorage
import fr.geoking.julius.poi.Poi

class LocalFavoritesRepository(
    private val storage: FavoritePoiStorage
) : FavoritesRepository {

    private suspend fun getFavoritesInternal(): List<FavoritePoiEntity> {
        var list = storage.getFavorites()
        if (list.isEmpty()) {
            list = DEFAULT_FAVORITES
            storage.saveFavorites(list)
        }
        return list
    }

    override suspend fun getFavorites(): List<Poi> =
        getFavoritesInternal().map { it.toPoi() }

    override suspend fun isFavorite(poiId: String): Boolean =
        getFavoritesInternal().any { it.poiId == poiId }

    override suspend fun addFavorite(poi: Poi) {
        val list = getFavoritesInternal().toMutableList()
        list.removeAll { it.poiId == poi.id }
        list.add(
            FavoritePoiEntity(
                poiId = poi.id,
                name = poi.name,
                address = poi.address,
                latitude = poi.latitude,
                longitude = poi.longitude,
                isElectric = poi.isElectric,
                brand = poi.brand,
                createdAt = System.currentTimeMillis()
            )
        )
        storage.saveFavorites(list)
    }

    override suspend fun removeFavorite(poiId: String) {
        val list = getFavoritesInternal().filter { it.poiId != poiId }
        storage.saveFavorites(list)
    }

    override suspend fun toggleFavorite(poi: Poi): Boolean {
        return if (isFavorite(poi.id)) {
            removeFavorite(poi.id)
            false
        } else {
            addFavorite(poi)
            true
        }
    }

    override suspend fun updateFavorite(poi: Poi) {
        val list = getFavoritesInternal().toMutableList()
        val index = list.indexOfFirst { it.poiId == poi.id }
        if (index >= 0) {
            list[index] = list[index].copy(
                name = poi.name,
                address = poi.address,
                latitude = poi.latitude,
                longitude = poi.longitude,
                isElectric = poi.isElectric,
                brand = poi.brand
            )
            storage.saveFavorites(list)
        }
    }
}

private fun FavoritePoiEntity.toPoi(): Poi = Poi(
    id = poiId,
    name = name,
    address = address,
    latitude = latitude,
    longitude = longitude,
    brand = brand,
    isElectric = isElectric
)

private val DEFAULT_FAVORITES = listOf(
    FavoritePoiEntity(
        poiId = "fav_total_metz",
        name = "TotalEnergies Express",
        address = "Saint-Julien-lès-Metz, France",
        latitude = 49.1271714,
        longitude = 6.1892563,
        brand = "total",
        createdAt = 1700000000000L
    ),
    FavoritePoiEntity(
        poiId = "fav_madrid",
        name = "Madrid",
        address = "Comunidad de Madrid, España",
        latitude = 40.416782,
        longitude = -3.703507,
        createdAt = 1700000000001L
    ),
    FavoritePoiEntity(
        poiId = "fav_esso_paris",
        name = "Esso Express",
        address = "19 Bd des Frères Voisin, 75015 Paris, France",
        latitude = 48.8279527,
        longitude = 2.2719314,
        brand = "esso",
        createdAt = 1700000000002L
    )
)
