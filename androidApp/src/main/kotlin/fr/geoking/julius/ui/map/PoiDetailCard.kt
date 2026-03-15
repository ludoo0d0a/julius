package fr.geoking.julius.ui.map

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.geoking.julius.R
import fr.geoking.julius.providers.IrveDetails
import fr.geoking.julius.providers.Poi
import fr.geoking.julius.providers.availability.StationAvailabilitySummary
import fr.geoking.julius.ui.BrandHelper

private fun connectorTypeLabel(id: String): String = when (id) {
    "type_2" -> "Type 2"
    "combo_ccs" -> "CCS"
    "chademo" -> "CHAdeMO"
    "ef" -> "E/F"
    "autre" -> "Autre"
    else -> id
}

@Composable
fun PoiDetailCard(
    poi: Poi,
    availabilitySummary: StationAvailabilitySummary? = null,
    rating: Int? = null,
    onRate: ((Int) -> Unit)? = null,
    onNavigate: () -> Unit,
    onLocate: () -> Unit,
    onShowDetails: (() -> Unit)? = null,
    isLoggedIn: Boolean = false,
    isCommunityPoi: Boolean = false,
    isFavorite: Boolean = false,
    onToggleFavorite: (() -> Unit)? = null,
    onEdit: (() -> Unit)? = null,
    onRemove: (() -> Unit)? = null,
    onHide: (() -> Unit)? = null,
    onSuggestCorrection: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val rawSiteName = poi.siteName?.takeIf { it.isNotBlank() } ?: poi.name
    val isGenericName = rawSiteName.isBlank() ||
        rawSiteName.equals("Gas station", ignoreCase = true) ||
        rawSiteName.equals("Station", ignoreCase = true)
    val brandInfo = BrandHelper.getBrandInfo(poi.brand)
    val locationSummary = buildList {
        listOf(poi.townLocal, poi.postcode).filter { !it.isNullOrBlank() }.joinToString(", ").takeIf { it.isNotBlank() }?.let { add(it) }
        poi.countryLocal?.takeIf { it.isNotBlank() }?.let { add(it) }
    }.joinToString(", ").takeIf { it.isNotBlank() }
    val streetAddress = poi.addressLocal?.takeIf { it.isNotBlank() } ?: poi.address.takeIf { it.isNotBlank() }
    val displayTitle = when {
        !isGenericName -> rawSiteName
        brandInfo != null && !locationSummary.isNullOrBlank() -> "${brandInfo.displayName} – $locationSummary"
        brandInfo != null && !streetAddress.isNullOrBlank() -> "${brandInfo.displayName} – ${streetAddress.take(40)}${if (streetAddress.length > 40) "…" else ""}"
        brandInfo != null -> brandInfo.displayName
        !locationSummary.isNullOrBlank() -> locationSummary
        !streetAddress.isNullOrBlank() -> streetAddress.take(50).let { if (streetAddress.length > 50) "$it…" else it }
        else -> "%.4f, %.4f".format(poi.latitude, poi.longitude)
    }
    val addressLines = buildList {
        if (!streetAddress.isNullOrBlank()) add(streetAddress)
        if (!locationSummary.isNullOrBlank() && locationSummary != streetAddress) add(locationSummary)
        if (isEmpty()) add("%.4f, %.4f".format(poi.latitude, poi.longitude))
    }

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
                        text = displayTitle,
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = if (onShowDetails != null) Modifier.clickable { onShowDetails() } else Modifier
                    )
                    brandInfo?.let { info ->
                        if (isGenericName || !displayTitle.startsWith(info.displayName, ignoreCase = true)) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = info.displayName,
                                color = Color.White.copy(alpha = 0.75f),
                                fontSize = 13.sp
                            )
                        }
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
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = info,
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = Color.White.copy(alpha = 0.15f))
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
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
                    addressLines.forEachIndexed { index, line ->
                        Text(
                            text = line,
                            color = Color.White.copy(alpha = 0.95f),
                            fontSize = 14.sp
                        )
                        if (index < addressLines.lastIndex) Spacer(modifier = Modifier.height(2.dp))
                    }
                }
                IconButton(onClick = onLocate) {
                    Icon(
                        imageVector = Icons.Default.MyLocation,
                        contentDescription = "Locate",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(onClick = onNavigate) {
                    Icon(
                        imageVector = Icons.Default.Directions,
                        contentDescription = "Navigate",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
                if (isLoggedIn && onToggleFavorite != null) {
                    IconButton(onClick = onToggleFavorite) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Default.Star else Icons.Outlined.StarBorder,
                            contentDescription = if (isFavorite) "Saved" else "Save",
                            tint = if (isFavorite) Color(0xFFEAB308) else Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            if (isLoggedIn && (onEdit != null || onRemove != null || onHide != null || onSuggestCorrection != null)) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = Color.White.copy(alpha = 0.15f))
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (isCommunityPoi) {
                        onEdit?.let { TextButton(onClick = it) { Text("Edit", color = Color(0xFF94A3B8), fontSize = 12.sp) } }
                        onRemove?.let { TextButton(onClick = it) { Text("Remove", color = Color(0xFFFF6B6B), fontSize = 12.sp) } }
                    } else {
                        onHide?.let { TextButton(onClick = it) { Text("Hide on map", color = Color(0xFF94A3B8), fontSize = 12.sp) } }
                        onSuggestCorrection?.let { TextButton(onClick = it) { Text("Suggest correction", color = Color(0xFF94A3B8), fontSize = 12.sp) } }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            if (rating != null || onRate != null) {
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = Color.White.copy(alpha = 0.15f))
                Spacer(modifier = Modifier.height(12.dp))
                if (rating != null) {
                    Text(
                        text = "Note: $rating/5",
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                if (onRate != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        (1..5).forEach { star ->
                            IconButton(
                                onClick = { onRate(star) },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = if (rating != null && star <= rating) Icons.Default.Star else Icons.Outlined.StarBorder,
                                    contentDescription = "Note $star",
                                    tint = if (rating != null && star <= rating) Color(0xFFEAB308) else Color.White.copy(alpha = 0.5f),
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                    }
                }
            }

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

            if (poi.isElectric && poi.irveDetails != null) {
                val d = poi.irveDetails!!
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = Color.White.copy(alpha = 0.15f))
                Spacer(modifier = Modifier.height(12.dp))
                if (d.connectorTypes.isNotEmpty()) {
                    Text(
                        text = "Connecteurs",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        d.connectorTypes.sorted().forEach { id ->
                            AssistChip(
                                onClick = {},
                                label = { Text(connectorTypeLabel(id), fontSize = 12.sp) },
                                colors = AssistChipDefaults.assistChipColors(containerColor = Color(0xFF475569))
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }
                if (d.gratuit == true) {
                    Text(
                        text = "Gratuit",
                        color = Color(0xFF22C55E),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                d.tarification?.takeIf { it.isNotBlank() }?.let { text ->
                    Text(
                        text = "Tarification: $text",
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }
                d.openingHours?.takeIf { it.isNotBlank() }?.let { text ->
                    Text(
                        text = "Horaires: $text",
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }
                if (d.reservation == true) {
                    Text(
                        text = "Réservation possible",
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }
                listOfNotNull(
                    if (d.paymentActe == true) "À l'acte" else null,
                    if (d.paymentCb == true) "CB" else null,
                    if (d.paymentAutre == true) "Autre" else null
                ).joinToString(", ").takeIf { it.isNotBlank() }?.let { pay ->
                    Text(
                        text = "Paiement: $pay",
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }
                d.conditionAcces?.takeIf { it.isNotBlank() }?.let { text ->
                    Text(
                        text = "Accès: $text",
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 12.sp
                    )
                }
            }

        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0F172A)
@Composable
private fun PoiDetailCardPreview() {
    PoiDetailCard(
        poi = Poi(
            id = "preview",
            name = "Sample Station",
            brand = "Total",
            latitude = 48.8566,
            longitude = 2.3522,
            address = "1 Avenue des Champs-Élysées, 75008 Paris",
            addressLocal = null,
            townLocal = "Paris",
            postcode = "75008",
            countryLocal = "France",
            fuelPrices = emptyList(),
            routexDetails = null
        ),
        onNavigate = {},
        onLocate = {},
        onShowDetails = {}
    )
}

