package fr.geoking.julius.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.geoking.julius.SettingsManager
import fr.geoking.julius.VehicleType
import fr.geoking.julius.poi.PoiProviderType
import fr.geoking.julius.poi.anyProvidesElectric
import fr.geoking.julius.poi.isUserSelectablePoiDataSource

val OVERPASS_AMENITY_OPTIONS = listOf(
    "toilets" to "Toilets",
    "drinking_water" to "Water",
    "camp_site" to "Camping",
    "caravan_site" to "Aire CC",
    "picnic_site" to "Picnic",
    "truck_stop" to "Truck",
    "rest_area" to "Rest",
    "restaurant" to "Resto",
    "fast_food" to "Fast food",
    "speed_camera" to "Radar"
)

val VEHICLE_TYPE_OPTIONS = listOf(
    VehicleType.Car to "Car",
    VehicleType.Truck to "Truck",
    VehicleType.Motorcycle to "Moto",
    VehicleType.Motorhome to "CC"
)

val MAP_ENERGY_OPTIONS = listOf(
    "electric" to "Elec",
    "gazole" to "Gazole",
    "sp98" to "SP98",
    "sp95" to "SP95",
    "gplc" to "GPLc",
    "e85" to "E85"
)

val MAP_ENSEIGNE_OPTIONS = listOf(
    "all" to "Toutes",
    "major" to "Major",
    "gms" to "GMS",
    "independant" to "Indépendant"
)

val MAP_IRVE_OPERATOR_OPTIONS = listOf(
    "all" to "Tous",
    "atlante" to "Atlante",
    "avia" to "Avia",
    "zunder" to "Zunder",
    "ionity" to "Ionity",
    "fastned" to "Fastned",
    "tesla" to "Tesla"
)

val MAP_CONNECTOR_OPTIONS = listOf(
    "type_2" to "Type 2",
    "combo_ccs" to "CCS",
    "chademo" to "CHAdeMO",
    "ef" to "E/F",
    "autre" to "Autre"
)

val MAP_IRVE_POWER_OPTIONS = listOf(
    0 to "0kW",
    20 to "20+",
    50 to "50+",
    100 to "100+",
    200 to "200+",
    300 to "300+"
)

