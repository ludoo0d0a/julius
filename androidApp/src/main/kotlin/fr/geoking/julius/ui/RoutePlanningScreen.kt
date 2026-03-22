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
import fr.geoking.julius.api.geocoding.GeocodingClient

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutePlanningScreen(
    routePlanner: RoutePlanner,
    routingClient: RoutingClient,
    tollCalculator: TollCalculator,
    trafficProviderFactory: TrafficProviderFactory? = null,
    poiProvider: PoiProvider,
    geocodingClient: GeocodingClient,
    settingsManager: SettingsManager,
    onBack: () -> Unit,
    initialDestination: fr.geoking.julius.NavDestination? = null
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    var originQuery by remember { mutableStateOf("") }
    var destQuery by remember(initialDestination) {
        mutableStateOf(
            if (initialDestination != null) {
                initialDestination.address ?: initialDestination.latitude?.let { "${initialDestination.latitude}, ${initialDestination.longitude}" } ?: ""
            } else ""
        )
    }
    var useCurrentLocationAsOrigin by remember { mutableStateOf(true) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var stations by remember { mutableStateOf<List<Poi>>(emptyList()) }
    var tollEstimate by remember { mutableStateOf<TollEstimate?>(null) }
    var routeTraffic by remember { mutableStateOf<TrafficInfo?>(null) }
    var calculateTrigger by remember { mutableStateOf(0) }

    val hasLocation = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

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
            Text("Origin", color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelMedium)
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                androidx.compose.material3.Switch(
                    checked = useCurrentLocationAsOrigin,
                    onCheckedChange = { useCurrentLocationAsOrigin = it }
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    if (useCurrentLocationAsOrigin) "Use my current location" else "Enter an address / city",
                    color = Color.White.copy(alpha = 0.9f)
                )
            }
            if (!useCurrentLocationAsOrigin) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = originQuery,
                    onValueChange = { originQuery = it.take(120) },
                    label = { Text("Origin address or city") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text("Destination", color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelMedium)
            Spacer(modifier = Modifier.height(4.dp))
            OutlinedTextField(
                value = destQuery,
                onValueChange = { destQuery = it.take(120) },
                label = { Text("Destination address or city") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = {
                    loading = true
                    error = null
                    stations = emptyList()
                    tollEstimate = null
                    routeTraffic = null
                    calculateTrigger++
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !loading && destQuery.isNotBlank() && (useCurrentLocationAsOrigin || originQuery.isNotBlank())
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

    LaunchedEffect(calculateTrigger, initialDestination) {
        if (calculateTrigger == 0 && initialDestination == null) return@LaunchedEffect
        loading = true
        error = null
        stations = emptyList()
        tollEstimate = null
        routeTraffic = null
        try {
            val origin = if (useCurrentLocationAsOrigin) {
                if (!hasLocation) {
                    loading = false
                    error = "Location permission is required to use current location"
                    return@LaunchedEffect
                }
                val loc = LocationHelper.getCurrentLocation(context)
                if (loc == null) {
                    loading = false
                    error = "Could not determine current location"
                    return@LaunchedEffect
                }
                Pair("Current location", loc.latitude to loc.longitude)
            } else {
                val results = geocodingClient.geocode(originQuery, limit = 1)
                val first = results.firstOrNull()
                if (first == null) {
                    loading = false
                    error = "Origin not found"
                    return@LaunchedEffect
                }
                Pair(first.label, first.latitude to first.longitude)
            }

            val destination = if (initialDestination?.latitude != null && initialDestination.longitude != null) {
                Pair(initialDestination.address ?: destQuery, initialDestination.latitude to initialDestination.longitude)
            } else {
                val destResults = geocodingClient.geocode(destQuery, limit = 1)
                val destFirst = destResults.firstOrNull()
                if (destFirst == null) {
                    loading = false
                    error = "Destination not found"
                    return@LaunchedEffect
                }
                Pair(destFirst.label, destFirst.latitude to destFirst.longitude)
            }

            val (oLat, oLon) = origin.second
            val (dLat, dLon) = destination.second

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
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            loading = false
            error = e.message ?: e.toString()
        }
    }
}
