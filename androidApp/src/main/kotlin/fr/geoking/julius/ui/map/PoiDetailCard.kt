package fr.geoking.julius.ui.map

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
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
import fr.geoking.julius.providers.Poi
import fr.geoking.julius.ui.BrandHelper

@Composable
fun PoiDetailCard(
    poi: Poi,
    onNavigate: () -> Unit,
    onShowDetails: (() -> Unit)? = null,
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
                        fontWeight = FontWeight.SemiBold
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
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = Color.White.copy(alpha = 0.15f))
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                verticalAlignment = Alignment.Top,
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

            Spacer(modifier = Modifier.height(20.dp))
            if (onShowDetails != null) {
                OutlinedButton(
                    onClick = onShowDetails,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = Color.White
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Station details")
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
            Button(
                onClick = onNavigate,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0EA5E9)),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Directions,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = Color.White
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Navigate to")
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
        onShowDetails = {}
    )
}

