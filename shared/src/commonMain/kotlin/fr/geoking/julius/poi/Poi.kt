package fr.geoking.julius.poi

import fr.geoking.julius.api.routex.RoutexSiteDetails
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Category of POI for unified search. Extensible: add new values and wire providers as needed.
 * Used by [PoiSearchRequest] and [Poi.poiCategory].
 */
enum class PoiCategory {
    /** Fuel / gas stations (Routex, Etalab, GasApi, DataGouv). */
    Gas,
    /** EV charging / IRVE (DataGouvElec, OpenChargeMap, EcoMovement). */
    Irve,
    /** Public toilets (e.g. Overpass amenity=toilets). */
    Toilet,
    /** Drinking water / fountains (e.g. Overpass amenity=drinking_water). */
    DrinkingWater,
    /** Camp sites (OSM tourism=camp_site). */
    Camping,
    /** Caravan / motorhome aires (OSM tourism=caravan_site; data.gouv.fr aires). */
    CaravanSite,
    /** Picnic areas (OSM tourism=picnic_site). */
    PicnicSite,
    /** Truck stops (OSM amenity=truck_stop). */
    TruckStop,
    /** Rest areas (OSM highway=rest_area). */
    RestArea,
    /** Restaurants (OSM amenity=restaurant). */
    Restaurant,
    /** Fast food (OSM amenity=fast_food). */
    FastFood,
    /** Radars / speed cameras (OSM highway=speed_camera). */
    Radar;
    companion object {
        /** OSM amenity tag value for this category, when applicable. */
        fun fromOsmAmenity(amenity: String): PoiCategory? = when (amenity) {
            "toilets" -> Toilet
            "drinking_water" -> DrinkingWater
            "truck_stop" -> TruckStop
            "restaurant" -> Restaurant
            "fast_food" -> FastFood
            else -> null
        }
        /** OSM tourism tag value for this category. */
        fun fromOsmTourism(tourism: String): PoiCategory? = when (tourism) {
            "camp_site" -> Camping
            "caravan_site" -> CaravanSite
            "picnic_site" -> PicnicSite
            else -> null
        }
        /** OSM highway tag value for this category (e.g. rest_area). */
        fun fromOsmHighway(highway: String): PoiCategory? = when (highway) {
            "rest_area" -> RestArea
            "speed_camera" -> Radar
            else -> null
        }
        /** Resolve category from OSM tags (amenity, tourism, highway). */
        fun fromOsmTags(tags: Map<String, String>): PoiCategory? {
            tags["amenity"]?.let { fromOsmAmenity(it) }?.let { return it }
            tags["tourism"]?.let { fromOsmTourism(it) }?.let { return it }
            tags["highway"]?.let { fromOsmHighway(it) }?.let { return it }
            return null
        }
    }
}

/**
 * POI data source. [providesFuel] / [providesElectric] classify providers for UI (e.g. filter mode), not OSM extras.
 */
