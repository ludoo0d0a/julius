package fr.geoking.julius.api.mexico

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText

data class MexicoPlace(
    val id: String,
    val name: String,
    val lat: Double,
    val lon: Double
)

data class MexicoPrice(
    val placeId: String,
    val type: String,
    val price: Double
)

class MexicoCREClient(private val client: HttpClient) {
    private val placesUrl = "https://publicacionexterna.azurewebsites.net/publicaciones/places"
    private val pricesUrl = "https://publicacionexterna.azurewebsites.net/publicaciones/prices"

    suspend fun fetchPlaces(): List<MexicoPlace> {
        val response = client.get(placesUrl) {
            header("User-Agent", "Mozilla/5.0 (compatible; Julius/1.0)")
        }
        val xml = response.bodyAsText()
        return parsePlaces(xml)
    }

    suspend fun fetchPrices(): List<MexicoPrice> {
        val response = client.get(pricesUrl) {
            header("User-Agent", "Mozilla/5.0 (compatible; Julius/1.0)")
        }
        val xml = response.bodyAsText()
        return parsePrices(xml)
    }

    private fun parsePlaces(xml: String): List<MexicoPlace> {
        val places = mutableListOf<MexicoPlace>()
        val placeRegex = Regex("<place\\s+place_id=\"(\\d+)\">([\\s\\S]*?)</place>")
        placeRegex.findAll(xml).forEach { match ->
            val id = match.groupValues[1]
            val body = match.groupValues[2]
            val name = Regex("<name>([\\s\\S]*?)</name>").find(body)?.groupValues?.get(1)?.trim() ?: ""
            val lon = Regex("<x>([\\s\\S]*?)</x>").find(body)?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
            val lat = Regex("<y>([\\s\\S]*?)</y>").find(body)?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
            if (lat != 0.0 && lon != 0.0) {
                places.add(MexicoPlace(id, name, lat, lon))
            }
        }
        return places
    }

    private fun parsePrices(xml: String): List<MexicoPrice> {
        val prices = mutableListOf<MexicoPrice>()
        val placeRegex = Regex("<place\\s+place_id=\"(\\d+)\">([\\s\\S]*?)</place>")
        placeRegex.findAll(xml).forEach { match ->
            val id = match.groupValues[1]
            val body = match.groupValues[2]
            val gpRegex = Regex("<gas_price\\s+type=\"(\\w+)\">([^<]+)</gas_price>")
            gpRegex.findAll(body).forEach { gpMatch ->
                val type = gpMatch.groupValues[1]
                val price = gpMatch.groupValues[2].toDoubleOrNull() ?: 0.0
                if (price > 0) {
                    prices.add(MexicoPrice(id, type, price))
                }
            }
        }
        return prices
    }
}
