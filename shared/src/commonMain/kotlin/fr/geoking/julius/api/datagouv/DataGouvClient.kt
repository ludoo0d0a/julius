package fr.geoking.julius.api.datagouv

import fr.geoking.julius.poi.IrveDetails
import fr.geoking.julius.shared.network.NetworkException
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.encodeURLParameter
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
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
 * The export may omit historical fields such as `marque`; [DataGouvProvider] can enrich brands via Gas API.
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
        val effectiveLimit = limit.coerceIn(1, 100)
        // ODSQL: within_distance(geo_field, GEOM'POINT(lng lat)', Xkm); POINT is (longitude, latitude).
        val where = "within_distance(geom, geom'POINT($longitude $latitude)', ${radiusKm}km)"
        val encodedWhere = where.encodeURLParameter()
        val url = "$baseUrl/records?where=$encodedWhere&limit=$effectiveLimit"

        val response = client.get(url)
        val body = response.bodyAsText()
        if (response.status.value != 200) {
            throw NetworkException(
                httpCode = response.status.value,
                message = "DataGouv API error: $body",
                url = url,
                provider = "DataGouv"
            )
        }
        return parseRecords(body)
    }

    internal fun parseRecords(body: String): List<DataGouvStation> {
        val response = try {
            json.decodeFromString<DataGouvOdsResponse>(body)
        } catch (e: Exception) {
            return emptyList()
        }

        val stations = mutableMapOf<String, DataGouvStation>()
        for (item in response.results) {
            val record = item as? JsonObject ?: continue
            val fields = record["fields"]?.let { f -> (f as? JsonObject) } ?: record
            parseStationFromRecord(fields)?.let { station ->
                // API may return one row per fuel type; merge by id so we keep one POI per station
                stations[station.id] = stations[station.id]?.let { existing ->
                    existing.copy(
                        prices = (existing.prices + station.prices).distinctBy { it.fuelName }
                    )
                } ?: station
            }
        }
        return stations.values.toList()
    }

    internal fun parseStationFromRecord(record: JsonObject): DataGouvStation? {
        val id = record["id"]?.jsonPrimitive?.contentOrNull
            ?: record["id_"]?.jsonPrimitive?.contentOrNull
            ?: return null
        val (lat, lng) = parseGeo(record) ?: return null
        val adresse = record["adresse"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        val ville = record["ville"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        val cp = record["cp"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        val address = listOf(adresse, cp, ville).filter { it.isNotBlank() }.joinToString(", ")
        val brand = record["marque"]?.jsonPrimitive?.contentOrNull?.trim()
            ?: record["enseigne"]?.jsonPrimitive?.contentOrNull?.trim()
            ?: record["libelle_enseigne"]?.jsonPrimitive?.contentOrNull?.trim()
        val name = record["nom"]?.jsonPrimitive?.contentOrNull?.trim()
            ?: record["name"]?.jsonPrimitive?.contentOrNull?.trim()
            ?: brand
            ?: if (ville.isNotBlank()) "Station $ville" else "Station"
        val prices = parsePrices(record)
        return DataGouvStation(
            id = id,
            name = name.ifBlank { if (ville.isNotBlank()) "Station $ville" else "Station" },
            address = address.ifBlank { "$cp $ville" },
            latitude = lat,
            longitude = lng,
            brand = brand,
            prices = prices
        )
    }

    internal fun parseGeo(record: JsonObject): Pair<Double, Double>? {
        val lat = record["latitude"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()
        val lng = record["longitude"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()
        if (lat != null && lng != null) return Pair(lat, lng)

        val geo = record["geom"]?.jsonObject
            ?: record["geolocation"]?.jsonObject
            ?: record["coordonnees_geo"]?.jsonObject

        if (geo != null) {
            // Handle GeoJSON-style { "type": "Point", "coordinates": [lng, lat] }
            val coords = geo["coordinates"]?.jsonArray
            if (coords != null && coords.size >= 2) {
                val lng2 = coords[0].jsonPrimitive.contentOrNull?.toDoubleOrNull()
                val lat2 = coords[1].jsonPrimitive.contentOrNull?.toDoubleOrNull()
                if (lat2 != null && lng2 != null) return Pair(lat2, lng2)
            }
            // Handle ODS Explore v2.1 style { "lat": 48.8, "lon": 2.3 }
            val latVal = geo["lat"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()
            val lonVal = geo["lon"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()
                ?: geo["lng"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()
            if (latVal != null && lonVal != null) return Pair(latVal, lonVal)
        }
        return null
    }

    private fun parsePrices(record: JsonObject): List<DataGouvPrice> {
        val list = mutableListOf<DataGouvPrice>()
        val prixElement = record["prix"]
        val prixArray = try {
            if (prixElement is kotlinx.serialization.json.JsonPrimitive && prixElement.content.startsWith("[")) {
                json.parseToJsonElement(prixElement.content).jsonArray
            } else {
                prixElement?.jsonArray
            }
        } catch (e: Exception) {
            null
        }

        if (prixArray != null) {
            for (p in prixArray) {
                val obj = p as? JsonObject ?: continue
                val nom = obj["nom"]?.jsonPrimitive?.contentOrNull ?: obj["name"]?.jsonPrimitive?.contentOrNull ?: continue
                val raw = obj["valeur"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()
                    ?: obj["value"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()
                val outOfStock = obj["rupture"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() ?: false
                val maj = obj["maj"]?.jsonPrimitive?.contentOrNull
                if (raw != null) {
                    list.add(DataGouvPrice(
                        fuelName = nom,
                        price = raw,
                        updatedAt = maj,
                        outOfStock = outOfStock
                    ))
                }
            }
        }

        // Single fuel per record (flattened): prix_nom, prix_valeur
        val singleNom = record["prix_nom"]?.jsonPrimitive?.contentOrNull
        val singleVal = record["prix_valeur"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()
        val singleRupture = record["prix_rupture"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() ?: false
        if (singleNom != null && singleVal != null) {
            val exists = list.any { it.fuelName == singleNom }
            if (!exists) {
                list.add(DataGouvPrice(
                    fuelName = singleNom,
                    price = singleVal,
                    updatedAt = record["prix_maj"]?.jsonPrimitive?.contentOrNull,
                    outOfStock = singleRupture
                ))
            }
        }
        return list
    }
}

@Serializable
internal data class DataGouvOdsResponse(
    @Serializable(with = OdsResultsSerializer::class)
    val results: List<JsonElement> = emptyList()
)

internal object OdsResultsSerializer : KSerializer<List<JsonElement>> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("OdsResults")

    override fun deserialize(decoder: Decoder): List<JsonElement> {
        val input = decoder as? kotlinx.serialization.json.JsonDecoder ?: return emptyList()
        val element = input.decodeJsonElement()
        return when (element) {
            is JsonArray -> element.toList()
            is JsonObject -> listOf(element)
            else -> emptyList()
        }
    }

    override fun serialize(encoder: Encoder, value: List<JsonElement>) {
        val output = encoder as? kotlinx.serialization.json.JsonEncoder ?: return
        output.encodeJsonElement(JsonArray(value))
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

/** Fuel price from data.gouv.fr / Gas API. */
@Serializable
data class DataGouvPrice(
    val fuelName: String,
    val price: Double,
    val updatedAt: String? = null,
    val outOfStock: Boolean = false
)
