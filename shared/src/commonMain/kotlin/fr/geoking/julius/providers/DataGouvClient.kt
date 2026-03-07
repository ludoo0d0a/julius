package fr.geoking.julius.providers

import fr.geoking.julius.shared.NetworkException
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Client for the French open data "Prix des carburants en France - Flux quotidien"
 * (data.economie.gouv.fr), dataset [prix-carburants-quotidien].
 *
 * Source: [transport.data.gouv.fr](https://transport.data.gouv.fr/datasets/prix-des-carburants-en-france-flux-quotidien-1),
 * API: [data.economie.gouv.fr](https://data.economie.gouv.fr/explore/dataset/prix-carburants-quotidien/api/).
 *
 * Data includes station locations (address, coordinates) and fuel prices, updated daily (J-1).
 * No API key required. Licence: Licence Ouverte 1.0.
 */
class DataGouvClient(
    private val client: HttpClient,
    private val baseUrl: String = "https://data.economie.gouv.fr/api/explore/v2.1/catalog/datasets/prix-carburants-quotidien"
) {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Fetches gas stations (points de vente) within [radiusKm] of (latitude, longitude),
     * with fuel prices and locations.
     */
    suspend fun getStations(
        latitude: Double,
        longitude: Double,
        radiusKm: Int = 10,
        limit: Int = 100
    ): List<DataGouvStation> {
        // ODSQL: within_distance(geo_field, GEOM'POINT(lng lat)', Xkm); POINT is (longitude, latitude).
        val where = "within_distance(geolocation, geom'POINT($longitude $latitude)', ${radiusKm}km)"
        val encodedWhere = java.net.URLEncoder.encode(where, "UTF-8")
        val url = "$baseUrl/records?where=$encodedWhere&limit=$limit"

        val response = client.get(url)
        val body = response.bodyAsText()
        if (response.status.value != 200) {
            throw NetworkException(response.status.value, "DataGouv API error: $body")
        }
        return parseRecords(body)
    }

    private fun parseRecords(body: String): List<DataGouvStation> {
        val element = json.parseToJsonElement(body)
        val obj = element.jsonObject
        val results = obj["results"]?.jsonArray ?: return emptyList()
        val stations = mutableMapOf<String, DataGouvStation>()
        for (item in results) {
            val record = item.jsonObject
            val fields = record["fields"]?.jsonObject ?: record
            parseStationFromRecord(fields)?.let { station ->
                stations[station.id] = stations[station.id]?.let { existing ->
                    existing.copy(
                        prices = (existing.prices + station.prices).distinctBy { it.fuelName }
                    )
                } ?: station
            }
        }
        return stations.values.toList()
    }

    private fun parseStationFromRecord(record: JsonObject): DataGouvStation? {
        val id = record["id"]?.jsonPrimitive?.content
            ?: record["id_"]?.jsonPrimitive?.content
            ?: return null
        val (lat, lng) = parseGeo(record) ?: return null
        val adresse = record["adresse"]?.jsonPrimitive?.content?.trim().orEmpty()
        val ville = record["ville"]?.jsonPrimitive?.content?.trim().orEmpty()
        val cp = record["cp"]?.jsonPrimitive?.content?.trim().orEmpty()
        val address = listOf(adresse, cp, ville).filter { it.isNotBlank() }.joinToString(", ")
        val name = record["nom"]?.jsonPrimitive?.content?.trim()
            ?: record["name"]?.jsonPrimitive?.content?.trim()
            ?: "Station $id"
        val brand = record["marque"]?.jsonPrimitive?.content?.trim()
        val prices = parsePrices(record)
        return DataGouvStation(
            id = id,
            name = name.ifBlank { "Station $id" },
            address = address.ifBlank { "$cp $ville" },
            latitude = lat,
            longitude = lng,
            brand = brand,
            prices = prices
        )
    }

    private fun parseGeo(record: JsonObject): Pair<Double, Double>? {
        val lat = record["latitude"]?.jsonPrimitive?.content?.toDoubleOrNull()
        val lng = record["longitude"]?.jsonPrimitive?.content?.toDoubleOrNull()
        if (lat != null && lng != null) return Pair(lat, lng)
        val geo = record["geolocation"]?.jsonObject
            ?: record["coordonnees_geo"]?.jsonObject
        if (geo != null) {
            val coords = geo["coordinates"]?.jsonArray
            if (coords != null && coords.size >= 2) {
                val lng2 = (coords[0] as? kotlinx.serialization.json.JsonPrimitive)?.content?.toDoubleOrNull()
                val lat2 = (coords[1] as? kotlinx.serialization.json.JsonPrimitive)?.content?.toDoubleOrNull()
                if (lat2 != null && lng2 != null) return Pair(lat2, lng2)
            }
        }
        return null
    }

    private fun parsePrices(record: JsonObject): List<DataGouvPrice> {
        val list = mutableListOf<DataGouvPrice>()
        val prix = record["prix"]?.jsonArray ?: return list
        for (p in prix) {
            val obj = p as? JsonObject ?: continue
            val nom = obj["nom"]?.jsonPrimitive?.content ?: obj["name"]?.jsonPrimitive?.content ?: continue
            val raw = obj["valeur"]?.jsonPrimitive?.content?.toIntOrNull()
                ?: obj["value"]?.jsonPrimitive?.content?.toIntOrNull()
            val outOfStock = obj["rupture"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false
            val maj = obj["maj"]?.jsonPrimitive?.content
            if (raw != null) {
                list.add(DataGouvPrice(
                    fuelName = nom,
                    price = raw / 1000.0,
                    updatedAt = maj,
                    outOfStock = outOfStock
                ))
            }
        }
        // Single fuel per record (flattened)
        val singleNom = record["prix_nom"]?.jsonPrimitive?.content
        val singleVal = record["prix_valeur"]?.jsonPrimitive?.content?.toIntOrNull()
        val singleRupture = record["prix_rupture"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false
        if (list.isEmpty() && singleNom != null && singleVal != null) {
            list.add(DataGouvPrice(
                fuelName = singleNom,
                price = singleVal / 1000.0,
                updatedAt = record["prix_maj"]?.jsonPrimitive?.content,
                outOfStock = singleRupture
            ))
        }
        return list
    }
}

/** Gas station from data.economie.gouv.fr prix-carburants-quotidien. */
@Serializable
data class DataGouvStation(
    val id: String,
    val name: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val brand: String? = null,
    val prices: List<DataGouvPrice> = emptyList()
)
