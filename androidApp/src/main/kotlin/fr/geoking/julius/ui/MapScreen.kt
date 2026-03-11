package fr.geoking.julius.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import fr.geoking.julius.R
import fr.geoking.julius.providers.FuelPrice
import fr.geoking.julius.providers.MapViewport
import fr.geoking.julius.providers.Poi
import fr.geoking.julius.providers.PoiProvider
import fr.geoking.julius.providers.PoiProviderType
import fr.geoking.julius.providers.RoutexSiteDetails
import fr.geoking.julius.shared.ConversationStore
import kotlinx.coroutines.launch

private val PROVIDER_OPTIONS = listOf(
    PoiProviderType.Routex to "Routex (SiteFinder)",
    PoiProviderType.Etalab to "Etalab (open data)",
    PoiProviderType.GasApi to "gas-api.ovh",
    PoiProviderType.DataGouv to "data.gouv.fr (fuel)",
    PoiProviderType.DataGouvElec to "data.gouv.fr (IRVE)"
)

/** Converts a vector drawable to a BitmapDescriptor for map markers (fromResource only supports bitmaps). */
private fun vectorDrawableToBitmapDescriptor(
    context: android.content.Context,
    drawableResId: Int,
    sizePx: Int = 96
): BitmapDescriptor? {
    val drawable = ContextCompat.getDrawable(context, drawableResId) ?: return null
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    drawable.setBounds(0, 0, sizePx, sizePx)
    drawable.draw(canvas)
    return BitmapDescriptorFactory.fromBitmap(bitmap)
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
    var providerDropdownExpanded by remember { mutableStateOf(false) }

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

    var mapSizePx by remember { mutableStateOf(IntSize.Zero) }
    var selectedPoi by remember { mutableStateOf<Poi?>(null) }
    var poiForDetailsDialog by remember { mutableStateOf<Poi?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val scope = rememberCoroutineScope()

    LaunchedEffect(selectedProvider, cameraPositionState.position, mapSizePx, retryCount) {
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
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Provider listbox
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                ExposedDropdownMenuBox(
                    expanded = providerDropdownExpanded,
                    onExpandedChange = { providerDropdownExpanded = it }
                ) {
                    OutlinedTextField(
                        value = PROVIDER_OPTIONS.find { it.first == selectedProvider }?.second ?: selectedProvider.name,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Data source") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = providerDropdownExpanded) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.5f),
                            focusedLabelColor = MaterialTheme.colorScheme.primary,
                            unfocusedLabelColor = Color.White.copy(alpha = 0.7f),
                            cursorColor = Color.White,
                            focusedTrailingIconColor = Color.White,
                            unfocusedTrailingIconColor = Color.White.copy(alpha = 0.7f)
                        )
                    )
                    ExposedDropdownMenu(
                        expanded = providerDropdownExpanded,
                        onDismissRequest = { providerDropdownExpanded = false }
                    ) {
                        PROVIDER_OPTIONS.forEach { (type, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    settingsManager.setPoiProviderType(type)
                                    providerDropdownExpanded = false
                                }
                            )
                        }
                    }
                }
            }
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
                    val defaultMarkerIcon = remember(mapContext) {
                        vectorDrawableToBitmapDescriptor(mapContext, R.drawable.ic_poi_gas)
                            ?: BitmapDescriptorFactory.defaultMarker()
                    }
                    val iconCache = remember(mapContext) {
                        mutableMapOf(R.drawable.ic_poi_gas to defaultMarkerIcon)
                    }
                    pois.forEach { poi ->
                        val iconResId = BrandHelper.getBrandInfo(poi.brand)?.iconResId ?: R.drawable.ic_poi_gas
                        val markerIcon = iconCache.getOrPut(iconResId) {
                            vectorDrawableToBitmapDescriptor(mapContext, iconResId) ?: defaultMarkerIcon
                        }
                        Marker(
                            state = MarkerState(position = LatLng(poi.latitude, poi.longitude)),
                            title = poi.name,
                            snippet = poi.address,
                            icon = markerIcon,
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
                        onShowDetails = poi.routexDetails?.let { { poiForDetailsDialog = poi } }
                    )
                }
            }
        }
    }

    poiForDetailsDialog?.routexDetails?.let { details ->
        RoutexDetailsFullscreenDialog(
            details = details,
            onDismiss = { poiForDetailsDialog = null }
        )
    }
}

