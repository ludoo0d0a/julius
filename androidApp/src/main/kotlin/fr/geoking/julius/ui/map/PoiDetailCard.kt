package fr.geoking.julius.ui.map

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.geoking.julius.R
import fr.geoking.julius.api.availability.StationAvailabilitySummary
import fr.geoking.julius.poi.Poi
import fr.geoking.julius.ui.BrandHelper

@Composable
fun PoiDetailCard(
    poi: Poi,
    availabilitySummary: StationAvailabilitySummary? = null,
    onNavigate: () -> Unit,
    onLocate: () -> Unit,
    onShowDetails: () -> Unit,
    isLoggedIn: Boolean = false,
    isFavorite: Boolean = false,
    onToggleFavorite: (() -> Unit)? = null,
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
        modifier = modifier
            .widthIn(min = 300.dp, max = 360.dp)
            .height(210.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF334155)),
        shape = MaterialTheme.shapes.large
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
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
                                .size(48.dp)
                                .padding(10.dp)
                        ) {
                            val resId = brandInfo?.iconResId ?: if (poi.isElectric) R.drawable.ic_poi_electric else R.drawable.ic_poi_gas
                            Icon(
                                painter = painterResource(id = resId),
                                contentDescription = brandInfo?.displayName ?: if (poi.isElectric) "Charging station" else "Gas station",
                                modifier = Modifier.size(28.dp),
                                tint = Color.White
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = displayTitle,
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        brandInfo?.let { info ->
                            if (isGenericName || !displayTitle.startsWith(info.displayName, ignoreCase = true)) {
                                Text(
                                    text = info.displayName,
                                    color = Color.White.copy(alpha = 0.75f),
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        if (poi.isElectric) {
                            val info = listOfNotNull(
                                if (poi.isOnHighway) "Autoroute" else null,
                                poi.chargePointCount?.let { n ->
                                    if (n == 1) "1 point" else "$n points"
                                },
                                availabilitySummary?.let { s ->
                                    "${s.availableCount}/${s.totalCount} libres"
                                }
                            ).joinToString(" • ")
                            if (info.isNotBlank()) {
                                Text(
                                    text = info,
                                    color = Color.White.copy(alpha = 0.7f),
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = Color.White.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        addressLines.take(2).forEach { line ->
                            Text(
                                text = line,
                                color = Color.White.copy(alpha = 0.9f),
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                IconButton(onClick = onShowDetails) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Details",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.weight(1f))

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
