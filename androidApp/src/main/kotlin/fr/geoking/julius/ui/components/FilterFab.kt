package fr.geoking.julius.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import fr.geoking.julius.poi.anyProvidesElectric
import fr.geoking.julius.poi.anyProvidesFuel
import fr.geoking.julius.ui.*
import fr.geoking.julius.ui.ColorHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterFab(
    settingsManager: SettingsManager,
    modifier: Modifier = Modifier,
    favoritesFilterEnabled: Boolean = false,
    showFavoritesOnly: Boolean = false,
    onShowFavoritesOnlyChange: ((Boolean) -> Unit)? = null
) {
    val settings by settingsManager.settings.collectAsState()
    var showSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()

    // Mode: 0 for Fuel, 1 for Electric, 2 for Hybrid
    var filterMode by remember(settings.selectedPoiProviders, settings.useVehicleFilter, settings.vehicleEnergy) {
        mutableStateOf(
            if (settings.useVehicleFilter) {
                when (settings.vehicleEnergy) {
                    "electric" -> 1
                    "hybrid" -> 2
                    else -> 0
                }
            } else {
                val p = settings.selectedPoiProviders
                val hasElec = p.anyProvidesElectric()
                val hasFuel = p.anyProvidesFuel()
                val hasHybrid = p.contains(PoiProviderType.Hybrid)

                when {
                    hasHybrid || (hasElec && hasFuel) -> 2
                    hasElec -> 1
                    else -> 0
                }
            }
        )
    }

    val favoritesFilterActive = favoritesFilterEnabled && showFavoritesOnly
    val activeFilterCount = remember(settings, filterMode, favoritesFilterActive) {
        val favCount = if (favoritesFilterActive) 1 else 0
        if (settings.useVehicleFilter) return@remember 1 + favCount
        val fuelFilters = if (filterMode == 0 || filterMode == 2) {
            val brandFilter = if (settings.mapBrands.isNotEmpty()) 1 else 0
            val energyFilter = if (settings.selectedMapEnergyTypes.size < DEFAULT_MAP_ENERGY_TYPES.size) 1 else 0
            brandFilter + energyFilter
        } else 0

        val elecFilters = if (filterMode == 1 || filterMode == 2) {
            val operatorFilter = if (settings.mapIrveOperators.isNotEmpty()) 1 else 0
            val powerFilter = if (settings.mapPowerLevels.isNotEmpty()) 1 else 0
            val connectorFilter = if (settings.selectedMapConnectorTypes.isNotEmpty()) 1 else 0
            operatorFilter + powerFilter + connectorFilter
        } else 0

        favCount + fuelFilters + elecFilters
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
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 32.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Search Filters",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("For my car", color = Color.White, style = MaterialTheme.typography.bodyMedium)
                        Switch(
                            checked = settings.useVehicleFilter,
                            onCheckedChange = { settingsManager.setUseVehicleFilter(it) },
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }

                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp)
                ) {
                    SegmentedButton(
                        selected = filterMode == 0,
                        onClick = {
                            filterMode = 0
                            val p = settings.selectedPoiProviders
                            val hasElec = p.anyProvidesElectric()
                            val hasHybrid = p.contains(PoiProviderType.Hybrid)
                            if (hasElec || hasHybrid) {
                                settingsManager.setPoiProviderTypes(setOf(PoiProviderType.Routex))
                            }
                        },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3),
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
                            val p = settings.selectedPoiProviders
                            val hasFuel = p.anyProvidesFuel()
                            val hasHybrid = p.contains(PoiProviderType.Hybrid)
                            if (hasFuel || hasHybrid || p.isEmpty()) {
                                settingsManager.setPoiProviderTypes(setOf(PoiProviderType.DataGouvElec))
                            }
                        },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3),
                        label = { Text("Electric") },
                        colors = SegmentedButtonDefaults.colors(
                            activeContainerColor = MaterialTheme.colorScheme.primary,
                            activeContentColor = MaterialTheme.colorScheme.onPrimary,
                            inactiveContainerColor = Color(0xFF334155),
                            inactiveContentColor = Color.White
                        )
                    )
                    SegmentedButton(
                        selected = filterMode == 2,
                        onClick = {
                            filterMode = 2
                            if (!settings.selectedPoiProviders.contains(PoiProviderType.Hybrid)) {
                                settingsManager.setPoiProviderTypes(setOf(PoiProviderType.Hybrid))
                            }
                        },
                        shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3),
                        label = { Text("Hybrid") },
                        colors = SegmentedButtonDefaults.colors(
                            activeContainerColor = MaterialTheme.colorScheme.primary,
                            activeContentColor = MaterialTheme.colorScheme.onPrimary,
                            inactiveContainerColor = Color(0xFF334155),
                            inactiveContentColor = Color.White
                        )
                    )
                }

                if (favoritesFilterEnabled && onShowFavoritesOnlyChange != null) {
                    FilterSectionTitle("Favorites")
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = showFavoritesOnly,
                            onClick = { onShowFavoritesOnlyChange(!showFavoritesOnly) },
                            label = { Text("My favorites") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                labelColor = Color.White,
                                containerColor = Color(0xFF334155)
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = showFavoritesOnly,
                                borderColor = Color.White.copy(alpha = 0.3f),
                                selectedBorderColor = MaterialTheme.colorScheme.primary
                            )
                        )
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }

                if (settings.useVehicleFilter) {
                    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)) {
                        val energyLabel = when (settings.vehicleEnergy) {
                            "electric" -> "Electric"
                            "hybrid" -> "Gas & Electric"
                            else -> "Fuel"
                        }
                        Text(
                            "Vehicle filters active: $energyLabel\n" +
                            "Using your car's predefined preferences.",
                            color = Color.White.copy(alpha = 0.7f),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                } else {
                    if (filterMode == 0 || filterMode == 2) {
                        FuelFilters(settingsManager)
                        if (filterMode == 2) Spacer(modifier = Modifier.height(24.dp))
                    }
                    if (filterMode == 1 || filterMode == 2) {
                        ElectricFilters(settingsManager)
                    }
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

    val brandOptions = remember { BrandHelper.getGasBrands() }

    FilterSectionTitle("Brands")
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        brandOptions.forEach { (id, label) ->
            FilterChip(
                selected = settings.mapBrands.contains(id),
                onClick = {
                    val newBrands = if (settings.mapBrands.contains(id)) settings.mapBrands - id else settings.mapBrands + id
                    settingsManager.setMapBrands(newBrands)
                },
                label = { Text(label) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                    labelColor = Color.White,
                    containerColor = Color(0xFF334155)
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = settings.mapBrands.contains(id),
                    borderColor = Color.White.copy(alpha = 0.3f),
                    selectedBorderColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    }

    Spacer(modifier = Modifier.height(16.dp))

    FilterSectionTitle("Fuel Types")
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        MAP_ENERGY_OPTIONS.filter { it.first != "electric" }.forEach { (id, label) ->
            val isSelected = settings.selectedMapEnergyTypes.contains(id)
            val chipColor = ColorHelper.getFuelColor(id) ?: MaterialTheme.colorScheme.primary
            FilterChip(
                selected = isSelected,
                onClick = {
                    val newEnergies = if (isSelected) settings.selectedMapEnergyTypes - id else settings.selectedMapEnergyTypes + id
                    settingsManager.setMapEnergyTypes(newEnergies)
                },
                label = { Text(label) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = chipColor,
                    selectedLabelColor = Color.White,
                    labelColor = Color.White,
                    containerColor = Color(0xFF334155)
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = isSelected,
                    borderColor = Color.White.copy(alpha = 0.3f),
                    selectedBorderColor = chipColor
                )
            )
        }
    }
}

@Composable
private fun ElectricFilters(settingsManager: SettingsManager) {
    val settings by settingsManager.settings.collectAsState()
    val brandOptions = remember { BrandHelper.getElectricBrands() }

    FilterSectionTitle("Brands / Operators")
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        brandOptions.forEach { (id, label) ->
            FilterChip(
                selected = settings.mapIrveOperators.contains(id),
                onClick = {
                    val newOps = if (settings.mapIrveOperators.contains(id)) settings.mapIrveOperators - id else settings.mapIrveOperators + id
                    settingsManager.setMapIrveOperators(newOps)
                },
                label = { Text(label) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                    labelColor = Color.White,
                    containerColor = Color(0xFF334155)
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = settings.mapIrveOperators.contains(id),
                    borderColor = Color.White.copy(alpha = 0.3f),
                    selectedBorderColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    }

    Spacer(modifier = Modifier.height(16.dp))

    FilterSectionTitle("Power Range")
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        MAP_IRVE_POWER_OPTIONS.forEach { (id, label) ->
            val isSelected = settings.mapPowerLevels.contains(id)
            val chipColor = ColorHelper.getPowerColorByLevel(id)
            FilterChip(
                selected = isSelected,
                onClick = {
                    val newLevels = if (isSelected) settings.mapPowerLevels - id else settings.mapPowerLevels + id
                    settingsManager.setMapPowerLevels(newLevels)
                },
                label = { Text(label) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = chipColor,
                    selectedLabelColor = Color.White,
                    labelColor = Color.White,
                    containerColor = Color(0xFF334155)
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = isSelected,
                    borderColor = Color.White.copy(alpha = 0.3f),
                    selectedBorderColor = chipColor
                )
            )
        }
    }

    Spacer(modifier = Modifier.height(16.dp))

    FilterSectionTitle("Connectors")
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        MAP_CONNECTOR_OPTIONS.forEach { (id, label) ->
            FilterChip(
                selected = settings.selectedMapConnectorTypes.contains(id),
                onClick = {
                    val newTypes = if (settings.selectedMapConnectorTypes.contains(id)) settings.selectedMapConnectorTypes - id else settings.selectedMapConnectorTypes + id
                    settingsManager.setMapConnectorTypes(newTypes)
                },
                label = { Text(label) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                    labelColor = Color.White,
                    containerColor = Color(0xFF334155)
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = settings.selectedMapConnectorTypes.contains(id),
                    borderColor = Color.White.copy(alpha = 0.3f),
                    selectedBorderColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    }
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