val MAP_SERVICES_OPTIONS = listOf(
    "aire_camping_cars" to "Aire CC",
    "automate_cb" to "CB 24/24",
    "bornes_electriques" to "Elec",
    "boutique_alimentaire" to "Boutique",
    "dab" to "DAB",
    "lavage_auto" to "Lavage Auto",
    "lavage_manuel" to "Lavage Manuel",
    "restauration_place" to "Resto",
    "restauration_emporter" to "Takeaway",
    "toilettes" to "Toilettes",
    "station_gonflage" to "Gonflage",
    "wifi" to "Wifi"
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MapSettingsScreen(
    settingsManager: SettingsManager,
    onDismiss: () -> Unit
) {
    val settings by settingsManager.settings.collectAsState()

    var selectedProviders by remember(settings.selectedPoiProviders) { mutableStateOf(settings.selectedPoiProviders) }
    var selectedEnergies by remember(settings.selectedMapEnergyTypes) { mutableStateOf(settings.selectedMapEnergyTypes) }
    var selectedBrands by remember(settings.mapBrands) { mutableStateOf(settings.mapBrands) }
    var selectedEnseigne by remember(settings.mapEnseigneType) { mutableStateOf(settings.mapEnseigneType) }
    var selectedServices by remember(settings.selectedMapServices) { mutableStateOf(settings.selectedMapServices) }
    var selectedPowerLevels by remember(settings.mapPowerLevels) { mutableStateOf(settings.mapPowerLevels) }
    var selectedIrveOperators by remember(settings.mapIrveOperators) { mutableStateOf(settings.mapIrveOperators) }
    var selectedConnectorTypes by remember(settings.selectedMapConnectorTypes) { mutableStateOf(settings.selectedMapConnectorTypes) }
    var selectedMapTrafficEnabled by remember(settings.mapTrafficEnabled) { mutableStateOf(settings.mapTrafficEnabled) }
    var selectedOverpassAmenities by remember(settings.selectedOverpassAmenityTypes) { mutableStateOf(settings.selectedOverpassAmenityTypes) }
    var selectedVehicleType by remember(settings.vehicleType) { mutableStateOf(settings.vehicleType) }
    var evRangeKm by remember(settings.evRangeKm) { mutableStateOf(settings.evRangeKm.toString()) }
    var evConsumptionKwh by remember(settings.evConsumptionKwhPer100km) {
        mutableStateOf(settings.evConsumptionKwhPer100km?.toString() ?: "")
    }

    fun persist() {
        if (selectedProviders != settings.selectedPoiProviders) settingsManager.setPoiProviderTypes(selectedProviders)
        if (selectedEnergies != settings.selectedMapEnergyTypes) settingsManager.setMapEnergyTypes(selectedEnergies)
        if (selectedBrands != settings.mapBrands) settingsManager.setMapBrands(selectedBrands)
        if (selectedEnseigne != settings.mapEnseigneType) settingsManager.setMapEnseigneType(selectedEnseigne)
        if (selectedServices != settings.selectedMapServices) settingsManager.setMapServices(selectedServices)
        if (selectedPowerLevels != settings.mapPowerLevels) settingsManager.setMapPowerLevels(selectedPowerLevels)
        if (selectedIrveOperators != settings.mapIrveOperators) settingsManager.setMapIrveOperators(selectedIrveOperators)
        if (selectedConnectorTypes != settings.selectedMapConnectorTypes) settingsManager.setMapConnectorTypes(selectedConnectorTypes)
        if (selectedMapTrafficEnabled != settings.mapTrafficEnabled) settingsManager.setMapTrafficEnabled(selectedMapTrafficEnabled)
        if (selectedOverpassAmenities != settings.selectedOverpassAmenityTypes) settingsManager.setOverpassAmenityTypes(selectedOverpassAmenities)
        if (selectedVehicleType != settings.vehicleType) settingsManager.setVehicleType(selectedVehicleType)

        evRangeKm.toIntOrNull()?.coerceIn(50, 1000)?.let { km ->
            if (km != settings.evRangeKm) settingsManager.setEvRangeKm(km)
        }
        evConsumptionKwh.toFloatOrNull()?.takeIf { it > 0f }?.let { c ->
            if (c != settings.evConsumptionKwhPer100km) settingsManager.setEvConsumptionKwhPer100km(c)
        } ?: run {
            if (settings.evConsumptionKwhPer100km != null) settingsManager.setEvConsumptionKwhPer100km(null)
        }
        onDismiss()
    }

    BackHandler { persist() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Map Settings") },
                navigationIcon = {
                    IconButton(onClick = { persist() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Group 1: Map Display & Data Source
            SettingsGroup(title = "Data Sources") {
                Text("Electric", style = MaterialTheme.typography.labelMedium)
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val elecProviders = listOf(
                        PoiProviderType.DataGouvElec to "data.gouv (IRVE)",
                        PoiProviderType.Chargy to "Chargy",
                        PoiProviderType.OpenChargeMap to "OpenChargeMap"
                    )
                    elecProviders.forEach { (type, label) ->
                        FilterChip(
                            selected = selectedProviders.contains(type),
                            onClick = {
                                selectedProviders = if (selectedProviders.contains(type)) selectedProviders - type else selectedProviders + type
                            },
                            label = { Text(label) }
                        )
                    }
                }

                Text("Fuel", style = MaterialTheme.typography.labelMedium)
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val fuelProviders = listOf(
                        PoiProviderType.Routex to "Routex",
                        PoiProviderType.Etalab to "Prix carburant (instantané)",
                        PoiProviderType.GasApi to "GasApi",
                        PoiProviderType.DataGouv to "data.gouv (Fuel)",
                        PoiProviderType.OpenVanCamp to "OpenVan.camp (LU)",
                        PoiProviderType.SpainMinetur to "Spain Minetur",
                        PoiProviderType.GermanyTankerkoenig to "Tankerkönig",
                        PoiProviderType.AustriaEControl to "E-Control"
                    ).filter { (type, _) -> type.isUserSelectablePoiDataSource() }
                    fuelProviders.forEach { (type, label) ->
                        FilterChip(
                            selected = selectedProviders.contains(type),
                            onClick = {
                                selectedProviders = if (selectedProviders.contains(type)) selectedProviders - type else selectedProviders + type
                            },
                            label = { Text(label) }
                        )
                    }
                }

                Text("Others", style = MaterialTheme.typography.labelMedium)
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val otherProviders = listOf(
                        PoiProviderType.Overpass to "Overpass",
                        PoiProviderType.Hybrid to "Hybrid (Alias)"
                    )
                    otherProviders.forEach { (type, label) ->
                        FilterChip(
                            selected = if (type == PoiProviderType.Hybrid) {
                                selectedProviders.contains(PoiProviderType.Hybrid) ||
                                    (selectedProviders.contains(PoiProviderType.DataGouv) &&
                                        selectedProviders.contains(PoiProviderType.DataGouvElec))
                            } else {
                                selectedProviders.contains(type)
                            },
                            onClick = {
                                if (type == PoiProviderType.Hybrid) {
                                    val hybridAlias = selectedProviders.contains(PoiProviderType.DataGouv) &&
                                        selectedProviders.contains(PoiProviderType.DataGouvElec)
                                    selectedProviders = when {
                                        selectedProviders.contains(PoiProviderType.Hybrid) ->
                                            selectedProviders - PoiProviderType.Hybrid
                                        hybridAlias ->
                                            selectedProviders - PoiProviderType.DataGouv - PoiProviderType.DataGouvElec
                                        else ->
                                            selectedProviders + PoiProviderType.DataGouv + PoiProviderType.DataGouvElec
                                    }
                                } else {
                                    selectedProviders = if (selectedProviders.contains(type)) selectedProviders - type else selectedProviders + type
                                }
                            },
                            label = { Text(label) }
                        )
                    }
                }
            }

            SettingsGroup(title = "Map Display") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Show Traffic", style = MaterialTheme.typography.bodyLarge)
                        Text("Google traffic layer", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = selectedMapTrafficEnabled,
                        onCheckedChange = { selectedMapTrafficEnabled = it }
                    )
                }
            }

            // Group 2: Vehicle & Range
            SettingsGroup(title = "Vehicle & Range") {
                Text("Vehicle Type", style = MaterialTheme.typography.labelMedium)
                CompactSegmentedButton(
                    options = VEHICLE_TYPE_OPTIONS,
                    selectedOption = selectedVehicleType,
                    onOptionSelected = { selectedVehicleType = it }
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = evRangeKm,
                        onValueChange = { evRangeKm = it.filter { c -> c.isDigit() }.take(4) },
                        label = { Text("Range (km)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = evConsumptionKwh,
                        onValueChange = { evConsumptionKwh = it.filter { c -> c.isDigit() || c == '.' }.take(6) },
                        label = { Text("kWh/100km") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Group 3: General Filters
            SettingsGroup(title = "General Filters") {
                Text("Energy Types", style = MaterialTheme.typography.labelMedium)
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MAP_ENERGY_OPTIONS.filter { it.first != "electric" }.forEach { (id, label) ->
                        val isSelected = selectedEnergies.contains(id)
                        val chipColor = ColorHelper.getFuelColor(id) ?: MaterialTheme.colorScheme.primary
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                selectedEnergies = if (isSelected) selectedEnergies - id else selectedEnergies + id
                            },
                            label = { Text(label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = chipColor,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }

                Text("Brands", style = MaterialTheme.typography.labelMedium)
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    BrandHelper.getGasBrands().forEach { (id, label) ->
                        FilterChip(
                            selected = selectedBrands.contains(id),
                            onClick = {
                                selectedBrands = if (selectedBrands.contains(id)) selectedBrands - id else selectedBrands + id
                            },
                            label = { Text(label) }
                        )
                    }
                }

                CompactDropdown(
                    label = "Enseigne",
                    options = MAP_ENSEIGNE_OPTIONS,
                    selectedOption = selectedEnseigne,
                    onOptionSelected = { selectedEnseigne = it as String }
                )

                Text("Services", style = MaterialTheme.typography.labelMedium)
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MAP_SERVICES_OPTIONS.forEach { (id, label) ->
                        FilterChip(
                            selected = selectedServices.contains(id),
                            onClick = {
                                selectedServices = if (selectedServices.contains(id)) selectedServices - id else selectedServices + id
                            },
                            label = { Text(label) }
                        )
                    }
                }
            }

            // Group 4: EV Specific (Conditional)
            val isElecActive = selectedProviders.anyProvidesElectric()
            if (isElecActive) {
                SettingsGroup(title = "EV Filters") {
                    Text("Operators", style = MaterialTheme.typography.labelMedium)
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        BrandHelper.getElectricBrands().forEach { (id, label) ->
                            FilterChip(
                                selected = selectedIrveOperators.contains(id),
                                onClick = {
                                    selectedIrveOperators = if (selectedIrveOperators.contains(id)) selectedIrveOperators - id else selectedIrveOperators + id
                                },
                                label = { Text(label) }
                            )
                        }
                    }

                    Text("Power Range", style = MaterialTheme.typography.labelMedium)
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MAP_IRVE_POWER_OPTIONS.forEach { (id, label) ->
                        val isSelected = selectedPowerLevels.contains(id)
                        val chipColor = ColorHelper.getPowerColorByLevel(id)
                            FilterChip(
                            selected = isSelected,
                                onClick = {
                                selectedPowerLevels = if (isSelected) selectedPowerLevels - id else selectedPowerLevels + id
                                },
                            label = { Text(label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = chipColor,
                                selectedLabelColor = Color.White
                            )
                            )
                        }
                    }

                    Text("Connectors", style = MaterialTheme.typography.labelMedium)
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MAP_CONNECTOR_OPTIONS.forEach { (id, label) ->
                            FilterChip(
                                selected = selectedConnectorTypes.contains(id),
                                onClick = {
                                    selectedConnectorTypes = if (selectedConnectorTypes.contains(id)) selectedConnectorTypes - id else selectedConnectorTypes + id
                                },
                                label = { Text(label) }
                            )
                        }
                    }
                }
            }

            // Group 5: Overpass (Conditional)
            if (selectedProviders.contains(PoiProviderType.Overpass)) {
                SettingsGroup(title = "POI Types (Overpass)") {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OVERPASS_AMENITY_OPTIONS.forEach { (id, label) ->
                            FilterChip(
                                selected = selectedOverpassAmenities.contains(id),
                                onClick = {
                                    selectedOverpassAmenities = if (selectedOverpassAmenities.contains(id)) selectedOverpassAmenities - id else selectedOverpassAmenities + id
                                },
                                label = { Text(label) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsGroup(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )
        OutlinedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                content()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompactDropdown(
    label: String,
    options: List<Pair<*, String>>,
    selectedOption: Any?,
    onOptionSelected: (Any?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = options.find { it.first == selectedOption }?.second ?: ""

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = Modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { (option, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        onOptionSelected(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> CompactSegmentedButton(
    options: List<Pair<T, String>>,
    selectedOption: T,
    onOptionSelected: (T) -> Unit
) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, (option, label) ->
            SegmentedButton(
                selected = selectedOption == option,
                onClick = { onOptionSelected(option) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            )
        }
    }
}
