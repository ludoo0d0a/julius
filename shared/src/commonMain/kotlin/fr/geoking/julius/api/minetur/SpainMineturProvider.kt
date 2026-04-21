package fr.geoking.julius.api.minetur

import fr.geoking.julius.poi.FuelPrice
import fr.geoking.julius.poi.MapViewport
import fr.geoking.julius.poi.Poi
import fr.geoking.julius.poi.PoiCategory
import fr.geoking.julius.poi.PoiProvider
import fr.geoking.julius.shared.network.NetworkException
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sqrt

/**
 * [PoiProvider] for Spanish fuel prices using the free Minetur REST API.
 * API provides all stations in Spain; we filter by distance in-memory.
 * Base URL: https://sedeaplicaciones.minetur.gob.es/ServiciosRESTCarburantes/PreciosCarburantes/EstacionesTerrestres/
 */
class SpainMineturProvider(
    private val client: HttpClient,
    private val radiusKm: Int = 10,
    private val limit: Int = 50
) : PoiProvider {

    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()
    private var cachedStations: List<MineturCompactStation>? = null
    private var lastCacheLat: Double = 0.0
    private var lastCacheLon: Double = 0.0

    override fun supportedCategories(): Set<PoiCategory> = setOf(PoiCategory.Gas)

    override suspend fun getGasStations(
        latitude: Double,
        longitude: Double,
        viewport: MapViewport?
    ): List<Poi> {
        val stations = getOrFetchStations(latitude, longitude)
        if (stations.isEmpty()) return emptyList()

        return withContext(Dispatchers.Default) {
            stations.asSequence()
                .mapNotNull { it.toPoi(latitude, longitude) }
                .filter { it.distance <= radiusKm }
                .sortedBy { it.distance }
                .take(limit)
                .map { it.poi }
                .toList()
        }
    }

    private suspend fun getOrFetchStations(lat: Double, lon: Double): List<MineturCompactStation> = mutex.withLock {
        // If cache exists and is close to current request, reuse it
        cachedStations?.let {
            val dLat = (lat - lastCacheLat) * 111.0
            val dLon = (lon - lastCacheLon) * 111.0 * cos(lat * PI / 180.0)
            val dist = sqrt(dLat * dLat + dLon * dLon)
            if (dist < radiusKm / 2.0) return@withLock it
        }

        val url = "https://sedeaplicaciones.minetur.gob.es/ServiciosRESTCarburantes/PreciosCarburantes/EstacionesTerrestres/"
        val response = client.get(url)

        val body = response.bodyAsText()
        if (response.status.value != 200) {
            throw NetworkException(
                httpCode = response.status.value,
                message = "Spain Minetur API returned ${response.status.value}",
                url = url,
                provider = "SpainMinetur"
            )
        }

        val stations = withContext(Dispatchers.Default) {
            val root = json.decodeFromString<MineturResponse>(body)
            root.listaEESSPrecio?.asSequence()
                ?.mapNotNull { it.toCompact() }
                ?.map {
                    val dLat = (it.lat - lat) * 111.0
                    val dLon = (it.lon - lon) * 111.0 * cos(lat * PI / 180.0)
                    val dist = sqrt(dLat * dLat + dLon * dLon)
                    it to dist
                }
                ?.sortedBy { it.second }
                ?.take(1000) // Limit cache to 1000 closest stations to save memory
                ?.map { it.first }
                ?.toList() ?: emptyList()
        }

        cachedStations = stations
        lastCacheLat = lat
        lastCacheLon = lon
        return@withLock stations
    }

    private data class StationWithDistance(val poi: Poi, val distance: Double)

    private fun MineturStation.toCompact(): MineturCompactStation? {
        val lat = latitud?.replace(",", ".")?.toDoubleOrNull() ?: return null
        val lon = longitudWGS84?.replace(",", ".")?.toDoubleOrNull() ?: return null
        return MineturCompactStation(
            id = ideess ?: "",
            name = rotulo ?: "Gas Station",
            address = direccion ?: "",
            lat = lat,
            lon = lon,
            sp95 = precioGasolina95E5?.replace(",", ".")?.toDoubleOrNull(),
            gazole = (precioGasoilA ?: precioGasoleoA)?.replace(",", ".")?.toDoubleOrNull(),
            gazolePlus = precioGasoleoPremium?.replace(",", ".")?.toDoubleOrNull(),
            sp98 = precioGasolina98E5?.replace(",", ".")?.toDoubleOrNull()
        )
    }

    private fun MineturCompactStation.toPoi(centerLat: Double, centerLon: Double): StationWithDistance {
        val dLat = (lat - centerLat) * 111.0
        val dLon = (lon - centerLon) * 111.0 * cos(centerLat * PI / 180.0)
        val dist = sqrt(dLat * dLat + dLon * dLon)

        val prices = mutableListOf<FuelPrice>()
        sp95?.let { prices.add(FuelPrice("SP95 E5", it)) }
        gazole?.let { prices.add(FuelPrice("Gazole", it)) }
        gazolePlus?.let { prices.add(FuelPrice("Gazole Premium", it)) }
        sp98?.let { prices.add(FuelPrice("SP98", it)) }

        val poi = Poi(
            id = "minetur:$id",
            name = name,
            address = address,
            latitude = lat,
            longitude = lon,
            brand = name,
            poiCategory = PoiCategory.Gas,
            fuelPrices = prices.ifEmpty { null },
            source = "Minetur (Spain)"
        )
        return StationWithDistance(poi, dist)
    }
}

@Serializable
data class MineturCompactStation(
    val id: String,
    val name: String,
    val address: String,
    val lat: Double,
    val lon: Double,
    val sp95: Double?,
    val gazole: Double?,
    val gazolePlus: Double? = null,
    val sp98: Double?
)

@Serializable
data class MineturResponse(
    @SerialName("ListaEESSPrecio") val listaEESSPrecio: List<MineturStation>? = null
)

@Serializable
data class MineturStation(
    @SerialName("IDEESS") val ideess: String? = null,
    @SerialName("Rotulo") val rotulo: String? = null,
    @SerialName("Direccion") val direccion: String? = null,
    @SerialName("Latitud") val latitud: String? = null,
    @SerialName("Longitud (WGS84)") val longitudWGS84: String? = null,
    @SerialName("Precio Gasolina 95 E5") val precioGasolina95E5: String? = null,
    @SerialName("Precio Gasóleo A") val precioGasoilA: String? = null,
    @SerialName("Precio Gasoleo A") val precioGasoleoA: String? = null,
    @SerialName("Precio Gasoleo Premium") val precioGasoleoPremium: String? = null,
    @SerialName("Precio Gasolina 98 E5") val precioGasolina98E5: String? = null
)
