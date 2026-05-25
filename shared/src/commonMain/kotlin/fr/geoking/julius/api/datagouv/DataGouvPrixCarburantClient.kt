package fr.geoking.julius.api.datagouv

import io.ktor.client.HttpClient
import kotlinx.serialization.json.*

/**
 * Wrapper around Data.gouv ODS API for French fuel prices. Only parsing helpers are used by unit tests.
 */
class DataGouvPrixCarburantClient(
    private val httpClient: HttpClient
) {
    private val json = Json { ignoreUnknownKeys = true }

    private fun JsonElement?.stringOrNull(): String? =
        (this as? JsonPrimitive)?.content

    private fun JsonElement?.doubleOrNull(): Double? =
        (this as? JsonPrimitive)?.content?.toDoubleOrNull()

    data class Fuel(
        val name: String,
        val priceEur: Double
    )

    data class Station(
        val id: String,
        val name: String,
        val address: String?,
        val latitude: Double,
        val longitude: Double,
        val fuels: List<Fuel>
    )

    fun parseRecords(body: String): List<Station> {
        val root = json.parseToJsonElement(body).jsonObject
        val results = root["results"] ?: return emptyList()

        val records: List<JsonObject> =
            when (results) {
                is JsonArray -> results.mapNotNull { it as? JsonObject }
                is JsonObject -> listOf(results)
                else -> emptyList()
            }

        return records.mapNotNull(::parseStationFromRecord)
    }

    fun parseGeo(record: JsonObject): Pair<Double, Double>? {
        fun coordsOf(key: String): Pair<Double, Double>? {
            val obj = record[key]?.jsonObject ?: return null
            val coords = obj["coordinates"] as? JsonArray ?: return null
            val lon = coords.getOrNull(0).doubleOrNull() ?: return null
            val lat = coords.getOrNull(1).doubleOrNull() ?: return null
            return lat to lon
        }
        return coordsOf("geom") ?: coordsOf("geolocation")
    }

    fun parseStationFromRecord(record: JsonObject): Station? {
        val id = record["id"].stringOrNull() ?: return null

        val (latitude, longitude) = parseLatLon(record) ?: return null
        val city = record["ville"].stringOrNull()
        val brand = record["marque"].stringOrNull()
        val nameFromField = record["nom"].stringOrNull()
        val name =
            nameFromField
                ?: brand
                ?: (city?.let { "Station $it" })
                ?: "Station"

        val address = record["adresse"].stringOrNull()
        val fuels = parseFuels(record)

        return Station(
            id = id,
            name = name,
            address = address,
            latitude = latitude,
            longitude = longitude,
            fuels = fuels
        )
    }

    private fun parseLatLon(record: JsonObject): Pair<Double, Double>? {
        val lat = record["latitude"].doubleOrNull()
        val lon = record["longitude"].doubleOrNull()

        if (lat != null && lon != null) {
            // DataGouv sometimes returns scaled integers (e.g. "4886205" for 48.86205).
            val scaledLat = if (lat > 1000) lat / 100000.0 else lat
            val scaledLon = if (lon > 1000) lon / 100000.0 else lon
            return scaledLat to scaledLon
        }

        return parseGeo(record)
    }

    private fun parseFuels(record: JsonObject): List<Fuel> {
        // Variant A: `prix` field is array of {nom,valeur} objects or a JSON string of array.
        val fuelsFromPrix = parseFuelsFromPrixField(record)
        if (fuelsFromPrix.isNotEmpty()) return fuelsFromPrix

        // Variant B: top-level fields like gazole_prix, sp95_prix
        val out = mutableListOf<Fuel>()
        fun addIfPresent(field: String, name: String) {
            val v = record[field].doubleOrNull() ?: return
            out += Fuel(name = name, priceEur = v)
        }
        addIfPresent("gazole_prix", "Gazole")
        addIfPresent("sp95_prix", "SP95")
        addIfPresent("sp98_prix", "SP98")
        addIfPresent("e10_prix", "E10")
        addIfPresent("e85_prix", "E85")
        return out
    }

    private fun parseFuelsFromPrixField(record: JsonObject): List<Fuel> {
        val prix = record["prix"] ?: return emptyList()
        val entries: List<JsonElement> =
            when (prix) {
                is JsonArray -> prix
                is JsonObject -> listOf(prix)
                is JsonPrimitive -> {
                    val str = prix.content
                    runCatching { json.parseToJsonElement(str) }.getOrNull() as? JsonArray ?: return emptyList()
                }
            }

        return entries.mapNotNull { el ->
            val obj = el as? JsonObject ?: return@mapNotNull null
            val name = (obj["nom"] ?: obj["@nom"]).stringOrNull() ?: return@mapNotNull null
            val raw = (obj["valeur"] ?: obj["@valeur"])
            val value = raw.doubleOrNull() ?: return@mapNotNull null
            Fuel(name = name, priceEur = value)
        }
    }
}