@Composable
private fun PoiDetailCard(
    poi: Poi,
    onNavigate: () -> Unit,
    onShowDetails: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val siteName = poi.siteName?.takeIf { it.isNotBlank() } ?: poi.name
    val addressForTitle = poi.addressLocal?.takeIf { it.isNotBlank() } ?: poi.address.takeIf { it.isNotBlank() }
    val addressLines = buildList {
        listOf(poi.townLocal, poi.postcode).filter { !it.isNullOrBlank() }.joinToString(", ").takeIf { it.isNotBlank() }?.let { add(it) }
        poi.countryLocal?.takeIf { it.isNotBlank() }?.let { add(it) }
        if (isEmpty() && addressForTitle == null && poi.address.isNotBlank()) add(poi.address)
    }
    val brandInfo = BrandHelper.getBrandInfo(poi.brand)
    val hasLocation = !addressForTitle.isNullOrBlank() || addressLines.isNotEmpty()

    Card(
        modifier = modifier.widthIn(min = 300.dp, max = 360.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF334155)),
        shape = MaterialTheme.shapes.large
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            // Header: icon + name + brand
            Row(
                verticalAlignment = Alignment.Top,
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = Color(0xFF475569)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(56.dp)
                            .padding(12.dp)
                    ) {
                        (brandInfo?.iconResId ?: R.drawable.ic_poi_gas).let { resId ->
                            Icon(
                                painter = painterResource(id = resId),
                                contentDescription = brandInfo?.displayName ?: "Gas station",
                                modifier = Modifier.size(32.dp),
                                tint = Color.White
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = siteName,
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    brandInfo?.let { info ->
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = info.displayName,
                            color = Color.White.copy(alpha = 0.75f),
                            fontSize = 13.sp
                        )
                    }
                }
            }

            // Location block
            if (hasLocation) {
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = Color.White.copy(alpha = 0.15f))
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    verticalAlignment = Alignment.Top,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = Color.White.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        if (!addressForTitle.isNullOrBlank()) {
                            Text(
                                text = addressForTitle,
                                color = Color.White.copy(alpha = 0.95f),
                                fontSize = 14.sp
                            )
                            if (addressLines.isNotEmpty()) Spacer(modifier = Modifier.height(2.dp))
                        }
                        addressLines.forEach { line ->
                            Text(
                                text = line,
                                color = Color.White.copy(alpha = 0.8f),
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }

            // Prices
            poi.fuelPrices?.let { prices ->
                if (prices.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = Color.White.copy(alpha = 0.15f))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Prices",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    prices.forEach { fp ->
                        Column(modifier = Modifier.padding(vertical = 2.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = fp.fuelName,
                                    color = Color.White.copy(alpha = 0.9f),
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = if (fp.outOfStock) "—" else "€%.3f".format(fp.price),
                                    color = if (fp.outOfStock) Color.White.copy(alpha = 0.5f) else Color(0xFF22C55E),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            fp.updatedAt?.let { updated ->
                                Text(
                                    text = "Updated $updated",
                                    color = Color.White.copy(alpha = 0.5f),
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            }

            // Actions
            Spacer(modifier = Modifier.height(20.dp))
            if (onShowDetails != null) {
                OutlinedButton(
                    onClick = onShowDetails,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = Color.White
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Station details")
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
            Button(
                onClick = onNavigate,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0EA5E9)),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Directions,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = Color.White
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Navigate to")
            }
        }
    }
}

@Composable
private fun RoutexDetailRow(label: String, value: Boolean?) {
    if (value == null) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = Color.White.copy(alpha = 0.9f), fontSize = 14.sp)
        Text(
            if (value) "Yes" else "No",
            color = if (value) Color(0xFF22C55E) else Color.White.copy(alpha = 0.6f),
            fontSize = 14.sp
        )
    }
}

@Composable
private fun RoutexDetailRowStr(label: String, value: String?) {
    if (value.isNullOrBlank()) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        Text(label, color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp, modifier = Modifier.width(120.dp))
        Text(value, color = Color.White, fontSize = 14.sp)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RoutexDetailsFullscreenDialog(
    details: RoutexSiteDetails,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFF1E293B)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                TopAppBar(
                    title = { Text("Station details", color = Color.White) },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Close",
                                tint = Color.White
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0F172A))
                )
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp)
                ) {
                    Text("Services & amenities", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(8.dp))
                    RoutexDetailRow("Manned 24h", details.manned24h)
                    RoutexDetailRow("Manned / automat 24h", details.mannedAutomat24h)
                    RoutexDetailRow("Automat", details.automat)
                    RoutexDetailRow("Motorway", details.motorwayIndicator)
                    RoutexDetailRow("Restaurant", details.restaurant)
                    RoutexDetailRow("Shop", details.shop)
                    RoutexDetailRow("Snackbar", details.snackbar)
                    RoutexDetailRow("Car wash", details.carWash)
                    RoutexDetailRow("Showers", details.showers)
                    RoutexDetailRow("AdBlue pump", details.adBluePump)
                    RoutexDetailRow("R4T network", details.r4tNetwork)
                    RoutexDetailRow("Car vignette", details.carVignette)
                    RoutexDetailRow("High-speed diesel", details.highspeedDiesel)
                    RoutexDetailRow("Truck station", details.truckIndicator)
                    RoutexDetailRow("Truck parking", details.truckParking)
                    RoutexDetailRow("Truck diesel", details.truckDiesel)
                    RoutexDetailRow("Truck lane", details.truckLane)
                    RoutexDetailRow("Diesel bio", details.dieselBio)
                    RoutexDetailRow("HVO100", details.hvo100)
                    RoutexDetailRow("LNG", details.lng)
                    RoutexDetailRow("LPG", details.lpg)
                    RoutexDetailRow("CNG", details.cng)
                    RoutexDetailRow("AdBlue canister", details.adBlueCanister)
                    RoutexDetailRow("Open 24h", details.open24h)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Fuel opening hours", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(8.dp))
                    RoutexDetailRowStr("Mon", details.monOpenFuel?.let { o -> details.monCloseFuel?.let { c -> "$o – $c" } ?: o })
                    RoutexDetailRowStr("Tue", details.tueOpenFuel?.let { o -> details.tueCloseFuel?.let { c -> "$o – $c" } ?: o })
                    RoutexDetailRowStr("Wed", details.wedOpenFuel?.let { o -> details.wedCloseFuel?.let { c -> "$o – $c" } ?: o })
                    RoutexDetailRowStr("Thu", details.thuOpenFuel?.let { o -> details.thuCloseFuel?.let { c -> "$o – $c" } ?: o })
                    RoutexDetailRowStr("Fri", details.friOpenFuel?.let { o -> details.friCloseFuel?.let { c -> "$o – $c" } ?: o })
                    RoutexDetailRowStr("Sat", details.satOpenFuel?.let { o -> details.satCloseFuel?.let { c -> "$o – $c" } ?: o })
                    RoutexDetailRowStr("Sun", details.sunOpenFuel?.let { o -> details.sunCloseFuel?.let { c -> "$o – $c" } ?: o })
                    if (details.openingHoursFuel.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        details.openingHoursFuel.forEach { line ->
                            Text(line, color = Color.White.copy(alpha = 0.9f), fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}
