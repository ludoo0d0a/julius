package fr.geoking.julius.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.location.LocationManager
import android.net.Uri
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.*
import androidx.compose.ui.zIndex
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.ContextCompat
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import fr.geoking.julius.R
import fr.geoking.julius.SettingsManager
import fr.geoking.julius.agents.AgentResponse
import fr.geoking.julius.agents.ConversationalAgent
import fr.geoking.julius.feature.location.LocationHelper
import fr.geoking.julius.poi.MapViewport
import fr.geoking.julius.poi.Poi
import fr.geoking.julius.poi.PoiProvider
import fr.geoking.julius.poi.PoiProviderType
import fr.geoking.julius.poi.PoiSearchRequest
import fr.geoking.julius.poi.PoiCategory
import fr.geoking.julius.poi.anyProvidesElectric
import fr.geoking.julius.poi.anyProvidesFuel
import fr.geoking.julius.api.belib.BorneAvailabilityProviderFactory
import fr.geoking.julius.api.belib.matchAvailabilityToPois
import fr.geoking.julius.api.belib.StationAvailabilitySummary
import fr.geoking.julius.api.traffic.TrafficInfo
import fr.geoking.julius.api.traffic.TrafficProviderFactory
import fr.geoking.julius.api.traffic.TrafficRequest
import fr.geoking.julius.api.traffic.TrafficSeverity
import fr.geoking.julius.shared.conversation.ConversationStore
import fr.geoking.julius.shared.network.NetworkException
import fr.geoking.julius.shared.voice.VoiceEvent
import fr.geoking.julius.shared.voice.VoiceManager
import fr.geoking.julius.community.CommunityPoiRepository
import fr.geoking.julius.community.FavoritesRepository
import fr.geoking.julius.community.isCommunityPoiId
import fr.geoking.julius.ui.components.FilterFab
import fr.geoking.julius.ui.components.MapLoader
import fr.geoking.julius.ui.ColorHelper
import fr.geoking.julius.ui.map.AddPoiSheet
import fr.geoking.julius.ui.map.PoiDetailCard
import fr.geoking.julius.ui.map.PoiDetailsFullscreenDialog
import fr.geoking.julius.ui.map.PoiMarkerHelper
import fr.geoking.julius.ui.map.MarkerStyle
import fr.geoking.julius.poi.PoiMerger
import fr.geoking.julius.StationMapFilters
import fr.geoking.julius.effectiveIrvePowerLevels
import fr.geoking.julius.effectiveMapEnergyFilterIds
import fr.geoking.julius.effectiveProviders
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import fr.geoking.julius.api.routex.radiusKmFromMapViewport
import fr.geoking.julius.ui.anim.AnimationPalette
import fr.geoking.julius.ui.anim.AnimationPalettes

/** Converts a vector drawable to a BitmapDescriptor for map markers (fromResource only supports bitmaps). Scales with zoom when sizePx varies. */
private fun vectorDrawableToBitmapDescriptor(
    context: android.content.Context,
    drawableResId: Int,
    sizePx: Int
): BitmapDescriptor? {
    val drawable = ContextCompat.getDrawable(context, drawableResId) ?: return null
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    drawable.setBounds(0, 0, sizePx, sizePx)
    drawable.draw(canvas)
    return BitmapDescriptorFactory.fromBitmap(bitmap)
}

/** Marker size in px (fixed, not scaling with zoom). */
private fun markerSizePxForZoom(zoom: Float): Int {
    // Larger marker + label for readability on phone screens.
    return 120
}

private data class LoadedPoiRegion(
    val centerLat: Double,
    val centerLng: Double,
    val maxRadiusKmLoaded: Int,
    val loadedAtMs: Long
)

private fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val r = 6371.0
    val rad = kotlin.math.PI / 180.0
    val dLat = (lat2 - lat1) * rad
    val dLon = (lon2 - lon1) * rad
    val sinDLat = kotlin.math.sin(dLat / 2)
    val sinDLon = kotlin.math.sin(dLon / 2)
    val a = sinDLat * sinDLat +
        kotlin.math.cos(lat1 * rad) * kotlin.math.cos(lat2 * rad) *
        sinDLon * sinDLon
    val c = 2 * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))
    return r * c
}

private fun approxDistanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    // Fast equirectangular approximation (good for small distances).
    val dLatKm = (lat2 - lat1) * 111.0
    val avgLatRad = ((lat1 + lat2) / 2.0) * kotlin.math.PI / 180.0
    val dLonKm = (lon2 - lon1) * 111.0 * kotlin.math.cos(avgLatRad)
    return kotlin.math.sqrt(dLatKm * dLatKm + dLonKm * dLonKm)
}

