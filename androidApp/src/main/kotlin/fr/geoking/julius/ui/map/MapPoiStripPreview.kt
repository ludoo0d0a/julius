package fr.geoking.julius.ui.map

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import fr.geoking.julius.poi.FuelPrice
import fr.geoking.julius.poi.IrveDetails
import fr.geoking.julius.poi.Poi
import fr.geoking.julius.poi.PoiCategory

/**
 * Map area placeholder + bottom POI strip, for Compose previews (no Google Maps / network).
 * Mirrors the layout of [fr.geoking.julius.ui.MapScreen] POI carousel.
 */
@Composable
private fun MapPoiStripPreviewContent(
    pois: List<Poi>,
    highlightedFuelIds: Set<String> = emptySet(),
    highlightedPowerLevels: Set<Int> = emptySet(),
    selectedIndex: Int = 0,
    mapCaption: String,
) {
    Column(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Color(0xFF0C4A6E)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = mapCaption,
                color = Color.White.copy(alpha = 0.85f),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(24.dp)
            )
        }
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 24.dp, start = 16.dp, end = 16.dp)
        ) {
            itemsIndexed(pois, key = { _, p -> p.id }) { index, poi ->
                PoiDetailCard(
                    poi = poi,
                    highlightedFuelIds = highlightedFuelIds,
                    highlightedPowerLevels = highlightedPowerLevels,
                    onNavigate = {},
                    onLocate = {},
                    onShowDetails = {},
                    isSelected = index == selectedIndex
                )
            }
        }
    }
}

private fun previewIrvePoisDataGouv(): List<Poi> = listOf(
    Poi(
        id = "preview-irve-1",
        name = "Parking République • 22 kW",
        address = "12 Place de la République, 75011 Paris",
        latitude = 48.8676,
        longitude = 2.3635,
        brand = null,
        isElectric = true,
        poiCategory = PoiCategory.Irve,
        powerKw = 22.0,
        operator = "Izivia",
        isOnHighway = false,
        chargePointCount = 2,
        irveDetails = IrveDetails(
            connectorTypes = setOf("type_2", "combo_ccs"),
            tarification = "Payant",
            gratuit = false,
            openingHours = "24/7"
        ),
        siteName = "IRVE — Parking République",
        postcode = "75011",
        addressLocal = "12 Place de la République",
        countryLocal = "France",
        townLocal = "Paris",
        source = "DataGouv"
    ),
    Poi(
        id = "preview-irve-2",
        name = "Aire A6 • 150 kW",
        address = "A6, Aire de Beaune-Tailly, 21200 Beaune",
        latitude = 47.059,
        longitude = 4.842,
        brand = null,
        isElectric = true,
        poiCategory = PoiCategory.Irve,
        powerKw = 150.0,
        operator = "Ionity",
        isOnHighway = true,
        chargePointCount = 6,
        irveDetails = IrveDetails(
            connectorTypes = setOf("combo_ccs", "chademo"),
            tarification = "Payant",
            gratuit = false
        ),
        siteName = "IRVE — Aire Beaune-Tailly",
        postcode = "21200",
        addressLocal = "Aire de Beaune-Tailly",
        countryLocal = "France",
        townLocal = "Beaune",
        source = "DataGouv"
    )
)

private fun previewFuelPoisDataGouvSp95Filter(): List<Poi> = listOf(
    Poi(
        id = "preview-fuel-1",
        name = "Station",
        address = "42 Avenue de Suffren, 75015 Paris",
        latitude = 48.8504,
        longitude = 2.2945,
        brand = "TotalEnergies",
        fuelPrices = listOf(
            FuelPrice(fuelName = "Gazole", price = 1.789, updatedAt = "2025-03-28"),
            FuelPrice(fuelName = "SP95", price = 1.892, updatedAt = "2025-03-28"),
            FuelPrice(fuelName = "SP98", price = 1.992, updatedAt = "2025-03-28"),
            FuelPrice(fuelName = "E10", price = 1.852, updatedAt = "2025-03-28")
        ),
        siteName = "TotalEnergies Suffren",
        postcode = "75015",
        addressLocal = "42 Avenue de Suffren",
        countryLocal = "France",
        townLocal = "Paris",
        source = "DataGouv + GasApi"
    ),
    Poi(
        id = "preview-fuel-2",
        name = "Station",
        address = "8 Rue de Vaugirard, 75006 Paris",
        latitude = 48.8498,
        longitude = 2.3371,
        brand = "Esso",
        fuelPrices = listOf(
            FuelPrice(fuelName = "Gazole", price = 1.769),
            FuelPrice(fuelName = "SP95", price = 1.879, outOfStock = true)
        ),
        postcode = "75006",
        addressLocal = "8 Rue de Vaugirard",
        countryLocal = "France",
        townLocal = "Paris",
        source = "DataGouv"
    )
)

@Preview(showBackground = true, backgroundColor = 0xFF0F172A, name = "Map — data.gouv IRVE (mock)")
@Composable
private fun MapPoiStripPreviewDataGouvIrve() {
    MapPoiStripPreviewContent(
        pois = previewIrvePoisDataGouv(),
        highlightedFuelIds = emptySet(),
        highlightedPowerLevels = setOf(20, 50, 100, 150, 200),
        selectedIndex = 0,
        mapCaption = "Carte (aperçu)\nBase nationale IRVE — data.gouv.fr\nPOIs fictifs"
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF0F172A, name = "Map — data.gouv fuel, filtre SP95 (mock)")
@Composable
private fun MapPoiStripPreviewDataGouvFuelSp95() {
    MapPoiStripPreviewContent(
        pois = previewFuelPoisDataGouvSp95Filter(),
        highlightedFuelIds = setOf("sp95"),
        highlightedPowerLevels = emptySet(),
        selectedIndex = 0,
        mapCaption = "Carte (aperçu)\nPrix carburants (flux quotidien) — data.gouv / économie.gouv\nFiltre sur SP95"
    )
}
