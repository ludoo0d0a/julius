package fr.geoking.julius.providers

import fr.geoking.julius.shared.NetworkException
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.encodeURLParameter
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Safely get results as a list; API may return results as array or single object. */
private fun resultsAsList(obj: JsonObject): List<JsonElement> {
    val results = obj["results"] ?: return emptyList()
    return when (results) {
        is JsonArray -> results
        is JsonObject -> listOf(results)
        else -> emptyList()
    }
}

/**
 * Client for the French open data "Prix des carburants en France - Flux instantané - v2"
 * (Etalab / donnees.roulez-eco.fr), served via data.economie.gouv.fr Explore API.
 *
 * Data: fuel stations with address, coordinates, and fuel prices. Updated every ~10 minutes.
 * Licence: Open Licence 2.0 (Etalab).
 */
class EtalabClient(
    private val client: HttpClient,
    private val baseUrl: String = "https://data.economie.gouv.fr/api/explore/v2.1/catalog/datasets/prix-des-carburants-en-france-flux-instantane-v2"
) {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Fetches gas stations (points de vente) within [radiusKm] of (latitude, longitude).
     * Uses the Explore API records endpoint with a distance filter.
     */
    suspend fun getStations(
        latitude: Double,
        longitude: Double,
        radiusKm: Int = 10,
        limit: Int = 100
    ): List<EtalabStation> {
        // ODSQL: within_distance(geo_field, GEOM'POINT(lng lat)', Xkm)
        // POINT is (longitude, latitude) in WGS84.
        val where = "within_distance(geom, geom'POINT($longitude $latitude)', ${radiusKm}km)"
        val encodedWhere = where.encodeURLParameter()
        val url = "$baseUrl/records?where=$encodedWhere&limit=$limit"

        val response = client.get(url)
        val body = response.bodyAsText()
        if (response.status.value != 200) {
            throw NetworkException(response.status.value, "Etalab API error: $body")
        }
        return parseRecords(body)
    }

    internal fun parseRecords(body: String): List<EtalabStation> {
        val element = json.parseToJsonElement(body)
        val obj = element.jsonObject
        val results = resultsAsList(obj)
        val stations = mutableMapOf<String, EtalabStation>()
        for (item in results) {
            val record = item as? JsonObject ?: continue
            parseStationFromRecord(record)?.let { station ->
                // API may return one row per fuel type; merge by id so we keep one POI per station
                stations[station.id] = stations[station.id]?.let { existing ->
                    existing.copy(
                        fuels = (existing.fuels + station.fuels).distinctBy { it.name }
                    )
                } ?: station
            }
        }
        return stations.values.toList()
    }

    internal fun parseStationFromRecord(record: JsonObject): EtalabStation? {
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
        val pop = record["pop"]?.jsonPrimitive?.content
        val brand = when (pop) {
            "A" -> "Autoroute"
            "R" -> "Route"
            else -> record["marque"]?.jsonPrimitive?.content?.trim()
        }
        val fuels = parseFuels(record)
        return EtalabStation(
            id = id,
            name = name.ifBlank { "Station $id" },
            address = address.ifBlank { "$cp $ville" },
            latitude = lat,
            longitude = lng,
            brand = brand,
            fuels = fuels
        )
    }

    internal fun parseGeo(record: JsonObject): Pair<Double, Double>? {
        val lat = record["latitude"]?.jsonPrimitive?.content?.toDoubleOrNull()
        val lng = record["longitude"]?.jsonPrimitive?.content?.toDoubleOrNull()
        if (lat != null && lng != null) return Pair(lat, lng)
        val geo = record["geom"]?.jsonObject
            ?: record["geolocation"]?.jsonObject
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

    private fun parseFuels(record: JsonObject): List<EtalabFuelPrice> {
        val list = mutableListOf<EtalabFuelPrice>()
        val prix = record["prix"]?.jsonArray ?: return list
        for (p in prix) {
            val obj = p as? JsonObject ?: continue
            val nom = obj["nom"]?.jsonPrimitive?.content ?: obj["name"]?.jsonPrimitive?.content ?: continue
            val raw = obj["valeur"]?.jsonPrimitive?.content?.toIntOrNull()
                ?: obj["value"]?.jsonPrimitive?.content?.toIntOrNull()
            if (raw != null) list.add(EtalabFuelPrice(name = nom, priceEur = raw / 1000.0))
        }
        // Single fuel per record (flattened): prix_nom, prix_valeur
        val singleNom = record["prix_nom"]?.jsonPrimitive?.content
        val singleVal = record["prix_valeur"]?.jsonPrimitive?.content?.toIntOrNull()
        if (list.isEmpty() && singleNom != null && singleVal != null) {
            list.add(EtalabFuelPrice(name = singleNom, priceEur = singleVal / 1000.0))
        }
        return list
    }
}

/** Gas station from Etalab fuel prices open data. */
@Serializable
data class EtalabStation(
    val id: String,
    val name: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val brand: String? = null,
    val fuels: List<EtalabFuelPrice> = emptyList()
)

/** Fuel type and price (euros) from Etalab API. */
@Serializable
data class EtalabFuelPrice(
    val name: String,
    val priceEur: Double
)