enum class PoiProviderType(
    val providesFuel: Boolean = false,
    val providesElectric: Boolean = false,
    val providesPrices: Boolean = false,
    val providesLocation: Boolean = true,
    val eligibleToAuto: Boolean = true,
    val supportedCountries: Set<String> = emptySet(),
) {
    Routex(providesFuel = true, providesPrices = true, eligibleToAuto = false),
    Etalab(providesFuel = true, providesPrices = true, supportedCountries = setOf("FR"), eligibleToAuto = false),
    GasApi(providesFuel = true, providesPrices = true, supportedCountries = setOf("FR")),
    DataGouv(providesFuel = true, providesPrices = true, supportedCountries = setOf("FR")),
    DataGouvElec(providesElectric = true, supportedCountries = setOf("FR")),
    OpenChargeMap(providesElectric = true),
    EcoMovement(providesElectric = true),
    Chargy(providesElectric = true, supportedCountries = setOf("LU")),
    /** Luxembourg OSM fuel + OpenVan.camp weekly reference prices (CC BY 4.0). */
    OpenVanCamp(
        providesFuel = true,
        providesPrices = true,
        supportedCountries = setOf(
            "LU", "ME", "MK", "HR", "SI", "BE", "PT", "PT-MA", "PT-AC", "IT", "SE", "DK", "FI",
            "NO", "PL", "HU", "IE", "GR", "RO", "CZ", "SK", "BG"
        )
    ),
    /** Spanish government fuel prices (Minetur). */
    SpainMinetur(providesFuel = true, providesPrices = true, supportedCountries = setOf("ES")),
    /** German fuel prices via Tankerkönig (MTS-K). */
    GermanyTankerkoenig(providesFuel = true, providesPrices = true, supportedCountries = setOf("DE")),
    /** Austrian fuel prices via E-Control. */
    AustriaEControl(providesFuel = true, providesPrices = true, supportedCountries = setOf("AT")),
    /** Netherlands and Luxembourg fuel prices via ANWB. */
    NetherlandsAnwb(providesFuel = true, providesPrices = true, supportedCountries = setOf("NL", "LU")),
    /** Belgian official maximum fuel prices. */
    BelgiumOfficial(providesFuel = true, providesPrices = true, supportedCountries = setOf("BE")),
    /** Portuguese fuel prices via DGEG (Mainland). */
    PortugalDgeg(providesFuel = true, providesPrices = true, supportedCountries = setOf("PT")),
    /** Madeira regional official maximum fuel prices. */
    MadeiraOfficial(providesFuel = true, providesPrices = true, supportedCountries = setOf("PT-MA")),
    /** UK fuel prices via CMA open data feeds. */
    UnitedKingdomCma(providesFuel = true, providesPrices = true, supportedCountries = setOf("GB")),
    /** Italian fuel prices via MIMIT open data feeds. */
    ItalyMimit(providesFuel = true, providesPrices = true, supportedCountries = setOf("IT")),
    /** Slovenia fuel prices via Goriva.si. */
    SloveniaGoriva(providesFuel = true, providesPrices = true, supportedCountries = setOf("SI")),
    /** Romania fuel prices via Peco Online. */
    RomaniaPeco(providesFuel = true, providesPrices = true, supportedCountries = setOf("RO")),
    /** Fuel prices via Fuelo.net (BG, CZ, HU, PL, SK, EE, LV, LT, CH, BA, TR, MK, AT, BE, DE, ES, FR, GB, GR, HR, IE, IT, NL, PT, RO, RS, SI, PT-MA, PT-AC, ES-CN, ES-IB). */
    Fuelo(
        providesFuel = true,
        providesPrices = true,
        supportedCountries = setOf(
            "BG", "CZ", "HU", "PL", "SK", "EE", "LV", "LT", "CH", "BA", "TR", "MK",
            "AT", "BE", "DE", "ES", "FR", "GB", "GR", "HR", "IE", "IT", "NL", "PT", "RO", "RS", "SI",
            "PT-MA", "PT-AC", "ES-CN", "ES-IB"
        )
    ),
    /** Greece fuel prices via FuelGR. */
    GreeceFuelGR(providesFuel = true, providesPrices = true, supportedCountries = setOf("GR")),
    /** Serbia fuel prices via NIS / Cenagoriva. */
    SerbiaNis(providesFuel = true, providesPrices = true, supportedCountries = setOf("RS")),
    /** Croatia fuel prices via MZOE. */
    CroatiaMzoe(providesFuel = true, providesPrices = true, supportedCountries = setOf("HR")),
    /** DrivstoffAppen (Norway, Sweden, Denmark, Finland). */
    DrivstoffAppen(providesFuel = true, providesPrices = true, supportedCountries = setOf("NO", "SE", "DK", "FI")),
    /** Denmark fuel prices via Fuelprices.dk. */
    DenmarkFuelprices(providesFuel = true, providesPrices = true, supportedCountries = setOf("DK")),
    /** Finland fuel prices via Polttoaine.net. */
    FinlandPolttoaine(providesFuel = true, providesPrices = true, supportedCountries = setOf("FI")),
    /** Argentina fuel prices via Secretaría de Energía. */
    ArgentinaEnergia(providesFuel = true, providesPrices = true, supportedCountries = setOf("AR")),
    /** Mexico fuel prices via CRE. */
    MexicoCRE(providesFuel = true, providesPrices = true, supportedCountries = setOf("MX")),
    /** Moldova fuel prices via ANRE. */
    MoldovaAnre(providesFuel = true, providesPrices = true, supportedCountries = setOf("MD")),
    /** Australia fuel prices via FuelWatch and FuelCheck. */
    AustraliaFuel(providesFuel = true, providesPrices = true, supportedCountries = setOf("AU")),
    /** Ireland fuel prices via Pick A Pump. */
    IrelandPickAPump(providesFuel = true, providesPrices = true, supportedCountries = setOf("IE")),
    Overpass,
    Ionity(providesElectric = true),
    Fastned(providesElectric = true),
    Hybrid(providesFuel = true, providesElectric = true, providesPrices = true, supportedCountries = setOf("FR")),
}

