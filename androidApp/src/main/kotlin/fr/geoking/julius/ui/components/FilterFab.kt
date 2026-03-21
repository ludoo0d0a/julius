package fr.geoking.julius.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import fr.geoking.julius.DEFAULT_MAP_ENERGY_TYPES
import fr.geoking.julius.SettingsManager
import fr.geoking.julius.poi.PoiProviderType
import fr.geoking.julius.ui.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterFab(
    settingsManager: SettingsManager,
    modifier: Modifier = Modifier
) {
    val settings by settingsManager.settings.collectAsState()
    var showSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()

    // Mode: 0 for Fuel, 1 for Electric
    var filterMode by remember(settings.selectedPoiProvider) {
        mutableStateOf(
            if (settings.selectedPoiProvider == PoiProviderType.DataGouvElec ||
                settings.selectedPoiProvider == PoiProviderType.OpenChargeMap ||
                settings.selectedPoiProvider == PoiProviderType.Chargy
            ) 1 else 0
        )
    }

    val activeFilterCount = remember(settings, filterMode) {
        if (filterMode == 0) {
            val brandFilter = if (settings.mapBrand != "all") 1 else 0
            val energyFilter = if (settings.selectedMapEnergyTypes.size < DEFAULT_MAP_ENERGY_TYPES.size) 1 else 0
            brandFilter + energyFilter
        } else {
            val operatorFilter = if (settings.mapIrveOperator != "all") 1 else 0
            val powerFilter = if (settings.mapMinPowerKw > 0) 1 else 0
            val connectorFilter = if (settings.selectedMapConnectorTypes.isNotEmpty()) 1 else 0
            operatorFilter + powerFilter + connectorFilter
        }
    }

    ExtendedFloatingActionButton(
        onClick = { showSheet = true },
        icon = { Icon(Icons.Default.FilterList, contentDescription = null) },
        text = {
            Text(
                if (activeFilterCount > 0) "Filters ($activeFilterCount)" else "Filters"
            )
        },
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = modifier
    )

    if (showSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSheet = false },
            sheetState = sheetState,
            containerColor = Color(0xFF1E293B),
            dragHandle = { BottomSheetDefaults.DragHandle(color = Color.White.copy(alpha = 0.7f)) }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 32.dp)
            ) {
                Text(
                    "Search Filters",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp)
                ) {
                    SegmentedButton(
                        selected = filterMode == 0,
                        onClick = {
                            filterMode = 0
                            if (settings.selectedPoiProvider == PoiProviderType.DataGouvElec ||
                                settings.selectedPoiProvider == PoiProviderType.OpenChargeMap ||
                                settings.selectedPoiProvider == PoiProviderType.Chargy
                            ) {
                                settingsManager.setPoiProviderType(PoiProviderType.Routex)
                            }
                        },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                        label = { Text("Fuel") },
                        colors = SegmentedButtonDefaults.colors(
                            activeContainerColor = MaterialTheme.colorScheme.primary,
                            activeContentColor = MaterialTheme.colorScheme.onPrimary,
                            inactiveContainerColor = Color(0xFF334155),
                            inactiveContentColor = Color.White
                        )
                    )
                    SegmentedButton(
                        selected = filterMode == 1,
                        onClick = {
                            filterMode = 1
                            if (settings.selectedPoiProvider != PoiProviderType.DataGouvElec &&
                                settings.selectedPoiProvider != PoiProviderType.OpenChargeMap &&
                                settings.selectedPoiProvider != PoiProviderType.Chargy
                            ) {
                                settingsManager.setPoiProviderType(PoiProviderType.DataGouvElec)
                            }
                        },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                        label = { Text("Electric") },
                        colors = SegmentedButtonDefaults.colors(
                            activeContainerColor = MaterialTheme.colorScheme.primary,
                            activeContentColor = MaterialTheme.colorScheme.onPrimary,
                            inactiveContainerColor = Color(0xFF334155),
                            inactiveContentColor = Color.White
                        )
                    )
                }

                if (filterMode == 0) {
                    FuelFilters(settingsManager)
                } else {
                    ElectricFilters(settingsManager)
                }

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = { showSheet = false },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Apply")
                }
            }
        }
    }
}

@Composable
private fun FuelFilters(settingsManager: SettingsManager) {
    val settings by settingsManager.settings.collectAsState()

    val brandOptions = remember {
        listOf("all" to "Toutes les enseignes") + BrandHelper.brandNames.entries
            .map { it.key to it.value }
            .distinctBy { it.second }
            .sortedBy { it.second }
    }

    FilterSectionTitle("Brand")
    CompactDropdownField(
        options = brandOptions,
        selectedOption = settings.mapBrand,
        onOptionSelected = { settingsManager.setMapBrand(it as String) }
    )

    Spacer(modifier = Modifier.height(16.dp))

    FilterSectionTitle("Fuel Type")
    CompactDropdownField(
        options = listOf("all" to "Tous") + MAP_ENERGY_OPTIONS,
        selectedOption = if (settings.selectedMapEnergyTypes.size >= 7) "all" else settings.selectedMapEnergyTypes.firstOrNull() ?: "all",
        onOptionSelected = {
            val id = it as String
            if (id == "all") {
                settingsManager.setMapEnergyTypes(DEFAULT_MAP_ENERGY_TYPES)
            } else {
                settingsManager.setMapEnergyTypes(setOf(id))
            }
        }
    )
}

@Composable
private fun ElectricFilters(settingsManager: SettingsManager) {
    val settings by settingsManager.settings.collectAsState()

    FilterSectionTitle("Operator")
    CompactDropdownField(
        options = MAP_IRVE_OPERATOR_OPTIONS,
        selectedOption = settings.mapIrveOperator,
        onOptionSelected = { settingsManager.setMapIrveOperator(it as String) }
    )

    Spacer(modifier = Modifier.height(16.dp))

    FilterSectionTitle("Min. Power")
    CompactDropdownField(
        options = MAP_IRVE_POWER_OPTIONS,
        selectedOption = settings.mapMinPowerKw,
        onOptionSelected = { settingsManager.setMapMinPowerKw(it as Int) }
    )
}

@Composable
private fun FilterSectionTitle(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.labelLarge,
        color = Color.White.copy(alpha = 0.7f),
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CompactDropdownField(
    options: List<Pair<*, String>>,
    selectedOption: Any?,
    onOptionSelected: (Any?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = options.find { it.first == selectedOption }?.second ?: "Select..."

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = Modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                focusedTrailingIconColor = Color.White,
                unfocusedTrailingIconColor = Color.White.copy(alpha = 0.7f)
            ),
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = Color(0xFF334155)
        ) {
            options.forEach { (option, label) ->
                DropdownMenuItem(
                    text = { Text(label, color = Color.White) },
                    onClick = {
                        onOptionSelected(option)
                        expanded = false
                    }
                )
            }
        }
    }
}
