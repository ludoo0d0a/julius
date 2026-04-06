package fr.geoking.julius.ui

import android.location.Address
import android.location.Geocoder
import android.os.Build
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.EvStation
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SignalCellular4Bar
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.geoking.julius.SettingsManager
import fr.geoking.julius.StationMapFilters
import fr.geoking.julius.effectiveMapEnergyFilterIds
import fr.geoking.julius.effectiveProviders
import fr.geoking.julius.poi.PoiProviderType
import fr.geoking.julius.ui.SettingsScreenPage
import fr.geoking.julius.feature.location.LocationHelper
import fr.geoking.julius.poi.MapPoiFilter
import fr.geoking.julius.poi.Poi
import fr.geoking.julius.poi.PoiProvider
import fr.geoking.julius.poi.PoiSearchRequest
import fr.geoking.julius.shared.network.NetworkService
import fr.geoking.julius.shared.network.NetworkStatus
import fr.geoking.julius.shared.network.NetworkType
import fr.geoking.julius.ui.components.CheapestStationsCard
import fr.geoking.julius.ui.map.PoiDetailsFullscreenDialog
import kotlinx.coroutines.Dispatchers
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhoneDashboardScreen(
    settingsManager: SettingsManager,
    poiProvider: PoiProvider?,
    hasLocationPermission: Boolean,
    mapDepsReady: Boolean,
    onOpenMap: () -> Unit,
    onOpenRoutes: () -> Unit,
    onOpenJules: () -> Unit,
    onOpenNetworkDiagnostics: () -> Unit,
    onOpenSettings: (List<SettingsScreenPage>?) -> Unit
) {
    val context = LocalContext.current
    val settings by settingsManager.settings.collectAsState()
    var nearbyPois by remember { mutableStateOf<List<Poi>>(emptyList()) }
    var isLoadingPois by remember { mutableStateOf(false) }
    var userLat by remember { mutableStateOf<Double?>(null) }
    var userLon by remember { mutableStateOf<Double?>(null) }
    var poiForDetails by remember { mutableStateOf<Poi?>(null) }

    val energyFilterIds = settings.effectiveMapEnergyFilterIds()
    val providers = settings.effectiveProviders()

    LaunchedEffect(poiProvider, hasLocationPermission, energyFilterIds, providers, settings) {
        if (poiProvider == null || !hasLocationPermission) return@LaunchedEffect

        isLoadingPois = true
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
                    settings = settings,
                    pois = results,
                    providers = providers,
                    skipWhenOnlyOverpass = true
                )

                val fuelIds = energyFilterIds - "electric"

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
                    .take(3)
            } catch (e: Exception) {
                android.util.Log.e("PhoneDashboardScreen", "Failed to fetch nearby POIs", e)
            }
        }
        isLoadingPois = false
    }

    val quickActions = listOf(
        DashboardRow(
            title = "Fuel",
            subtitle = "Gas stations",
            icon = Icons.Default.LocalGasStation,
            onClick = {
                settingsManager.setUseVehicleFilter(false)
                settingsManager.setPoiProviderTypes(setOf(PoiProviderType.DataGouv))
                settingsManager.setMapEnergyTypes(emptySet())
                settingsManager.setMapPowerLevels(emptySet())
                settingsManager.setMapIrveOperators(emptySet())
                settingsManager.setMapConnectorTypes(emptySet())
                onOpenMap()
            }
        ),
        DashboardRow(
            title = "EV",
            subtitle = "Charging",
            icon = Icons.Default.EvStation,
            onClick = {
                settingsManager.setUseVehicleFilter(false)
                settingsManager.setPoiProviderTypes(setOf(PoiProviderType.DataGouvElec))
                settingsManager.setMapEnergyTypes(setOf("electric"))
                settingsManager.setMapPowerLevels(emptySet())
                settingsManager.setMapIrveOperators(emptySet())
                settingsManager.setMapConnectorTypes(emptySet())
                onOpenMap()
            }
        ),
        DashboardRow(
            title = "Hybrid",
            subtitle = "Both",
            icon = Icons.Default.Map,
            onClick = {
                settingsManager.setUseVehicleFilter(false)
                settingsManager.setPoiProviderTypes(setOf(PoiProviderType.Hybrid))
                settingsManager.setMapEnergyTypes(emptySet())
                settingsManager.setMapPowerLevels(emptySet())
                settingsManager.setMapIrveOperators(emptySet())
                settingsManager.setMapConnectorTypes(emptySet())
                onOpenMap()
            }
        )
    )

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
                    title = { Text("Julius — POI") },
                    actions = {
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
                // 1. Nearby Cheapest (loader or card)
                item {
                    if (isLoadingPois) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                        ) {
                            Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    "Nearby cheapest",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.align(Alignment.Start).padding(bottom = 12.dp)
                                )
                                Box(Modifier.fillMaxWidth().height(60.dp), contentAlignment = Alignment.Center) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                                        Spacer(Modifier.height(8.dp))
                                        Text("Searching nearby...", style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                    } else if (nearbyPois.isNotEmpty()) {
                        CheapestStationsCard(
                            stations = nearbyPois,
                            userLatitude = userLat,
                            userLongitude = userLon,
                            selectedEnergyIds = energyFilterIds,
                            onClick = { poiForDetails = it }
                        )
                    }
                }

                // 2. Quick Actions Row (Fuel, EV, Hybrid)
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        quickActions.forEach { action ->
                            Card(
                                onClick = action.onClick,
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surface
                                ),
                                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxSize().padding(8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = action.icon,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(32.dp)
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        text = action.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = action.subtitle,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
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
            }
        }
    }

    poiForDetails?.let { poi ->
        PoiDetailsFullscreenDialog(
            poi = poi,
            onDismiss = { poiForDetails = null }
        )
    }
}

private fun approxDistanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val dLatKm = (lat2 - lat1) * 111.0
    val avgLatRad = ((lat1 + lat2) / 2.0) * Math.PI / 180.0
    val dLonKm = (lon2 - lon1) * 111.0 * Math.cos(avgLatRad)
    return Math.sqrt(dLatKm * dLatKm + dLonKm * dLonKm)
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
