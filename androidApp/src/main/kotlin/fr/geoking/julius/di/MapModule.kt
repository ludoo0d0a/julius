package fr.geoking.julius.di

import org.koin.core.context.GlobalContext

import fr.geoking.julius.api.belib.BelibAvailabilityClient
import fr.geoking.julius.api.belib.BelibAvailabilityProvider
import fr.geoking.julius.api.belib.BorneAvailabilityProvider
import fr.geoking.julius.api.belib.BorneAvailabilityProviderFactory
import fr.geoking.julius.api.chargy.ChargyProvider
import fr.geoking.julius.api.datagouv.DataGouvCampingClient
import fr.geoking.julius.api.datagouv.DataGouvCampingProvider
import fr.geoking.julius.api.datagouv.DataGouvElecProvider
import fr.geoking.julius.api.datagouv.DataGouvProvider
import fr.geoking.julius.api.datagouv.DataGouvPrixCarburantProvider
import fr.geoking.julius.api.minetur.SpainMineturProvider
import fr.geoking.julius.api.tankerkoenig.GermanyTankerkoenigProvider
import fr.geoking.julius.api.econtrol.AustriaEControlProvider
import fr.geoking.julius.api.belgium.BelgiumPetrolPricesClient
import fr.geoking.julius.api.belgium.BelgiumOfficialProvider
import fr.geoking.julius.api.dgeg.PortugalDgegProvider
import fr.geoking.julius.api.gas.GasApiClient
import fr.geoking.julius.api.madeira.MadeiraFuelPricesClient
import fr.geoking.julius.api.madeira.MadeiraOfficialProvider
import fr.geoking.julius.api.uk.UnitedKingdomCmaProvider
import fr.geoking.julius.api.italy.ItalyMimitProvider
import fr.geoking.julius.api.gas.GasApiProvider
import fr.geoking.julius.api.openvan.OpenVanCampClient
import fr.geoking.julius.api.openvan.OpenVanCampProvider
import fr.geoking.julius.api.ocpi.OcpiClient
import fr.geoking.julius.api.ocpi.OcpiPoiProvider
import fr.geoking.julius.api.ocpi.OcpiAvailabilityProvider
import fr.geoking.julius.api.openchargemap.OpenChargeMapClient
import fr.geoking.julius.api.openchargemap.OpenChargeMapProvider
import fr.geoking.julius.api.overpass.OverpassClient
import fr.geoking.julius.api.overpass.OverpassProvider
import fr.geoking.julius.api.parking.ParkingProviderFactory
import fr.geoking.julius.api.routing.OsrmRoutingClient
import fr.geoking.julius.api.routing.RoutePlanner
import fr.geoking.julius.api.routing.RoutingClient
import fr.geoking.julius.api.routex.RoutexProvider
import fr.geoking.julius.api.geocoding.AdresseDataGouvGeocodingClient
import fr.geoking.julius.api.geocoding.NominatimGeocodingClient
import fr.geoking.julius.api.geocoding.GeocodingClient
import fr.geoking.julius.api.transit.BelgiumTransitProvider
import fr.geoking.julius.api.transit.FranceTransitProvider
import fr.geoking.julius.api.transit.LuxembourgTransitProvider
import fr.geoking.julius.api.traffic.CitaGeoJsonTrafficClient
import fr.geoking.julius.api.traffic.CitaTrafficProvider
import fr.geoking.julius.api.traffic.GeographicRegion
import fr.geoking.julius.api.traffic.TomTomTrafficClient
import fr.geoking.julius.api.traffic.TomTomTrafficProvider
import fr.geoking.julius.api.traffic.TrafficProviderFactory
import fr.geoking.julius.api.weather.MetNorwayWeatherProvider
import fr.geoking.julius.api.weather.OpenMeteoGeocodingClient
import fr.geoking.julius.api.weather.OpenMeteoWeatherProvider
import fr.geoking.julius.api.weather.WeatherProvider
import fr.geoking.julius.api.weather.WeatherProviderFactory
import fr.geoking.julius.api.toll.OpenTollDataParser
import fr.geoking.julius.community.CommunityPoiRepository
import fr.geoking.julius.community.FavoritesRepository
import fr.geoking.julius.community.LocalCommunityPoiRepository
import fr.geoking.julius.community.LocalFavoritesRepository
import fr.geoking.julius.community.storage.CommunityPoiStorage
import fr.geoking.julius.community.storage.FavoritePoiStorage
import fr.geoking.julius.parking.ParkingAggregator
import fr.geoking.julius.poi.MergedPoiProvider
import fr.geoking.julius.poi.PoiProvider
import fr.geoking.julius.poi.SelectorPoiProvider
import fr.geoking.julius.transit.TransitAggregator
import fr.geoking.julius.transit.TransitApiSelector
import fr.geoking.julius.transit.TransitProvider
import fr.geoking.julius.toll.TollCalculator
import fr.geoking.julius.ui.OpenTollDataHelper
import org.koin.android.ext.koin.androidContext
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * Map-related dependencies (POI, routing, traffic, transit, parking, community, toll).
 * Loaded only when the user opens the map or route planning, so app startup stays light.
 */
