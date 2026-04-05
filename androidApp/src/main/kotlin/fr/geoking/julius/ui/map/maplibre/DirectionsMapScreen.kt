package fr.geoking.julius.ui.map.maplibre

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import fr.geoking.julius.SettingsManager
import fr.geoking.julius.api.routing.RouteResult
import fr.geoking.julius.poi.Poi
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.annotations.PolylineOptions
import org.maplibre.android.annotations.MarkerOptions

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DirectionsMapScreen(
    route: RouteResult?,
    pois: List<Poi>,
    settingsManager: SettingsManager,
    onBack: () -> Unit
) {
    BackHandler { onBack() }

    val settings by settingsManager.settings.collectAsState()
    var mapLibreMap by remember { mutableStateOf<MapLibreMap?>(null) }

    val initialCameraPosition = remember(route) {
        route?.points?.firstOrNull()?.let { point ->
            CameraPosition.Builder()
                .target(LatLng(point.first, point.second))
                .zoom(10.0)
                .build()
        } ?: CameraPosition.Builder()
            .target(LatLng(48.8566, 2.3522))
            .zoom(10.0)
            .build()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Navigation Preview", color = Color.White) },
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

                    // Draw route polyline
                    route?.points?.let { points ->
                        val latLngPoints = points.map { LatLng(it.first, it.second) }
                        map.addPolyline(
                            PolylineOptions()
                                .addAll(latLngPoints)
                                .color(android.graphics.Color.CYAN)
                                .width(5f)
                        )
                    }

                    // Draw markers for POIs along route
                    pois.forEach { poi ->
                        map.addMarker(
                            MarkerOptions()
                                .position(LatLng(poi.latitude, poi.longitude))
                                .title(poi.name)
                                .snippet(poi.address)
                        )
                    }
                }
            )

            // Zoom and center control
            FloatingActionButton(
                onClick = {
                    route?.points?.firstOrNull()?.let { point ->
                        mapLibreMap?.animateCamera(
                            CameraUpdateFactory.newLatLngZoom(LatLng(point.first, point.second), 15.0)
                        )
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ) {
                Text("Recenter", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