private val POI_DATA_SOURCES_DISABLED_FOR_USER_SELECTION: Set<PoiProviderType> = emptySet()

/** True if this source is shown in map / Auto POI data source pickers. */
fun PoiProviderType.isUserSelectablePoiDataSource(): Boolean =
    this !in POI_DATA_SOURCES_DISABLED_FOR_USER_SELECTION

/**
 * Ensures user selection is valid.
 */
fun Set<PoiProviderType>.sanitizeUserPoiProviderSelection(): Set<PoiProviderType> =
    this.filter { it.isUserSelectablePoiDataSource() }.toSet()

/** True if any selected provider can supply fuel POIs (for filter / mode chips). */
fun Iterable<PoiProviderType>.anyProvidesFuel(): Boolean = any { it.providesFuel }

/** True if any selected provider can supply electric / IRVE POIs. */
fun Iterable<PoiProviderType>.anyProvidesElectric(): Boolean = any { it.providesElectric }

/**
 * Returns the display name for a country code, including its flag emoji.
 */
fun getCountryDisplayName(code: String): String = when (code) {
    "FR" -> "🇫🇷 France"
    "LU" -> "🇱🇺 Luxembourg"
    "BE" -> "🇧🇪 Belgium"
    "NL" -> "🇳🇱 Netherlands"
    "DE" -> "🇩🇪 Germany"
    "AT" -> "🇦🇹 Austria"
    "ES" -> "🇪🇸 Spain"
    "PT" -> "🇵🇹 Portugal"
    "IT" -> "🇮🇹 Italy"
    "GB" -> "🇬🇧 United Kingdom"
    "IE" -> "🇮🇪 Ireland"
    "DK" -> "🇩🇰 Denmark"
    "FI" -> "🇫🇮 Finland"
    "NO" -> "🇳🇴 Norway"
    "SE" -> "🇸🇪 Sweden"
    "SI" -> "🇸🇮 Slovenia"
    "HR" -> "🇭🇷 Croatia"
    "RO" -> "🇷🇴 Romania"
    "BG" -> "🇧🇬 Bulgaria"
    "GR" -> "🇬🇷 Greece"
    "HU" -> "🇭🇺 Hungary"
    "PL" -> "🇵🇱 Poland"
    "CZ" -> "🇨🇿 Czechia"
    "SK" -> "🇸🇰 Slovakia"
    "RS" -> "🇷🇸 Serbia"
    "AR" -> "🇦🇷 Argentina"
    "MX" -> "🇲🇽 Mexico"
    "MD" -> "🇲🇩 Moldova"
    "AU" -> "🇦🇺 Australia"
    "EE" -> "🇪🇪 Estonia"
    "LV" -> "🇱🇻 Latvia"
    "LT" -> "🇱🇹 Lithuania"
    "CH" -> "🇨🇭 Switzerland"
    "BA" -> "🇧🇦 Bosnia"
    "TR" -> "🇹🇷 Turkey"
    "MK" -> "🇲🇰 North Macedonia"
    "ME" -> "🇲🇪 Montenegro"
    "ES-CN" -> "🇪🇸 Canary Islands"
    "ES-IB" -> "🇪🇸 Balearic Islands"
    "PT-MA" -> "🇵🇹 Madeira"
    "PT-AC" -> "🇵🇹 Azores"
    else -> "🌍 $code"
}

