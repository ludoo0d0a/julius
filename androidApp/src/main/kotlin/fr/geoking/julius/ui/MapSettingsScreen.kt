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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import fr.geoking.julius.DEFAULT_EV_RANGE_KM
import fr.geoking.julius.SettingsManager
import fr.geoking.julius.VehicleType
import fr.geoking.julius.providers.PoiProviderType

private val PROVIDER_OPTIONS = listOf(
    PoiProviderType.Routex to "Routex (SiteFinder)",
    PoiProviderType.Etalab to "Etalab (open data)",
    PoiProviderType.GasApi to "gas-api.ovh",
    PoiProviderType.DataGouv to "data.gouv.fr (fuel)",
    PoiProviderType.DataGouvElec to "data.gouv.fr (IRVE)",
    PoiProviderType.OpenChargeMap to "Open Charge Map (EV)",
    PoiProviderType.Overpass to "Overpass (OSM + data.gouv: toilets, water, camping, picnic)"
)

/** Overpass / motorhome POI types: id used in settings, label for UI. */
val OVERPASS_AMENITY_OPTIONS = listOf(
    "toilets" to "Toilets",
    "drinking_water" to "Drinking water",
    "camp_site" to "Camping",
    "caravan_site" to "Aire camping-car",
    "picnic_site" to "Picnic",
    "truck_stop" to "Truck stop",
    "rest_area" to "Rest area"
)

/** Vehicle type options for map/route POI relevance. */
val VEHICLE_TYPE_OPTIONS = listOf(
    VehicleType.Car to "Car",
    VehicleType.Truck to "Truck",
    VehicleType.Motorcycle to "Motorcycle",
    VehicleType.Motorhome to "Motorhome"
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

/** IRVE operator filter options. */
val MAP_IRVE_OPERATOR_OPTIONS = listOf(
    "all" to "Tous les opérateurs",
    "atlante" to "Atlante",
    "avia" to "Avia",
    "zunder" to "Zunder",
    "ionity" to "Ionity",
    "fastned" to "Fastned",
    "tesla" to "Tesla"
)

/** Connector types for IRVE filter. Empty selection = show all. */
val MAP_CONNECTOR_OPTIONS = listOf(
    "type_2" to "Type 2",
    "combo_ccs" to "CCS",
    "chademo" to "CHAdeMO",
    "ef" to "E/F",
    "autre" to "Autre"
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
    var selectedIrveOperator by remember(settings.mapIrveOperator) {
        mutableStateOf(settings.mapIrveOperator)
    }
    var selectedConnectorTypes by remember(settings.selectedMapConnectorTypes) {
        mutableStateOf(settings.selectedMapConnectorTypes)
    }
    var selectedOverpassAmenities by remember(settings.selectedOverpassAmenityTypes) {
        mutableStateOf(settings.selectedOverpassAmenityTypes)
    }
    var selectedVehicleType by remember(settings.vehicleType) {
        mutableStateOf(settings.vehicleType)
    }
    var evRangeKm by remember(settings.evRangeKm) { mutableStateOf(settings.evRangeKm.toString()) }
    var evConsumptionKwh by remember(settings.evConsumptionKwhPer100km) {
        mutableStateOf(settings.evConsumptionKwhPer100km?.toString() ?: "")
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
        if (selectedIrveOperator != settings.mapIrveOperator) {
            settingsManager.setMapIrveOperator(selectedIrveOperator)
        }
        if (selectedConnectorTypes != settings.selectedMapConnectorTypes) {
            settingsManager.setMapConnectorTypes(selectedConnectorTypes)
        }
        if (selectedOverpassAmenities != settings.selectedOverpassAmenityTypes) {
            settingsManager.setOverpassAmenityTypes(selectedOverpassAmenities)
        }
        if (selectedVehicleType != settings.vehicleType) {
            settingsManager.setVehicleType(selectedVehicleType)
        }
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
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "Vehicle type",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                "Affects which POIs are relevant on the map and along routes (e.g. truck stops for Truck).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            VEHICLE_TYPE_OPTIONS.forEach { (type, label) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = selectedVehicleType == type,
                        onClick = { selectedVehicleType = type }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            if (selectedProvider == PoiProviderType.OpenChargeMap) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Data from Open Charge Map (CC BY 4.0). Attribution required.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (selectedProvider == PoiProviderType.Overpass) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "POI types (OSM + data.gouv.fr)",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Toilets, water, camping, aires camping-car, picnic. OSM © contributors; aires from data.gouv.fr (e.g. Hérault).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OVERPASS_AMENITY_OPTIONS.forEach { (id, label) ->
                        FilterChip(
                            selected = selectedOverpassAmenities.contains(id),
                            onClick = {
                                selectedOverpassAmenities = if (selectedOverpassAmenities.contains(id)) {
                                    selectedOverpassAmenities - id
                                } else {
                                    selectedOverpassAmenities + id
                                }
                            },
                            label = { Text(label) }
                        )
                    }
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
                "Opérateur (IRVE)",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Filter by charging network. Applied when data source is data.gouv.fr (IRVE).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            MAP_IRVE_OPERATOR_OPTIONS.forEach { (id, label) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = selectedIrveOperator == id,
                        onClick = { selectedIrveOperator = id }
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

            if (selectedProvider == PoiProviderType.DataGouvElec || selectedProvider == PoiProviderType.OpenChargeMap) {
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    "Connecteurs (IRVE / EV)",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Show only stations with at least one selected connector. Empty = all.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MAP_CONNECTOR_OPTIONS.forEach { (id, label) ->
                        FilterChip(
                            selected = selectedConnectorTypes.contains(id),
                            onClick = {
                                selectedConnectorTypes = if (selectedConnectorTypes.contains(id)) {
                                    selectedConnectorTypes - id
                                } else {
                                    selectedConnectorTypes + id
                                }
                            },
                            label = { Text(label) }
                        )
                    }
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

            Spacer(modifier = Modifier.height(24.dp))
            Text(
                "Vehicle (EV)",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Used for route planning: range and optional consumption.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(150 to "150 km", 300 to "300 km", 500 to "500 km").forEach { (km, label) ->
                    FilterChip(
                        selected = evRangeKm == km.toString(),
                        onClick = { evRangeKm = km.toString() },
                        label = { Text(label) }
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = evRangeKm,
                onValueChange = { evRangeKm = it.filter { c -> c.isDigit() }.take(4) },
                label = { Text("Range (km)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = evConsumptionKwh,
                onValueChange = { evConsumptionKwh = it.filter { c -> c.isDigit() || c == '.' }.take(6) },
                label = { Text("Consumption (kWh/100 km, optional)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
