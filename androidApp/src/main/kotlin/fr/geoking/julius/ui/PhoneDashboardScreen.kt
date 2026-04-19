package fr.geoking.julius.ui

import android.location.Address
import android.location.Geocoder
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.EvStation
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SignalCellular4Bar
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.geoking.julius.BuildConfig
import fr.geoking.julius.SettingsManager
import fr.geoking.julius.StationMapFilters
import fr.geoking.julius.effectiveMapEnergyFilterIds
import fr.geoking.julius.effectiveIrvePowerLevels
import com.google.android.gms.maps.model.LatLng
import fr.geoking.julius.effectiveProviders
import fr.geoking.julius.api.geocoding.GeocodedPlace
import fr.geoking.julius.api.geocoding.GeocodingClient
import fr.geoking.julius.community.FavoritesRepository
import fr.geoking.julius.poi.PoiProviderType
import fr.geoking.julius.ui.ColorHelper
import fr.geoking.julius.ui.MAP_ENERGY_OPTIONS
import fr.geoking.julius.ui.MAP_IRVE_POWER_OPTIONS
import fr.geoking.julius.ui.SettingsScreenPage
import fr.geoking.julius.feature.location.LocationHelper
import fr.geoking.julius.poi.MapPoiFilter
import fr.geoking.julius.poi.Poi
import fr.geoking.julius.poi.PoiProvider
import fr.geoking.julius.poi.PoiSearchRequest
import fr.geoking.julius.shared.network.NetworkService
import fr.geoking.julius.shared.network.NetworkStatus
import fr.geoking.julius.shared.network.NetworkType
import fr.geoking.julius.poi.anyProvidesElectric
import fr.geoking.julius.poi.anyProvidesFuel
import fr.geoking.julius.shared.location.approxDistanceKm
import fr.geoking.julius.repository.FuelForecastRepository
import fr.geoking.julius.repository.FuelForecastUiState
import fr.geoking.julius.ui.components.CheapestStationsCard
import fr.geoking.julius.ui.components.FuelForecastChartCard
import fr.geoking.julius.ui.components.FuelForecastCompactCard
import fr.geoking.julius.ui.map.NavigationHelper
import fr.geoking.julius.ui.map.PoiDetailsFullscreenDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/** Light theme for Play Store phone surfaces (home, diagnostics, map settings). */
val PlaystoreHomeLightScheme = lightColorScheme(
    primary = Color(0xFF2563EB),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDBEAFE),
    onPrimaryContainer = Color(0xFF1E3A8A),
    surface = Color(0xFFF8FAFC),
    onSurface = Color(0xFF0F172A),
    surfaceContainerHighest = Color(0xFFE2E8F0),
    background = Color(0xFFF1F5F9),
    onBackground = Color(0xFF0F172A)
)

/** Dark theme for Play Store phone surfaces (home, diagnostics, map settings). */
val PlaystoreHomeDarkScheme = darkColorScheme(
    primary = Color(0xFF60A5FA),
    onPrimary = Color(0xFF1E3A8A),
    primaryContainer = Color(0xFF1E40AF),
    onPrimaryContainer = Color(0xFFDBEAFE),
    surface = Color(0xFF0F172A),
    onSurface = Color(0xFFF8FAFC),
    surfaceContainerHighest = Color(0xFF1E293B),
    background = Color(0xFF020617),
    onBackground = Color(0xFFF1F5F9)
)

@Composable
fun PlaystoreTheme(content: @Composable () -> Unit) {
    val colorScheme = if (isSystemInDarkTheme()) PlaystoreHomeDarkScheme else PlaystoreHomeLightScheme
    MaterialTheme(colorScheme = colorScheme, content = content)
}

@Composable
fun PlaystoreLightTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = PlaystoreHomeLightScheme, content = content)
}

private data class DashboardRow(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
    val enabled: Boolean = true
)

