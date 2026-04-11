package fr.geoking.julius.api.datagouv

import fr.geoking.julius.shared.network.NetworkException
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.encodeURLParameter
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Safely get results as a list; API may return results as array or single object. */
private fun prixCarburantResultsAsList(obj: JsonObject): List<JsonElement> {
    val results = obj["results"] ?: return emptyList()
    return when (results) {
        is JsonArray -> results
        is JsonObject -> listOf(results)
        else -> emptyList()
    }
}

/**
 * Client for French fuel price open data from the information system behind
 * [prix-carburants.gouv.fr](https://www.prix-carburants.gouv.fr/) (DGCCRF).
 *
 * Programmatic access is via the Explore API on [data.economie.gouv.fr](https://data.economie.gouv.fr),
 * dataset **Prix des carburants en France – Flux instantané v2**
 * (`prix-des-carburants-en-france-flux-instantane-v2`). The feed is refreshed about every 10 minutes.
 * Bulk ZIP mirrors (same system): [donnees.roulez-eco.fr](https://donnees.roulez-eco.fr/opendata/instantane).
 *
 * [data.gouv.fr](https://www.data.gouv.fr) lists and links this dataset but API requests are served from
 * `data.economie.gouv.fr`, not from the prix-carburants website hostname. Licence: Open Licence 2.0 (Etalab).
 */
