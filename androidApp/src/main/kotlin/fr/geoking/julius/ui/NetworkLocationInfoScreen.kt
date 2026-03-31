package fr.geoking.julius.ui

import android.location.Address
import android.location.Geocoder
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.geoking.julius.LocationHelper
import fr.geoking.julius.shared.NetworkService
import fr.geoking.julius.shared.NetworkStatus
import fr.geoking.julius.shared.NetworkType
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetworkLocationInfoScreen(
    networkService: NetworkService,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val networkStatus by networkService.status.collectAsState()
    var locationInfo by remember { mutableStateOf<LocationData?>(null) }
    var isLoadingLocation by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        isLoadingLocation = true
        val location = LocationHelper.getCurrentLocation(context)
        if (location != null) {
            val geocoder = Geocoder(context, Locale.getDefault())
            val addressInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                kotlin.coroutines.suspendCoroutine { continuation ->
                    geocoder.getFromLocation(location.latitude, location.longitude, 1, object : Geocoder.GeocodeListener {
                        override fun onGeocode(addresses: MutableList<Address>) {
                            continuation.resumeWith(Result.success(addresses.firstOrNull()?.let { formatAddress(it) }))
                        }
                        override fun onError(errorMessage: String?) {
                            continuation.resumeWith(Result.success(null))
                        }
                    })
                }
            } else {
                @Suppress("DEPRECATION")
                geocoder.getFromLocation(location.latitude, location.longitude, 1)?.firstOrNull()?.let { formatAddress(it) }
            }
            locationInfo = LocationData(
                latitude = location.latitude,
                longitude = location.longitude,
                address = addressInfo
            )
        }
        isLoadingLocation = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Network & Location", color = Color.White) },
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
        containerColor = Color(0xFF0F172A)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            InfoCard(title = "Network Status") {
                NetworkInfoRows(networkStatus)
            }

            InfoCard(title = "Location Information") {
                if (isLoadingLocation) {
                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                } else if (locationInfo != null) {
                    LocationInfoRows(locationInfo!!)
                } else {
                    Text("Location not available", color = Color.White.copy(alpha = 0.6f))
                }
            }

            Button(
                onClick = {
                    scope.launch {
                        isLoadingLocation = true
                        val location = LocationHelper.getCurrentLocation(context)
                        if (location != null) {
                            // ... same logic for geocoding ...
                            val geocoder = Geocoder(context, Locale.getDefault())
                            val addressInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                kotlin.coroutines.suspendCoroutine { continuation ->
                                    geocoder.getFromLocation(location.latitude, location.longitude, 1, object : Geocoder.GeocodeListener {
                                        override fun onGeocode(addresses: MutableList<Address>) {
                                            continuation.resumeWith(Result.success(addresses.firstOrNull()?.let { formatAddress(it) }))
                                        }
                                        override fun onError(errorMessage: String?) {
                                            continuation.resumeWith(Result.success(null))
                                        }
                                    })
                                }
                            } else {
                                @Suppress("DEPRECATION")
                                geocoder.getFromLocation(location.latitude, location.longitude, 1)?.firstOrNull()?.let { formatAddress(it) }
                            }
                            locationInfo = LocationData(location.latitude, location.longitude, addressInfo)
                        }
                        isLoadingLocation = false
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("Refresh Location")
            }
        }
    }
}

@Composable
private fun InfoCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.primary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            content()
        }
    }
}

@Composable
private fun NetworkInfoRows(status: NetworkStatus) {
    InfoRow(label = "Connected", value = if (status.isConnected) "Yes" else "No")
    InfoRow(label = "Type", value = status.networkType.toReadableString())
    InfoRow(label = "Operator", value = status.operatorName ?: "Unknown")
    InfoRow(label = "Country", value = status.countryName ?: status.countryCode ?: "Unknown")
    InfoRow(label = "Roaming", value = if (status.isRoaming) "Yes" else "No")
    InfoRow(label = "Signal Level", value = "${status.signalLevel} / 4")
}

@Composable
private fun LocationInfoRows(data: LocationData) {
    InfoRow(label = "Latitude", value = String.format("%.6f", data.latitude))
    InfoRow(label = "Longitude", value = String.format("%.6f", data.longitude))
    InfoRow(label = "Address", value = data.address ?: "Searching address...")
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
        Text(text = value, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

private fun NetworkType.toReadableString(): String = when (this) {
    NetworkType.WIFI -> "WiFi"
    NetworkType.FIVE_G -> "5G"
    NetworkType.FOUR_G -> "4G"
    NetworkType.THREE_G -> "3G"
    NetworkType.TWO_G -> "2G"
    NetworkType.EDGE -> "Edge"
    NetworkType.GPRS -> "GPRS"
    NetworkType.UNKNOWN -> "Unknown"
    NetworkType.NONE -> "None"
}

private fun formatAddress(address: Address): String {
    val sb = StringBuilder()
    for (i in 0..address.maxAddressLineIndex) {
        sb.append(address.getAddressLine(i))
        if (i < address.maxAddressLineIndex) sb.append(", ")
    }
    return sb.toString()
}

private data class LocationData(
    val latitude: Double,
    val longitude: Double,
    val address: String?
)
