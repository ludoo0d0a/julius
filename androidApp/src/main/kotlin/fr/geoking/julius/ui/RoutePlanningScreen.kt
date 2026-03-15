package fr.geoking.julius.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import fr.geoking.julius.LocationHelper
import fr.geoking.julius.SettingsManager
import fr.geoking.julius.VehicleType
import fr.geoking.julius.poi.Poi
import fr.geoking.julius.poi.PoiProvider
import fr.geoking.julius.api.routing.RoutePlanner
import fr.geoking.julius.api.routing.RoutingClient
import fr.geoking.julius.toll.TollCalculator
import fr.geoking.julius.toll.TollEstimate
import fr.geoking.julius.api.traffic.TrafficInfo
import fr.geoking.julius.api.traffic.TrafficProviderFactory
import fr.geoking.julius.api.traffic.TrafficRequest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutePlanningScreen(
    routePlanner: RoutePlanner,
    routingClient: RoutingClient,
    tollCalculator: TollCalculator,
    trafficProviderFactory: TrafficProviderFactory? = null,
    poiProvider: PoiProvider,
    settingsManager: SettingsManager,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    var originLat by remember { mutableStateOf("") }
    var originLon by remember { mutableStateOf("") }
    var destLat by remember { mutableStateOf("") }
    var destLon by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var stations by remember { mutableStateOf<List<Poi>>(emptyList()) }
    var tollEstimate by remember { mutableStateOf<TollEstimate?>(null) }
    var routeTraffic by remember { mutableStateOf<TrafficInfo?>(null) }
    var calculateTrigger by remember { mutableStateOf(0) }

    val hasLocation = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    LaunchedEffect(Unit) {
        if (hasLocation && originLat.isEmpty()) {
            val loc = LocationHelper.getCurrentLocation(context)
            loc?.let {
                originLat = "%.4f".format(it.latitude)
                originLon = "%.4f".format(it.longitude)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Plan route") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF1E293B),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        },
        containerColor = Color(0xFF0F172A)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            Text("Origin (latitude, longitude)", color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelMedium)
            Spacer(modifier = Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = originLat,
                    onValueChange = { originLat = it.filter { c -> c.isDigit() || c == '-' || c == '.' }.take(12) },
                    label = { Text("Lat") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = originLon,
                    onValueChange = { originLon = it.filter { c -> c.isDigit() || c == '-' || c == '.' }.take(12) },
                    label = { Text("Lon") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text("Destination (latitude, longitude)", color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelMedium)
            Spacer(modifier = Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = destLat,
                    onValueChange = { destLat = it.filter { c -> c.isDigit() || c == '-' || c == '.' }.take(12) },
                    label = { Text("Lat") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = destLon,
                    onValueChange = { destLon = it.filter { c -> c.isDigit() || c == '-' || c == '.' }.take(12) },
                    label = { Text("Lon") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = {
                    if (originLat.isNotBlank() && originLon.isNotBlank() && destLat.isNotBlank() && destLon.isNotBlank()) {
                        loading = true
                        error = null
                        stations = emptyList()
                        tollEstimate = null
                        routeTraffic = null
                        calculateTrigger++
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !loading && originLat.isNotBlank() && originLon.isNotBlank() && destLat.isNotBlank() && destLon.isNotBlank()
            ) {
                Text(if (loading) "Calculating…" else "Calculate route")
            }

            error?.let { err ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(err, color = Color(0xFFF87171), style = MaterialTheme.typography.bodySmall)
            }

            if (loading) {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color.White)
                }
            }

            if (stations.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                tollEstimate?.let { toll ->
                    Text(
                        "Estimated toll: €%.2f".format(toll.amountEur),
                        color = Color.White.copy(alpha = 0.9f),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }
                routeTraffic?.let { info ->
                    val roadSummary = info.events.map { it.roadRef }.distinct().sorted().joinToString(", ")
                    Text(
                        "Traffic (${info.providerId}): ${info.events.size} events on $roadSummary",
                        color = Color.White.copy(alpha = 0.8f),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }
                val settings by settingsManager.settings.collectAsState()
                val title = if (settings.vehicleType == VehicleType.Truck || settings.vehicleType == VehicleType.Motorhome) {
                    "POIs along route (${stations.size})"
                } else {
                    "Stations along route (${stations.size})"
                }
                Text(title, color = Color.White, style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(stations, key = { it.id }) { poi ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF334155)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(poi.name.ifBlank { poi.address }, color = Color.White, style = MaterialTheme.typography.bodyLarge)
                                    Text(poi.address, color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall)
                                    poi.powerKw?.let { Text("$it kW", color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.labelSmall) }
                                }
                                IconButton(
                                    onClick = {
                                        val uri = Uri.parse("geo:${poi.latitude},${poi.longitude}?q=${Uri.encode(poi.name)}")
                                        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                                    }
                                ) {
                                    Icon(Icons.Default.Directions, contentDescription = "Navigate", tint = Color.White)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(calculateTrigger) {
        if (calculateTrigger == 0) return@LaunchedEffect
        val oLat = originLat.toDoubleOrNull()
        val oLon = originLon.toDoubleOrNull()
        val dLat = destLat.toDoubleOrNull()
        val dLon = destLon.toDoubleOrNull()
        if (oLat == null || oLon == null || dLat == null || dLon == null) {
            loading = false
            error = "Enter valid coordinates"
            return@LaunchedEffect
        }
        val settings = settingsManager.settings.value
        val route = routingClient.getRoute(oLat, oLon, dLat, dLon)
        if (route != null) {
            tollEstimate = tollCalculator.estimateToll(route.points, settings.vehicleType)
            val trafficProviders = trafficProviderFactory?.getProvidersForRoute(route.points).orEmpty()
            routeTraffic = trafficProviders.firstOrNull()?.let { provider ->
                provider.getTraffic(TrafficRequest.Route(route.points))
            }
        } else {
            tollEstimate = null
            routeTraffic = null
        }
        val result = routePlanner.getStationsAlongRoute(oLat, oLon, dLat, dLon, poiProvider)
        loading = false
        result.fold(
            onSuccess = { stations = it },
            onFailure = { error = it.message ?: "Route failed" }
        )
    }
}
