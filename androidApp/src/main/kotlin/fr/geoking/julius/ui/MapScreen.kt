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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboardManager
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
import fr.geoking.julius.poi.MapViewport
import fr.geoking.julius.poi.Poi
import fr.geoking.julius.poi.PoiProvider
import fr.geoking.julius.poi.PoiProviderType
import fr.geoking.julius.poi.PoiSearchRequest
import fr.geoking.julius.poi.PoiCategory
import fr.geoking.julius.api.availability.BorneAvailabilityProviderFactory
import fr.geoking.julius.api.availability.matchAvailabilityToPois
import fr.geoking.julius.api.availability.StationAvailabilitySummary
import fr.geoking.julius.api.traffic.TrafficInfo
import fr.geoking.julius.api.traffic.TrafficProviderFactory
import fr.geoking.julius.api.traffic.TrafficRequest
import fr.geoking.julius.api.traffic.TrafficSeverity
import fr.geoking.julius.shared.ConversationStore
import fr.geoking.julius.community.CommunityPoiRepository
import fr.geoking.julius.community.FavoritesRepository
import fr.geoking.julius.community.isCommunityPoiId
import fr.geoking.julius.ui.map.AddPoiSheet
import fr.geoking.julius.ui.map.PoiDetailCard
import fr.geoking.julius.ui.map.PoiDetailsFullscreenDialog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

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
    return 80
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    poiProvider: PoiProvider,
    availabilityProviderFactory: BorneAvailabilityProviderFactory?,
    trafficProviderFactory: TrafficProviderFactory? = null,
    settingsManager: fr.geoking.julius.SettingsManager,
    store: ConversationStore,
    onBack: () -> Unit,
    onPlanRoute: (() -> Unit)? = null,
    communityRepo: CommunityPoiRepository? = null,
    favoritesRepo: FavoritesRepository? = null
) {
    BackHandler { onBack() }

    val context = LocalContext.current
    val settings by settingsManager.settings.collectAsState()
    val selectedProvider = settings.selectedPoiProvider
    var pois by remember { mutableStateOf<List<Poi>>(emptyList()) }
    var trafficInfo by remember { mutableStateOf<TrafficInfo?>(null) }
    var mapErrorMessage by remember(selectedProvider) { mutableStateOf<String?>(null) }
    var isErrorPaused by remember(selectedProvider) { mutableStateOf(false) }
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
            val location = fr.geoking.julius.LocationHelper.getCurrentLocation(context)
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
    var poiForDetailsDialog by remember { mutableStateOf<Poi?>(null) }
    var availabilityByPoiId by remember { mutableStateOf<Map<String, StationAvailabilitySummary>>(emptyMap()) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val scope = rememberCoroutineScope()

    LaunchedEffect(pois, favoritesRepo) {
        if (favoritesRepo != null) {
            favoriteIds = favoritesRepo.getFavorites().map { it.id }.toSet()
        }
    }

    LaunchedEffect(selectedProvider, settings.selectedMapEnergyTypes, settings.mapMinPowerKw, settings.mapIrveOperator, settings.selectedMapConnectorTypes, cameraPositionState.position, mapSizePx, retryCount) {
        if (!hasLocationPermission) {
            launcher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (isErrorPaused) return@LaunchedEffect

        mapErrorMessage = null
        val center = cameraPositionState.position.target
        val centerLat = center.latitude
        val centerLng = center.longitude
        val zoom = cameraPositionState.position.zoom
        val viewport = if (mapSizePx.width > 0 && mapSizePx.height > 0) {
            MapViewport(zoom = zoom, mapWidthPx = mapSizePx.width, mapHeightPx = mapSizePx.height)
        } else null
        try {
            pois = poiProvider.search(PoiSearchRequest(centerLat, centerLng, viewport, emptySet()))
            val radiusKm = 10
            val availabilityProvider = availabilityProviderFactory?.getProvider(centerLat, centerLng)
            if (availabilityProvider != null) {
                try {
                    val availabilities = availabilityProvider.getAvailability(centerLat, centerLng, radiusKm)
                    availabilityByPoiId = matchAvailabilityToPois(availabilities, pois)
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    availabilityByPoiId = emptyMap()
                }
            } else {
                availabilityByPoiId = emptyMap()
            }
            val trafficProvider = trafficProviderFactory?.getProvider(centerLat, centerLng)
            if (trafficProvider != null) {
                try {
                    val halfSpan = 0.15
                    trafficInfo = trafficProvider.getTraffic(
                        TrafficRequest.Bbox(
                            centerLat - halfSpan,
                            centerLng - halfSpan,
                            centerLat + halfSpan,
                            centerLng + halfSpan
                        )
                    )
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    trafficInfo = null
                }
            } else {
                trafficInfo = null
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            val msg = e.message?.takeIf { it.isNotBlank() } ?: e.toString()
            mapErrorMessage = msg
            isErrorPaused = true
            store.recordError((e as? fr.geoking.julius.shared.NetworkException)?.httpCode, "Map ($selectedProvider): $msg")
            pois = emptyList()
            availabilityByPoiId = emptyMap()
            trafficInfo = null
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
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            mapErrorMessage?.let { msg ->
                val configuration = LocalConfiguration.current
                val maxHeight = configuration.screenHeightDp.dp * 0.15f
                val clipboardManager = LocalClipboardManager.current

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
                                    clipboardManager.setText(AnnotatedString(msg))
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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = false,
                    onClick = { showMapSettings = true },
                    label = {
                        Text(
                            when (selectedProvider) {
                                PoiProviderType.Routex -> "Source: Routex"
                                PoiProviderType.Etalab -> "Source: Etalab"
                                PoiProviderType.GasApi -> "Source: Gas API"
                                PoiProviderType.DataGouv -> "Source: data.gouv.fr"
                                PoiProviderType.DataGouvElec -> "Source: IRVE"
                                PoiProviderType.OpenChargeMap -> "Source: Open Charge Map"
                                PoiProviderType.Overpass -> "Source: OSM + data.gouv (camping, picnic…)"
                            }
                        )
                    }
                )
                if (settings.isLoggedIn && (communityRepo != null || favoritesRepo != null)) {
                    if (communityRepo != null) {
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
                    if (favoritesRepo != null) {
                        FilterChip(
                            selected = showFavoritesOnly,
                            onClick = { showFavoritesOnly = !showFavoritesOnly },
                            label = { Text(if (showFavoritesOnly) "Saved only" else "Saved") }
                        )
                    }
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .onSizeChanged { mapSizePx = it }
            ) {
                GoogleMap(
                    modifier = Modifier.fillMaxSize(),
                    cameraPositionState = cameraPositionState,
                    properties = MapProperties(
                        isMyLocationEnabled = hasLocationPermission,
                        isTrafficEnabled = settings.mapTrafficEnabled
                    ),
                    uiSettings = MapUiSettings(myLocationButtonEnabled = hasLocationPermission)
                ) {
                    val mapContext = LocalContext.current
                    val zoom = cameraPositionState.position.zoom
                    val sizePx = remember(zoom) { markerSizePxForZoom(zoom) }
                    val defaultGasIcon = remember(mapContext, sizePx) {
                        vectorDrawableToBitmapDescriptor(mapContext, R.drawable.ic_poi_gas_rounded, sizePx)
                            ?: BitmapDescriptorFactory.defaultMarker()
                    }
                    val defaultElectricIcon = remember(mapContext, sizePx) {
                        vectorDrawableToBitmapDescriptor(mapContext, R.drawable.ic_poi_electric_rounded, sizePx)
                            ?: defaultGasIcon
                    }
                    val defaultToiletIcon = remember(mapContext, sizePx) {
                        vectorDrawableToBitmapDescriptor(mapContext, R.drawable.ic_poi_toilet_rounded, sizePx) ?: defaultGasIcon
                    }
                    val defaultWaterIcon = remember(mapContext, sizePx) {
                        vectorDrawableToBitmapDescriptor(mapContext, R.drawable.ic_poi_water_rounded, sizePx) ?: defaultGasIcon
                    }
                    val defaultCampingIcon = remember(mapContext, sizePx) {
                        vectorDrawableToBitmapDescriptor(mapContext, R.drawable.ic_poi_camping_rounded, sizePx) ?: defaultGasIcon
                    }
                    val defaultCaravanIcon = remember(mapContext, sizePx) {
                        vectorDrawableToBitmapDescriptor(mapContext, R.drawable.ic_poi_caravan_rounded, sizePx) ?: defaultGasIcon
                    }
                    val defaultPicnicIcon = remember(mapContext, sizePx) {
                        vectorDrawableToBitmapDescriptor(mapContext, R.drawable.ic_poi_picnic_rounded, sizePx) ?: defaultGasIcon
                    }
                    val defaultTruckIcon = remember(mapContext, sizePx) {
                        vectorDrawableToBitmapDescriptor(mapContext, R.drawable.ic_poi_truck_rounded, sizePx) ?: defaultGasIcon
                    }
                    val defaultRestIcon = remember(mapContext, sizePx) {
                        vectorDrawableToBitmapDescriptor(mapContext, R.drawable.ic_poi_rest_rounded, sizePx) ?: defaultGasIcon
                    }
                    val defaultRestaurantIcon = remember(mapContext, sizePx) {
                        vectorDrawableToBitmapDescriptor(mapContext, R.drawable.ic_poi_restaurant_rounded, sizePx) ?: defaultGasIcon
                    }
                    val defaultFastFoodIcon = remember(mapContext, sizePx) {
                        vectorDrawableToBitmapDescriptor(mapContext, R.drawable.ic_poi_fastfood_rounded, sizePx) ?: defaultGasIcon
                    }
                    val iconCache = remember(mapContext, sizePx, defaultTruckIcon, defaultRestIcon, defaultRestaurantIcon, defaultFastFoodIcon) {
                        mutableMapOf<Int, BitmapDescriptor>().apply {
                            put(R.drawable.ic_poi_gas_rounded, defaultGasIcon)
                            put(R.drawable.ic_poi_electric_rounded, defaultElectricIcon)
                            put(R.drawable.ic_poi_toilet_rounded, defaultToiletIcon)
                            put(R.drawable.ic_poi_water_rounded, defaultWaterIcon)
                            put(R.drawable.ic_poi_camping_rounded, defaultCampingIcon)
                            put(R.drawable.ic_poi_caravan_rounded, defaultCaravanIcon)
                            put(R.drawable.ic_poi_picnic_rounded, defaultPicnicIcon)
                            put(R.drawable.ic_poi_truck_rounded, defaultTruckIcon)
                            put(R.drawable.ic_poi_rest_rounded, defaultRestIcon)
                            put(R.drawable.ic_poi_restaurant_rounded, defaultRestaurantIcon)
                            put(R.drawable.ic_poi_fastfood_rounded, defaultFastFoodIcon)
                        }
                    }
                    fun iconFor(poi: Poi): BitmapDescriptor {
                        val iconResId = when (poi.poiCategory) {
                            PoiCategory.Toilet -> R.drawable.ic_poi_toilet_rounded
                            PoiCategory.DrinkingWater -> R.drawable.ic_poi_water_rounded
                            PoiCategory.Camping -> R.drawable.ic_poi_camping_rounded
                            PoiCategory.CaravanSite -> R.drawable.ic_poi_caravan_rounded
                            PoiCategory.PicnicSite -> R.drawable.ic_poi_picnic_rounded
                            PoiCategory.TruckStop -> R.drawable.ic_poi_truck_rounded
                            PoiCategory.RestArea -> R.drawable.ic_poi_rest_rounded
                            PoiCategory.Restaurant -> R.drawable.ic_poi_restaurant_rounded
                            PoiCategory.FastFood -> R.drawable.ic_poi_fastfood_rounded
                            else -> when {
                                poi.isElectric -> R.drawable.ic_poi_electric_rounded
                                else -> BrandHelper.getBrandInfo(poi.brand)?.roundedIconResId ?: R.drawable.ic_poi_gas_rounded
                            }
                        }
                        return iconCache.getOrPut(iconResId) {
                            vectorDrawableToBitmapDescriptor(mapContext, iconResId, sizePx) ?: defaultGasIcon
                        }
                    }
                    val poisToShow = if (showFavoritesOnly && favoriteIds.isNotEmpty()) pois.filter { it.id in favoriteIds } else pois
                    poisToShow.forEach { poi ->
                        Marker(
                            state = MarkerState(position = LatLng(poi.latitude, poi.longitude)),
                            title = poi.name,
                            snippet = poi.address,
                            icon = iconFor(poi),
                            onClick = {
                                selectedPoi = poi
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

    if (selectedPoi != null) {
        val listToShow = remember(pois, selectedPoi, showFavoritesOnly, favoriteIds) {
            val base = if (showFavoritesOnly && favoriteIds.isNotEmpty()) pois.filter { it.id in favoriteIds } else pois
            val sel = selectedPoi ?: return@remember base
            listOf(sel) + base.filter { it.id != sel.id }
        }
        ModalBottomSheet(
            onDismissRequest = { scope.launch { sheetState.hide() }; selectedPoi = null },
            sheetState = sheetState,
            containerColor = Color(0xFF1E293B),
            dragHandle = { BottomSheetDefaults.DragHandle(color = Color.White.copy(alpha = 0.7f)) }
        ) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = 32.dp)
            ) {
                items(listToShow, key = { it.id }) { poi ->
                    val ratingState = remember(poi.id) { mutableStateOf(settingsManager.getPoiRating(poi.id)) }
                    val isFav = poi.id in favoriteIds
                    PoiDetailCard(
                        poi = poi,
                        availabilitySummary = availabilityByPoiId[poi.id],
                        rating = ratingState.value,
                        onRate = { r ->
                            settingsManager.setPoiRating(poi.id, r)
                            ratingState.value = r
                        },
                        onNavigate = {
                            val uri = Uri.parse("geo:${poi.latitude},${poi.longitude}?q=${Uri.encode(poi.name)}")
                            context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                        },
                        onLocate = {
                            scope.launch {
                                cameraPositionState.animate(
                                    CameraUpdateFactory.newLatLngZoom(
                                        LatLng(poi.latitude, poi.longitude),
                                        15f
                                    )
                                )
                            }
                        },
                        onShowDetails = poi.routexDetails?.let { { poiForDetailsDialog = poi } },
                        isLoggedIn = settings.isLoggedIn,
                        isCommunityPoi = isCommunityPoiId(poi.id),
                        isFavorite = isFav,
                        onToggleFavorite = if (settings.isLoggedIn && favoritesRepo != null) {
                            {
                                scope.launch {
                                    favoritesRepo.toggleFavorite(poi)
                                    favoriteIds = favoritesRepo.getFavorites().map { it.id }.toSet()
                                }
                            }
                        } else null,
                        onEdit = if (settings.isLoggedIn && isCommunityPoiId(poi.id) && communityRepo != null) {
                            {
                                addPoiExistingCommunityId = poi.id
                                addPoiInitialName = poi.name
                                addPoiInitialAddress = poi.address
                                addPoiInitialLat = poi.latitude
                                addPoiInitialLng = poi.longitude
                                addPoiLinkedOfficialId = null
                                showAddPoiSheet = true
                                scope.launch { sheetState.hide() }; selectedPoi = null
                            }
                        } else null,
                        onRemove = if (settings.isLoggedIn && isCommunityPoiId(poi.id) && communityRepo != null) {
                            {
                                scope.launch {
                                    communityRepo.removeCommunityPoi(poi.id)
                                    retryCount++
                                    sheetState.hide()
                                    selectedPoi = null
                                }
                            }
                        } else null,
                        onHide = if (settings.isLoggedIn && !isCommunityPoiId(poi.id) && communityRepo != null) {
                            {
                                scope.launch {
                                    communityRepo.hideOfficialPoi(poi.id)
                                    retryCount++
                                    sheetState.hide()
                                    selectedPoi = null
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
                                scope.launch { sheetState.hide() }; selectedPoi = null
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

    poiForDetailsDialog?.routexDetails?.let { details ->
        PoiDetailsFullscreenDialog(
            details = details,
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
            agent = object : fr.geoking.julius.agents.ConversationalAgent {
                override suspend fun process(input: String) =
                    fr.geoking.julius.agents.AgentResponse("Preview", null, null)
            },
            voiceManager = object : fr.geoking.julius.shared.VoiceManager {
                override val events = MutableStateFlow(fr.geoking.julius.shared.VoiceEvent.Silence)
                override val transcribedText = MutableStateFlow("")
                override val partialText = MutableStateFlow("")
                override fun startListening() {}
                override fun stopListening() {}
                override fun speak(text: String, languageTag: String?) {}
                override fun playAudio(bytes: ByteArray) {}
                override fun stopSpeaking() {}
                override fun setTranscriber(transcriber: suspend (ByteArray) -> String?) {}
            },
            actionExecutor = null,
            initialSpeechLanguageTag = null
        )
    }
    val fakeSettingsManager = remember {
        fr.geoking.julius.SettingsManager(context).apply {
            setPoiProviderType(PoiProviderType.Routex)
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
        onBack = {}
    )
}
