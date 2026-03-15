package fr.geoking.julius.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import fr.geoking.julius.SettingsManager
import fr.geoking.julius.providers.PoiProviderType

private val PROVIDER_OPTIONS = listOf(
    PoiProviderType.Routex to "Routex (SiteFinder)",
    PoiProviderType.Etalab to "Etalab (open data)",
    PoiProviderType.GasApi to "gas-api.ovh",
    PoiProviderType.DataGouv to "data.gouv.fr (fuel)",
    PoiProviderType.DataGouvElec to "data.gouv.fr (IRVE)"
)

/**
 * Fuel/energy types for map filter, aligned with [prix-carburants.gouv.fr](https://www.prix-carburants.gouv.fr/).
 * Ids are used for persistence; labels match the official site.
 */
val MAP_ENERGY_OPTIONS = listOf(
    "electric" to "Electric (IRVE)",
    "gazole" to "Gazole (B7)",
    "sp98" to "SP98 (E5)",
    "sp95_e10" to "SP95-E10 (E10)",
    "sp95" to "SP95 (E5)",
    "gplc" to "GPLc (LPG)",
    "e85" to "E85 (E85)"
)

/** Type d'enseigne, aligned with prix-carburants.gouv.fr. */
val MAP_ENSEIGNE_OPTIONS = listOf(
    "all" to "Toutes les enseignes",
    "major" to "Major",
    "gms" to "Grande et moyenne surface",
    "independant" to "Indépendant"
)

/** Min power (kW) for IRVE filter, aligned with [LibreChargeMap](https://libre-charge-map.cipherbliss.com/). */
val MAP_IRVE_POWER_OPTIONS = listOf(
    0 to "0–20 kW",
    20 to "≥ 20 kW",
    50 to "≥ 50 kW",
    100 to "≥ 100 kW",
    200 to "≥ 200 kW",
    300 to "≥ 300 kW"
)

/** Services filter options, aligned with prix-carburants.gouv.fr. Subset of main services. */
val MAP_SERVICES_OPTIONS = listOf(
    "aire_camping_cars" to "Aire de camping-cars",
    "automate_cb" to "Automate CB 24/24",
    "bornes_electriques" to "Bornes électriques",
    "boutique_alimentaire" to "Boutique alimentaire",
    "dab" to "DAB",
    "lavage_auto" to "Lavage automatique",
    "lavage_manuel" to "Lavage manuel",
    "restauration_place" to "Restauration sur place",
    "restauration_emporter" to "Restauration à emporter",
    "toilettes" to "Toilettes publiques",
    "station_gonflage" to "Station de gonflage",
    "wifi" to "Wifi"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapSettingsScreen(
    settingsManager: SettingsManager,
    onDismiss: () -> Unit
) {
    val settings by settingsManager.settings.collectAsState()
    var selectedProvider by remember(settings.selectedPoiProvider) {
        mutableStateOf(settings.selectedPoiProvider)
    }
    var selectedEnergies by remember(settings.selectedMapEnergyTypes) {
        mutableStateOf(settings.selectedMapEnergyTypes)
    }
    var selectedEnseigne by remember(settings.mapEnseigneType) {
        mutableStateOf(settings.mapEnseigneType)
    }
    var selectedServices by remember(settings.selectedMapServices) {
        mutableStateOf(settings.selectedMapServices)
    }
    var selectedMinPowerKw by remember(settings.mapMinPowerKw) {
        mutableStateOf(settings.mapMinPowerKw)
    }

    fun persist() {
        if (selectedProvider != settings.selectedPoiProvider) {
            settingsManager.setPoiProviderType(selectedProvider)
        }
        if (selectedEnergies != settings.selectedMapEnergyTypes) {
            settingsManager.setMapEnergyTypes(selectedEnergies)
        }
        if (selectedEnseigne != settings.mapEnseigneType) {
            settingsManager.setMapEnseigneType(selectedEnseigne)
        }
        if (selectedServices != settings.selectedMapServices) {
            settingsManager.setMapServices(selectedServices)
        }
        if (selectedMinPowerKw != settings.mapMinPowerKw) {
            settingsManager.setMapMinPowerKw(selectedMinPowerKw)
        }
        onDismiss()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Map settings") },
                navigationIcon = {
                    IconButton(onClick = { persist() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(
                "Data source",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            PROVIDER_OPTIONS.forEach { (type, label) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = selectedProvider == type,
                        onClick = { selectedProvider = type }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            Text(
                "Energy types to show",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Select one or more; stations matching any selected type are shown.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MAP_ENERGY_OPTIONS.forEach { (id, label) ->
                    FilterChip(
                        selected = selectedEnergies.contains(id),
                        onClick = {
                            selectedEnergies = if (selectedEnergies.contains(id)) {
                                selectedEnergies - id
                            } else {
                                selectedEnergies + id
                            }
                        },
                        label = { Text(label) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            Text(
                "Min. power (IRVE)",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Show only stations with at least this power. Applied when data source is data.gouv.fr (IRVE).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MAP_IRVE_POWER_OPTIONS.forEach { (kw, label) ->
                    FilterChip(
                        selected = selectedMinPowerKw == kw,
                        onClick = { selectedMinPowerKw = kw },
                        label = { Text(label) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            Text(
                "Type d'enseigne",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Filter by station network type (applied when data is available).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            MAP_ENSEIGNE_OPTIONS.forEach { (id, label) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = selectedEnseigne == id,
                        onClick = { selectedEnseigne = id }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            Text(
                "Services",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Show only stations offering at least one selected service (when data is available).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MAP_SERVICES_OPTIONS.forEach { (id, label) ->
                    FilterChip(
                        selected = selectedServices.contains(id),
                        onClick = {
                            selectedServices = if (selectedServices.contains(id)) {
                                selectedServices - id
                            } else {
                                selectedServices + id
                            }
                        },
                        label = { Text(label) }
                    )
                }
            }
        }
    }
}
