package fr.geoking.julius.ui.map

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import fr.geoking.julius.api.belib.StationAvailabilitySummary
import fr.geoking.julius.poi.MapPoiFilter
import fr.geoking.julius.poi.Poi
import fr.geoking.julius.ui.BrandHelper
import fr.geoking.julius.ui.ColorHelper
import fr.geoking.julius.ui.util.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PoiDetailsFullscreenDialog(
    poi: Poi,
    availabilitySummary: StationAvailabilitySummary? = null,
    highlightedFuelIds: Set<String> = emptySet(),
    highlightedPowerLevels: Set<Int> = emptySet(),
    rating: Int? = null,
    onRate: ((Int) -> Unit)? = null,
    isLoggedIn: Boolean = false,
    isCommunityPoi: Boolean = false,
    onEdit: (() -> Unit)? = null,
    onRemove: (() -> Unit)? = null,
    onHide: (() -> Unit)? = null,
    onSuggestCorrection: (() -> Unit)? = null,
    onNavigate: (() -> Unit)? = null,
    isFavorite: Boolean = false,
    onToggleFavorite: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val brandInfo = BrandHelper.getBrandInfo(poi.brand)
    val sources = remember(poi.source) {
        poi.source
            ?.split("+")
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            ?.distinct()
            ?: emptyList()
    }
    val locationSummary = buildList {
        listOf(poi.townLocal, poi.postcode).filter { !it.isNullOrBlank() }.joinToString(", ").takeIf { it.isNotBlank() }?.let { add(it) }
        poi.countryLocal?.takeIf { it.isNotBlank() }?.let { add(it) }
    }.joinToString(", ").takeIf { it.isNotBlank() }
    val streetAddress = poi.addressLocal?.takeIf { it.isNotBlank() } ?: poi.address.takeIf { it.isNotBlank() }

    val addressLines = buildList {
        if (!streetAddress.isNullOrBlank()) add(streetAddress)
        if (!locationSummary.isNullOrBlank() && locationSummary != streetAddress) add(locationSummary)
        if (isEmpty()) add("%.4f, %.4f".format(poi.latitude, poi.longitude))
    }

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
                    title = { Text(poi.name.takeIf { it.isNotBlank() } ?: "Station details", color = Color.White) },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Close",
                                tint = Color.White
                            )
                        }
                    },
                    actions = {
                        if (isLoggedIn && onToggleFavorite != null) {
                            IconButton(onClick = onToggleFavorite) {
                                Icon(
                                    imageVector = if (isFavorite) Icons.Default.Star else Icons.Outlined.StarBorder,
                                    contentDescription = if (isFavorite) "Remove from favorites" else "Add to favorites",
                                    tint = if (isFavorite) Color(0xFFEAB308) else Color.White
                                )
                            }
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
                    // Header Info
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = poi.name.ifBlank { "Station" },
                                color = Color.White,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold
                            )
                            brandInfo?.let {
                                Text(it.displayName, color = Color.White.copy(alpha = 0.7f), fontSize = 16.sp)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onNavigate?.invoke() },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            addressLines.forEach { line ->
                                Text(line, color = Color.White.copy(alpha = 0.9f), fontSize = 14.sp)
                            }
                        }
                        Icon(
                            imageVector = Icons.Default.Directions,
                            contentDescription = "Navigate",
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    if (poi.isElectric) {
                         listOfNotNull(
                            poi.operator?.takeIf { it.isNotBlank() },
                            if (poi.isOnHighway) "Autoroute" else null,
                            poi.chargePointCount?.let { n ->
                                if (n == 1) "1 point de charge" else "$n points de charge"
                            },
                            availabilitySummary?.let { s ->
                                "${s.availableCount} / ${s.totalCount} disponibles"
                            }
                        ).joinToString(" • ").takeIf { it.isNotBlank() }?.let { info ->
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = info,
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 13.sp
                            )
                        }
                    }


                    // Community Actions
                    if (isLoggedIn && (onEdit != null || onRemove != null || onHide != null || onSuggestCorrection != null)) {
                        SectionHeader("Actions")
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (isCommunityPoi) {
                                onEdit?.let { TextButton(onClick = it) { Text("Edit", color = Color(0xFF94A3B8), fontSize = 13.sp) } }
                                onRemove?.let { TextButton(onClick = it) { Text("Remove", color = Color(0xFFFF6B6B), fontSize = 13.sp) } }
                            } else {
                                onHide?.let { TextButton(onClick = it) { Text("Hide on map", color = Color(0xFF94A3B8), fontSize = 13.sp) } }
                                onSuggestCorrection?.let { TextButton(onClick = it) { Text("Suggest correction", color = Color(0xFF94A3B8), fontSize = 13.sp) } }
                            }
                        }
                    }


                    // Fuel Prices
                    poi.fuelPrices?.let { prices ->
                        if (prices.isNotEmpty()) {
                            SectionHeader("Prices")
                            prices.forEach { fp ->
                                val fuelId = MapPoiFilter.fuelNameToId(fp.fuelName)
                                val matchColor = fuelId?.let { ColorHelper.getFuelColor(it) }
                                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = fp.fuelName,
                                            color = matchColor ?: Color.White.copy(alpha = 0.9f),
                                            fontSize = 14.sp
                                        )
                                        Text(
                                            text = if (fp.outOfStock) "—" else "€%.3f".format(fp.price),
                                            color = if (fp.outOfStock) Color.White.copy(alpha = 0.5f) else Color(0xFF22C55E),
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // IRVE Details
                    if (poi.isElectric && poi.irveDetails != null) {
                        val d = poi.irveDetails!!
                        SectionHeader("Connecteurs")
                        val powerKw = poi.powerKw
                        val powerColor = powerKw?.let { ColorHelper.getPowerColor(it) }
                        if (powerKw != null) {
                            Text(
                                text = "${powerKw.toInt()} kW",
                                color = powerColor ?: Color.White.copy(alpha = 0.9f),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        if (d.connectorTypes.isNotEmpty()) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                d.connectorTypes.sorted().forEach { id ->
                                    AssistChip(
                                        onClick = {},
                                        label = { Text(BrandHelper.connectorTypeLabel(id), fontSize = 12.sp) },
                                        colors = AssistChipDefaults.assistChipColors(containerColor = Color(0xFF475569)),
                                        interactionSource = remember { MutableInteractionSource() }
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                        if (d.gratuit == true) {
                            Text(
                                text = "Gratuit",
                                color = Color(0xFF22C55E),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        d.tarification?.takeIf { it.isNotBlank() }?.let { text ->
                            PoiDetailRowStr("Tarification", text)
                        }
                        d.openingHours?.takeIf { it.isNotBlank() }?.let { text ->
                            PoiDetailRowStr("Horaires", text)
                        }
                        if (d.reservation == true) {
                            PoiDetailRow("Réservation possible", true)
                        }
                        listOfNotNull(
                            if (d.paymentActe == true) "À l'acte" else null,
                            if (d.paymentCb == true) "CB" else null,
                            if (d.paymentAutre == true) "Autre" else null
                        ).joinToString(", ").takeIf { it.isNotBlank() }?.let { pay ->
                            PoiDetailRowStr("Paiement", pay)
                        }
                        d.conditionAcces?.takeIf { it.isNotBlank() }?.let { text ->
                            PoiDetailRowStr("Accès", text)
                        }
                    }

                    // Restaurant Details
                    poi.restaurantDetails?.let { d ->
                        SectionHeader("Restaurant")
                        if (d.isFastFood) {
                            Text(
                                text = "Fast food",
                                color = Color.White.copy(alpha = 0.9f),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        d.brand?.takeIf { it.isNotBlank() }?.let { text ->
                            PoiDetailRowStr("Enseigne", text)
                        }
                        d.cuisine?.takeIf { it.isNotBlank() }?.let { text ->
                            PoiDetailRowStr("Cuisine", text)
                        }
                        d.openingHours?.takeIf { it.isNotBlank() }?.let { text ->
                            PoiDetailRowStr("Horaires", text)
                        }
                    }

                    // Routex Details
                    poi.routexDetails?.let { details ->
                        val hasAmenities = listOf(
                            details.manned24h, details.mannedAutomat24h, details.automat, details.motorwayIndicator,
                            details.restaurant, details.shop, details.snackbar, details.carWash, details.showers,
                            details.adBluePump, details.r4tNetwork, details.carVignette, details.highspeedDiesel,
                            details.truckIndicator, details.truckParking, details.truckDiesel, details.truckLane,
                            details.dieselBio, details.hvo100, details.lng, details.lpg, details.cng,
                            details.adBlueCanister, details.open24h
                        ).any { it == true }

                        if (hasAmenities) {
                            SectionHeader("Services & amenities")
                            PoiDetailRow("Manned 24h", details.manned24h, Icons.Default.Person)
                            PoiDetailRow("Manned / automat 24h", details.mannedAutomat24h, Icons.Default.BrightnessAuto)
                            PoiDetailRow("Automat", details.automat, Icons.Default.Atm)
                            PoiDetailRow("Motorway", details.motorwayIndicator, Icons.Default.AddRoad)
                            PoiDetailRow("Restaurant", details.restaurant, Icons.Default.Restaurant)
                            PoiDetailRow("Shop", details.shop, Icons.Default.Storefront)
                            PoiDetailRow("Snackbar", details.snackbar, Icons.Default.Fastfood)
                            PoiDetailRow("Car wash", details.carWash, Icons.Default.LocalCarWash)
                            PoiDetailRow("Showers", details.showers, Icons.Default.Shower)
                            PoiDetailRow("AdBlue pump", details.adBluePump, Icons.Default.GasMeter)
                            PoiDetailRow("R4T network", details.r4tNetwork, Icons.Default.Hub)
                            PoiDetailRow("Car vignette", details.carVignette, Icons.Default.Style)
                            PoiDetailRow("High-speed diesel", details.highspeedDiesel, Icons.Default.Speed)
                            PoiDetailRow("Truck station", details.truckIndicator, Icons.Default.LocalShipping)
                            PoiDetailRow("Truck parking", details.truckParking, Icons.Default.Park)
                            PoiDetailRow("Truck diesel", details.truckDiesel, Icons.Default.LocalGasStation)
                            PoiDetailRow("Truck lane", details.truckLane, Icons.Default.MergeType)
                            PoiDetailRow("Diesel bio", details.dieselBio, Icons.Default.Eco)
                            PoiDetailRow("HVO100", details.hvo100, Icons.Default.Eco)
                            PoiDetailRow("LNG", details.lng, Icons.Default.PropaneTank)
                            PoiDetailRow("LPG", details.lpg, Icons.Default.Propane)
                            PoiDetailRow("CNG", details.cng, Icons.Default.WindPower)
                            PoiDetailRow("AdBlue canister", details.adBlueCanister, Icons.Default.ShoppingBag)
                            PoiDetailRow("Open 24h", details.open24h, Icons.Default.Schedule)
                        }

                        val hasOpeningHours = listOf(
                            details.monOpenFuel, details.tueOpenFuel, details.wedOpenFuel, details.thuOpenFuel,
                            details.friOpenFuel, details.satOpenFuel, details.sunOpenFuel
                        ).any { !it.isNullOrBlank() } || details.openingHoursFuel.isNotEmpty()

                        if (hasOpeningHours) {
                            SectionHeader("Fuel opening hours")
                            fun dayLine(open: String?, close: String?): String? {
                                val o = open?.takeIf { it.isNotBlank() && it != "null" }
                                val c = close?.takeIf { it.isNotBlank() && it != "null" }
                                if (o == null && c == null) return null
                                return if (o != null && c != null) "$o – $c" else o ?: c
                            }

                            PoiDetailRowStr("Mon", dayLine(details.monOpenFuel, details.monCloseFuel))
                            PoiDetailRowStr("Tue", dayLine(details.tueOpenFuel, details.tueCloseFuel))
                            PoiDetailRowStr("Wed", dayLine(details.wedOpenFuel, details.wedCloseFuel))
                            PoiDetailRowStr("Thu", dayLine(details.thuOpenFuel, details.thuCloseFuel))
                            PoiDetailRowStr("Fri", dayLine(details.friOpenFuel, details.friCloseFuel))
                            PoiDetailRowStr("Sat", dayLine(details.satOpenFuel, details.satCloseFuel))
                            PoiDetailRowStr("Sun", dayLine(details.sunOpenFuel, details.sunCloseFuel))

                            val filteredExtra = details.openingHoursFuel.filter { it.isNotBlank() && it != "null" }
                            if (filteredExtra.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                filteredExtra.forEach { line ->
                                    Text(line, color = Color.White.copy(alpha = 0.9f), fontSize = 13.sp)
                                }
                            }
                        }
                    }

                    if (sources.isNotEmpty()) {
                        SectionHeader("Sources")
                        sources.forEach { s ->
                            Text(
                                text = "• $s",
                                color = Color.White.copy(alpha = 0.9f),
                                fontSize = 14.sp
                            )
                        }
                    }

                    DateTimeFormatter.formatRelative(poi.effectiveUpdatedAt)?.let { relativeTime ->
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            text = "Last updated $relativeTime",
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 12.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }
}


@Composable
private fun SectionHeader(title: String) {
    Spacer(modifier = Modifier.height(24.dp))
    HorizontalDivider(color = Color.White.copy(alpha = 0.15f))
    Spacer(modifier = Modifier.height(12.dp))
    Text(
        text = title,
        color = Color.White,
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold
    )
    Spacer(modifier = Modifier.height(12.dp))
}
