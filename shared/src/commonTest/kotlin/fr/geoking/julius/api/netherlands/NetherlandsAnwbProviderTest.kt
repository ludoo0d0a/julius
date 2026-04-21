package fr.geoking.julius.api.netherlands

import fr.geoking.julius.poi.PoiCategory
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NetherlandsAnwbProviderTest {

    private var lastUrl: String? = null

    private val mockEngine = MockEngine { request ->
        lastUrl = request.url.toString()
        respond(
            content = """{"value": []}""",
            headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
        )
    }

    private val httpClient = HttpClient(mockEngine) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    private val provider = NetherlandsAnwbProvider(httpClient)

    @Test
    fun testBboxSelectionForNetherlands() = runBlocking {
        // Amsterdam
        provider.getGasStations(52.3676, 4.9041)
        assertTrue(lastUrl?.contains("bounding-box-filter=50.7,3.3,53.6,7.3") == true)
    }

    @Test
    fun testBboxSelectionForLuxembourg() = runBlocking {
        // Luxembourg City
        provider.getGasStations(49.6116, 6.1319)
        assertTrue(lastUrl?.contains("bounding-box-filter=49.4,5.7,50.2,6.6") == true)
    }

    @Test
    fun testBboxSelectionForBelgium() = runBlocking {
        // Brussels
        provider.getGasStations(50.8503, 4.3517)
        assertTrue(lastUrl?.contains("bounding-box-filter=49.4,2.4,51.6,6.5") == true)
    }

    @Test
    fun testBboxSelectionOutsideCoverage() = runBlocking {
        // Paris
        lastUrl = null
        val pois = provider.getGasStations(48.8566, 2.3522)
        assertTrue(pois.isEmpty())
        assertTrue(lastUrl == null)
    }

    @Test
    fun testSupportedCategories() {
        assertEquals(setOf(PoiCategory.Gas), provider.supportedCategories())
    }
}