@OptIn(ExperimentalMaterial3Api::class, kotlinx.coroutines.FlowPreview::class)
@Composable
fun PhoneDashboardScreen(
    settingsManager: SettingsManager,
    poiProvider: PoiProvider?,
    geocodingClient: GeocodingClient? = null,
    favoritesRepo: FavoritesRepository? = null,
    hasLocationPermission: Boolean,
    mapDepsReady: Boolean,
    fuelForecastRepository: FuelForecastRepository? = null,
    onOpenMap: (LatLng?) -> Unit,
    onOpenRoutes: () -> Unit,
    onOpenJules: () -> Unit,
    onOpenFavorites: () -> Unit,
    onOpenNetworkDiagnostics: () -> Unit,
    onOpenFuelForecast: () -> Unit,
    onOpenSettings: (List<SettingsScreenPage>?) -> Unit
) {
    val context = LocalContext.current
    val settings by settingsManager.settings.collectAsState()
    var nearbyPois by remember { mutableStateOf<List<Poi>>(emptyList()) }
    var isLoadingPois by remember { mutableStateOf(false) }
    var searchError by remember { mutableStateOf<String?>(null) }
    var userLat by remember { mutableStateOf<Double?>(null) }
    var userLon by remember { mutableStateOf<Double?>(null) }
    var poiForDetails by remember { mutableStateOf<Poi?>(null) }
    var fuelForecastState by remember {
        mutableStateOf(
            FuelForecastUiState(fuelId = "gazole", locationKey = "")
        )
    }
    var fuelForecastLoading by remember { mutableStateOf(false) }

    var favorites by remember { mutableStateOf<List<Poi>>(emptyList()) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(favoritesRepo) {
        if (favoritesRepo != null) {
            favorites = favoritesRepo.getFavorites()
        }
    }

    val energyFilterIds = settings.effectiveMapEnergyFilterIds()
    val providers = settings.effectiveProviders()

    // 400ms delay for the loader appearance to prevent "flashing" on fast/cached requests
    var showLoaderByDelay by remember { mutableStateOf(false) }
    LaunchedEffect(isLoadingPois) {
        if (isLoadingPois) {
            delay(400)
            showLoaderByDelay = true
        } else {
            showLoaderByDelay = false
        }
    }

    LaunchedEffect(poiProvider, hasLocationPermission) {
        if (poiProvider == null) return@LaunchedEffect

        // Use a flow to debounce settings/filter changes (300ms)
        snapshotFlow {
            Triple(
                settings.effectiveMapEnergyFilterIds(),
                settings.effectiveProviders(),
                settings.useVehicleFilter
            )
        }
        .debounce(300)
        .collectLatest { (currentEnergyIds, currentProviders, _) ->
            isLoadingPois = true
            searchError = null

            if (!hasLocationPermission) {
                nearbyPois = emptyList()
                searchError = "Location permission is required to find nearby stations."
                isLoadingPois = false
                return@collectLatest
            }

            val location = LocationHelper.getCurrentLocation(context)
            if (location != null) {
                userLat = location.latitude
                userLon = location.longitude

                try {
                    val results = poiProvider.search(
                        PoiSearchRequest(
                            latitude = location.latitude,
                            longitude = location.longitude,
                            categories = emptySet(),
                            skipFilters = true
                        )
                    )

                    val filteredResults = StationMapFilters.apply(
                        settings = settingsManager.settings.value,
                        pois = results,
                        providers = currentProviders,
                        skipWhenOnlyOverpass = true,
                        limit = 200,
                        centerLat = location.latitude,
                        centerLng = location.longitude
                    )

                    val fuelIds = currentEnergyIds - "electric"

                    nearbyPois = filteredResults
                        .sortedWith { a, b ->
                            val pricesA = if (fuelIds.isEmpty()) a.fuelPrices else a.fuelPrices?.filter { MapPoiFilter.fuelNameToId(it.fuelName) in fuelIds }
                            val pricesB = if (fuelIds.isEmpty()) b.fuelPrices else b.fuelPrices?.filter { MapPoiFilter.fuelNameToId(it.fuelName) in fuelIds }

                            val priceA = pricesA?.minByOrNull { it.price }?.price ?: Double.MAX_VALUE
                            val priceB = pricesB?.minByOrNull { it.price }?.price ?: Double.MAX_VALUE

                            if (priceA != priceB && (priceA != Double.MAX_VALUE || priceB != Double.MAX_VALUE)) {
                                priceA.compareTo(priceB)
                            } else {
                                val distA = approxDistanceKm(location.latitude, location.longitude, a.latitude, a.longitude)
                                val distB = approxDistanceKm(location.latitude, location.longitude, b.latitude, b.longitude)
                                distA.compareTo(distB)
                            }
                        }
                        .take(5)
                } catch (e: Exception) {
                    android.util.Log.e("PhoneDashboardScreen", "Failed to fetch nearby POIs", e)
                    searchError = "Unable to fetch nearby stations. Please check your connection."
                    nearbyPois = emptyList()
                }
            } else {
                searchError = "Unable to determine your location."
                nearbyPois = emptyList()
            }
            isLoadingPois = false
        }
    }

    LaunchedEffect(userLat, userLon, energyFilterIds, hasLocationPermission, fuelForecastRepository) {
        val repo = fuelForecastRepository ?: return@LaunchedEffect
        if (!hasLocationPermission) {
            fuelForecastState = FuelForecastUiState(
                fuelId = "gazole",
                locationKey = "",
                errorMessage = "Location needed for local price forecast."
            )
            return@LaunchedEffect
        }
        val locLatLon: Pair<Double, Double> = when {
            userLat != null && userLon != null -> Pair(userLat!!, userLon!!)
            else -> {
                val loc = withContext(Dispatchers.IO) { LocationHelper.getCurrentLocation(context) }
                if (loc == null) {
                    fuelForecastState = FuelForecastUiState(
                        fuelId = "gazole",
                        locationKey = "",
                        errorMessage = "Unable to read location for forecast."
                    )
                    return@LaunchedEffect
                }
                Pair(loc.latitude, loc.longitude)
            }
        }
        val (la, lo) = locLatLon
        fuelForecastLoading = true
        try {
            fuelForecastState = repo.refreshAndBuildUiState(la, lo, energyFilterIds)
        } catch (e: Exception) {
            android.util.Log.e("PhoneDashboardScreen", "Fuel forecast refresh failed", e)
            fuelForecastState = FuelForecastUiState(
                fuelId = energyFilterIds.firstOrNull { it != "electric" } ?: "gazole",
                locationKey = repo.locationKey(la, lo),
                errorMessage = "Could not refresh forecast."
            )
        } finally {
            fuelForecastLoading = false
        }
    }

    val otherActions = listOf(
        DashboardRow(
            title = "My car settings",
            subtitle = if (settings.vehicleBrand.isNotEmpty()) "${settings.vehicleBrand} ${settings.vehicleModel}" else "Configure your vehicle",
            icon = Icons.Default.DirectionsCar,
            onClick = { onOpenSettings(listOf(SettingsScreenPage.VehicleConfig)) }
        ),
        DashboardRow(
            title = "Routes",
            subtitle = "Plan a journey",
            icon = Icons.Default.Directions,
            onClick = onOpenRoutes,
            enabled = mapDepsReady
        ),
        DashboardRow(
            title = "Network & location",
            subtitle = "Diagnostics",
            icon = Icons.Default.SignalCellular4Bar,
            onClick = onOpenNetworkDiagnostics
        ),
        DashboardRow(
            title = "Jules",
            subtitle = "AI Assistant",
            icon = Icons.Default.Code,
            onClick = onOpenJules
        ),
    )

    PlaystoreTheme {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Julius - station finder") },
                    actions = {
                        if (fuelForecastRepository != null) {
                            FuelForecastCompactCard(
                                state = fuelForecastState,
                                isLoading = fuelForecastLoading,
                                onClick = onOpenFuelForecast,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                        }
                        IconButton(onClick = onOpenFavorites) {
                            Icon(Icons.Default.FavoriteBorder, contentDescription = "Favorites")
                        }
                        IconButton(onClick = { onOpenSettings(null) }) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings")
                        }
                    },
                    colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface
                    )
                )
            },
            containerColor = MaterialTheme.colorScheme.background
        ) { padding ->
            LazyColumn(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 1. Energy selector (Fuel, EV, Hybrid)
                item {
                    val currentMode = remember(settings.selectedPoiProviders) {
                        val p = settings.selectedPoiProviders
                        when {
                            p.contains(PoiProviderType.Hybrid) -> 2
                            p.anyProvidesElectric() -> 1
                            else -> 0
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            SegmentedButton(
                                selected = currentMode == 0,
                                onClick = {
                                    settingsManager.setUseVehicleFilter(false)
                                    settingsManager.setPoiProviderTypes(setOf(PoiProviderType.DataGouv))
                                    settingsManager.setMapEnergyTypes(settings.selectedMapEnergyTypes - "electric")
                                },
                                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3),
                                label = { Text("Fuel") }
                            )
                            SegmentedButton(
                                selected = currentMode == 1,
                                onClick = {
                                    settingsManager.setUseVehicleFilter(false)
                                    settingsManager.setPoiProviderTypes(setOf(PoiProviderType.DataGouvElec))
                                    settingsManager.setMapEnergyTypes(setOf("electric"))
                                },
                                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3),
                                label = { Text("EV") }
                            )
                            SegmentedButton(
                                selected = currentMode == 2,
                                onClick = {
                                    settingsManager.setUseVehicleFilter(false)
                                    settingsManager.setPoiProviderTypes(setOf(PoiProviderType.Hybrid))
                                },
                                shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3),
                                label = { Text("Hybrid") }
                            )
                        }

                        // Contextual Chips
                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (currentMode == 0 || currentMode == 2) {
                                items(MAP_ENERGY_OPTIONS.filter { it.first != "electric" }) { (id, label) ->
                                    val isSelected = settings.effectiveMapEnergyFilterIds().contains(id)
                                    val color = ColorHelper.getFuelColor(id) ?: MaterialTheme.colorScheme.primary
                                    androidx.compose.material3.FilterChip(
                                        selected = isSelected,
                                        onClick = {
                                            val current = settings.selectedMapEnergyTypes
                                            val next = if (current.contains(id)) {
                                                current - id
                                            } else {
                                                val allFuelIds = MAP_ENERGY_OPTIONS.map { it.first }.filter { it != "electric" }.toSet()
                                                (current - allFuelIds) + id
                                            }
                                            settingsManager.setUseVehicleFilter(false)
                                            settingsManager.setMapEnergyTypes(next)
                                        },
                                        label = { Text(label) },
                                        colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = color,
                                            selectedLabelColor = Color.White,
                                            iconColor = color,
                                            selectedLeadingIconColor = Color.White
                                        ),
                                        leadingIcon = {
                                            Box(modifier = Modifier.size(12.dp).background(color, MaterialTheme.shapes.small))
                                        }
                                    )
                                }
                            }

                            if (currentMode == 1 || currentMode == 2) {
                                items(MAP_IRVE_POWER_OPTIONS) { (kw, label) ->
                                    val isSelected = settings.effectiveIrvePowerLevels().contains(kw)
                                    val color = ColorHelper.getPowerColorByLevel(kw)
                                    androidx.compose.material3.FilterChip(
                                        selected = isSelected,
                                        onClick = {
                                            val current = settings.mapPowerLevels
                                            val next = if (current.contains(kw)) current - kw else current + kw
                                            settingsManager.setUseVehicleFilter(false)
                                            settingsManager.setMapPowerLevels(next)
                                        },
                                        label = { Text(label) },
                                        colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = color,
                                            selectedLabelColor = Color.White,
                                            iconColor = color,
                                            selectedLeadingIconColor = Color.White
                                        ),
                                        leadingIcon = {
                                            Box(modifier = Modifier.size(12.dp).background(color, MaterialTheme.shapes.small))
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // 1. Nearby Cheapest (loader or card)
                item {
                    CheapestStationsCard(
                        stations = nearbyPois,
                        userLatitude = userLat,
                        userLongitude = userLon,
                        selectedEnergyIds = energyFilterIds,
                        onClick = { poiForDetails = it },
                        onMapClick = { onOpenMap(null) },
                        isLoading = isLoadingPois && showLoaderByDelay,
                        emptyMessage = searchError
                    )
                }

                // 1.5 Favorites Card
                if (favorites.isNotEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "Favorites",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                                favorites.take(3).forEachIndexed { index, poi ->
                                    ListItem(
                                        headlineContent = { Text(poi.name, fontWeight = FontWeight.SemiBold) },
                                        supportingContent = { Text(poi.address, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                        leadingContent = {
                                            Icon(
                                                Icons.Default.Place,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        },
                                        modifier = Modifier.clickable {
                                            onOpenMap(LatLng(poi.latitude, poi.longitude))
                                        },
                                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                                    )
                                    if (index < favorites.take(3).size - 1) {
                                        HorizontalDivider(
                                            modifier = Modifier.padding(vertical = 4.dp),
                                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }


                // 3. Other Actions
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        otherActions.chunked(2).forEach { pair ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                pair.forEach { action ->
                                    Card(
                                        onClick = action.onClick,
                                        enabled = action.enabled,
                                        modifier = Modifier.weight(1f),
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.surface,
                                            disabledContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
                                        ),
                                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                                    ) {
                                        ListItem(
                                            headlineContent = { Text(action.title, style = MaterialTheme.typography.titleSmall) },
                                            supportingContent = { Text(action.subtitle, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                            leadingContent = {
                                                Icon(action.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                                            },
                                            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                                        )
                                    }
                                }
                                if (pair.size == 1) {
                                    Spacer(Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }

                item {
                    Spacer(Modifier.height(16.dp))
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }

    poiForDetails?.let { poi ->
        PoiDetailsFullscreenDialog(
            poi = poi,
            isLoggedIn = settings.isLoggedIn,
            isFavorite = favorites.any { it.id == poi.id },
            onToggleFavorite = if (settings.isLoggedIn && favoritesRepo != null) {
                {
                    scope.launch {
                        favoritesRepo.toggleFavorite(poi)
                        favorites = favoritesRepo.getFavorites()
                    }
                }
            } else null,
            onNavigate = {
                NavigationHelper.navigateToPoi(context, poi)
            },
            onDismiss = { poiForDetails = null }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhoneNetworkLocationScreen(
    networkService: NetworkService,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val networkStatus by networkService.status.collectAsState()
    var refreshTick by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var address by remember { mutableStateOf<String?>(null) }
    var latLng by remember { mutableStateOf<Pair<Double, Double>?>(null) }

    LaunchedEffect(refreshTick) {
        loading = true
        address = null
        latLng = null
        val location = withContext(Dispatchers.IO) {
            LocationHelper.getCurrentLocation(context)
        }
        if (location != null) {
            latLng = location.latitude to location.longitude
            address = withContext(Dispatchers.IO) {
                geocodeAddress(context, location.latitude, location.longitude)
            }
        } else {
            address = "Location not available"
        }
        loading = false
    }

    PlaystoreTheme {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Network & location") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        IconButton(onClick = { refreshTick++ }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                        }
                    },
                    colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface
                    )
                )
            },
            containerColor = MaterialTheme.colorScheme.background
        ) { padding ->
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    "Network: ${if (networkStatus.isConnected) "Connected" else "Disconnected"}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    "Type: ${networkStatus.networkType.toReadableString()} · Operator: ${networkStatus.operatorName ?: "Unknown"}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "Country: ${networkStatus.countryName ?: networkStatus.countryCode ?: "Unknown"} · Roaming: ${if (networkStatus.isRoaming) "Yes" else "No"}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "Current location",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 8.dp)
                )
                when {
                    loading -> Text("Loading coordinates…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    latLng != null -> {
                        Text(
                            "Lat: ${String.format(Locale.US, "%.6f", latLng!!.first)}, Lon: ${String.format(Locale.US, "%.6f", latLng!!.second)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            address ?: "",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    else -> Text(address ?: "", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

private suspend fun geocodeAddress(context: android.content.Context, lat: Double, lon: Double): String? {
    val geocoder = Geocoder(context, Locale.getDefault())
    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            suspendCoroutine { continuation ->
                geocoder.getFromLocation(lat, lon, 1, object : Geocoder.GeocodeListener {
                    override fun onGeocode(addresses: MutableList<Address>) {
                        continuation.resume(addresses.firstOrNull()?.let { formatAddress(it) })
                    }
                    override fun onError(errorMessage: String?) {
                        continuation.resume(null)
                    }
                })
            }
        } else {
            withContext(Dispatchers.IO) {
                @Suppress("DEPRECATION")
                geocoder.getFromLocation(lat, lon, 1)?.firstOrNull()?.let { formatAddress(it) }
            }
        }
    } catch (_: Exception) {
        "Geocoding error"
    }
}

private fun formatAddress(address: Address): String {
    val sb = StringBuilder()
    for (i in 0..address.maxAddressLineIndex) {
        sb.append(address.getAddressLine(i))
        if (i < address.maxAddressLineIndex) sb.append(", ")
    }
    return sb.toString()
}

private fun NetworkType.toReadableString(): String = when (this) {
    NetworkType.WIFI -> "WiFi"
    NetworkType.FIVE_G -> "5G"
    NetworkType.FOUR_G -> "4G"
    NetworkType.THREE_G -> "3G"
    NetworkType.TWO_G -> "2G"
    NetworkType.EDGE -> "Edge"
    NetworkType.GPRS -> "GPRS"
    NetworkType.UNKNOWN -> "Unknown"
    NetworkType.NONE -> "None"
}
