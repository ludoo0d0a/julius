package fr.geoking.julius.api.dgeg

import fr.geoking.julius.poi.FuelPrice
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class PortugalDgegProviderTest {

    private val mockJsonResponse = """
    {
      "status": true,
      "mensagem": "sucesso",
      "resultado": [
        {
          "Id": 123,
          "Nome": "GALP LISBOA",
          "Morada": "Av. Liberdade",
          "Localidade": "Lisboa",
          "Latitude": 38.7223,
          "Longitude": -9.1393,
          "Marca": "Galp",
          "Combustivel": "Gasóleo simples",
          "Preco": "1,549 €/litro"
        },
        {
          "Id": 123,
          "Nome": "GALP LISBOA",
          "Morada": "Av. Liberdade",
          "Localidade": "Lisboa",
          "Latitude": 38.7223,
          "Longitude": -9.1393,
          "Marca": "Galp",
          "Combustivel": "Gasolina simples 95",
          "Preco": "1,749 €/litro"
        }
      ]
    }
    """.trimIndent()

    @Test
    fun testProviderFetchesAndParsesCorrectly() = runBlocking {
        val mockEngine = MockEngine { _ ->
            respond(
                content = mockJsonResponse,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            )
        }

        val client = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }
        val provider = PortugalDgegProvider(client)
        val stations = provider.getGasStations(38.72, -9.14, null)

        assertEquals(1, stations.size)
        val station = stations.first()
        assertEquals("GALP LISBOA", station.name)
        assertEquals("Galp", station.brand)
        assertNotNull(station.fuelPrices)
        assertEquals(2, station.fuelPrices?.size)
        assertEquals(1.549, station.fuelPrices?.find { it.fuelName == "Gazole" }?.price)
        assertEquals(1.749, station.fuelPrices?.find { it.fuelName == "SP95" }?.price)
    }
}
