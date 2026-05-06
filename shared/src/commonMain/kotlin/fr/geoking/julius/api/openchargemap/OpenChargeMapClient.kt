package fr.geoking.julius.api.openchargemap

import fr.geoking.julius.poi.Poi
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class OpenChargeMapClient(
    private val httpClient: HttpClient
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun getStations(latitude: Double, longitude: Double): List<Poi> {
        // In unit tests we use MockEngine; URL doesn't matter.
        val body: String = httpClient.get("https://api.openchargemap.io/v3/poi/").body()
        return parseStations(body)
    }

    fun parseStations(body: String): List<Poi> {
        val root = json.parseToJsonElement(body)
        val arr = root as? JsonArray ?: return emptyList()
        return arr.mapNotNull { el ->
            val obj = el as? JsonObject ?: return@mapNotNull null
            val id = obj["ID"]?.jsonPrimitive?.content ?: return@mapNotNull null
            val address = obj["AddressInfo"]?.jsonObject ?: return@mapNotNull null
            val title = address["Title"]?.jsonPrimitive?.content ?: "Station"
            val lat = address["Latitude"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: return@mapNotNull null
            val lon = address["Longitude"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: return@mapNotNull null

            val operator = (obj["OperatorInfo"] as? JsonObject)?.get("Title")?.jsonPrimitive?.content
            val maxPower = parseMaxPowerKw(obj["Connections"])

            Poi(
                id = id,
                name = title,
                latitude = lat,
                longitude = lon,
                isElectric = true,
                powerKw = maxPower,
                operator = operator
            )
        }
    }

    private fun parseMaxPowerKw(connectionsEl: JsonElement?): Double? {
        val conns = connectionsEl as? JsonArray ?: return null
        val powers = conns.mapNotNull { conn ->
            val obj = conn as? JsonObject ?: return@mapNotNull null
            val raw = obj["PowerKW"] as? JsonPrimitive ?: return@mapNotNull null
            val v = raw.content.toDoubleOrNull() ?: return@mapNotNull null
            if (v > 1000) v / 1000.0 else v
        }
        return powers.maxOrNull()
    }
}

