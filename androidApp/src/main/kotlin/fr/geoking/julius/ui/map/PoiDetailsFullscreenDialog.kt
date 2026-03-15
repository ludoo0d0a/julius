package fr.geoking.julius.ui.map

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import fr.geoking.julius.api.routex.RoutexSiteDetails

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PoiDetailsFullscreenDialog(
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
                    PoiDetailRow("Manned 24h", details.manned24h)
                    PoiDetailRow("Manned / automat 24h", details.mannedAutomat24h)
                    PoiDetailRow("Automat", details.automat)
                    PoiDetailRow("Motorway", details.motorwayIndicator)
                    PoiDetailRow("Restaurant", details.restaurant)
                    PoiDetailRow("Shop", details.shop)
                    PoiDetailRow("Snackbar", details.snackbar)
                    PoiDetailRow("Car wash", details.carWash)
                    PoiDetailRow("Showers", details.showers)
                    PoiDetailRow("AdBlue pump", details.adBluePump)
                    PoiDetailRow("R4T network", details.r4tNetwork)
                    PoiDetailRow("Car vignette", details.carVignette)
                    PoiDetailRow("High-speed diesel", details.highspeedDiesel)
                    PoiDetailRow("Truck station", details.truckIndicator)
                    PoiDetailRow("Truck parking", details.truckParking)
                    PoiDetailRow("Truck diesel", details.truckDiesel)
                    PoiDetailRow("Truck lane", details.truckLane)
                    PoiDetailRow("Diesel bio", details.dieselBio)
                    PoiDetailRow("HVO100", details.hvo100)
                    PoiDetailRow("LNG", details.lng)
                    PoiDetailRow("LPG", details.lpg)
                    PoiDetailRow("CNG", details.cng)
                    PoiDetailRow("AdBlue canister", details.adBlueCanister)
                    PoiDetailRow("Open 24h", details.open24h)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Fuel opening hours", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(8.dp))
                    PoiDetailRowStr("Mon", details.monOpenFuel?.let { o -> details.monCloseFuel?.let { c -> "$o – $c" } ?: o })
                    PoiDetailRowStr("Tue", details.tueOpenFuel?.let { o -> details.tueCloseFuel?.let { c -> "$o – $c" } ?: o })
                    PoiDetailRowStr("Wed", details.wedOpenFuel?.let { o -> details.wedCloseFuel?.let { c -> "$o – $c" } ?: o })
                    PoiDetailRowStr("Thu", details.thuOpenFuel?.let { o -> details.thuCloseFuel?.let { c -> "$o – $c" } ?: o })
                    PoiDetailRowStr("Fri", details.friOpenFuel?.let { o -> details.friCloseFuel?.let { c -> "$o – $c" } ?: o })
                    PoiDetailRowStr("Sat", details.satOpenFuel?.let { o -> details.satCloseFuel?.let { c -> "$o – $c" } ?: o })
                    PoiDetailRowStr("Sun", details.sunOpenFuel?.let { o -> details.sunCloseFuel?.let { c -> "$o – $c" } ?: o })
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