@OptIn(ExperimentalMaterial3Api::class, kotlinx.coroutines.FlowPreview::class)
@Composable
fun MapScreen(
    poiProvider: PoiProvider,
    availabilityProviderFactory: BorneAvailabilityProviderFactory?,
    trafficProviderFactory: TrafficProviderFactory? = null,
    settingsManager: SettingsManager,
    store: ConversationStore,
    palette: AnimationPalette,
    onBack: () -> Unit,
    onPlanRoute: (() -> Unit)? = null,
    communityRepo: CommunityPoiRepository? = null,
    favoritesRepo: FavoritesRepository? = null
) {
    BackHandler { onBack() }

    val context = LocalContext.current
    val settings by settingsManager.settings.collectAsState()
    val selectedProviders = settings.selectedPoiProviders
    var cachedPois by remember { mutableStateOf<List<Poi>>(emptyList()) }
    var trafficInfo by remember { mutableStateOf<TrafficInfo?>(null) }
    var mapErrorMessage by remember(selectedProviders) { mutableStateOf<String?>(null) }
    var isErrorPaused by remember(selectedProviders) { mutableStateOf(false) }
    var retryCount by remember { mutableStateOf(0) }
    var showMapSettings by remember { mutableStateOf(false) }
    var showAddPoiSheet by remember { mutableStateOf(false) }
    var addPoiLinkedOfficialId by remember { mutableStateOf<String?>(null) }
    var addPoiInitialName by remember { mutableStateOf("") }
    var addPoiInitialAddress by remember { mutableStateOf("") }
    var addPoiInitialLat by remember { mutableStateOf<Double?>(null) }
    var addPoiInitialLng by remember { mutableStateOf<Double?>(null) }
    var addPoiExistingCommunityId by remember { mutableStateOf<String?>(null) }
    var showFavoritesOnly by remember { mutableStateOf(false) }
    var favoriteIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var isLoading by remember { mutableStateOf(false) }

    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            hasLocationPermission = isGranted
        }
    )

    val defaultLat = 48.8566
    val defaultLng = 2.3522

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(defaultLat, defaultLng), 12f)
    }

    var didInitialCenter by remember { mutableStateOf(false) }

    LaunchedEffect(hasLocationPermission) {
        if (hasLocationPermission && !didInitialCenter) {
            val location = LocationHelper.getCurrentLocation(context)
            if (location != null) {
                cameraPositionState.position = CameraPosition.fromLatLngZoom(
                    LatLng(location.latitude, location.longitude),
                    12f
                )
                didInitialCenter = true
            }
        }
    }

    var mapSizePx by remember { mutableStateOf(IntSize.Zero) }
    var selectedPoi by remember { mutableStateOf<Poi?>(null) }
    var scrollRequestPoiId by remember { mutableStateOf<String?>(null) }
    var poiForDetailsDialog by remember { mutableStateOf<Poi?>(null) }
    var availabilityByPoiId by remember { mutableStateOf<Map<String, StationAvailabilitySummary>>(emptyMap()) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val lazyListState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(favoritesRepo) {
        if (favoritesRepo != null) {
            favoriteIds = favoritesRepo.getFavorites().map { it.id }.toSet()
        }
    }

    val effectiveProviders = settings.effectiveProviders()
    val poiFetchKey = remember(
        effectiveProviders,
        settings.useVehicleFilter,
        settings.fuelCard,
        settings.vehicleType,
        settings.vehicleEnergy,
        settings.selectedOverpassAmenityTypes
    ) {
        buildString {
            append(effectiveProviders.sortedBy { it.name }.joinToString(",") { it.name })
            append("|vehicleFilter=").append(settings.useVehicleFilter)
            append("|fuelCard=").append(settings.fuelCard)
            append("|vehicleType=").append(settings.vehicleType)
            append("|vehicleEnergy=").append(settings.vehicleEnergy)
            append("|overpassAmenities=").append(settings.selectedOverpassAmenityTypes.sorted().joinToString(","))
        }
    }

    val loadedRegions = remember { mutableListOf<LoadedPoiRegion>() }
    // POI cache metadata: used for TTL + bounded cache size.
    val poiSeenAtMs = remember { mutableStateMapOf<String, Long>() }
    var lastCacheKey by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(poiFetchKey, mapSizePx, retryCount) {
        val currentCacheKey = "$poiFetchKey|size=${mapSizePx.width}x${mapSizePx.height}"
        val cacheKeyChanged = lastCacheKey != currentCacheKey
        if (cacheKeyChanged) {
            loadedRegions.clear()
            cachedPois = emptyList()
            poiSeenAtMs.clear()
            availabilityByPoiId = emptyMap()
            trafficInfo = null
            mapErrorMessage = null
            isErrorPaused = false
            lastCacheKey = currentCacheKey
        }

        if (mapSizePx.width <= 0 || mapSizePx.height <= 0) return@LaunchedEffect

        if (!hasLocationPermission) {
            launcher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        snapshotFlow { cameraPositionState.position }
            .debounce(350)
            .collectLatest { position ->
                if (isErrorPaused) return@collectLatest

                val centerLat = position.target.latitude
                val centerLng = position.target.longitude
                val zoom = position.zoom
                val nowMs = System.currentTimeMillis()
                val ttlMs = 8L * 60L * 60L * 1000L
                val expiresBeforeMs = nowMs - ttlMs
                val maxRegions = 8
                val maxPoisInCache = 1200

                val requiredRadiusKm = radiusKmFromMapViewport(
                    centerLat,
                    centerLng,
                    zoom,
                    mapSizePx.width,
                    mapSizePx.height
                ).coerceIn(1, 50)

                // TTL eviction before coverage check, so we don't "think" an expired region still covers the view.
                loadedRegions.removeAll { it.loadedAtMs < expiresBeforeMs }
                if (poiSeenAtMs.isNotEmpty()) {
                    val expiredPoiIds = poiSeenAtMs
                        .asSequence()
                        .filter { (_, seenAt) -> seenAt < expiresBeforeMs }
                        .map { (id, _) -> id }
                        .toSet()
                    if (expiredPoiIds.isNotEmpty()) {
                        poiSeenAtMs.keys.removeAll(expiredPoiIds)
                        cachedPois = cachedPois.filterNot { it.id in expiredPoiIds }
                        availabilityByPoiId = availabilityByPoiId - expiredPoiIds
                    }
                }

                val viewportCovered = loadedRegions.any { region ->
                    region.maxRadiusKmLoaded >= requiredRadiusKm &&
                        haversineKm(
                            centerLat,
                            centerLng,
                            region.centerLat,
                            region.centerLng
                        ) <= (region.maxRadiusKmLoaded - requiredRadiusKm).toDouble() + 0.5
                }

                if (viewportCovered) return@collectLatest

                mapErrorMessage = null

                val viewport = MapViewport(
                    zoom = zoom,
                    mapWidthPx = mapSizePx.width,
                    mapHeightPx = mapSizePx.height
                )

                try {
                    isLoading = true
                    val newPois = poiProvider.search(
                        PoiSearchRequest(
                            latitude = centerLat,
                            longitude = centerLng,
                            viewport = viewport,
                            categories = emptySet(),
                            skipFilters = true
                        )
                    )

                    cachedPois = PoiMerger.mergeInto(cachedPois, newPois)
                    // Update "seen" timestamps for TTL tracking.
                    val mergedNow = System.currentTimeMillis()
                    newPois.forEach { poiSeenAtMs[it.id] = mergedNow }
                    // POIs that were merged into existing entries may have kept the existing ID (and that is OK).
                    cachedPois.forEach { p ->
                        // Ensure stable entries remain "warm" when reloaded.
                        if (poiSeenAtMs[p.id] == null) poiSeenAtMs[p.id] = mergedNow
                    }
                    loadedRegions.add(
                        LoadedPoiRegion(
                            centerLat = centerLat,
                            centerLng = centerLng,
                            maxRadiusKmLoaded = requiredRadiusKm,
                            loadedAtMs = System.currentTimeMillis()
                        )
                    )

                    // Keep the region cache bounded.
                    while (loadedRegions.size > maxRegions) {
                        val currentCenter = LatLng(centerLat, centerLng)
                        val farthest = loadedRegions.maxBy { r ->
                            haversineKm(
                                r.centerLat,
                                r.centerLng,
                                currentCenter.latitude,
                                currentCenter.longitude
                            )
                        }
                        loadedRegions.remove(farthest)
                    }

                    // Keep the POI cache bounded: keep closest POIs to current center.
                    if (cachedPois.size > maxPoisInCache) {
                        val sortedByDist = cachedPois
                            .asSequence()
                            .map { p -> p to approxDistanceKm(centerLat, centerLng, p.latitude, p.longitude) }
                            .sortedBy { it.second }
                            .take(maxPoisInCache)
                            .map { it.first }
                            .toList()
                        val keepIds = sortedByDist.asSequence().map { it.id }.toSet()
                        cachedPois = sortedByDist
                        poiSeenAtMs.keys.retainAll(keepIds)
                        availabilityByPoiId = availabilityByPoiId.filterKeys { it in keepIds }
                    }

                    // Availability: refresh for POIs close enough for the cards.
                    val availabilityProvider = availabilityProviderFactory?.getProvider(centerLat, centerLng)
                    if (availabilityProvider != null) {
                        val availabilityRadiusKm = requiredRadiusKm.coerceAtMost(20).coerceAtLeast(10)
                        val availabilities = availabilityProvider.getAvailability(centerLat, centerLng, availabilityRadiusKm)
                        val poisForAvailability = cachedPois.filter { poi ->
                            approxDistanceKm(centerLat, centerLng, poi.latitude, poi.longitude) <= availabilityRadiusKm * 1.05
                        }
                        val matched = matchAvailabilityToPois(availabilities, poisForAvailability)
                        availabilityByPoiId = availabilityByPoiId + matched
                    }

                    // Traffic: updated only when a POI fetch happens.
                    val trafficProvider = trafficProviderFactory?.getProvider(centerLat, centerLng)
                    if (trafficProvider != null) {
                        val halfSpan = 0.15
                        trafficInfo = trafficProvider.getTraffic(
                            TrafficRequest.Bbox(
                                centerLat - halfSpan,
                                centerLng - halfSpan,
                                centerLat + halfSpan,
                                centerLng + halfSpan
                            )
                        )
                    } else {
                        trafficInfo = null
                    }
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    val msg = e.message?.takeIf { it.isNotBlank() } ?: e.toString()
                    mapErrorMessage = msg
                    isErrorPaused = true
                    store.recordError(
                        (e as? NetworkException)?.httpCode,
                        "Map ($selectedProviders): $msg"
                    )
                } finally {
                    isLoading = false
                }
            }
    }

    if (showMapSettings) {
        MapSettingsScreen(
            settingsManager = settingsManager,
            onDismiss = { showMapSettings = false }
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Gas Stations", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                actions = {
                    onPlanRoute?.let { plan ->
                        TextButton(onClick = plan) {
                            Text("Plan route", color = Color.White)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF0F172A)
                )
            )
        },
        floatingActionButton = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                FilterFab(
                    settingsManager = settingsManager,
                    favoritesFilterEnabled = settings.isLoggedIn && favoritesRepo != null,
                    showFavoritesOnly = showFavoritesOnly,
                    onShowFavoritesOnlyChange = { showFavoritesOnly = it }
                )

                FloatingActionButton(
                    onClick = { showMapSettings = true },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    shape = MaterialTheme.shapes.medium
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Map settings"
                    )
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            mapErrorMessage?.let { msg ->
                val configuration = LocalConfiguration.current
                val maxHeight = configuration.screenHeightDp.dp * 0.15f
                val clipboard = LocalClipboard.current

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = maxHeight),
                    color = MaterialTheme.colorScheme.errorContainer,
                    shadowElevation = 2.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = msg,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontSize = 14.sp,
                            modifier = Modifier.weight(1f),
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                onClick = {
                                    scope.launch {
                                        clipboard.setClipEntry(ClipEntry(android.content.ClipData.newPlainText("error", msg)))
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text("Copy", fontSize = 12.sp)
                            }
                            TextButton(
                                onClick = {
                                    mapErrorMessage = null
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text("Ignore", fontSize = 12.sp)
                            }
                            Button(
                                onClick = {
                                    mapErrorMessage = null
                                    isErrorPaused = false
                                    retryCount++
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error,
                                    contentColor = MaterialTheme.colorScheme.onError
                                ),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text("Retry", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                contentPadding = PaddingValues(horizontal = 16.dp)
            ) {
                item {
                    FilterChip(
                        selected = false,
                        onClick = { showMapSettings = true },
                        label = {
                            Text(
                                if (selectedProviders.isEmpty()) "No Source"
                                else if (selectedProviders.size == 1) {
                                    when (selectedProviders.first()) {
                                        PoiProviderType.Routex -> "Source: Routex"
                                        PoiProviderType.Etalab -> "Source: data.gouv.fr (prix carburants, instantané)"
                                        PoiProviderType.GasApi -> "Source: Gas API"
                                        PoiProviderType.DataGouv -> "Source: data.gouv.fr"
                                        PoiProviderType.DataGouvElec -> "Source: IRVE"
                                        PoiProviderType.OpenChargeMap -> "Source: Open Charge Map"
                                        PoiProviderType.Chargy -> "Source: Chargy (real-time)"
                                        PoiProviderType.OpenVanCamp -> "Source: OpenVan.camp (Luxembourg)"
                                        PoiProviderType.SpainMinetur -> "Source: Spain Minetur (official)"
                                        PoiProviderType.GermanyTankerkoenig -> "Source: Tankerkönig (Germany)"
                                        PoiProviderType.AustriaEControl -> "Source: E-Control (Austria)"
                                        PoiProviderType.Overpass -> "Source: OSM + data.gouv (camping, picnic…)"
                                        PoiProviderType.Hybrid -> "Source: Hybrid (Gas + EV)"
                                    }
                                } else "Sources (${selectedProviders.size})"
                            )
                        }
                    )
                }

                if (selectedProviders.anyProvidesFuel()) {
                    items(MAP_ENERGY_OPTIONS.filter { it.first != "electric" }) { (id, label) ->
                        val isSelected = settings.selectedMapEnergyTypes.contains(id)
                        val color = ColorHelper.getFuelColor(id) ?: MaterialTheme.colorScheme.primary
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                val newEnergies = if (isSelected) settings.selectedMapEnergyTypes - id else settings.selectedMapEnergyTypes + id
                                settingsManager.setMapEnergyTypes(newEnergies)
                            },
                            label = { Text(label) },
                            colors = FilterChipDefaults.filterChipColors(
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

                if (selectedProviders.anyProvidesElectric()) {
                    items(MAP_IRVE_POWER_OPTIONS) { (kw, label) ->
                        val isSelected = settings.mapPowerLevels.contains(kw)
                        val color = ColorHelper.getPowerColorByLevel(kw)
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                val newLevels = if (isSelected) settings.mapPowerLevels - kw else settings.mapPowerLevels + kw
                                settingsManager.setMapPowerLevels(newLevels)
                            },
                            label = { Text(label) },
                            colors = FilterChipDefaults.filterChipColors(
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

                if (settings.isLoggedIn && (communityRepo != null || favoritesRepo != null)) {
                    if (communityRepo != null) {
                        item {
                            FilterChip(
                                selected = false,
                                onClick = {
                                    addPoiInitialLat = cameraPositionState.position.target.latitude
                                    addPoiInitialLng = cameraPositionState.position.target.longitude
                                    addPoiLinkedOfficialId = null
                                    addPoiExistingCommunityId = null
                                    addPoiInitialName = ""
                                    addPoiInitialAddress = ""
                                    showAddPoiSheet = true
                                },
                                label = { Text("+ POI") }
                            )
                        }
                    }
                    if (favoritesRepo != null) {
                        item {
                            FilterChip(
                                selected = showFavoritesOnly,
                                onClick = { showFavoritesOnly = !showFavoritesOnly },
                                label = { Text(if (showFavoritesOnly) "Saved only" else "Saved") }
                            )
                        }
                    }
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .onSizeChanged { mapSizePx = it }
            ) {
                if (isLoading) {
                    MapLoader(
                        palette = palette,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .zIndex(1f)
                    )
                }

                val configuration = LocalConfiguration.current
                val mapPaddingBottom = if (selectedPoi != null) (configuration.screenHeightDp * 0.4f).dp else 0.dp

                GoogleMap(
                    modifier = Modifier.fillMaxSize(),
                    cameraPositionState = cameraPositionState,
                    properties = MapProperties(
                        isMyLocationEnabled = hasLocationPermission,
                        isTrafficEnabled = settings.mapTrafficEnabled
                    ),
                    uiSettings = MapUiSettings(myLocationButtonEnabled = hasLocationPermission),
                    contentPadding = PaddingValues(bottom = mapPaddingBottom)
                ) {
                    val mapContext = LocalContext.current
                    val zoom = cameraPositionState.position.zoom
                    val sizePx = remember(zoom) { markerSizePxForZoom(zoom) }

                    val center = cameraPositionState.position.target
                    val displayRadiusKm = if (mapSizePx.width > 0 && mapSizePx.height > 0) {
                        radiusKmFromMapViewport(
                            center.latitude,
                            center.longitude,
                            zoom,
                            mapSizePx.width,
                            mapSizePx.height
                        ).coerceIn(1, 50)
                    } else 0

                    val poisInViewRaw = remember(cachedPois, center, displayRadiusKm) {
                        if (displayRadiusKm > 0) {
                            cachedPois.filter { poi ->
                                approxDistanceKm(center.latitude, center.longitude, poi.latitude, poi.longitude) <= displayRadiusKm * 1.05
                            }
                        } else {
                            cachedPois
                        }
                    }

                    val poisInView = remember(poisInViewRaw, settings, effectiveProviders) {
                        StationMapFilters.apply(
                            settings = settings,
                            pois = poisInViewRaw,
                            providers = effectiveProviders,
                            skipWhenOnlyOverpass = true
                        )
                    }

                    val effectiveEnergies = settings.effectiveMapEnergyFilterIds()
                    val effectivePowerLevels = settings.effectiveIrvePowerLevels()

                    val poisToShow =
                        if (showFavoritesOnly && favoriteIds.isNotEmpty()) poisInView.filter { it.id in favoriteIds } else poisInView
                    poisToShow.forEach { poi ->
                        val availability = availabilityByPoiId[poi.id]
                        val isPoiSelected = selectedPoi?.id == poi.id
                        val markerBitmap = remember(poi, effectiveEnergies, effectivePowerLevels, isPoiSelected, sizePx, availability) {
                            BitmapDescriptorFactory.fromBitmap(
                                PoiMarkerHelper.getMarkerBitmap(
                                    context = mapContext,
                                    poi = poi,
                                    effectiveEnergyTypes = effectiveEnergies,
                                    effectivePowerLevels = effectivePowerLevels,
                                    isSelected = isPoiSelected,
                                    sizePx = sizePx,
                                    availability = availability,
                                    markerStyle = MarkerStyle.Bubble
                                )
                            )
                        }

                        Marker(
                            state = MarkerState(position = LatLng(poi.latitude, poi.longitude)),
                            title = poi.name,
                            snippet = poi.address,
                            icon = markerBitmap,
                            anchor = Offset(0.5f, 1f),
                            onClick = {
                                selectedPoi = poi
                                scrollRequestPoiId = poi.id
                                scope.launch { sheetState.show() }
                                true
                            }
                        )
                    }
                    trafficInfo?.events?.forEach { event ->
                        val bbox = event.bbox ?: return@forEach
                        val lat = (bbox.latMin + bbox.latMax) / 2
                        val lon = (bbox.lonMin + bbox.lonMax) / 2
                        val hue = when (event.severity) {
                            TrafficSeverity.Normal -> 120f
                            TrafficSeverity.Congestion -> 30f
                            TrafficSeverity.Closure, TrafficSeverity.Accident, TrafficSeverity.Roadworks -> 0f
                            TrafficSeverity.Unknown -> 60f
                        }
                        Marker(
                            state = MarkerState(position = LatLng(lat, lon)),
                            title = "${event.roadRef}${event.direction?.let { " ($it)" } ?: ""}",
                            snippet = event.message,
                            icon = BitmapDescriptorFactory.defaultMarker(hue),
                            onClick = { true }
                        )
                    }
                }
            }
        }
    }

    // Keep the map camera centered on the POI currently selected in the bottom sheet.
    // We intentionally wait for programmatic sheet scrolling to finish (see `scrollRequestPoiId`),
    // otherwise the map animation could fight with the sheet repositioning.
    LaunchedEffect(selectedPoi?.id, scrollRequestPoiId) {
        val poi = selectedPoi ?: return@LaunchedEffect
        if (scrollRequestPoiId != null) return@LaunchedEffect
        // Animation accounts for the contentPadding set in GoogleMap when selectedPoi != null
        cameraPositionState.animate(
            CameraUpdateFactory.newLatLng(
                LatLng(poi.latitude, poi.longitude)
            )
        )
    }

    if (selectedPoi != null) {
        val center = cameraPositionState.position.target
        val displayRadiusKm = if (mapSizePx.width > 0 && mapSizePx.height > 0) {
            radiusKmFromMapViewport(
                center.latitude,
                center.longitude,
                cameraPositionState.position.zoom,
                mapSizePx.width,
                mapSizePx.height
            ).coerceIn(1, 50)
        } else 0

        val poisInViewRaw = remember(cachedPois, center, displayRadiusKm) {
            if (displayRadiusKm > 0) {
                cachedPois.filter { poi ->
                    approxDistanceKm(center.latitude, center.longitude, poi.latitude, poi.longitude) <= displayRadiusKm * 1.05
                }
            } else {
                cachedPois
            }
        }

        val poisInView = remember(poisInViewRaw, settings, effectiveProviders) {
            StationMapFilters.apply(
                settings = settings,
                pois = poisInViewRaw,
                providers = effectiveProviders,
                skipWhenOnlyOverpass = true
            )
        }

        val base = if (showFavoritesOnly && favoriteIds.isNotEmpty()) poisInView.filter { it.id in favoriteIds } else poisInView
        val listToShow = remember(base, selectedPoi?.id) {
            val sel = selectedPoi ?: return@remember base
            if (base.any { it.id == sel.id }) base else listOf(sel) + base
        }
        val currentListToShow by rememberUpdatedState(listToShow)

        // If the user tapped a map marker, scroll the bottom sheet so the corresponding card is shown.
        LaunchedEffect(scrollRequestPoiId) {
            val requestId = scrollRequestPoiId ?: return@LaunchedEffect
            val index = currentListToShow.indexOfFirst { it.id == requestId }
            if (index >= 0) {
                lazyListState.scrollToItem(index)
            }
            scrollRequestPoiId = null
        }

        // When the LazyRow is snapped, consider the card closest to the center as the "selected" card.
        // Selecting a new card drives the camera centering via the `LaunchedEffect(selectedPoi?.id, ...)` above.
        val currentScrollRequestPoiId by rememberUpdatedState(scrollRequestPoiId)
        LaunchedEffect(lazyListState) {
            snapshotFlow { lazyListState.isScrollInProgress }.collect { inProgress ->
                if (inProgress) return@collect
                if (currentScrollRequestPoiId != null) return@collect

                val viewportWidth = lazyListState.layoutInfo.viewportSize.width
                if (viewportWidth <= 0) return@collect

                val viewportCenter = viewportWidth / 2
                val closestItem = lazyListState.layoutInfo.visibleItemsInfo.minByOrNull { item ->
                    val itemCenter = item.offset + item.size / 2
                    kotlin.math.abs(itemCenter - viewportCenter)
                }

                val centeredPoi = closestItem?.index?.let { idx -> currentListToShow.getOrNull(idx) } ?: return@collect
                if (selectedPoi?.id != centeredPoi.id) {
                    selectedPoi = centeredPoi
                }
            }
        }

        ModalBottomSheet(
            onDismissRequest = {
                scope.launch { sheetState.hide() }
                selectedPoi = null
                scrollRequestPoiId = null
            },
            sheetState = sheetState,
            sheetGesturesEnabled = true,
            containerColor = Color(0xFF1E293B),
            dragHandle = { BottomSheetDefaults.DragHandle(color = Color.White.copy(alpha = 0.7f)) }
        ) {
            LazyRow(
                state = lazyListState,
                flingBehavior = rememberSnapFlingBehavior(lazyListState = lazyListState),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = 32.dp, start = 16.dp, end = 16.dp)
            ) {
                items(listToShow, key = { it.id }) { poi ->
                    val isFav = poi.id in favoriteIds
                    PoiDetailCard(
                        modifier = Modifier.width((LocalConfiguration.current.screenWidthDp - 32).dp),
                        poi = poi,
                        availabilitySummary = availabilityByPoiId[poi.id],
                        highlightedFuelIds = settings.effectiveMapEnergyFilterIds(),
                        highlightedPowerLevels = settings.effectiveIrvePowerLevels(),
                        onNavigate = {
                            val uri = Uri.parse("geo:${poi.latitude},${poi.longitude}?q=${Uri.encode(poi.name)}")
                            context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                        },
                        onLocate = {
                            scope.launch {
                                cameraPositionState.animate(
                                    CameraUpdateFactory.newLatLng(
                                        LatLng(poi.latitude, poi.longitude)
                                    )
                                )
                            }
                        },
                        onShowDetails = { poiForDetailsDialog = poi },
                        isSelected = poi.id == selectedPoi?.id,
                        isLoggedIn = settings.isLoggedIn,
                        isFavorite = isFav,
                        onToggleFavorite = if (settings.isLoggedIn && favoritesRepo != null) {
                            {
                                scope.launch {
                                    favoritesRepo.toggleFavorite(poi)
                                    favoriteIds = favoritesRepo.getFavorites().map { it.id }.toSet()
                                }
                            }
                        } else null
                    )
                }
            }
        }
    }

    if (showAddPoiSheet) {
        AddPoiSheet(
            initialLat = addPoiInitialLat,
            initialLng = addPoiInitialLng,
            linkedOfficialId = addPoiLinkedOfficialId,
            existingCommunityId = addPoiExistingCommunityId,
            initialName = addPoiInitialName,
            initialAddress = addPoiInitialAddress,
            communityRepo = communityRepo,
            onDismiss = { showAddPoiSheet = false },
            onSaved = { retryCount++ }
        )
    }

    poiForDetailsDialog?.let { poi ->
        val ratingState = remember(poi.id) { mutableStateOf(settingsManager.getPoiRating(poi.id)) }
        PoiDetailsFullscreenDialog(
            poi = poi,
            availabilitySummary = availabilityByPoiId[poi.id],
            highlightedFuelIds = settings.effectiveMapEnergyFilterIds(),
            highlightedPowerLevels = settings.effectiveIrvePowerLevels(),
            rating = ratingState.value,
            onRate = { r ->
                settingsManager.setPoiRating(poi.id, r)
                ratingState.value = r
            },
            isLoggedIn = settings.isLoggedIn,
            isCommunityPoi = isCommunityPoiId(poi.id),
            onEdit = if (settings.isLoggedIn && isCommunityPoiId(poi.id) && communityRepo != null) {
                {
                    addPoiExistingCommunityId = poi.id
                    addPoiInitialName = poi.name
                    addPoiInitialAddress = poi.address
                    addPoiInitialLat = poi.latitude
                    addPoiInitialLng = poi.longitude
                    addPoiLinkedOfficialId = null
                    showAddPoiSheet = true
                    poiForDetailsDialog = null
                    selectedPoi = null
                    scope.launch { sheetState.hide() }
                }
            } else null,
            onRemove = if (settings.isLoggedIn && isCommunityPoiId(poi.id) && communityRepo != null) {
                {
                    scope.launch {
                        communityRepo.removeCommunityPoi(poi.id)
                        retryCount++
                        poiForDetailsDialog = null
                        selectedPoi = null
                        sheetState.hide()
                    }
                }
            } else null,
            onHide = if (settings.isLoggedIn && !isCommunityPoiId(poi.id) && communityRepo != null) {
                {
                    scope.launch {
                        communityRepo.hideOfficialPoi(poi.id)
                        retryCount++
                        poiForDetailsDialog = null
                        selectedPoi = null
                        sheetState.hide()
                    }
                }
            } else null,
            onSuggestCorrection = if (settings.isLoggedIn && !isCommunityPoiId(poi.id) && communityRepo != null) {
                {
                    addPoiLinkedOfficialId = poi.id
                    addPoiExistingCommunityId = null
                    addPoiInitialName = poi.name
                    addPoiInitialAddress = poi.address
                    addPoiInitialLat = poi.latitude
                    addPoiInitialLng = poi.longitude
                    showAddPoiSheet = true
                    poiForDetailsDialog = null
                    selectedPoi = null
                    scope.launch { sheetState.hide() }
                }
            } else null,
            onDismiss = { poiForDetailsDialog = null }
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0F172A)
@Composable
private fun MapScreenPreview() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val fakeStore = remember {
        ConversationStore(
            scope = scope,
            agent = object : ConversationalAgent {
                override suspend fun process(input: String) =
                    AgentResponse("Preview", null, null)
            },
            voiceManager = object : VoiceManager {
                override val events = MutableStateFlow(VoiceEvent.Silence)
                override val transcribedText = MutableStateFlow("")
                override val partialText = MutableStateFlow("")
                override fun startListening() {}
                override fun stopListening() {}
                override fun speak(text: String, languageTag: String?, isInterruptible: Boolean) {}
                override fun playAudio(bytes: ByteArray) {}
                override fun stopSpeaking() {}
                override fun setTranscriber(transcriber: suspend (ByteArray) -> String?) {}
            },
            actionExecutor = null,
            initialSpeechLanguageTag = null
        )
    }
    val fakeSettingsManager = remember {
        SettingsManager(context).apply {
            setPoiProviderTypes(setOf(PoiProviderType.Routex))
        }
    }
    val fakePoiProvider = object : PoiProvider {
        override suspend fun getGasStations(
            latitude: Double,
            longitude: Double,
            viewport: MapViewport?
        ): List<Poi> = emptyList()
    }

    MapScreen(
        poiProvider = fakePoiProvider,
        availabilityProviderFactory = null,
        settingsManager = fakeSettingsManager,
        store = fakeStore,
        palette = AnimationPalettes.paletteFor(0),
        onBack = {}
    )
}