class DataGouvPrixCarburantClient(
    private val client: HttpClient,
    private val baseUrl: String = DEFAULT_BASE_URL
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
    ): List<DataGouvPrixCarburantStation> {
        val effectiveLimit = limit.coerceIn(1, 100)
        val where = "within_distance(geom, geom'POINT($longitude $latitude)', ${radiusKm}km)"
        val encodedWhere = where.encodeURLParameter()
        val url = "$baseUrl/records?where=$encodedWhere&limit=$effectiveLimit"

        val response = client.get(url)
        val body = response.bodyAsText()
        if (response.status.value != 200) {
            throw NetworkException(response.status.value, "Prix carburants API error: $body")
        }
        return parseRecords(body)
    }

    internal fun parseRecords(body: String): List<DataGouvPrixCarburantStation> {
        val element = json.parseToJsonElement(body)
        val obj = element.jsonObject
        val results = prixCarburantResultsAsList(obj)
        val stations = mutableMapOf<String, DataGouvPrixCarburantStation>()
        for (item in results) {
            val record = item as? JsonObject ?: continue
            parseStationFromRecord(record)?.let { station ->
                stations[station.id] = stations[station.id]?.let { existing ->
                    existing.copy(
                        fuels = (existing.fuels + station.fuels).distinctBy { it.name }
                    )
                } ?: station
            }
        }
        return stations.values.toList()
    }

    internal fun parseStationFromRecord(record: JsonObject): DataGouvPrixCarburantStation? {
        val id = record["id"]?.jsonPrimitive?.contentOrNull
            ?: record["id_"]?.jsonPrimitive?.contentOrNull
            ?: return null
        val (lat, lng) = parseGeo(record) ?: return null
        val adresse = record["adresse"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        val ville = record["ville"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        val cp = record["cp"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        val address = listOf(adresse, cp, ville).filter { it.isNotBlank() }.joinToString(", ")
        val pop = record["pop"]?.jsonPrimitive?.contentOrNull
        val brand = when (pop) {
            "A" -> "Autoroute"
            "R" -> "Route"
            else -> record["marque"]?.jsonPrimitive?.contentOrNull?.trim()
        }
        val name = record["nom"]?.jsonPrimitive?.contentOrNull?.trim()
            ?: record["name"]?.jsonPrimitive?.contentOrNull?.trim()
            ?: brand
            ?: if (ville.isNotBlank()) "Station $ville" else "Station"
        val fuels = parseFuels(record)
        return DataGouvPrixCarburantStation(
            id = id,
            name = name.ifBlank { if (ville.isNotBlank()) "Station $ville" else "Station" },
            address = address.ifBlank { "$cp $ville" },
            latitude = lat,
            longitude = lng,
            brand = brand,
            fuels = fuels
        )
    }

    internal fun parseGeo(record: JsonObject): Pair<Double, Double>? {
        val latRaw = record["latitude"]?.jsonPrimitive?.contentOrNull
        val lngRaw = record["longitude"]?.jsonPrimitive?.contentOrNull
        if (latRaw != null && lngRaw != null) {
            val lat = parseEconomyDegree(latRaw)
            val lng = parseEconomyDegree(lngRaw)
            if (lat != null && lng != null) return Pair(lat, lng)
        }
        val geo = record["geom"]?.jsonObject
            ?: record["geolocation"]?.jsonObject
            ?: record["coordonnees_geo"]?.jsonObject
        if (geo != null) {
            val coords = geo["coordinates"]?.jsonArray
            if (coords != null && coords.size >= 2) {
                val lng2 = coords[0].jsonPrimitive.contentOrNull?.toDoubleOrNull()
                val lat2 = coords[1].jsonPrimitive.contentOrNull?.toDoubleOrNull()
                if (lat2 != null && lng2 != null) return Pair(lat2, lng2)
            }
            val latVal = geo["lat"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()
            val lonVal = geo["lon"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()
                ?: geo["lng"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()
            if (latVal != null && lonVal != null) return Pair(latVal, lonVal)
        }
        return null
    }

    /**
     * data.economie.gouv.fr sometimes returns latitude/longitude as fixed-point integers
     * scaled by 1e5 (e.g. 4886205 → 48.86205) instead of decimal degrees.
     */
    private fun parseEconomyDegree(raw: String): Double? {
        val d = raw.toDoubleOrNull() ?: return null
        return if (kotlin.math.abs(d) > 1000.0) d / 100_000.0 else d
    }

    private fun parseFuels(record: JsonObject): List<DataGouvPrixCarburantFuelPrice> {
        val list = mutableListOf<DataGouvPrixCarburantFuelPrice>()
        val prixElement = record["prix"]
        val prixArray = try {
            when {
                prixElement is kotlinx.serialization.json.JsonPrimitive && prixElement.content.startsWith("[") ->
                    json.parseToJsonElement(prixElement.content).jsonArray
                prixElement is JsonArray -> prixElement
                else -> null
            }
        } catch (e: Exception) {
            null
        }

        if (prixArray != null) {
            for (p in prixArray) {
                val obj = p as? JsonObject ?: continue
                val nom = obj["nom"]?.jsonPrimitive?.contentOrNull
                    ?: obj["@nom"]?.jsonPrimitive?.contentOrNull
                    ?: obj["name"]?.jsonPrimitive?.contentOrNull
                    ?: continue
                val raw = obj["valeur"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()
                    ?: obj["@valeur"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()
                    ?: obj["value"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()
                if (raw != null) list.add(DataGouvPrixCarburantFuelPrice(name = nom, priceEur = raw))
            }
        }

        // data.economie.gouv.fr Explore v2.1 (flux instantané v2) provides individual fuel fields
        listOf(
            "gazole" to "Gazole",
            "sp95" to "SP95",
            "sp98" to "SP98",
            "e10" to "E10",
            "e85" to "E85",
            "gplc" to "GPLc"
        ).forEach { (fieldPrefix, fuelName) ->
            val price = record["${fieldPrefix}_prix"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()
            if (price != null && list.none { it.name.equals(fuelName, ignoreCase = true) }) {
                list.add(DataGouvPrixCarburantFuelPrice(name = fuelName, priceEur = price))
            }
        }

        val singleNom = record["prix_nom"]?.jsonPrimitive?.contentOrNull
        val singleVal = record["prix_valeur"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()
        if (singleNom != null && singleVal != null) {
            val exists = list.any { it.name == singleNom }
            if (!exists) {
                list.add(DataGouvPrixCarburantFuelPrice(name = singleNom, priceEur = singleVal))
            }
        }
        return list
    }

    /**
     * Fetches national fuel price averages per day for the last [days] days.
     * Uses the "Prix des carburants en France - Flux quotidien" dataset.
     * Returns a map of fuel name to a list of daily average points.
     */
    suspend fun getNationalAverages(days: Int = 30): Map<String, List<DataGouvNationalAvgPoint>> {
        val baseUrl = "https://data.economie.gouv.fr/api/explore/v2.1/catalog/datasets/prix-carburants-quotidien"
        val limit = (days * 7).coerceAtMost(100) // approx 7 fuel types
        val url = "$baseUrl/records?group_by=year(prix_maj),month(prix_maj),day(prix_maj),prix_nom" +
                "&select=avg(prix_valeur)" +
                "&where=prix_maj%20%3C%20now()" +
                "&order_by=year(prix_maj)%20desc,month(prix_maj)%20desc,day(prix_maj)%20desc" +
                "&limit=$limit"

        val response = client.get(url)
        val body = response.bodyAsText()
        if (response.status.value != 200) {
            throw NetworkException(response.status.value, "National averages API error: $body")
        }

        val element = json.parseToJsonElement(body)
        val results = element.jsonObject["results"]?.jsonArray ?: return emptyMap()

        val out = mutableMapOf<String, MutableList<DataGouvNationalAvgPoint>>()
        for (item in results) {
            val obj = item.jsonObject
            val y = obj["year(prix_maj)"]?.jsonPrimitive?.contentOrNull ?: continue
            val m = obj["month(prix_maj)"]?.jsonPrimitive?.contentOrNull ?: continue
            val d = obj["day(prix_maj)"]?.jsonPrimitive?.contentOrNull ?: continue
            val fuel = obj["prix_nom"]?.jsonPrimitive?.contentOrNull ?: continue
            val avg = obj["avg(prix_valeur)"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull() ?: continue

            val monthStr = m.toInt().toString().let { if (it.length == 1) "0$it" else it }
            val dayStr = d.toInt().toString().let { if (it.length == 1) "0$it" else it }
            val date = "$y-$monthStr-$dayStr"
            out.getOrPut(fuel) { mutableListOf() }.add(DataGouvNationalAvgPoint(date, avg))
        }
        return out.mapValues { it.value.sortedBy { p -> p.day } }
    }

    companion object {
        const val DEFAULT_BASE_URL =
            "https://data.economie.gouv.fr/api/explore/v2.1/catalog/datasets/prix-des-carburants-en-france-flux-instantane-v2"
    }
}

@Serializable
data class DataGouvNationalAvgPoint(
    val day: String,
    val avgPrice: Double
)

@Serializable
data class DataGouvPrixCarburantStation(
    val id: String,
    val name: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val brand: String? = null,
    val fuels: List<DataGouvPrixCarburantFuelPrice> = emptyList()
)

@Serializable
data class DataGouvPrixCarburantFuelPrice(
    val name: String,
    val priceEur: Double
)
