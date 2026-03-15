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

/** Energy type id → display label for map filter. */
val MAP_ENERGY_OPTIONS = listOf(
    "electric" to "Electric (IRVE)",
    "sp95" to "SP95",
    "sp98" to "SP98",
    "e85" to "E85",
    "gazole" to "Gazole"
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

    fun persist() {
        if (selectedProvider != settings.selectedPoiProvider) {
            settingsManager.setPoiProviderType(selectedProvider)
        }
        if (selectedEnergies != settings.selectedMapEnergyTypes) {
            settingsManager.setMapEnergyTypes(selectedEnergies)
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
        }
    }
}