/**
 * Returns a display group name for the provider (e.g. flag + country name).
 */
fun PoiProviderType.getDisplayGroup(): String {
    if (this == PoiProviderType.Routex) return "🌍 International"
    if (this == PoiProviderType.OpenChargeMap || this == PoiProviderType.EcoMovement || this == PoiProviderType.Ionity || this == PoiProviderType.Fastned) return "🌍 Global"
    if (this == PoiProviderType.OpenVanCamp) return "🇪🇺 Europe (Reference)"
    if (this == PoiProviderType.Overpass) return "🌍 General"

    val countries = supportedCountries
    if (countries.isEmpty()) return "🌍 Other"
    if (countries.size > 1) return "🇪🇺 Multi-country"

    return getCountryDisplayName(countries.first())
}

/**
 * IRVE-only details: connector types, tarification (free text), opening hours, payment, etc.
 * Used when [Poi.isElectric] and data comes from data.gouv.fr IRVE.
 */
data class IrveDetails(
    /** Connector type ids: "type_2", "combo_ccs", "chademo", "ef", "autre". */
    val connectorTypes: Set<String> = emptySet(),
    /** Free-text tarification; display as-is. */
    val tarification: String? = null,
    val gratuit: Boolean? = null,
    val openingHours: String? = null,
    val reservation: Boolean? = null,
    val paymentActe: Boolean? = null,
    val paymentCb: Boolean? = null,
    val paymentAutre: Boolean? = null,
    /** "Accès libre" / "Accès réservé". */
    val conditionAcces: String? = null,
    /** Real-time availability: number of free connectors. */
    val availableConnectors: Int? = null,
    /** Real-time availability: total number of connectors. */
    val totalConnectors: Int? = null
)

/**
 * Restaurant/fast food details from OSM (Overpass): opening hours, cuisine, brand.
 * Used when [Poi.poiCategory] is Restaurant or FastFood and data comes from Overpass.
 */
data class RestaurantDetails(
    val openingHours: String? = null,
    val cuisine: String? = null,
    val brand: String? = null,
    val isFastFood: Boolean = false
)

/**
 * Fuel type and price at a gas station (e.g. from data.gouv.fr / gas-api.ovh).
 */
data class FuelPrice(
    val fuelName: String,
    val price: Double,
    val updatedAt: String? = null,
    val outOfStock: Boolean = false,
    /** When true, this is a regional or national reference/maximum price, not specific to this station. */
    val isReference: Boolean = false
)

data class Poi(
    val id: String,
    val name: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val brand: String? = null,
    /** True for IRVE / EV charging stations (e.g. data.gouv.fr IRVE). */
    val isElectric: Boolean = false,
    /** Unified category (toilet, drinking water, gas, irve). Inferred from [isElectric] when null. */
    val poiCategory: PoiCategory? = null,
    /** Nominal power in kW (IRVE only). Used for min-power filter. */
    val powerKw: Double? = null,
    /** Operator name (IRVE only). Used for operator filter. */
    val operator: String? = null,
    /** True when station is on highway/autoroute (IRVE only). */
    val isOnHighway: Boolean = false,
    /** Number of charging points / points de charge (IRVE only). */
    val chargePointCount: Int? = null,
    /** When provided by the provider (e.g. DataGouv), lists fuel types and prices. */
    val fuelPrices: List<FuelPrice>? = null,
    /** Site name (e.g. Routex site_name) for title. */
    val siteName: String? = null,
    val postcode: String? = null,
    val addressLocal: String? = null,
    val countryLocal: String? = null,
    val townLocal: String? = null,
    /** Routex-only: amenities and opening hours for fullscreen details. */
    val routexDetails: RoutexSiteDetails? = null,
    /** IRVE-only: connector types, tarification, horaires, payment, etc. */
    val irveDetails: IrveDetails? = null,
    /** Restaurant/fast food only: opening hours, cuisine, brand (e.g. from Overpass). */
    val restaurantDetails: RestaurantDetails? = null,
    /** The source of the POI data (e.g. "Routex", "DataGouv", "Chargy"). */
    val source: String? = null
)

