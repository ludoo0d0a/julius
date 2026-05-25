package fr.geoking.julius.api.datagouv

import io.ktor.client.HttpClient
import kotlinx.serialization.json.*

class DataGouvClient(
    private val httpClient: HttpClient
) {
    private val json = Json { ignoreUnknownKeys = true }

    private fun JsonElement?.stringOrNull(): String? =
        (this as? JsonPrimitive)?.content

    private fun JsonElement?.doubleOrNull(): Double? =
        (this as? JsonPrimitive)?.content?.toDoubleOrNull()

    fun parseRecords(body: String): List<DataGouvStation> {
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

    fun parseStationFromRecord(record: JsonObject): DataGouvStation? {
        val id = record["id"].stringOrNull() ?: return null

        val lat = record["latitude"].doubleOrNull()
        val lon = record["longitude"].doubleOrNull()
        val geo = parseGeo(record)

        val latitude = lat ?: geo?.first ?: return null
        val longitude = lon ?: geo?.second ?: return null

        val city = record["ville"].stringOrNull()
        val cp = record["cp"].stringOrNull()
        val addressLine = record["adresse"].stringOrNull()
        val address = listOfNotNull(addressLine, cp, city).joinToString(", ").ifBlank { "" }

        val brand = record["marque"].stringOrNull()
        val nameFromField = record["nom"].stringOrNull()
        val name =
            nameFromField
                ?: brand
                ?: (city?.let { "Station $it" })
                ?: "Station"

        val prices = parsePrices(record)

        return DataGouvStation(
            id = id,
            name = name,
            address = address,
            latitude = latitude,
            longitude = longitude,
            brand = brand,
            prices = prices
        )
    }

    private fun parsePrices(record: JsonObject): List<DataGouvPrice> {
        val prix = record["prix"] ?: return emptyList()
        val entries: List<JsonElement> =
            when (prix) {
                is JsonArray -> prix
                is JsonObject -> listOf(prix)
                is JsonPrimitive -> {
                    // Sometimes `prix` is a JSON string of an array.
                    val str = prix.content
                    runCatching { json.parseToJsonElement(str) }.getOrNull() as? JsonArray ?: return emptyList()
                }
            }

        return entries.mapNotNull { el ->
            val obj = el as? JsonObject ?: return@mapNotNull null
            val name = (obj["nom"] ?: obj["@nom"]).stringOrNull() ?: return@mapNotNull null
            val raw = (obj["valeur"] ?: obj["@valeur"])
            val value = raw.doubleOrNull() ?: return@mapNotNull null
            DataGouvPrice(fuelName = name, price = value)
        }
    }
}