val mapModule = module {

    // Map data source: Routex (default), flux instantané prix carburants, gas-api, or quotidien DataGouv.
    single<PoiProvider>(named("routex")) {
        RoutexProvider(get(), radiusKm = 5)
    }
    single<PoiProvider>(named("datagouvprixcarburant")) {
        DataGouvPrixCarburantProvider(get(), radiusKm = 10, limit = 100)
    }
    single<PoiProvider>(named("gasapi")) {
        GasApiProvider(get(), radiusKm = 10, limit = 100)
    }
    single { MadeiraFuelPricesClient(get()) }
    single<PoiProvider>(named("madeiraofficial")) {
        MadeiraOfficialProvider(madeiraClient = get(), overpassClient = get(), radiusKm = 10, limit = 50)
    }
    single<PoiProvider>(named("datagouv")) {
        DataGouvProvider(
            client = get(),
            radiusKm = 10,
            limit = 100,
            gasApiClient = null
        )
    }
    single<PoiProvider>(named("datagouvelec")) {
        DataGouvElecProvider(get(), radiusKm = 10, limit = 100)
    }
    single<OpenChargeMapClient> {
        OpenChargeMapClient(get(), apiKey = get<fr.geoking.julius.SettingsManager>().settings.value.openChargeMapKey.ifBlank { null })
    }
    single<PoiProvider>(named("openchargemap")) {
        OpenChargeMapProvider(get(), radiusKm = 10, limit = 50)
    }
    single<OcpiClient>(named("ecomovement_client")) {
        val sm = get<fr.geoking.julius.SettingsManager>()
        OcpiClient(
            client = get(),
            baseUrl = sm.settings.value.ecoMovementUrl,
            token = sm.settings.value.ecoMovementToken
        )
    }
    single<PoiProvider>(named("ecomovement")) {
        fr.geoking.julius.api.ocpi.OcpiPoiProvider(get(named("ecomovement_client")), providerName = "Eco-Movement")
    }
    single<PoiProvider>(named("chargy")) {
        ChargyProvider(get(), radiusKm = 15, limit = 100)
    }
    single { OverpassClient(get()) }
    single<PoiProvider>(named("overpass")) {
        OverpassProvider(get(), radiusKm = 5, limit = 100)
    }
    single { OpenVanCampClient(get()) }
    single<PoiProvider>(named("openvancamp")) {
        OpenVanCampProvider(openVanClient = get(), overpassClient = get(), radiusKm = 10, limit = 100)
    }
    single<PoiProvider>(named("spainminetur")) {
        SpainMineturProvider(get(), radiusKm = 10, limit = 50)
    }
    single<PoiProvider>(named("germanytankerkoenig")) {
        GermanyTankerkoenigProvider(get(), radiusKm = 10, limit = 50)
    }
    single<PoiProvider>(named("austriaecontrol")) {
        AustriaEControlProvider(get(), limit = 50)
    }
    single { BelgiumPetrolPricesClient(get()) }
    single<PoiProvider>(named("belgiumofficial")) {
        BelgiumOfficialProvider(belgiumClient = get(), overpassClient = get(), radiusKm = 10, limit = 50)
    }
    single<PoiProvider>(named("portugaldgeg")) {
        PortugalDgegProvider(get())
    }
    single<PoiProvider>(named("ionity")) {
        fr.geoking.julius.api.ocpi.OcpiPoiProvider(
            client = fr.geoking.julius.api.ocpi.OcpiClient(get(), baseUrl = "https://api.ionity.eu/ocpi/2.2.1", token = ""),
            providerName = "Ionity"
        )
    }
    single<PoiProvider>(named("fastned")) {
        fr.geoking.julius.api.ocpi.OcpiPoiProvider(
            client = fr.geoking.julius.api.ocpi.OcpiClient(get(), baseUrl = "https://api.fastned.nl/ocpi/2.2.1", token = ""),
            providerName = "Fastned"
        )
    }
    single<PoiProvider>(named("unitedkingdomcma")) {
        UnitedKingdomCmaProvider(get(), radiusKm = 15, limit = 100)
    }
    single<PoiProvider>(named("italymimit")) {
        ItalyMimitProvider(get(), radiusKm = 15, limit = 50)
    }
    single { DataGouvCampingClient(get()) }
    single<PoiProvider>(named("datagouvcamping")) {
        DataGouvCampingProvider(get(), radiusKm = 15, limit = 50)
    }
    single<PoiProvider>(named("selector")) {
        SelectorPoiProvider(
            routex = get(named("routex")),
            dataGouvPrixCarburant = get(named("datagouvprixcarburant")),
            gasApi = get(named("gasapi")),
            dataGouv = get(named("datagouv")),
            dataGouvElec = get(named("datagouvelec")),
            openChargeMap = get(named("openchargemap")),
            ecoMovement = get(named("ecomovement")),
            chargy = get(named("chargy")),
            openVanCamp = get(named("openvancamp")),
            spainMinetur = get(named("spainminetur")),
            germanyTankerkoenig = get(named("germanytankerkoenig")),
            austriaEControl = get(named("austriaecontrol")),
            belgiumOfficial = get(named("belgiumofficial")),
            portugalDgeg = get(named("portugaldgeg")),
            madeiraOfficial = get(named("madeiraofficial")),
            ionity = get(named("ionity")),
            fastned = get(named("fastned")),
            unitedKingdomCma = get(named("unitedkingdomcma")),
            italyMimit = get(named("italymimit")),
            openVanCampClient = get(),
            overpass = get(named("overpass")),
            dataGouvCamping = get(named("datagouvcamping")),
            settingsManager = get()
        )
    }
    single { CommunityPoiStorage(androidContext()) }
    single { FavoritePoiStorage(androidContext()) }
    single<CommunityPoiRepository> { LocalCommunityPoiRepository(get()) }
    single<FavoritesRepository> { LocalFavoritesRepository(get()) }
    single<PoiProvider> {
        MergedPoiProvider(base = get(named("selector")), communityRepo = get())
    }

    // Borne availability (e.g. Belib Paris): factory returns provider for current location.
    single { BelibAvailabilityClient(get()) }
    single<BorneAvailabilityProvider>(named("belib")) {
        BelibAvailabilityProvider(get(), radiusKm = 10, limit = 200)
    }
    single<BorneAvailabilityProvider>(named("ecomovement_availability")) {
        OcpiAvailabilityProvider(get(named("ecomovement_client")))
    }
    single<BorneAvailabilityProviderFactory> {
        BorneAvailabilityProviderFactory(
            belibProvider = get(named("belib")),
            ecoMovementProvider = get(named("ecomovement_availability"))
        )
    }

    // Traffic: Luxembourg CITA GeoJSON first; TomTom incidents as global fallback (needs TOMTOM_KEY).
    single { CitaGeoJsonTrafficClient(get()) }
    single { CitaTrafficProvider(get()) }
    single { TomTomTrafficClient(get()) }
    single { TomTomTrafficProvider(get(), fr.geoking.julius.BuildConfig.TOMTOM_KEY) }
    single<TrafficProviderFactory> {
        TrafficProviderFactory(
            listOf(
                GeographicRegion.Bbox(49.4, 5.7, 50.2, 6.6) to get<CitaTrafficProvider>(),
                GeographicRegion.Everywhere to get<TomTomTrafficProvider>()
            )
        )
    }

    // Geocoding for weather place names (global; no API key).
    single { OpenMeteoGeocodingClient(get()) }

    // Weather: MET Norway (Nordic), Open-Meteo Meteo-France blend (France + Corsica), Open-Meteo default elsewhere.
    single<WeatherProvider>(named("weather_met_norway")) {
        MetNorwayWeatherProvider(get())
    }
    single<WeatherProvider>(named("weather_open_meteo_fr")) {
        OpenMeteoWeatherProvider(
            get(),
            providerId = "open_meteo_meteofrance",
            models = "meteofrance_seamless"
        )
    }
    single<WeatherProvider>(named("weather_open_meteo")) {
        OpenMeteoWeatherProvider(get(), providerId = "open_meteo", models = null)
    }
    single<WeatherProviderFactory> {
        WeatherProviderFactory(
            listOf(
                GeographicRegion.Bbox(latMin = 55.0, lonMin = -10.0, latMax = 72.0, lonMax = 35.0) to get(named("weather_met_norway")),
                GeographicRegion.Bbox(latMin = 41.0, lonMin = -5.5, latMax = 51.6, lonMax = 10.0) to get(named("weather_open_meteo_fr")),
                GeographicRegion.Everywhere to get(named("weather_open_meteo"))
            )
        )
    }

    single<RoutingClient> { OsrmRoutingClient(get()) }
    single<RoutePlanner> { RoutePlanner(get()) }
    single<GeocodingClient> { NominatimGeocodingClient(get()) }

    // Transit (bus/tram): location-based provider selection (France, Luxembourg, Belgium).
    single<TransitProvider>(named("fr_ratp")) { FranceTransitProvider(get()) }
    single<TransitProvider>(named("lu_mobiliteit")) {
        val sm = get<fr.geoking.julius.SettingsManager>()
        val key = sm.settings.value.mobiliteitLuxembourgKey.ifBlank { fr.geoking.julius.BuildConfig.MOBILITEIT_LUXEMBOURG_KEY }
        LuxembourgTransitProvider(get(), key)
    }
    single<TransitProvider>(named("be_stib")) { BelgiumTransitProvider(get()) }
    single<List<TransitProvider>>(named("transitProviders")) {
        listOf(get(named("fr_ratp")), get(named("lu_mobiliteit")), get(named("be_stib")))
    }
    single { TransitApiSelector(get(named("transitProviders"))) }
    single { TransitAggregator(get(named("transitProviders")), get()) }

    // Parking POIs: LiveParking + ParkAPI + OSM, aggregated via factory
    single<ParkingProviderFactory> { ParkingProviderFactory(get(), get()) }
    single<ParkingAggregator> { get<ParkingProviderFactory>().createAggregator() }

    single { OpenTollDataHelper(androidContext()) }
    single<TollCalculator> {
        val settingsManager = get<fr.geoking.julius.SettingsManager>()
        TollCalculator(dataSource = {
            val path = settingsManager.settings.value.tollDataPath ?: return@TollCalculator null
            val file = java.io.File(path)
            if (!file.exists()) return@TollCalculator null
            OpenTollDataParser.parse(file.readText())
        })
    }
}

/** All map/route dependencies resolved after [MapModuleLoader.ensureLoaded]. */
data class MapDeps(
    val poiProvider: PoiProvider,
    val availabilityProviderFactory: BorneAvailabilityProviderFactory,
    val communityRepo: CommunityPoiRepository,
    val favoritesRepo: FavoritesRepository,
    val trafficProviderFactory: TrafficProviderFactory,
    val weatherProviderFactory: WeatherProviderFactory,
    val routePlanner: RoutePlanner,
    val routingClient: RoutingClient,
    val tollCalculator: TollCalculator,
    val geocodingClient: GeocodingClient
)

/**
 * Loads [mapModule] only when needed (e.g. when user opens the map). Call from a [org.koin.core.component.KoinComponent]
 * before resolving any map-related dependency, or use [ensureLoaded] and then resolve via Koin.
 */
object MapModuleLoader {

    @Volatile
    private var loaded = false

    private val lock = Any()

    fun ensureLoaded() {
        if (loaded) return
        synchronized(lock) {
            if (loaded) return
            android.util.Log.d("MapModuleLoader", "Loading map module (first map open)")
            GlobalContext.get().loadModules(listOf(mapModule))
            loaded = true
        }
    }
}
