package fr.geoking.julius.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import fr.geoking.julius.providers.Poi
import fr.geoking.julius.providers.PoiProvider
import fr.geoking.julius.providers.PoiProviderType
import kotlinx.coroutines.launch

private val PROVIDER_OPTIONS = listOf(
    PoiProviderType.Routex to "Routex (SiteFinder)",
    PoiProviderType.Etalab to "Etalab (open data)",
    PoiProviderType.DataGouv to "data.gouv.fr"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    poiProvider: PoiProvider,
    settingsManager: fr.geoking.julius.SettingsManager,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var pois by remember { mutableStateOf<List<Poi>>(emptyList()) }
    val scope = rememberCoroutineScope()
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

    val centerLat = 48.8566
    val centerLng = 2.3522

    LaunchedEffect(selectedProvider) {
        if (!hasLocationPermission) {
            launcher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        pois = poiProvider.getGasStations(centerLat, centerLng)
    }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(centerLat, centerLng), 12f)
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
            Box(modifier = Modifier.fillMaxSize()) {
                GoogleMap(
                    modifier = Modifier.fillMaxSize(),
                    cameraPositionState = cameraPositionState,
                    properties = MapProperties(isMyLocationEnabled = hasLocationPermission),
                    uiSettings = MapUiSettings(myLocationButtonEnabled = hasLocationPermission)
                ) {
                    pois.forEach { poi ->
                        Marker(
                            state = MarkerState(position = LatLng(poi.latitude, poi.longitude)),
                            title = poi.name,
                            snippet = poi.address
                        )
                    }
                }
            }
        }
    }
}