/**
 * Optional map viewport to scope the POI search to the visible area (e.g. for Routex API).
 * When provided, radius is derived from zoom and map size instead of a fixed default.
 */
data class MapViewport(
    val zoom: Float,
    val mapWidthPx: Int,
    val mapHeightPx: Int
)

/**
 * Unified POI search request. Used by [PoiProvider.search] for gas, IRVE, toilets, water, etc.
 * Empty [categories] means "all categories supported by the provider" (provider-specific default).
 */
data class PoiSearchRequest(
    val latitude: Double,
    val longitude: Double,
    val viewport: MapViewport? = null,
    /** Requested POI categories. Empty = provider default (e.g. Gas+Irve for fuel providers). */
    val categories: Set<PoiCategory> = emptySet(),
    /** When true, the provider should skip in-memory filtering (e.g. brands) and return raw results. */
    val skipFilters: Boolean = false
)

/**
 * Result of a POI search, containing the list of POIs and any errors encountered during the search.
 */
data class PoiSearchResult(
    val pois: List<Poi> = emptyList(),
    val errors: List<PoiProviderError> = emptyList()
)

/**
 * Error information from a specific POI provider.
 */
data class PoiProviderError(
    val providerName: String,
    val message: String,
    val httpCode: Int? = null,
    val isCritical: Boolean = false
)

/**
 * Maps API fuel names (data.gouv / prix instantané / gas-api.ovh) to filter ids used in map settings.
 * Aligned with [prix-carburants.gouv.fr](https://www.prix-carburants.gouv.fr/) fuel list.
 */
object MapPoiFilter {
    /** Normalize API fuel name to a filter id (gazole, gazole_plus, sp98, sp95, sp95_e10, gplc, e85). Returns null if unknown. */
    fun fuelNameToId(fuelName: String): String? {
        val n = fuelName.trim().lowercase()
        return when {
            n.contains("gazole") || n.contains("gasoil") || n.contains("diesel") ||
            n.contains("gasóleo") || n.contains("gasoleo") || n.contains("gasolio") -> {
                if (n.contains("premium") || n.contains("excellium") || n.contains("ultimate") ||
                    n.contains("supreme") || n.contains("v-power") || n.contains("vpower") ||
                    n.contains("especial") || n.contains("speciale") || n.contains("plus") ||
                    n.contains("optium") || n.contains("extra") || n.contains("star diesel")
                ) {
                    "gazole_plus"
                } else {
                    "gazole"
                }
            }
            n.contains("sp98") || n == "sp 98" -> "sp98"
            n.contains("e10") || n.contains("sp95-e10") || n == "sp95 e10" || n.contains("sp95") || n == "sp 95" -> "sp95"
            n.contains("gpl") || n == "gplc" || n == "lpg" -> "gplc"
            n.contains("e85") || n == "superéthanol" -> "e85"
            else -> null
        }
    }

    /**
     * Returns true if [poi] should be shown given [selectedEnergyIds].
     * When [selectedEnergyIds] is empty, returns true (show all).
     * Hybrid stations (both gas and electric) match if either gas or electric filter matches.
     * Stations without price data are shown if their primary category matches the selected filters.
     */
    fun matchesEnergyFilter(poi: Poi, selectedEnergyIds: Set<String>): Boolean {
        if (selectedEnergyIds.isEmpty()) return true

        val matchesElectric = poi.isElectric && "electric" in selectedEnergyIds

        val fuelIds = poi.fuelPrices?.mapNotNull { fuelNameToId(it.fuelName) }?.toSet() ?: emptySet()
        val matchesFuel = if (fuelIds.isNotEmpty()) {
            fuelIds.any { it in selectedEnergyIds }
        } else {
            // If no fuel prices, check if it's a gas station (explicitly or by default)
            val isGas = poi.poiCategory == PoiCategory.Gas || (poi.poiCategory == null && !poi.isElectric)
            isGas && selectedEnergyIds.any { it != "electric" }
        }

        return matchesElectric || matchesFuel
    }

