package fr.geoking.julius.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import fr.geoking.julius.AppSettings
import fr.geoking.julius.SettingsManager
import fr.geoking.julius.effectiveIrvePowerLevels
import fr.geoking.julius.effectiveMapEnergyFilterIds
import fr.geoking.julius.poi.PoiProviderType
import fr.geoking.julius.poi.anyProvidesElectric
import fr.geoking.julius.poi.anyProvidesFuel
import fr.geoking.julius.ui.ColorHelper
import fr.geoking.julius.ui.MAP_ENERGY_OPTIONS
import fr.geoking.julius.ui.MAP_IRVE_POWER_OPTIONS
import fr.geoking.julius.ui.anim.AnimationPalette

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScaffold(
    title: String,
    settingsManager: SettingsManager,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onShowSettings: () -> Unit,
    showFavoritesOnly: Boolean = false,
    onShowFavoritesOnlyChange: ((Boolean) -> Unit)? = null,
    favoritesFilterEnabled: Boolean = false,
    isLoading: Boolean = false,
    palette: AnimationPalette? = null,
    content: @Composable (PaddingValues) -> Unit
) {
    val settings by settingsManager.settings.collectAsState()
    val selectedProviders = settings.selectedPoiProviders

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(title, color = Color.White) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = onRefresh) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh",
                                tint = Color.White
                            )
                        }
                        IconButton(onClick = onShowSettings) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Settings",
                                tint = Color.White
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color(0xFF0F172A)
                    )
                )

                // Unified Filter Bar
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF0F172A))
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    contentPadding = PaddingValues(horizontal = 16.dp)
                ) {
                    item {
                        FilterChip(
                            selected = false,
                            onClick = onShowSettings,
                            label = {
                                Text(
                                    if (selectedProviders.isEmpty()) "No Source"
                                    else if (selectedProviders.size == 1) {
                                        when (selectedProviders.first()) {
                                            PoiProviderType.Routex -> "Source: Routex"
                                            PoiProviderType.Etalab -> "Source: France (official)"
                                            PoiProviderType.GasApi -> "Source: Gas API"
                                            PoiProviderType.DataGouv -> "Source: France (official)"
                                            PoiProviderType.DataGouvElec -> "Source: IRVE"
                                            PoiProviderType.OpenChargeMap -> "Source: Open Charge Map"
                                            PoiProviderType.Chargy -> "Source: Chargy (real-time)"
                                            PoiProviderType.OpenVanCamp -> "Source: OpenVan.camp (Europe-wide)"
                                            PoiProviderType.SpainMinetur -> "Source: Spain Minetur (official)"
                                            PoiProviderType.GermanyTankerkoenig -> "Source: Tankerkönig (Germany)"
                                            PoiProviderType.AustriaEControl -> "Source: E-Control (Austria)"
                                            PoiProviderType.BelgiumOfficial -> "Source: Belgium (official)"
                                            PoiProviderType.PortugalDgeg -> "Source: Portugal DGEG (official)"
                                            PoiProviderType.MadeiraOfficial -> "Source: Madeira (official)"
                                            PoiProviderType.NetherlandsAnwb -> "Source: ANWB"
                                            PoiProviderType.SloveniaGoriva -> "Source: Goriva.si"
                                            PoiProviderType.RomaniaPeco -> "Source: Peco Online"
                                            PoiProviderType.Fuelo -> "Source: Fuelo.net"
                                            PoiProviderType.GreeceFuelGR -> "Source: FuelGR"
                                            PoiProviderType.AustraliaFuel -> "Source: Australia (official)"
                                            PoiProviderType.IrelandPickAPump -> "Source: Pick A Pump"
                                            PoiProviderType.SerbiaNis -> "Source: NIS / Cenagoriva"
                                            PoiProviderType.CroatiaMzoe -> "Source: MZOE (Croatia)"
                                            PoiProviderType.DrivstoffAppen -> "Source: DrivstoffAppen"
                                            PoiProviderType.DenmarkFuelprices -> "Source: Fuelprices.dk"
                                            PoiProviderType.FinlandPolttoaine -> "Source: Polttoaine.net"
                                            PoiProviderType.ArgentinaEnergia -> "Source: Argentina (official)"
                                            PoiProviderType.MexicoCRE -> "Source: Mexico (official)"
                                            PoiProviderType.MoldovaAnre -> "Source: Moldova (official)"
                                            PoiProviderType.Overpass -> "Source: OSM + data.gouv (camping, picnic…)"
                                            PoiProviderType.Hybrid -> "Source: Hybrid (Gas + EV)"
                                            else -> "Source: ${selectedProviders.first()}"
                                        }
                                    } else "Sources (${selectedProviders.size})"
                                )
                            }
                        )
                    }

                    if (selectedProviders.anyProvidesFuel() || selectedProviders.anyProvidesElectric()) {
                        items(MAP_ENERGY_OPTIONS) { (id, label) ->
                            val isSelected = settings.effectiveMapEnergyFilterIds().contains(id)
                            val color = ColorHelper.getFuelColor(id) ?: MaterialTheme.colorScheme.primary
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    val current = settings.selectedMapEnergyTypes
                                    val next = if (current.contains(id)) current - id else current + id
                                    settingsManager.setUseVehicleFilter(false)
                                    settingsManager.setMapEnergyTypes(next)
                                },
                                label = { Text(label) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = color,
                                    selectedLabelColor = Color.White,
                                    iconColor = color,
                                    selectedLeadingIconColor = Color.White
                                ),
                                leadingIcon = {
                                    Box(modifier = Modifier.size(12.dp).background(color, MaterialTheme.shapes.small))
                                }
                            )
                        }
                    }

                    if (selectedProviders.anyProvidesElectric()) {
                        items(MAP_IRVE_POWER_OPTIONS) { (kw, label) ->
                            val isSelected = settings.effectiveIrvePowerLevels().contains(kw)
                            val color = ColorHelper.getPowerColorByLevel(kw)
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    val current = settings.mapPowerLevels
                                    val next = if (current.contains(kw)) current - kw else current + kw
                                    settingsManager.setUseVehicleFilter(false)
                                    settingsManager.setMapPowerLevels(next)
                                },
                                label = { Text(label) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = color,
                                    selectedLabelColor = Color.White,
                                    iconColor = color,
                                    selectedLeadingIconColor = Color.White
                                ),
                                leadingIcon = {
                                    Box(modifier = Modifier.size(12.dp).background(color, MaterialTheme.shapes.small))
                                }
                            )
                        }
                    }
                }
            }
        },
        floatingActionButton = {
            FilterFab(
                settingsManager = settingsManager,
                favoritesFilterEnabled = favoritesFilterEnabled,
                showFavoritesOnly = showFavoritesOnly,
                onShowFavoritesOnlyChange = onShowFavoritesOnlyChange
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            content(padding)

            if (isLoading && palette != null) {
                MapLoader(
                    palette = palette,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = padding.calculateTopPadding())
                        .zIndex(1f)
                )
            }
        }
    }
}
