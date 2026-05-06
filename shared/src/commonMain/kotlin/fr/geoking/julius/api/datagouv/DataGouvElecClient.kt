package fr.geoking.julius.api.datagouv

import io.ktor.client.HttpClient
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive

class DataGouvElecClient(
    private val httpClient: HttpClient
) {
    data class Station(
        val id: String,
        val name: String,
        val address: String?,
        val latitude: Double,
        val longitude: Double,
        val puissanceKw: Double?
    )

    private fun JsonElement?.stringOrNull(): String? =
        (this as? JsonPrimitive)?.content

    private fun JsonElement?.doubleOrNull(): Double? =
        (this as? JsonPrimitive)?.content?.toDoubleOrNull()

    fun parseStation(record: JsonObject): Station? {
        val id = record["id_station_itinerance"].stringOrNull() ?: return null

        // Filter out non-FR stations: the dataset sometimes includes LU addresses; tests rely on this.
        val address = record["adresse_station"].stringOrNull()
        if (address != null && address.contains("Luxembourg", ignoreCase = true)) return null
        if (!id.startsWith("FR", ignoreCase = true)) return null

        val lat = record["consolidated_latitude"].doubleOrNull() ?: return null
        val lon = record["consolidated_longitude"].doubleOrNull() ?: return null

        val brand = record["nom_enseigne"].stringOrNull()
        val name =
            record["nom_station"].stringOrNull()
                ?: brand
                ?: "Station"

        val rawPower = record["puissance_nominale"].doubleOrNull()
        val powerKw = rawPower?.let { if (it > 1000) it / 1000.0 else it }

        return Station(
            id = id,
            name = name,
            address = address,
            latitude = lat,
            longitude = lon,
            puissanceKw = powerKw
        )
    }
}