    /** Returns true if [powerKw] falls into any of the selected [levels] buckets. */
    fun powerMatchesAnyLevel(powerKw: Double, levels: Set<Int>): Boolean =
        levels.any { level ->
            when (level) {
                0 -> true
                20 -> powerKw in 20.0..49.9
                50 -> powerKw in 50.0..99.9
                100 -> powerKw in 100.0..199.9
                200 -> powerKw in 200.0..299.9
                300 -> powerKw >= 300.0
                else -> powerKw >= level
            }
        }
}

/**
 * Unified POI provider: supports [search] by [PoiCategory] and optional legacy [getGasStations].
 * New providers implement [search] and [supportedCategories]; [getGasStations] is for backward compatibility.
 */
interface PoiProvider {
    /** Categories this provider can return. Used by the selector to build [PoiSearchRequest]. */
    fun supportedCategories(): Set<PoiCategory> = setOf(PoiCategory.Gas)

    /**
     * Unified search: returns a [Flow] that emits [PoiSearchResult]s as they become available.
     * Default implementation emits the result of [searchResult].
     */
    fun searchFlow(request: PoiSearchRequest): Flow<PoiSearchResult> = flow {
        emit(searchResult(request))
    }

    /**
     * Unified search: returns a [PoiSearchResult] containing the list of POIs and any errors encountered.
     * Default implementation delegates to [getGasStations] and filters by category intersection.
     */
    suspend fun searchResult(request: PoiSearchRequest): PoiSearchResult {
        return try {
            val pois = search(request)
            PoiSearchResult(pois = pois)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            val code = (e as? fr.geoking.julius.shared.network.NetworkException)?.httpCode
            PoiSearchResult(
                errors = listOf(
                    PoiProviderError(
                        providerName = this::class.simpleName ?: "Unknown Provider",
                        message = e.message ?: "Unknown error",
                        httpCode = code
                    )
                )
            )
        }
    }

    /**
     * Unified search: returns POIs for the requested [request.categories] (or provider default if empty).
     * Default implementation delegates to [getGasStations] and filters by category intersection.
     */
    suspend fun search(request: PoiSearchRequest): List<Poi> {
        val cat = request.categories
        val supported = supportedCategories()
        val overlap = if (cat.isEmpty()) supported else cat.intersect(supported)
        if (overlap.isEmpty() || (PoiCategory.Gas !in overlap && PoiCategory.Irve !in overlap)) {
            return emptyList()
        }
        val list = getGasStations(request.latitude, request.longitude, request.viewport)
            .map { p -> p.ensureCategory() }
        return if (cat.isEmpty()) list else list.filter { it.poiCategory!! in overlap }
    }

    /**
     * Fetches gas/IRVE stations near the given center (legacy).
     * When [viewport] is non-null, providers may use it to limit the search to the visible map.
     */
    suspend fun getGasStations(
        latitude: Double,
        longitude: Double,
        viewport: MapViewport? = null
    ): List<Poi>

    /** Clears any internal cache this provider may have. */
    fun clearCache() {}
}

private fun Poi.ensureCategory(): Poi = copy(
    poiCategory = poiCategory ?: if (isElectric) PoiCategory.Irve else PoiCategory.Gas
)

class MockPoiProvider : PoiProvider {
    override suspend fun getGasStations(
        latitude: Double,
        longitude: Double,
        viewport: MapViewport?
    ): List<Poi> {
        // Mock data around some common coordinates or relative to input
        return listOf(
            Poi("1", "BP Paris Sud", "123 Avenue du Maine, Paris", latitude + 0.01, longitude + 0.01, "BP"),
            Poi("2", "Aral Station", "45 Rue de Rivoli, Paris", latitude - 0.01, longitude + 0.02, "Aral"),
            Poi("3", "Eni Live", "88 Boulevard Haussmann, Paris", latitude + 0.02, longitude - 0.01, "Eni"),
            Poi("4", "Circle K", "10 Place de la Bastille, Paris", latitude - 0.02, longitude - 0.02, "Circle K"),
            Poi("5", "OMV Station", "22 Rue de la Paix, Paris", latitude + 0.005, longitude - 0.005, "OMV")
        )
    }
}
