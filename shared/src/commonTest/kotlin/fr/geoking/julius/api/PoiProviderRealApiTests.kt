package fr.geoking.julius.api

import fr.geoking.julius.agents.TestPropertyReader
import fr.geoking.julius.agents.createTestHttpClientEngine
import fr.geoking.julius.api.datagouv.DataGouvCampingClient
import fr.geoking.julius.api.datagouv.DataGouvCampingProvider
import fr.geoking.julius.api.datagouv.DataGouvElecProvider
import fr.geoking.julius.api.datagouv.DataGouvProvider
import fr.geoking.julius.api.etalab.EtalabProvider
import fr.geoking.julius.api.gas.GasApiProvider
import fr.geoking.julius.api.openchargemap.OpenChargeMapClient
import fr.geoking.julius.api.openchargemap.OpenChargeMapProvider
import fr.geoking.julius.api.overpass.OverpassClient
import fr.geoking.julius.api.overpass.OverpassProvider
import fr.geoking.julius.api.routex.RoutexProvider
import fr.geoking.julius.poi.PoiCategory
import fr.geoking.julius.poi.PoiSearchRequest
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertTrue

class PoiProviderRealApiTests {

    private fun createHttpClient(): HttpClient {
        return HttpClient(createTestHttpClientEngine()) {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                })
            }
        }
    }

    private val parisLat = 48.8566
    private val parisLon = 2.3522

    @Test
    fun testRoutexProvider() = runBlocking {
        val client = createHttpClient()
        try {
            val provider = RoutexProvider(client)
            val pois = provider.getGasStations(parisLat, parisLon)
            println("Routex returned ${pois.size} POIs")
            assertTrue(pois.isNotEmpty(), "Routex should return some gas stations in Paris")
        } finally {
            client.close()
        }
    }

    @Test
    fun testEtalabProvider() = runBlocking {
        val client = createHttpClient()
        try {
            val provider = EtalabProvider(client)
            val pois = provider.getGasStations(parisLat, parisLon)
            println("Etalab returned ${pois.size} POIs")
            assertTrue(pois.isNotEmpty(), "Etalab should return some gas stations in Paris")
        } finally {
            client.close()
        }
    }

    @Test
    fun testGasApiProvider() = runBlocking {
        val client = createHttpClient()
        try {
            val provider = GasApiProvider(client)
            val pois = provider.getGasStations(parisLat, parisLon)
            println("GasApi returned ${pois.size} POIs")
            assertTrue(pois.isNotEmpty(), "GasApi should return some gas stations in Paris")
        } finally {
            client.close()
        }
    }

    @Test
    fun testDataGouvProvider() = runBlocking {
        val client = createHttpClient()
        try {
            val provider = DataGouvProvider(client)
            val pois = provider.getGasStations(parisLat, parisLon)
            println("DataGouv returned ${pois.size} POIs")
            assertTrue(pois.isNotEmpty(), "DataGouv should return some gas stations in Paris")
        } finally {
            client.close()
        }
    }

    @Test
    fun testDataGouvElecProvider() = runBlocking {
        val client = createHttpClient()
        try {
            val provider = DataGouvElecProvider(client)
            val pois = provider.getGasStations(parisLat, parisLon)
            println("DataGouvElec returned ${pois.size} POIs")
            assertTrue(pois.isNotEmpty(), "DataGouvElec should return some EV stations in Paris")
        } finally {
            client.close()
        }
    }

    @Test
    fun testOpenChargeMapProvider() = runBlocking {
        val apiKey = TestPropertyReader.getProperty("OPENCHARGEMAP_KEY")
        if (apiKey.isNullOrBlank()) {
            println("⚠️ Skipping OpenChargeMap test - no OPENCHARGEMAP_KEY provided")
            return@runBlocking
        }
        val client = createHttpClient()
        try {
            val ocmClient = OpenChargeMapClient(client, apiKey = apiKey)
            val provider = OpenChargeMapProvider(ocmClient)
            val pois = provider.getGasStations(parisLat, parisLon)
            println("OpenChargeMap returned ${pois.size} POIs")
            assertTrue(pois.isNotEmpty(), "OpenChargeMap should return some EV stations in Paris")
        } finally {
            client.close()
        }
    }

    @Test
    fun testOverpassProvider() = runBlocking {
        val client = createHttpClient()
        try {
            val overpassClient = OverpassClient(client)
            val provider = OverpassProvider(overpassClient)

            // Test for Toilets
            val toilets = provider.search(PoiSearchRequest(parisLat, parisLon, categories = setOf(PoiCategory.Toilet)))
            println("Overpass Toilets: ${toilets.size}")
            assertTrue(toilets.isNotEmpty(), "Overpass should return some toilets in Paris")

            // Test for Restaurants
            val restaurants = provider.search(PoiSearchRequest(parisLat, parisLon, categories = setOf(PoiCategory.Restaurant)))
            println("Overpass Restaurants: ${restaurants.size}")
            assertTrue(restaurants.isNotEmpty(), "Overpass should return some restaurants in Paris")
        } finally {
            client.close()
        }
    }

    @Test
    fun testDataGouvCampingProvider() = runBlocking {
        val client = createHttpClient()
        try {
            val campingClient = DataGouvCampingClient(client)
            val provider = DataGouvCampingProvider(campingClient)
            val montpellierLat = 43.6107
            val montpellierLon = 3.8767
            val pois = provider.search(PoiSearchRequest(montpellierLat, montpellierLon, categories = setOf(PoiCategory.CaravanSite)))
            println("DataGouvCamping returned ${pois.size} POIs")
            assertTrue(pois.isNotEmpty(), "DataGouvCamping should return some caravan sites in Herault")
        } finally {
            client.close()
        }
    }
}
