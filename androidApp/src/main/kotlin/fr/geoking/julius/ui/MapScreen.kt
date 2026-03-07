package fr.geoking.julius.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
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
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var pois by remember { mutableStateOf<List<Poi>>(emptyList()) }
    val settings by settingsManager.settings.collectAsState()
    val selectedProvider = settings.selectedPoiProvider
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
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val scope = rememberCoroutineScope()

    LaunchedEffect(selectedProvider, cameraPositionState.position, mapSizePx) {
        if (!hasLocationPermission) {
            launcher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        val center = cameraPositionState.position.target
        val centerLat = center.latitude
        val centerLng = center.longitude
        val zoom = cameraPositionState.position.zoom
        val viewport = if (mapSizePx.width > 0 && mapSizePx.height > 0) {
            MapViewport(zoom = zoom, mapWidthPx = mapSizePx.width, mapHeightPx = mapSizePx.height)
        } else null
        pois = poiProvider.getGasStations(centerLat, centerLng, viewport)
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
                    // Vector drawable must be converted to Bitmap; fromResource() only supports raster drawables
                    val mapContext = LocalContext.current
                    val poiMarkerIcon = remember(mapContext) {
                        vectorDrawableToBitmapDescriptor(mapContext, R.drawable.ic_poi_gas)
                            ?: BitmapDescriptorFactory.defaultMarker()
                    }
                    pois.forEach { poi ->
                        Marker(
                            state = MarkerState(position = LatLng(poi.latitude, poi.longitude)),
                            title = poi.name,
                            snippet = poi.address,
                            icon = poiMarkerIcon,
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
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun PoiDetailCard(
    poi: Poi,
    onNavigate: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.widthIn(min = 280.dp, max = 340.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF334155)),
        shape = MaterialTheme.shapes.large
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            Text(
                text = poi.name,
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )
            val brand = poi.brand
            if (!brand.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = brand,
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 14.sp
                )
            }
            if (poi.address.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = poi.address,
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 14.sp
                )
            }
            poi.fuelPrices?.let { prices ->
                if (prices.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Prices",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(6.dp))
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
            Spacer(modifier = Modifier.height(16.dp))
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
                Text("Go to this station")
            }
        }
    }
}
