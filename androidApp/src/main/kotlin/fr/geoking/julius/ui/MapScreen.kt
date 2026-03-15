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
import androidx.compose.ui.platform.LocalContext
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
import fr.geoking.julius.providers.MapViewport
import fr.geoking.julius.providers.Poi
import fr.geoking.julius.providers.PoiProvider
import fr.geoking.julius.providers.PoiProviderType
import fr.geoking.julius.shared.ConversationStore
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

/** Marker size in px from map zoom (base 96 at zoom 12; scales like other brand icons). */
private fun markerSizePxForZoom(zoom: Float): Int {
    val size = (96 * (zoom / 12f)).toInt()
    return size.coerceIn(32, 128)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    poiProvider: PoiProvider,
    settingsManager: fr.geoking.julius.SettingsManager,
    store: ConversationStore,
    onBack: () -> Unit
) {
    BackHandler { onBack() }

    val context = LocalContext.current
    val settings by settingsManager.settings.collectAsState()
    val selectedProvider = settings.selectedPoiProvider
    var pois by remember { mutableStateOf<List<Poi>>(emptyList()) }
    var mapErrorMessage by remember(selectedProvider) { mutableStateOf<String?>(null) }
    var isErrorPaused by remember(selectedProvider) { mutableStateOf(false) }
    var retryCount by remember { mutableStateOf(0) }
    var showMapSettings by remember { mutableStateOf(false) }

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
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val scope = rememberCoroutineScope()

    LaunchedEffect(selectedProvider, settings.selectedMapEnergyTypes, settings.mapMinPowerKw, cameraPositionState.position, mapSizePx, retryCount) {
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
            pois = poiProvider.getGasStations(centerLat, centerLng, viewport)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            val msg = e.message?.takeIf { it.isNotBlank() } ?: e.toString()
            mapErrorMessage = msg
            isErrorPaused = true
            store.recordError((e as? fr.geoking.julius.shared.NetworkException)?.httpCode, "Map ($selectedProvider): $msg")
            pois = emptyList()
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
                Surface(
                    modifier = Modifier.fillMaxWidth(),
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
                            modifier = Modifier.weight(1f)
                        )
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
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .onSizeChanged { mapSizePx = it }
            ) {
                GoogleMap(
                    modifier = Modifier.fillMaxSize(),
                    cameraPositionState = cameraPositionState,
                    properties = MapProperties(isMyLocationEnabled = hasLocationPermission),
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
                    val iconCache = remember(mapContext, sizePx) {
                        mutableMapOf<Int, BitmapDescriptor>().apply {
                            put(R.drawable.ic_poi_gas_rounded, defaultGasIcon)
                            put(R.drawable.ic_poi_electric_rounded, defaultElectricIcon)
                        }
                    }
                    fun iconFor(poi: Poi): BitmapDescriptor {
                        val iconResId = when {
                            poi.isElectric -> R.drawable.ic_poi_electric_rounded
                            else -> BrandHelper.getBrandInfo(poi.brand)?.roundedIconResId ?: R.drawable.ic_poi_gas_rounded
                        }
                        return iconCache.getOrPut(iconResId) {
                            vectorDrawableToBitmapDescriptor(mapContext, iconResId, sizePx) ?: defaultGasIcon
                        }
                    }
                    pois.forEach { poi ->
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
                }
            }
        }
    }

    if (selectedPoi != null) {
        ModalBottomSheet(
            onDismissRequest = { scope.launch { sheetState.hide() }; selectedPoi = null },
            sheetState = sheetState,
            containerColor = Color(0xFF1E293B),
            dragHandle = { BottomSheetDefaults.DragHandle(color = Color.White.copy(alpha = 0.7f)) }
        ) {
            val listToShow = remember(pois, selectedPoi) {
                val sel = selectedPoi ?: return@remember pois
                listOf(sel) + pois.filter { it.id != sel.id }
            }
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = 32.dp)
            ) {
                items(listToShow, key = { it.id }) { poi ->
                    PoiDetailCard(
                        poi = poi,
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
                        onShowDetails = poi.routexDetails?.let { { poiForDetailsDialog = poi } }
                    )
                }
            }
        }
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
        settingsManager = fakeSettingsManager,
        store = fakeStore,
        onBack = {}
    )
}
