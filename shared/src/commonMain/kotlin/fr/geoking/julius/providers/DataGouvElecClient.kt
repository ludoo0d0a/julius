package fr.geoking.julius.providers

import fr.geoking.julius.shared.NetworkException
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.*

private fun String.encodeWhereParameter(): String = map { c ->
    when (c) {
        ' ' -> "%20"
        '(' -> "%28"
        ')' -> "%29"
        '\'' -> "%27"
        else -> c.toString()
    }
}.joinToString("")

/**
 * Client for the French open data IRVE (Infrastructures de Recharge pour Véhicules Électriques),
 * consolidated base from [data.gouv.fr](https://www.data.gouv.fr/datasets/base-nationale-des-irve-infrastructures-de-recharge-pour-vehicules-electriques),
 * served via ODRÉ (Open Data Réseaux Énergies) Opendatasoft API.
 *
 * Source: [Bornes IRVE - ODRÉ](https://odre.opendatasoft.com/explore/dataset/bornes-irve/).
 * No API key required. Licence: Licence Ouverte 2.0.
 */
class DataGouvElecClient(
    private val client: HttpClient,
    private val baseUrl: String = "https://odre.opendatasoft.com/api/explore/v2.1/catalog/datasets/bornes-irve"
) {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Fetches EV charging stations (bornes IRVE) within [radiusKm] of (latitude, longitude).
     * Uses a bounding-box query then filters by distance; returns up to [limit] nearest stations.
     */
    suspend fun getStations(
        latitude: Double,
        longitude: Double,
        radiusKm: Int = 10,
        limit: Int = 100
    ): List<DataGouvElecStation> {
        val deltaLat = radiusKm / 111.0
        val deltaLng = radiusKm / (111.0 * cos(latitude * PI / 180)).coerceAtLeast(0.01)
        val latLo = latitude - deltaLat
        val latHi = latitude + deltaLat
        val lngLo = longitude - deltaLng
        val lngHi = longitude + deltaLng
        val where = "consolidated_latitude > $latLo and consolidated_latitude < $latHi and consolidated_longitude > $lngLo and consolidated_longitude < $lngHi"
        val encodedWhere = where.encodeWhereParameter()
        val url = "$baseUrl/records?where=$encodedWhere&limit=${limit.coerceAtLeast(500)}"

        val response = client.get(url)
        val body = response.bodyAsText()
        if (response.status.value != 200) {
            throw NetworkException(response.status.value, "DataGouvElec API error: $body")
        }
        val raw = parseRecords(body)
        return raw
            .mapNotNull { station ->
                val dist = haversineKm(latitude, longitude, station.latitude, station.longitude)
                if (dist <= radiusKm) station to dist else null
            }
            .sortedBy { it.second }
            .take(limit)
            .map { it.first }
    }

    private fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0
        val rad = PI / 180.0
        val dLat = (lat2 - lat1) * rad
        val dLon = (lon2 - lon1) * rad
        val a = sin(dLat / 2).pow(2) + cos(lat1 * rad) * cos(lat2 * rad) * sin(dLon / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }

    private fun parseRecords(body: String): List<DataGouvElecStation> {
        val element = json.parseToJsonElement(body)
        val obj = element.jsonObject
        val results = obj["results"]?.jsonArray ?: return emptyList()
        val stations = mutableListOf<DataGouvElecStation>()
        for (item in results) {
            val record = (item as? JsonObject) ?: continue
            parseStation(record)?.let { stations.add(it) }
        }
        return stations
    }

    private fun parseStation(record: JsonObject): DataGouvElecStation? {
        val id = record["id_station_itinerance"]?.jsonPrimitive?.content
            ?: record["id_pdc_itinerance"]?.jsonPrimitive?.content
            ?: return null
        val lat = record["consolidated_latitude"]?.jsonPrimitive?.content?.toDoubleOrNull()
            ?: (record["coordonneesxy"]?.jsonObject)?.let { coord ->
                // coordonneesxy uses lon=latitude, lat=longitude in the API
                coord["lon"]?.jsonPrimitive?.content?.toDoubleOrNull()
            }
        val lng = record["consolidated_longitude"]?.jsonPrimitive?.content?.toDoubleOrNull()
            ?: (record["coordonneesxy"]?.jsonObject)?.let { coord ->
                coord["lat"]?.jsonPrimitive?.content?.toDoubleOrNull()
            }
        if (lat == null || lng == null) return null
        val name = record["nom_station"]?.jsonPrimitive?.content?.trim().orEmpty().ifBlank {
            record["nom_enseigne"]?.jsonPrimitive?.content?.trim() ?: "IRVE $id"
        }
        val address = record["adresse_station"]?.jsonPrimitive?.content?.trim().orEmpty()
        val brand = record["nom_enseigne"]?.jsonPrimitive?.content?.trim()
        val power = record["puissance_nominale"]?.jsonPrimitive?.content?.toDoubleOrNull()
        return DataGouvElecStation(
            id = id,
            name = name,
            address = address,
            latitude = lat,
            longitude = lng,
            brand = brand,
            puissanceKw = power
        )
    }
}

/** EV charging station from ODRÉ IRVE (data.gouv.fr base nationale IRVE). */
data class DataGouvElecStation(
    val id: String,
    val name: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val brand: String? = null,
    val puissanceKw: Double? = null
)
