package fr.geoking.julius.ui.map.maplibre

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
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
import fr.geoking.julius.SettingsManager
import fr.geoking.julius.poi.Poi
import fr.geoking.julius.poi.PoiProvider
import fr.geoking.julius.feature.location.LocationHelper
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.location.LocationComponentActivationOptions

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VectorMapScreen(
    poiProvider: PoiProvider,
    settingsManager: SettingsManager,
    onBack: () -> Unit
) {
    BackHandler { onBack() }

    val context = LocalContext.current
    val settings by settingsManager.settings.collectAsState()
    var mapLibreMap by remember { mutableStateOf<MapLibreMap?>(null) }
    var pois by remember { mutableStateOf<List<Poi>>(emptyList()) }

    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val initialCameraPosition = remember {
        CameraPosition.Builder()
            .target(LatLng(48.8566, 2.3522))
            .zoom(12.0)
            .build()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Vector Map", color = Color.White) },
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            MapLibreView(
                modifier = Modifier.fillMaxSize(),
                styleUrl = settings.mapTheme.styleUrl,
                cameraPosition = initialCameraPosition,
                onMapReady = { map ->
                    mapLibreMap = map
                    // Enable location layer if permission is granted
                    if (hasLocationPermission) {
                        map.style?.let { style ->
                            val options = LocationComponentActivationOptions.builder(context, style).build()
                            map.locationComponent.activateLocationComponent(options)
                            map.locationComponent.isLocationComponentEnabled = true
                        }
                    }
                }
            )

            // Basic overlay controls
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FloatingActionButton(
                    onClick = {
                        // Implement zoom in
                        mapLibreMap?.let {
                            it.animateCamera(org.maplibre.android.camera.CameraUpdateFactory.zoomIn())
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text("+", style = MaterialTheme.typography.titleLarge)
                }

                FloatingActionButton(
                    onClick = {
                        // Implement zoom out
                        mapLibreMap?.let {
                            it.animateCamera(org.maplibre.android.camera.CameraUpdateFactory.zoomOut())
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text("-", style = MaterialTheme.typography.titleLarge)
                }
            }
        }
    }
}
