package fr.geoking.julius.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import fr.geoking.julius.agents.LlamatikModelHelper
import fr.geoking.julius.agents.LlamatikModelVariant
import fr.geoking.julius.AgentType
import fr.geoking.julius.enabledAgentTypes
import fr.geoking.julius.AppSettings
import fr.geoking.julius.FuelCard
import fr.geoking.julius.agents.AgentResponse
import fr.geoking.julius.agents.ConversationalAgent
import fr.geoking.julius.AppTheme
import fr.geoking.julius.FractalColorIntensity
import fr.geoking.julius.FractalQuality
import fr.geoking.julius.PerplexityModel
import fr.geoking.julius.OpenAiModel
import fr.geoking.julius.GeminiModel
import fr.geoking.julius.MapEngine
import fr.geoking.julius.MapTheme
import fr.geoking.julius.SettingsManager
import fr.geoking.julius.SpeakingInterruptMode
import fr.geoking.julius.feature.auth.GoogleAuthManager
import fr.geoking.julius.poi.PoiProviderType
import fr.geoking.julius.poi.anyProvidesElectric
import fr.geoking.julius.poi.isUserSelectablePoiDataSource
import fr.geoking.julius.poi.getDisplayGroup
import fr.geoking.julius.poi.getCountryDisplayName
import androidx.compose.foundation.Canvas
import fr.geoking.julius.TextAnimation
import fr.geoking.julius.CacheManager
import fr.geoking.julius.BuildConfig
import fr.geoking.julius.shared.conversation.ConversationStore
import fr.geoking.julius.shared.conversation.DetailedError
import fr.geoking.julius.shared.voice.VoiceEvent
import fr.geoking.julius.shared.voice.VoiceManager
import fr.geoking.julius.shared.voice.SttEnginePreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class SettingsScreenPage {
    Main,
    Theme,
    Agent,
    TextAnimation,
    SttEngine,
    GoogleAccount,
    AgentConfig,
    FractalConfig,
    JulesConfig,
    TollData,
    ErrorLog,
    VehicleConfig,
    MapConfig,
    About
}

private val Lavender = Color(0xFFD1D5FF)
private val DeepPurple = Color(0xFF21004C)
private val DarkBackground = Color(0xFF0A0A0A)
private val SeparatorColor = Color(0xFF2D2D44)

/** Used in About screen: API/service name, website URL, optional logo URL, optional license/credit line. */
private data class UsedApi(
    val name: String,
    val url: String,
    val logoUrl: String? = null,
    val attribution: String? = null
)

private val UsedApisList = listOf(
    // AI / chat agents
    UsedApi("OpenAI", "https://openai.com", "https://openai.com/favicon.ico"),
    UsedApi("Google Gemini", "https://ai.google.dev", "https://www.gstatic.com/lamda/images/favicon_final_18032024.png"),
    UsedApi("Perplexity", "https://perplexity.ai", "https://www.perplexity.ai/favicon.ico"),
    UsedApi("ElevenLabs", "https://elevenlabs.io", "https://elevenlabs.io/favicon.ico"),
    UsedApi("Deepgram", "https://deepgram.com", "https://deepgram.com/favicon.ico"),
    UsedApi("OpenCode Zen", "https://opencode.ai", null),
    UsedApi("Completions.me", "https://www.completions.me", null),
    UsedApi("ApiFreeLLM", "https://apifreellm.com", null),
    UsedApi("DeepSeek", "https://deepseek.com", "https://www.deepseek.com/favicon.ico"),
    UsedApi("Groq", "https://groq.com", "https://groq.com/favicon.ico"),
    UsedApi("OpenRouter", "https://openrouter.ai", "https://openrouter.ai/favicon.ico"),
    UsedApi("Jules (Google)", "https://jules.google.com", null),
    // Routing & maps
    UsedApi("OSRM", "https://project-osrm.org", "https://project-osrm.org/favicon.ico"),
    UsedApi("Overpass API (OpenStreetMap)", "https://wiki.openstreetmap.org/wiki/Overpass_API", "https://www.openstreetmap.org/favicon.ico"),
    // POI & fuel / charging
    UsedApi("Open Charge Map", "https://openchargemap.org", "https://openchargemap.org/favicon.ico"),
    UsedApi("data.gouv.fr", "https://www.data.gouv.fr", "https://www.data.gouv.fr/favicon.ico"),
    UsedApi("ODRE (bornes IRVE)", "https://odre.opendatasoft.com", null),
    UsedApi("Gas API (prix carburants)", "https://gas-api.ovh", null),
    UsedApi(
        name = "OpenVan.camp",
        url = "https://openvan.camp",
        logoUrl = null,
        attribution = "Weekly fuel price reference data (Luxembourg and others). Licensed under CC BY 4.0; attribution to OpenVan.camp required."
    ),
    UsedApi("data.economie.gouv.fr", "https://data.economie.gouv.fr", null),
    UsedApi("Routex / Wigeogis", "https://www.wigeogis.com", null),
    UsedApi("Belib (Paris EV)", "https://opendata.paris.fr", null),
    UsedApi("Hérault Data (camping-car)", "https://www.herault-data.fr", null),
    // Traffic & toll
    UsedApi("CITA (trafic Luxembourg)", "https://www.cita.lu", "https://www.cita.lu/favicon.ico"),
    UsedApi("OpenTollData", "https://github.com/louis2038/OpenTollData", null),
)

@Composable
fun SettingsScreen(
    settingsManager: SettingsManager,
    authManager: GoogleAuthManager,
    errorLog: List<DetailedError>,
    onDismiss: () -> Unit,
    initialScreenStack: List<SettingsScreenPage>? = null,
    onInitialRouteConsumed: () -> Unit = {}
) {
    val current by settingsManager.settings.collectAsState()
    var screenStack by remember { mutableStateOf(listOf(SettingsScreenPage.Main)) }
    val currentScreen = screenStack.last()

    LaunchedEffect(initialScreenStack) {
        val stack = initialScreenStack
        if (stack != null && stack.isNotEmpty()) {
            screenStack = stack
            onInitialRouteConsumed()
        }
    }

    BackHandler {
        if (screenStack.size > 1) {
            screenStack = screenStack.dropLast(1)
        } else {
            onDismiss()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(DeepPurple, DarkBackground)
                )
            )
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            SettingsHeader(
                title = when (currentScreen) {
                    SettingsScreenPage.Main -> "Julius Settings"
                    SettingsScreenPage.Theme -> "Theme"
                    SettingsScreenPage.Agent -> "Agent"
                    SettingsScreenPage.TextAnimation -> "Text animation"
                    SettingsScreenPage.SttEngine -> "STT engine (car mic)"
                    SettingsScreenPage.AgentConfig -> "${current.selectedAgent.name} Config"
                    SettingsScreenPage.FractalConfig -> "Fractal Settings"
                    SettingsScreenPage.JulesConfig -> "Jules & GitHub"
                    SettingsScreenPage.TollData -> "Highway toll (OpenTollData)"
                    SettingsScreenPage.ErrorLog -> "Error Log"
                    SettingsScreenPage.About -> "About"
                    SettingsScreenPage.GoogleAccount -> "Google Account"
                    SettingsScreenPage.VehicleConfig -> "Vehicle"
                    SettingsScreenPage.MapConfig -> "Map Settings"
                },
                onBack = {
                    if (screenStack.size > 1) {
                        screenStack = screenStack.dropLast(1)
                    } else {
                        onDismiss()
                    }
                }
            )

            Box(modifier = Modifier.weight(1f)) {
                when (currentScreen) {
                    SettingsScreenPage.Main -> MainMenu(
                        settings = current,
                        authManager = authManager,
                        onNavigate = { screenStack = screenStack + it },
                        onToggleExtendedActions = {
                            save(settingsManager, current.copy(extendedActionsEnabled = it))
                        },
                        onToggleMuteMediaOnCar = {
                            save(settingsManager, current.copy(muteMediaOnCar = it))
                        },
                        onSpeakingInterruptModeChange = { mode ->
                            save(settingsManager, current.copy(speakingInterruptMode = mode))
                        },
                        onSttEnginePreferenceChange = {
                            save(settingsManager, current.copy(sttEnginePreference = it))
                        }
                    )
                    SettingsScreenPage.VehicleConfig -> VehicleConfig(
                        settings = current,
                        onUpdate = { save(settingsManager, it) }
                    )
                    SettingsScreenPage.Theme -> ThemeSelection(
                        selected = current.selectedTheme,
                        onSelect = {
                            save(settingsManager, current.copy(selectedTheme = it))
                        },
                        onConfigureFractal = { screenStack = screenStack + SettingsScreenPage.FractalConfig }
                    )
                    SettingsScreenPage.Agent -> AgentSelection(
                        selected = current.selectedAgent,
                        onSelect = {
                            save(settingsManager, current.copy(selectedAgent = it))
                        },
                        onConfigure = { screenStack = screenStack + SettingsScreenPage.AgentConfig }
                    )
                    SettingsScreenPage.TextAnimation -> TextAnimationSelection(
                        selected = current.textAnimation,
                        onSelect = {
                            save(settingsManager, current.copy(textAnimation = it))
                        }
                    )
                    SettingsScreenPage.SttEngine -> SttEngineSelection(
                        selected = current.sttEnginePreference,
                        onSelect = {
                            save(settingsManager, current.copy(sttEnginePreference = it))
                        }
                    )
                    SettingsScreenPage.AgentConfig -> AgentConfig(
                        settings = current,
                        onUpdate = { save(settingsManager, it) }
                    )
                    SettingsScreenPage.JulesConfig -> JulesConfig(
                        settings = current,
                        onUpdate = { save(settingsManager, it) }
                    )
                    SettingsScreenPage.TollData -> TollDataSection(
                        settings = current,
                        onUpdate = { save(settingsManager, it) }
                    )
                    SettingsScreenPage.FractalConfig -> FractalConfig(
                        settings = current,
                        onUpdate = { save(settingsManager, it) }
                    )
                    SettingsScreenPage.ErrorLog -> ErrorLog(errorLog)
                    SettingsScreenPage.About -> AboutContent()
                    SettingsScreenPage.GoogleAccount -> GoogleAccount(
                        settings = current,
                        settingsManager = settingsManager,
                        authManager = authManager,
                        firebaseAuth = com.google.firebase.auth.FirebaseAuth.getInstance()
                    )
                    SettingsScreenPage.MapConfig -> MapConfig(
                        settings = current,
                        onUpdate = { save(settingsManager, it) }
                    )
                }
            }
        }

    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CountrySelector(
    availableCountries: List<String>,
    selectedCountryCode: String?,
    onCountrySelected: (String?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    val selectedLabel = if (selectedCountryCode == null) "All Countries" else getCountryDisplayName(selectedCountryCode)

    val filteredCountries = remember(searchQuery, availableCountries) {
        val all = listOf(null) + availableCountries
        if (searchQuery.isBlank()) all
        else all.filter {
            val label = if (it == null) "All Countries" else getCountryDisplayName(it)
            label.contains(searchQuery, ignoreCase = true)
        }
    }

    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        Text(
            "Filter by Country",
            color = Lavender.copy(alpha = 0.7f),
            fontSize = 14.sp,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded },
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = if (expanded) searchQuery else selectedLabel,
                onValueChange = { searchQuery = it },
                label = { Text("Search country...", color = Lavender.copy(alpha = 0.5f)) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                readOnly = !expanded,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Lavender,
                    unfocusedBorderColor = SeparatorColor,
                    focusedContainerColor = Color.Black.copy(alpha = 0.3f),
                    unfocusedContainerColor = Color.Black.copy(alpha = 0.2f),
                    focusedLabelColor = Lavender,
                    unfocusedLabelColor = Lavender.copy(alpha = 0.7f)
                ),
                modifier = Modifier
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = {
                    expanded = false
                    searchQuery = ""
                },
                containerColor = Color(0xFF1A1A2E),
                modifier = Modifier.background(Color(0xFF1A1A2E))
            ) {
                filteredCountries.forEach { code ->
                    val label = if (code == null) "All Countries" else getCountryDisplayName(code)
                    DropdownMenuItem(
                        text = { Text(label, color = Color.White) },
                        onClick = {
                            onCountrySelected(code)
                            expanded = false
                            searchQuery = ""
                        },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MapConfig(
    settings: AppSettings,
    onUpdate: (AppSettings) -> Unit
) {
    var selectedCountryCode by remember { mutableStateOf<String?>(null) }
    var selectedEnergyCategory by remember { mutableStateOf("Fuel") }

    val electricOptions = listOf(
        PoiProviderType.DataGouvElec to "DataGouv (EV)",
        PoiProviderType.Chargy to "Chargy (Luxembourg)",
        PoiProviderType.OpenChargeMap to "OpenChargeMap",
        PoiProviderType.Ionity to "Ionity",
        PoiProviderType.Fastned to "Fastned",
        PoiProviderType.EcoMovement to "Eco-Movement"
    ).filter { (type, _) -> type.isUserSelectablePoiDataSource() }

    val fuelOptions = listOf(
        PoiProviderType.Routex to "Routex",
        PoiProviderType.Etalab to "Prix carburant (official)",
        PoiProviderType.GasApi to "gas-api.ovh",
        PoiProviderType.DataGouv to "DataGouv (Fuel)",
        PoiProviderType.OpenVanCamp to "OpenVan.camp (Reference)",
        PoiProviderType.SpainMinetur to "Spain Minetur (official)",
        PoiProviderType.GermanyTankerkoenig to "Tankerkönig (Germany)",
        PoiProviderType.AustriaEControl to "E-Control (Austria)",
        PoiProviderType.BelgiumOfficial to "Belgium (official)",
        PoiProviderType.PortugalDgeg to "Portugal DGEG (official)",
        PoiProviderType.NetherlandsAnwb to "Netherlands/Luxembourg (ANWB)",
        PoiProviderType.SloveniaGoriva to "Slovenia (Goriva.si)",
        PoiProviderType.RomaniaPeco to "Romania (Peco Online)",
        PoiProviderType.Fuelo to "CEE / Turkey (Fuelo.net)",
        PoiProviderType.GreeceFuelGR to "Greece (FuelGR)",
        PoiProviderType.SerbiaNis to "Serbia (NIS)",
        PoiProviderType.CroatiaMzoe to "Croatia (MZOE)",
        PoiProviderType.DrivstoffAppen to "Nordics (DrivstoffAppen)",
        PoiProviderType.DenmarkFuelprices to "Denmark (Fuelprices.dk)",
        PoiProviderType.FinlandPolttoaine to "Finland (Polttoaine.net)",
        PoiProviderType.ArgentinaEnergia to "Argentina (Energia)",
        PoiProviderType.MexicoCRE to "Mexico (CRE)",
        PoiProviderType.MoldovaAnre to "Moldova (ANRE)",
        PoiProviderType.AustraliaFuel to "Australia (FuelWatch/Check)",
        PoiProviderType.IrelandPickAPump to "Ireland (Pick A Pump)",
        PoiProviderType.UnitedKingdomCma to "United Kingdom (CMA)",
        PoiProviderType.ItalyMimit to "Italy (MIMIT)",
        PoiProviderType.Hybrid to "Hybrid (Gas + EV)"
    ).filter { (type, _) -> type.isUserSelectablePoiDataSource() }

    val otherOptions = listOf(
        PoiProviderType.Overpass to "OSM (toilets, water, parking…)"
    ).filter { (type, _) -> type.isUserSelectablePoiDataSource() }

    val availableCountries = (electricOptions + fuelOptions + otherOptions)
        .flatMap { it.first.supportedCountries }
        .distinct()
        .sortedBy { getCountryDisplayName(it) }

    val isVisible = { type: PoiProviderType ->
        selectedCountryCode == null ||
                type.supportedCountries.contains(selectedCountryCode) ||
                type.supportedCountries.isEmpty() ||
                type.getDisplayGroup().let { it.contains("Global") || it.contains("International") || it.contains("General") || it.contains("Europe") }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Map Engine
        Column {
            Text("Map Engine", color = Lavender, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                MapEngine.entries.forEach { engine ->
                    FilterChip(
                        selected = settings.phoneMapEngine == engine,
                        onClick = { onUpdate(settings.copy(phoneMapEngine = engine)) },
                        label = { Text(engine.name) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Lavender,
                            selectedLabelColor = DeepPurple,
                            labelColor = Color.White,
                            containerColor = Color.White.copy(alpha = 0.1f)
                        )
                    )
                }
            }
        }

        // Map Theme (for both Google and MapLibre)
        Column {
            Text("Map Theme", color = Lavender, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                MapTheme.entries.forEach { theme ->
                    FilterChip(
                        selected = settings.mapTheme == theme,
                        onClick = { onUpdate(settings.copy(mapTheme = theme)) },
                        label = { Text(theme.name) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Lavender,
                            selectedLabelColor = DeepPurple,
                            labelColor = Color.White,
                            containerColor = Color.White.copy(alpha = 0.1f)
                        )
                    )
                }
            }
        }

        // Data Sources
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Data Sources", color = Lavender, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Auto mode", color = Lavender, fontSize = 14.sp, modifier = Modifier.padding(end = 8.dp))
                    Switch(
                        checked = settings.autoPoiProvidersEnabled,
                        onCheckedChange = { onUpdate(settings.copy(autoPoiProvidersEnabled = it)) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Lavender,
                            checkedTrackColor = DeepPurple,
                            uncheckedThumbColor = Color.Gray,
                            uncheckedTrackColor = Color.DarkGray
                        )
                    )
                }
            }
            if (settings.autoPoiProvidersEnabled) {
                Text(
                    "Providers are selected automatically based on your location.",
                    color = Lavender.copy(alpha = 0.7f),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                )
            } else {
                Spacer(modifier = Modifier.height(12.dp))
                CountrySelector(
                    availableCountries = availableCountries,
                    selectedCountryCode = selectedCountryCode,
                    onCountrySelected = { selectedCountryCode = it }
                )

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("Fuel", "Electric", "Other").forEach { cat ->
                        FilterChip(
                            selected = selectedEnergyCategory == cat,
                            onClick = { selectedEnergyCategory = cat },
                            label = { Text(cat) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Lavender,
                                selectedLabelColor = DeepPurple,
                                labelColor = Color.White,
                                containerColor = Color.White.copy(alpha = 0.1f)
                            )
                        )
                    }
                }
            }

            // Electric
            val filteredElectric = electricOptions.filter { isVisible(it.first) }
            if (selectedEnergyCategory == "Electric" && filteredElectric.isNotEmpty()) {
                filteredElectric.groupBy { (type, _) -> type.getDisplayGroup() }.forEach { (group, providers) ->
                    Text(
                        text = group,
                        color = Lavender.copy(alpha = 0.5f),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                    )
                    FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        providers.forEach { (type, label) ->
                            DataSourceChip(type, label, settings, onUpdate)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Fuel
            val filteredFuel = fuelOptions.filter { isVisible(it.first) }
            if (selectedEnergyCategory == "Fuel" && filteredFuel.isNotEmpty()) {
                filteredFuel.groupBy { (type, _) -> type.getDisplayGroup() }.forEach { (group, providers) ->
                    Text(
                        text = group,
                        color = Lavender.copy(alpha = 0.5f),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                    )
                    FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        providers.forEach { (type, label) ->
                            DataSourceChip(type, label, settings, onUpdate)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Other
            val filteredOther = otherOptions.filter { isVisible(it.first) }
            if (selectedEnergyCategory == "Other" && filteredOther.isNotEmpty()) {
                filteredOther.groupBy { (type, _) -> type.getDisplayGroup() }.forEach { (group, providers) ->
                    Text(
                        text = group,
                        color = Lavender.copy(alpha = 0.5f),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                    )
                    FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        providers.forEach { (type, label) ->
                            DataSourceChip(type, label, settings, onUpdate)
                        }
                    }
                }
            }
        }

        // Traffic
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Show Traffic", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                Text("Google traffic layer", color = Lavender.copy(alpha = 0.7f), fontSize = 14.sp)
            }
            Switch(
                checked = settings.mapTrafficEnabled,
                onCheckedChange = { onUpdate(settings.copy(mapTrafficEnabled = it)) },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Lavender,
                    checkedTrackColor = DeepPurple,
                    uncheckedThumbColor = Color.Gray,
                    uncheckedTrackColor = Color.DarkGray
                )
            )
        }

        // Debug Logging
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Debug Logging", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                Text("Capture network logs on map", color = Lavender.copy(alpha = 0.7f), fontSize = 14.sp)
            }
            Switch(
                checked = settings.debugLoggingEnabled,
                onCheckedChange = { onUpdate(settings.copy(debugLoggingEnabled = it)) },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Lavender,
                    checkedTrackColor = DeepPurple,
                    uncheckedThumbColor = Color.Gray,
                    uncheckedTrackColor = Color.DarkGray
                )
            )
        }

        // API Keys for EV Charging
        Column {
            Text("EV Charging API Keys", color = Lavender, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 12.dp))
            ConfigTextField("OpenChargeMap Key", settings.openChargeMapKey) { onUpdate(settings.copy(openChargeMapKey = it)) }
            ConfigTextField("Fastned x-api-key", settings.fastnedKey) { onUpdate(settings.copy(fastnedKey = it)) }
            ConfigTextField("Eco-Movement URL", settings.ecoMovementUrl) { onUpdate(settings.copy(ecoMovementUrl = it)) }
            ConfigTextField("Eco-Movement Token", settings.ecoMovementToken) { onUpdate(settings.copy(ecoMovementToken = it)) }
        }

        // Map Filters
        Column {
            Text("Map Filters", color = Lavender, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 12.dp))

            Text("Fuel Types", color = Lavender.copy(alpha = 0.7f), fontSize = 14.sp, modifier = Modifier.padding(bottom = 8.dp))
            FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MAP_ENERGY_OPTIONS.forEach { (id, label) ->
                    FilterChip(
                        selected = settings.selectedMapEnergyTypes.contains(id),
                        onClick = {
                            val next = if (settings.selectedMapEnergyTypes.contains(id)) settings.selectedMapEnergyTypes - id else settings.selectedMapEnergyTypes + id
                            onUpdate(settings.copy(selectedMapEnergyTypes = next, useVehicleFilter = false))
                        },
                        label = { Text(label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Lavender,
                            selectedLabelColor = DeepPurple,
                            labelColor = Color.White,
                            containerColor = Color.White.copy(alpha = 0.1f)
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text("Power Levels", color = Lavender.copy(alpha = 0.7f), fontSize = 14.sp, modifier = Modifier.padding(bottom = 8.dp))
            FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MAP_IRVE_POWER_OPTIONS.forEach { (kw, label) ->
                    FilterChip(
                        selected = settings.mapPowerLevels.contains(kw),
                        onClick = {
                            val next = if (settings.mapPowerLevels.contains(kw)) settings.mapPowerLevels - kw else settings.mapPowerLevels + kw
                            onUpdate(settings.copy(mapPowerLevels = next, useVehicleFilter = false))
                        },
                        label = { Text(label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Lavender,
                            selectedLabelColor = DeepPurple,
                            labelColor = Color.White,
                            containerColor = Color.White.copy(alpha = 0.1f)
                        )
                    )
                }
            }
        }

        // Itinerary
        Column {
            Text("Itinerary", color = Lavender, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 12.dp))

            Text("Search radius: ${settings.routeStationSearchRadiusMeters}m", color = Lavender.copy(alpha = 0.7f), fontSize = 14.sp)
            Slider(
                value = settings.routeStationSearchRadiusMeters.toFloat(),
                onValueChange = { onUpdate(settings.copy(routeStationSearchRadiusMeters = it.toInt())) },
                valueRange = 0f..2000f,
                steps = 19,
                colors = SliderDefaults.colors(
                    thumbColor = Lavender,
                    activeTrackColor = Lavender,
                    inactiveTrackColor = Color.DarkGray
                )
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Only Highway Stations", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    Text("Filter results to stations on highways", color = Lavender.copy(alpha = 0.7f), fontSize = 12.sp)
                }
                Switch(
                    checked = settings.filterOnlyHighwayStations,
                    onCheckedChange = { onUpdate(settings.copy(filterOnlyHighwayStations = it)) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Lavender,
                        checkedTrackColor = DeepPurple,
                        uncheckedThumbColor = Color.Gray,
                        uncheckedTrackColor = Color.DarkGray
                    )
                )
            }
        }
    }
}

@Composable
private fun DataSourceChip(
    type: PoiProviderType,
    label: String,
    settings: AppSettings,
    onUpdate: (AppSettings) -> Unit
) {
    val isSelected = settings.selectedPoiProviders.contains(type)
    val isAutoActive = settings.autoPoiProvidersEnabled && type.eligibleToAuto

    FilterChip(
        selected = isSelected || isAutoActive,
        onClick = {
            val next = if (isSelected) settings.selectedPoiProviders - type else settings.selectedPoiProviders + type
            onUpdate(settings.copy(selectedPoiProviders = next))
        },
        label = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label)
                if (type.eligibleToAuto) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Canvas(modifier = Modifier.size(6.dp)) {
                        drawCircle(color = if (settings.autoPoiProvidersEnabled) DeepPurple else Lavender)
                    }
                }
            }
        },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = if (isAutoActive && !isSelected) Lavender.copy(alpha = 0.6f) else Lavender,
            selectedLabelColor = DeepPurple,
            labelColor = Color.White,
            containerColor = Color.White.copy(alpha = 0.1f)
        )
    )
}

private fun save(settingsManager: SettingsManager, s: AppSettings) {
    settingsManager.saveSettingsWithThemeCheck(s)
}

@Composable
private fun SettingsHeader(title: String, onBack: () -> Unit) {
    Surface(
        color = Color(0xFF1A1A2E),
        shape = RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Lavender,
                    modifier = Modifier.size(28.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = title,
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun MainMenu(
    settings: AppSettings,
    authManager: GoogleAuthManager,
    onNavigate: (SettingsScreenPage) -> Unit,
    onToggleExtendedActions: (Boolean) -> Unit,
    onToggleMuteMediaOnCar: (Boolean) -> Unit,
    onSpeakingInterruptModeChange: (SpeakingInterruptMode) -> Unit,
    onSttEnginePreferenceChange: (SttEnginePreference) -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var showClearCacheConfirm by remember { mutableStateOf(false) }

    if (showClearCacheConfirm) {
        AlertDialog(
            onDismissRequest = { showClearCacheConfirm = false },
            title = { Text("Clear Cache") },
            text = { Text("This will clear map markers, image caches, temporary recordings, and debug logs. Continue?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearCacheConfirm = false
                        scope.launch {
                            CacheManager.clearAllCaches(context)
                            snackbarHostState.showSnackbar("Cache cleared")
                        }
                    }
                ) {
                    Text("Clear", color = Color(0xFFFF6B6B))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearCacheConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(top = 16.dp)
        ) {
            // Google Auth Section
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp)
                    .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(16.dp))
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (settings.isLoggedIn) "Hello, ${settings.googleUserName}" else "Not signed in",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (settings.isLoggedIn) "Google Account connected" else "Sign in to sync your profile",
                        color = Lavender.copy(alpha = 0.7f),
                        fontSize = 14.sp
                    )
                }
                if (settings.isLoggedIn) {
                    TextButton(onClick = {
                        authManager.signOut { success ->
                            if (!success) {
                                scope.launch { snackbarHostState.showSnackbar("Sign out failed") }
                            }
                        }
                    }) {
                        Text("Sign Out", color = Color(0xFFFF6B6B))
                    }
                } else {
                    Button(
                        onClick = {
                            authManager.signIn(context) { success, error ->
                                if (!success) {
                                    scope.launch { snackbarHostState.showSnackbar(error ?: "Sign in failed") }
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Lavender, contentColor = DeepPurple)
                    ) {
                        Text("Sign In")
                    }
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 24.dp),
                thickness = 0.5.dp,
                color = SeparatorColor
            )

            SettingsItem(
                label = "Theme",
            value = settings.selectedTheme.name,
            onClick = { onNavigate(SettingsScreenPage.Theme) }
        )
        SettingsItem(
            label = "Agent",
            value = settings.selectedAgent.name,
            onClick = { onNavigate(SettingsScreenPage.Agent) }
        )
        SettingsItem(
            label = "Text animation",
            value = settings.textAnimation.name,
            onClick = { onNavigate(SettingsScreenPage.TextAnimation) }
        )
        SettingsItem(
            label = "Vehicle",
            value = if (settings.vehicleBrand.isNotEmpty()) "${settings.vehicleBrand} ${settings.vehicleModel}" else "Not configured",
            onClick = { onNavigate(SettingsScreenPage.VehicleConfig) }
        )
        SettingsItem(
            label = "Map",
            value = "Data sources, traffic, filters",
            onClick = { onNavigate(SettingsScreenPage.MapConfig) }
        )
        SettingsItem(
            label = "STT engine (car)",
            value = sttEnginePreferenceLabel(settings.sttEnginePreference),
            onClick = { onNavigate(SettingsScreenPage.SttEngine) }
        )

        SettingsItem(
            label = "Google Account",
            value = settings.googleUserName ?: "Not connected",
            onClick = { onNavigate(SettingsScreenPage.GoogleAccount) }
        )

        // Mute Media Toggle
        Column(
            modifier = Modifier
                .fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 24.dp, vertical = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Mute Media",
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Pause other audio sources when Julius is active",
                        color = Lavender.copy(alpha = 0.7f),
                        fontSize = 16.sp
                    )
                }
                Switch(
                    checked = settings.muteMediaOnCar,
                    onCheckedChange = onToggleMuteMediaOnCar,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Lavender,
                        checkedTrackColor = DeepPurple,
                        uncheckedThumbColor = Color.Gray,
                        uncheckedTrackColor = Color.DarkGray
                    )
                )
            }
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 24.dp),
                thickness = 0.5.dp,
                color = SeparatorColor
            )
        }

        // Interrupt while Julius speaks (barge-in mode)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            Text(
                text = "Interrupt while speaking",
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "Keep the mic active during replies so you can cut off long answers.",
                color = Lavender.copy(alpha = 0.7f),
                fontSize = 16.sp,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
            )
            val radioColors = RadioButtonDefaults.colors(
                selectedColor = Lavender,
                unselectedColor = Color.Gray
            )
            SpeakingInterruptMode.entries.forEach { mode ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSpeakingInterruptModeChange(mode) }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = settings.speakingInterruptMode == mode,
                        onClick = { onSpeakingInterruptModeChange(mode) },
                        colors = radioColors
                    )
                    Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                        Text(
                            text = phoneSpeakingInterruptTitle(mode),
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = phoneSpeakingInterruptSubtitle(mode),
                            color = Lavender.copy(alpha = 0.7f),
                            fontSize = 14.sp
                        )
                    }
                }
            }
            HorizontalDivider(
                modifier = Modifier.padding(top = 16.dp),
                thickness = 0.5.dp,
                color = SeparatorColor
            )
        }
        
        // Extended Actions Toggle
        Column(
            modifier = Modifier
                .fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 24.dp, vertical = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Extended Actions",
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Allow AI to access sensors",
                        color = Lavender.copy(alpha = 0.7f),
                        fontSize = 16.sp
                    )
                }
                Switch(
                    checked = settings.extendedActionsEnabled,
                    onCheckedChange = onToggleExtendedActions,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Lavender,
                        checkedTrackColor = DeepPurple,
                        uncheckedThumbColor = Color.Gray,
                        uncheckedTrackColor = Color.DarkGray
                    )
                )
            }
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 24.dp),
                thickness = 0.5.dp,
                color = SeparatorColor
            )
        }

        SettingsItem(
            label = "Jules & GitHub",
            value = when {
                settings.julesKeys.isNotEmpty() && settings.githubApiKey.isNotEmpty() -> "${settings.julesKeys.size} keys + GitHub"
                settings.julesKeys.isNotEmpty() -> "${settings.julesKeys.size} keys"
                settings.githubApiKey.isNotEmpty() -> "GitHub only"
                else -> "Not set"
            },
            onClick = { onNavigate(SettingsScreenPage.JulesConfig) }
        )
        SettingsItem(
            label = "Highway toll (OpenTollData)",
            value = if (!settings.tollDataPath.isNullOrBlank()) "Downloaded" else "Not downloaded",
            onClick = { onNavigate(SettingsScreenPage.TollData) }
        )
        SettingsItem(
            label = "Error Log",
            value = "View recent errors",
            onClick = { onNavigate(SettingsScreenPage.ErrorLog) }
        )
        SettingsItem(
            label = "About",
            value = "Version & build info",
            onClick = { onNavigate(SettingsScreenPage.About) }
        )
        SettingsItem(
            label = "Clear Cache",
            value = "Markers, images, logs & temp files",
            onClick = { showClearCacheConfirm = true }
        )
    }

    SnackbarHost(
        hostState = snackbarHostState,
        modifier = Modifier.align(Alignment.BottomCenter)
    )
    }
}

private fun sttEnginePreferenceLabel(pref: SttEnginePreference): String = when (pref) {
    SttEnginePreference.LocalOnly -> "Local only (Vosk)"
    SttEnginePreference.LocalFirst -> "Local first (Vosk, then cloud)"
    SttEnginePreference.NativeOnly -> "Native only (cloud)"
}

private fun phoneSpeakingInterruptTitle(mode: SpeakingInterruptMode): String = when (mode) {
    SpeakingInterruptMode.OFF -> "Off"
    SpeakingInterruptMode.WAKE_WORD -> "Hey Julius only"
    SpeakingInterruptMode.ANY_SPEECH -> "Any speech"
}

private fun phoneSpeakingInterruptSubtitle(mode: SpeakingInterruptMode): String = when (mode) {
    SpeakingInterruptMode.OFF -> "No microphone while Julius is speaking"
    SpeakingInterruptMode.WAKE_WORD -> "Say \"hey julius\" or \"stop\" to interrupt (fewer false triggers)"
    SpeakingInterruptMode.ANY_SPEECH -> "Talking stops playback and sends your next message (may pick up echo)"
}

@Composable
private fun TollDataSection(
    settings: AppSettings,
    onUpdate: (AppSettings) -> Unit
) {
    val context = LocalContext.current
    val helper = remember(context) { OpenTollDataHelper(context) }
    val scope = rememberCoroutineScope()
    var downloadProgress by remember { mutableStateOf<Pair<Long, Long?>?>(null) }
    var downloadError by remember { mutableStateOf<String?>(null) }

    val downloaded = helper.isTollDataDownloaded(settings)
    val displayPath = helper.getDisplayPath(settings)

    val fileInfo = remember(settings.tollDataPath) {
        settings.tollDataPath?.let { path ->
            val file = File(path)
            if (file.exists()) {
                val size = file.length()
                val lastModified = file.lastModified()
                val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(lastModified))
                val sizeStr = if (size > 1024 * 1024) "${size / (1024 * 1024)} MB" else "${size / 1024} KB"
                "Size: $sizeStr, Downloaded: $dateStr"
            } else null
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Text(
            text = "French highway toll estimation uses OpenTollData. Download the data file to see estimated tolls on planned routes.",
            color = Lavender,
            fontSize = 14.sp,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        Text(
            text = if (downloaded) "Status: Downloaded" else "Status: Not downloaded",
            color = if (downloaded) Color(0xFF7FFF7F) else Color(0xFFFFB366),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(bottom = 2.dp)
        )
        fileInfo?.let {
            Text(
                text = it,
                color = Lavender.copy(alpha = 0.8f),
                fontSize = 12.sp,
                modifier = Modifier.padding(bottom = 2.dp)
            )
        }
        Text(
            text = "Path: $displayPath",
            color = Lavender,
            fontSize = 12.sp,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        if (!downloaded || downloadProgress != null) {
            when (val progress = downloadProgress) {
                null -> {
                    Button(
                        onClick = {
                            downloadError = null
                            downloadProgress = 0L to null
                            scope.launch {
                                val result = helper.download { bytes, total ->
                                    scope.launch(Dispatchers.Main) {
                                        downloadProgress = bytes to total
                                    }
                                }
                                withContext(Dispatchers.Main) { downloadProgress = null }
                                result.fold(
                                    onSuccess = { path ->
                                        withContext(Dispatchers.Main) {
                                            onUpdate(
                                                settings.copy(
                                                    tollDataPath = path
                                                )
                                            )
                                        }
                                    },
                                    onFailure = { e ->
                                        withContext(Dispatchers.Main) {
                                            downloadError = e.message ?: "Download failed"
                                        }
                                    }
                                )
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Lavender,
                            contentColor = DeepPurple
                        )
                    ) {
                        Text("Download toll data (OpenTollData)")
                    }
                }

                else -> {
                    val (bytes, total) = progress
                    val pct = if (total != null && total > 0) (100 * bytes / total).toInt() else null
                    Text(
                        text = if (pct != null) "Downloading… $pct%" else "Downloading… ${bytes / (1024 * 1024)} MB",
                        color = Lavender,
                        fontSize = 14.sp
                    )
                }
            }
        }
        downloadError?.let { err ->
            Text(
                text = "Error: $err",
                color = Color(0xFFFF6B6B),
                fontSize = 14.sp,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@Composable
private fun SttEngineSelection(
    selected: SttEnginePreference,
    onSelect: (SttEnginePreference) -> Unit
) {
    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        SttEnginePreference.entries.forEach { pref ->
            SelectionItem(
                label = sttEnginePreferenceLabel(pref),
                isSelected = pref == selected,
                onSelect = { onSelect(pref) }
            )
        }
    }
}

@Composable
private fun AboutContent() {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Text(
            text = "Julius",
            color = Color.White,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(24.dp))
        AboutRow("Version name", BuildConfig.VERSION_NAME)
        AboutRow("Version code", BuildConfig.VERSION_CODE.toString())
        AboutRow("Build date", BuildConfig.BUILD_DATE)
        Spacer(modifier = Modifier.height(32.dp))
        Text(
            text = "Used APIs & services",
            color = Lavender.copy(alpha = 0.9f),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(12.dp))
        UsedApisList.forEach { api ->
            AboutApiRow(
                name = api.name,
                url = api.url,
                logoUrl = api.logoUrl,
                attribution = api.attribution,
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(api.url))
                    context.startActivity(intent)
                }
            )
        }
    }
}

@Composable
private fun AboutApiRow(
    name: String,
    url: String,
    logoUrl: String?,
    attribution: String? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(DeepPurple, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (logoUrl != null) {
                AsyncImage(
                    model = logoUrl,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp)
                )
            } else {
                Text(
                    text = name.first().uppercaseChar().toString(),
                    color = Lavender,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = Uri.parse(url).host ?: url,
                color = Lavender.copy(alpha = 0.7f),
                fontSize = 12.sp
            )
            if (attribution != null) {
                Text(
                    text = attribution,
                    color = Lavender.copy(alpha = 0.6f),
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
            contentDescription = "Open website",
            tint = Lavender.copy(alpha = 0.8f),
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun AboutRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            color = Lavender.copy(alpha = 0.8f),
            fontSize = 16.sp
        )
        Text(
            text = value,
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun SettingsItem(
    label: String,
    value: String? = null,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 24.dp, vertical = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    color = Color.White,
                    fontSize = 22.sp, // Bigger font
                    fontWeight = FontWeight.Medium
                )
                if (value != null) {
                    Text(
                        text = value,
                        color = Lavender.copy(alpha = 0.7f),
                        fontSize = 16.sp // Bigger font
                    )
                }
            }
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = Lavender,
                modifier = Modifier.size(24.dp)
            )
        }
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 24.dp),
            thickness = 0.5.dp,
            color = SeparatorColor
        )
    }
}

@Composable
private fun ThemeSelection(
    selected: AppTheme,
    onSelect: (AppTheme) -> Unit,
    onConfigureFractal: () -> Unit
) {
    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        AppTheme.entries.forEach { theme ->
            SelectionItem(
                label = theme.name,
                isSelected = theme == selected,
                onSelect = { onSelect(theme) },
                extra = {
                    if (theme == AppTheme.Fractal && selected == AppTheme.Fractal) {
                        Text(
                            text = "Settings",
                            color = Lavender,
                            fontSize = 16.sp,
                            modifier = Modifier
                                .clickable(onClick = onConfigureFractal)
                                .padding(8.dp)
                        )
                    }
                }
            )
        }
    }
}


@Composable
private fun AgentSelection(
    selected: AgentType,
    onSelect: (AgentType) -> Unit,
    onConfigure: () -> Unit
) {
    val llamatikPathAgentTypes = listOf(
        AgentType.Llamatik, AgentType.GeminiNano, AgentType.RunAnywhere,
        AgentType.MlcLlm, AgentType.LlamaCpp, AgentType.MediaPipe,
        AgentType.AiEdge, AgentType.PocketPal, AgentType.Offline
    )
    val llamatikPathAgents = llamatikPathAgentTypes.filter { it.enabled }
    val remoteAgents = enabledAgentTypes().filter { it !in llamatikPathAgentTypes }

    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        Text(
            text = "Cloud AI",
            color = Lavender,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
        )
        remoteAgents.forEach { agent ->
            AgentSelectionItem(agent, selected, onSelect, onConfigure)
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "On-device AI",
            color = Lavender,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
        )
        llamatikPathAgents.forEach { agent ->
            AgentSelectionItem(agent, selected, onSelect, onConfigure)
        }
    }
}

@Composable
private fun AgentSelectionItem(
    agent: AgentType,
    selected: AgentType,
    onSelect: (AgentType) -> Unit,
    onConfigure: () -> Unit
) {
    val usesLlamatikModelPath = agent in listOf(
        AgentType.Llamatik, AgentType.GeminiNano, AgentType.RunAnywhere,
        AgentType.MlcLlm, AgentType.LlamaCpp, AgentType.MediaPipe,
        AgentType.AiEdge, AgentType.PocketPal, AgentType.Offline
    )
    val label = when {
        agent == AgentType.Offline -> agent.name
        agent == AgentType.Llamatik -> agent.name
        usesLlamatikModelPath -> "${agent.name} (on-device)"
        else -> agent.name
    }

    SelectionItem(
        label = label,
        isSelected = agent == selected,
        onSelect = { onSelect(agent) },
        extra = {
            if (agent == selected) {
                Text(
                    text = "Configure",
                    color = Lavender,
                    fontSize = 16.sp,
                    modifier = Modifier
                        .clickable(onClick = onConfigure)
                        .padding(8.dp)
                )
            }
        }
    )
}

@Composable
private fun TextAnimationSelection(
    selected: TextAnimation,
    onSelect: (TextAnimation) -> Unit
) {
    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        TextAnimation.entries.forEach { animation ->
            SelectionItem(
                label = animation.name,
                isSelected = animation == selected,
                onSelect = { onSelect(animation) }
            )
        }
    }
}

@Composable
private fun SelectionItem(
    label: String,
    isSelected: Boolean,
    onSelect: () -> Unit,
    extra: @Composable (() -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 24.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                color = if (isSelected) Lavender else Color.White,
                fontSize = 20.sp, // Bigger font
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier.weight(1f)
            )
            extra?.invoke()
            if (isSelected) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    tint = Lavender,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 24.dp),
            thickness = 0.5.dp,
            color = SeparatorColor
        )
    }
}

@Composable
private fun AgentConfig(
    settings: AppSettings,
    onUpdate: (AppSettings) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        when (settings.selectedAgent) {
            AgentType.OpenAI -> {
                ApiKeyHelpLink(
                    helpText = "Create a secret key in your OpenAI account. Usage may require billing.",
                    url = "https://platform.openai.com/api-keys"
                )
                ConfigTextField("OpenAI Key", settings.openAiKey) { onUpdate(settings.copy(openAiKey = it)) }
                Spacer(modifier = Modifier.height(16.dp))
                Text("Model", color = Lavender, fontSize = 16.sp, modifier = Modifier.padding(bottom = 8.dp))
                OpenAiModel.entries.forEach { model ->
                    SelectionItem(
                        label = model.displayName,
                        isSelected = model == settings.openAiModel,
                        onSelect = { onUpdate(settings.copy(openAiModel = model)) }
                    )
                }
            }
            AgentType.ElevenLabs -> {
                ApiKeyHelpLink(
                    helpText = "ElevenLabs provides voice synthesis. Create an API key in your account settings.",
                    url = "https://elevenlabs.io/app/settings/api-keys"
                )
                ConfigTextField("ElevenLabs Key", settings.elevenLabsKey) { onUpdate(settings.copy(elevenLabsKey = it)) }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Scribe v2 STT",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Use latest Scribe v2 model for speech-to-text",
                            color = Lavender.copy(alpha = 0.7f),
                            fontSize = 14.sp
                        )
                    }
                    Switch(
                        checked = settings.elevenLabsScribe2,
                        onCheckedChange = { onUpdate(settings.copy(elevenLabsScribe2 = it)) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Lavender,
                            checkedTrackColor = DeepPurple,
                            uncheckedThumbColor = Color.Gray,
                            uncheckedTrackColor = Color.DarkGray
                        )
                    )
                }
                ApiKeyHelpLink(
                    helpText = "Perplexity powers the chat model. Add an API key from your Perplexity account.",
                    url = "https://www.perplexity.ai/settings/api"
                )
                ConfigTextField("Perplexity Key", settings.perplexityKey) { onUpdate(settings.copy(perplexityKey = it)) }
                Spacer(modifier = Modifier.height(16.dp))
                Text("Perplexity Model", color = Lavender, fontSize = 16.sp, modifier = Modifier.padding(bottom = 8.dp))
                PerplexityModel.entries.forEach { model ->
                    SelectionItem(
                        label = model.displayName,
                        isSelected = model == settings.selectedModel,
                        onSelect = { onUpdate(settings.copy(selectedModel = model)) }
                    )
                }
            }
            AgentType.Gemini -> {
                ApiKeyHelpLink(
                    helpText = "Google AI Studio offers a free tier. Create a Gemini API key there.",
                    url = "https://aistudio.google.com/app/apikey"
                )
                ConfigTextField("Gemini Key", settings.geminiKey) { onUpdate(settings.copy(geminiKey = it)) }
                Spacer(modifier = Modifier.height(16.dp))
                Text("Model", color = Lavender, fontSize = 16.sp, modifier = Modifier.padding(bottom = 8.dp))
                GeminiModel.entries.forEach { model ->
                    SelectionItem(
                        label = model.displayName,
                        isSelected = model == settings.geminiModel,
                        onSelect = { onUpdate(settings.copy(geminiModel = model)) }
                    )
                }
            }
            AgentType.Deepgram -> {
                ApiKeyHelpLink(
                    helpText = "Sign up at Deepgram and create a project API key in the console.",
                    url = "https://console.deepgram.com/"
                )
                ConfigTextField("Deepgram Key", settings.deepgramKey) { onUpdate(settings.copy(deepgramKey = it)) }
            }
            AgentType.FirebaseAI -> {
                ApiKeyHelpLink(
                    helpText = "This agent calls the Google Gemini API. Create a key in Google AI Studio.",
                    url = "https://aistudio.google.com/app/apikey"
                )
                ConfigTextField("Firebase AI Key", settings.firebaseAiKey) { onUpdate(settings.copy(firebaseAiKey = it)) }
                ConfigTextField("Firebase AI Model", settings.firebaseAiModel) { onUpdate(settings.copy(firebaseAiModel = it)) }
            }
            AgentType.OpenCodeZen -> {
                ApiKeyHelpLink(
                    helpText = "OpenCode Zen is an OpenAI-compatible gateway. Get your API key via the Zen docs.",
                    url = "https://opencode.ai/docs/zen/",
                    linkLabel = "Open Zen documentation"
                )
                ConfigTextField("OpenCode Zen Key", settings.opencodeZenKey) { onUpdate(settings.copy(opencodeZenKey = it)) }
                ConfigTextField("Model (e.g. minimax-m2.5-free, big-pickle, gpt-5-nano)", settings.opencodeZenModel) { onUpdate(settings.copy(opencodeZenModel = it)) }
            }
            AgentType.CompletionsMe -> {
                ApiKeyHelpLink(
                    helpText = "Sign up at Completions.me and copy your API key from your account.",
                    url = "https://www.completions.me/"
                )
                ConfigTextField("Completions.me Key", settings.completionsMeKey) { onUpdate(settings.copy(completionsMeKey = it)) }
                ConfigTextField("Model (e.g. claude-sonnet-4.5, gpt-5.2)", settings.completionsMeModel) { onUpdate(settings.copy(completionsMeModel = it)) }
            }
            AgentType.ApiFreeLLM -> {
                ApiKeyHelpLink(
                    helpText = "Sign in with Google on ApiFreeLLM, then get your key from the API access page.",
                    url = "https://apifreellm.com/en/api-access"
                )
                ConfigTextField("ApiFreeLLM Key", settings.apifreellmKey) { onUpdate(settings.copy(apifreellmKey = it)) }
            }
            AgentType.DeepSeek -> {
                ApiKeyHelpLink(
                    helpText = "Get your API key from the DeepSeek platform.",
                    url = "https://platform.deepseek.com/"
                )
                ConfigTextField("DeepSeek Key", settings.deepSeekKey) { onUpdate(settings.copy(deepSeekKey = it)) }
                ConfigTextField("Model (e.g. deepseek-chat, deepseek-reasoner)", settings.deepSeekModel) { onUpdate(settings.copy(deepSeekModel = it)) }
            }
            AgentType.Groq -> {
                ApiKeyHelpLink(
                    helpText = "Get your API key from the Groq console.",
                    url = "https://console.groq.com/keys"
                )
                ConfigTextField("Groq Key", settings.groqKey) { onUpdate(settings.copy(groqKey = it)) }
                ConfigTextField("Model (e.g. llama-3.3-70b-versatile, mixtral-8x7b-32768)", settings.groqModel) { onUpdate(settings.copy(groqModel = it)) }
            }
            AgentType.OpenRouter -> {
                ApiKeyHelpLink(
                    helpText = "Get your API key from OpenRouter. They provide free access to many models.",
                    url = "https://openrouter.ai/keys"
                )
                ConfigTextField("OpenRouter Key", settings.openRouterKey) { onUpdate(settings.copy(openRouterKey = it)) }
                ConfigTextField("Model (e.g. openrouter/auto, stepfun/step-3-5-flash:free)", settings.openRouterModel) { onUpdate(settings.copy(openRouterModel = it)) }
            }
            AgentType.Llamatik, AgentType.GeminiNano, AgentType.RunAnywhere,
            AgentType.MlcLlm, AgentType.LlamaCpp, AgentType.MediaPipe,
            AgentType.AiEdge, AgentType.PocketPal -> {
                val agent = settings.selectedAgent
                val context = LocalContext.current
                val helper = remember(context) { LlamatikModelHelper(context) }
                val scope = rememberCoroutineScope()
                var downloadProgress by remember { mutableStateOf<Pair<Long, Long?>?>(null) }
                var downloadError by remember { mutableStateOf<String?>(null) }

                val selectedVariant = remember(settings.selectedLlamatikModelVariant, agent) {
                    LlamatikModelVariant.entries
                        .filter { it.forAgentName == agent.name }
                        .find { it.name == settings.selectedLlamatikModelVariant }
                        ?: LlamatikModelVariant.entries.firstOrNull { it.forAgentName == agent.name }
                }
                val downloaded = helper.isModelDownloaded(settings.llamatikModelPath)
                val variantDownloaded = selectedVariant?.let { helper.isVariantDownloaded(it) } ?: false
                val displayPath = helper.getDisplayPath(settings.llamatikModelPath)

                Text(
                    when (agent) {
                        AgentType.GeminiNano, AgentType.MediaPipe, AgentType.AiEdge ->
                            "This agent prefers Google AI Edge LiteRT-LM (.litertlm). Download the LiteRT variant below, or use a GGUF model with Llamatik. You can also place files under assets."
                        else ->
                            "This agent runs offline with a GGUF model via Llamatik (llama.cpp). Pick a variant and download, or place a .gguf under assets."
                    },
                    color = Lavender,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                val variants = LlamatikModelVariant.entries.filter { it.forAgentName == agent.name }
                if (variants.isEmpty()) {
                    Text(
                        text = "No pre-configured models available for ${agent.name} yet.",
                        color = Color.White,
                        fontSize = 16.sp,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                } else {
                    Text(
                        text = "Model variant",
                        color = Lavender,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    variants.forEach { variant ->
                        val isSelected = variant == selectedVariant
                        val isVariantDown = helper.isVariantDownloaded(variant)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable { onUpdate(settings.copy(selectedLlamatikModelVariant = variant.name)) }
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = { onUpdate(settings.copy(selectedLlamatikModelVariant = variant.name)) },
                                colors = RadioButtonDefaults.colors(selectedColor = Lavender, unselectedColor = Lavender)
                            )
                            Column(modifier = Modifier.padding(start = 8.dp)) {
                                Text(
                                    text = variant.displayName,
                                    color = Color.White,
                                    fontSize = 15.sp
                                )
                                Text(
                                    text = variant.sizeDescription,
                                    color = Lavender,
                                    fontSize = 12.sp
                                )
                                if (isVariantDown) {
                                    Text(
                                        text = "Downloaded",
                                        color = Color(0xFF7FFF7F),
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (downloaded) "Current model: Downloaded" else "Current model: Not downloaded",
                    color = if (downloaded) Color(0xFF7FFF7F) else Color(0xFFFFB366),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(bottom = 2.dp)
                )
                Text(
                    text = "Path: $displayPath",
                    color = Lavender,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                when (val progress = downloadProgress) {
                    null -> {
                        if (!variantDownloaded && selectedVariant != null) {
                            Button(
                                onClick = {
                                    downloadError = null
                                    downloadProgress = 0L to null
                                    scope.launch {
                                        val result = helper.download(selectedVariant) { bytes, total ->
                                            scope.launch(Dispatchers.Main) {
                                                downloadProgress = bytes to total
                                            }
                                        }
                                        withContext(Dispatchers.Main) { downloadProgress = null }
                                        result.fold(
                                            onSuccess = { path -> withContext(Dispatchers.Main) { onUpdate(settings.copy(llamatikModelPath = path)) } },
                                            onFailure = { e -> withContext(Dispatchers.Main) { downloadError = e.message ?: "Download failed" } }
                                        )
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Lavender, contentColor = DeepPurple)
                            ) {
                                Text("Download ${selectedVariant.displayName} (${selectedVariant.sizeDescription})")
                            }
                        }
                    }
                    else -> {
                        val (bytes, total) = progress
                        val pct = if (total != null && total > 0) (100 * bytes / total).toInt() else null
                        Text(
                            text = if (pct != null) "Downloading… $pct%" else "Downloading… ${bytes / (1024 * 1024)} MB",
                            color = Lavender,
                            fontSize = 14.sp
                        )
                    }
                }
                downloadError?.let { err ->
                    Text(
                        text = "Error: $err",
                        color = Color(0xFFFF6B6B),
                        fontSize = 14.sp,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
            AgentType.Offline -> {
                Text(
                    "Fully offline agent. Supports: basic math, counting (EN/FR), hangman, quote of the day. No config needed.",
                    color = Lavender,
                    fontSize = 18.sp
                )
            }
        }
    }
}

@Composable
private fun JulesConfig(
    settings: AppSettings,
    onUpdate: (AppSettings) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        ApiKeyHelpLink(
            helpText = "Jules suggests code changes from the Jules screen. In the web app, open Settings to create an API key. You can add multiple keys to merge projects/conversations from different accounts.",
            url = "https://jules.google.com/",
            linkLabel = "Open Jules"
        )

        Text(
            "Jules API Keys",
            color = Lavender,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        settings.julesKeys.forEachIndexed { index, key ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            ) {
                OutlinedTextField(
                    value = key,
                    onValueChange = { newValue ->
                        val newList = settings.julesKeys.toMutableList()
                        newList[index] = newValue
                        onUpdate(settings.copy(julesKeys = newList))
                    },
                    modifier = Modifier.weight(1f),
                    textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 16.sp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Lavender,
                        unfocusedBorderColor = SeparatorColor,
                        focusedContainerColor = Color.Black.copy(alpha = 0.3f),
                        unfocusedContainerColor = Color.Black.copy(alpha = 0.2f)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    label = { Text("Key #${index + 1}", color = Lavender.copy(alpha = 0.5f)) }
                )
                IconButton(onClick = {
                    val newList = settings.julesKeys.toMutableList()
                    newList.removeAt(index)
                    onUpdate(settings.copy(julesKeys = newList))
                }) {
                    Icon(Icons.Default.Close, contentDescription = "Remove", tint = Color.Red)
                }
            }
        }

        Button(
            onClick = {
                onUpdate(settings.copy(julesKeys = settings.julesKeys + ""))
            },
            colors = ButtonDefaults.buttonColors(containerColor = Lavender.copy(alpha = 0.1f), contentColor = Lavender),
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Add API Key")
        }

        Spacer(modifier = Modifier.height(24.dp))
        ApiKeyHelpLink(
            helpText = "GitHub personal access token: used on the Jules screen to list pull requests, merge, close, and post comments (with an @jules prefix). Create a classic token with repo scope (or a fine-grained token with Contents, Pull requests, and Issues for your repositories).",
            url = "https://github.com/settings/tokens/new?description=Julius%20Jules&scopes=repo",
            linkLabel = "Create GitHub token"
        )
        ConfigTextField("GitHub API token", settings.githubApiKey) { onUpdate(settings.copy(githubApiKey = it)) }
    }
}

@Composable
private fun FractalConfig(
    settings: AppSettings,
    onUpdate: (AppSettings) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Text("Quality", color = Lavender, fontSize = 16.sp, modifier = Modifier.padding(bottom = 8.dp))
        FractalQuality.entries.forEach { quality ->
            SelectionItem(
                label = quality.name,
                isSelected = quality == settings.fractalQuality,
                onSelect = { onUpdate(settings.copy(fractalQuality = quality)) }
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text("Color Intensity", color = Lavender, fontSize = 16.sp, modifier = Modifier.padding(bottom = 8.dp))
        FractalColorIntensity.entries.forEach { intensity ->
            SelectionItem(
                label = intensity.name,
                isSelected = intensity == settings.fractalColorIntensity,
                onSelect = { onUpdate(settings.copy(fractalColorIntensity = intensity)) }
            )
        }
    }
}

@Composable
private fun GoogleAccount(
    settings: AppSettings,
    settingsManager: SettingsManager,
    authManager: GoogleAuthManager,
    firebaseAuth: com.google.firebase.auth.FirebaseAuth
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val firebaseUser = remember { firebaseAuth.currentUser }
        if (settings.googleUserName != null || firebaseUser != null) {
            Text(
                "Connected as ${settings.googleUserName ?: firebaseUser?.displayName ?: "User"}",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = {
                    authManager.signOut { success ->
                        if (!success) {
                            android.util.Log.e("GoogleAuth", "Sign-out failed")
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color.Red.copy(alpha = 0.7f), contentColor = Color.White),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Logout")
            }
        } else {
            Text(
                "Sign in to personalize your experience.",
                color = Lavender,
                fontSize = 16.sp,
                modifier = Modifier.padding(bottom = 24.dp)
            )
            Button(
                onClick = {
                    scope.launch {
                        authManager.signIn(context) { success, error ->
                            if (!success) {
                                android.util.Log.e("GoogleAuth", "Sign-in failed: ${error ?: "Unknown error"}")
                            }
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Lavender, contentColor = DeepPurple),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Sign in with Google")
            }
        }
    }
}

@Composable
private fun VehicleConfig(
    settings: AppSettings,
    onUpdate: (AppSettings) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        ConfigTextField("Brand", settings.vehicleBrand) { onUpdate(settings.copy(vehicleBrand = it)) }
        ConfigTextField("Model", settings.vehicleModel) { onUpdate(settings.copy(vehicleModel = it)) }

        Spacer(modifier = Modifier.height(16.dp))
        Text("Energy Type", color = Lavender, fontSize = 16.sp, modifier = Modifier.padding(bottom = 8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            FilterChip(
                selected = settings.vehicleEnergy == "gas",
                onClick = { onUpdate(settings.copy(vehicleEnergy = "gas")) },
                label = { Text("Gas") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Lavender,
                    selectedLabelColor = DeepPurple,
                    labelColor = Color.White,
                    containerColor = Color.White.copy(alpha = 0.1f)
                )
            )
            FilterChip(
                selected = settings.vehicleEnergy == "electric",
                onClick = { onUpdate(settings.copy(vehicleEnergy = "electric")) },
                label = { Text("Electric") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Lavender,
                    selectedLabelColor = DeepPurple,
                    labelColor = Color.White,
                    containerColor = Color.White.copy(alpha = 0.1f)
                )
            )
            FilterChip(
                selected = settings.vehicleEnergy == "hybrid",
                onClick = { onUpdate(settings.copy(vehicleEnergy = "hybrid")) },
                label = { Text("Hybrid") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Lavender,
                    selectedLabelColor = DeepPurple,
                    labelColor = Color.White,
                    containerColor = Color.White.copy(alpha = 0.1f)
                )
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
        if (settings.vehicleEnergy == "gas" || settings.vehicleEnergy == "hybrid") {
            Text("Preferred Gas Types", color = Lavender, fontSize = 16.sp, modifier = Modifier.padding(bottom = 8.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MAP_ENERGY_OPTIONS.forEach { (id, label) ->
                    FilterChip(
                        selected = settings.vehicleGasTypes.contains(id),
                        onClick = {
                            val newTypes = if (settings.vehicleGasTypes.contains(id)) settings.vehicleGasTypes - id else settings.vehicleGasTypes + id
                            onUpdate(settings.copy(vehicleGasTypes = newTypes))
                        },
                        label = { Text(label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Lavender,
                            selectedLabelColor = DeepPurple,
                            labelColor = Color.White,
                            containerColor = Color.White.copy(alpha = 0.1f)
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            Text("Fuel Card", color = Lavender, fontSize = 16.sp, modifier = Modifier.padding(bottom = 8.dp))
            FuelCard.entries.forEach { card ->
                SelectionItem(
                    label = card.name,
                    isSelected = settings.fuelCard == card,
                    onSelect = { onUpdate(settings.copy(fuelCard = card)) }
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
        }

        if (settings.vehicleEnergy == "electric" || settings.vehicleEnergy == "hybrid") {
            Text("Preferred Power Range", color = Lavender, fontSize = 16.sp, modifier = Modifier.padding(bottom = 8.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MAP_IRVE_POWER_OPTIONS.forEach { (id, label) ->
                    FilterChip(
                        selected = settings.vehiclePowerLevels.contains(id),
                        onClick = {
                            val newLevels = if (settings.vehiclePowerLevels.contains(id)) settings.vehiclePowerLevels - id else settings.vehiclePowerLevels + id
                            onUpdate(settings.copy(vehiclePowerLevels = newLevels))
                        },
                        label = { Text(label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Lavender,
                            selectedLabelColor = DeepPurple,
                            labelColor = Color.White,
                            containerColor = Color.White.copy(alpha = 0.1f)
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun ApiKeyHelpLink(
    helpText: String,
    url: String,
    linkLabel: String = "Create API key",
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    Column(modifier = modifier.padding(bottom = 4.dp)) {
        Text(
            text = helpText,
            color = Lavender.copy(alpha = 0.85f),
            fontSize = 13.sp,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = linkLabel,
                color = Lavender,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
            Icon(
                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                contentDescription = null,
                tint = Lavender.copy(alpha = 0.9f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun ConfigTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit
) {
    Column(modifier = Modifier.padding(vertical = 12.dp)) {
        Text(
            text = label,
            color = Lavender,
            fontSize = 16.sp,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 18.sp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Lavender,
                unfocusedBorderColor = SeparatorColor,
                focusedContainerColor = Color.Black.copy(alpha = 0.3f),
                unfocusedContainerColor = Color.Black.copy(alpha = 0.2f)
            ),
            shape = RoundedCornerShape(12.dp)
        )
    }
}

@Composable
private fun ErrorLog(errorLog: List<DetailedError>) {
    val scrollState = rememberScrollState()
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val reversedLog = remember(errorLog) { errorLog.reversed() }

    SelectionContainer {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(24.dp)
        ) {
            if (reversedLog.isEmpty()) {
                Text("No errors recorded", color = Lavender, fontSize = 18.sp)
            } else {
                Button(
                    onClick = {
                        val allErrors = reversedLog.joinToString("\n\n") { error ->
                            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(error.timestamp))
                            val httpCode = error.httpCode?.let { "HTTP $it" } ?: "Generic"
                            "[$timestamp] $httpCode\n${error.message}"
                        }
                        scope.launch {
                            clipboard.setClipEntry(androidx.compose.ui.platform.ClipEntry(android.content.ClipData.newPlainText("", allErrors)))
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Lavender,
                        contentColor = DeepPurple
                    )
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Copy All Logs")
                }

                reversedLog.forEach { error ->
                    val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(error.timestamp))
                    val httpCode = error.httpCode?.let { "HTTP $it" } ?: "Generic"

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                            .background(Color.Black.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Text(
                                text = "[$timestamp] $httpCode",
                                color = Lavender,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = {
                                    val errorText = "[$timestamp] $httpCode\n${error.message}"
                                    scope.launch {
                                        clipboard.setClipEntry(androidx.compose.ui.platform.ClipEntry(android.content.ClipData.newPlainText("", errorText)))
                                    }
                                },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    Icons.Default.ContentCopy,
                                    contentDescription = "Copy error",
                                    tint = Lavender,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = error.message, color = Color.White, fontSize = 16.sp)
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0F172A)
@Composable
fun SettingsScreenPreview() {
    val context = LocalContext.current
    // Create a mock SettingsManager for preview (context required; we override settings/saveSettings)
    val mockSettingsManager = remember(context) {
        object : SettingsManager(context) {
            private val mockSettings = MutableStateFlow(
                AppSettings(
                    selectedPoiProviders = setOf(PoiProviderType.DataGouv),
                    openAiKey = "sk-preview-key-123",
                    elevenLabsKey = "preview-eleven-key",
                    perplexityKey = "preview-perplexity-key",
                    geminiKey = "preview-gemini-key",
                    deepgramKey = "preview-deepgram-key",
                    firebaseAiKey = "preview-firebase-key",
                    firebaseAiModel = "gemini-1.5-flash-latest",
                    selectedAgent = AgentType.OpenAI,
                    selectedTheme = AppTheme.Particles,
                    selectedModel = PerplexityModel.LLAMA_3_1_SONAR_SMALL,
                    fractalQuality = FractalQuality.Medium,
                    fractalColorIntensity = FractalColorIntensity.Medium,
                    extendedActionsEnabled = true,
                    textAnimation = TextAnimation.Fade,
                    julesKeys = listOf("key1", "key2")
                )
            )
            override val settings: StateFlow<AppSettings> = mockSettings.asStateFlow()
            override fun saveSettings(settings: AppSettings) {
                mockSettings.value = settings
            }
        }
    }
    val scope = rememberCoroutineScope()
    val store = remember {
        ConversationStore(
            scope = scope,
            agent = object : ConversationalAgent {
                override suspend fun process(input: String) = AgentResponse("Mock", null, null)
            },
            voiceManager = object : VoiceManager {
                override val events = MutableStateFlow(VoiceEvent.Silence)
                override val transcribedText = MutableStateFlow("")
                override val partialText = MutableStateFlow("")
                override fun startListening(continuous: Boolean) {}
                override fun stopListening() {}
                override fun speak(text: String, languageTag: String?, isInterruptible: Boolean) {}
                override fun playAudio(bytes: ByteArray) {}
                override fun stopSpeaking() {}
                override fun setTranscriber(transcriber: suspend (ByteArray) -> String?) {}
            },
            actionExecutor = null,
            initialSpeechLanguageTag = null
        )
    }
    val mockAuthManager = GoogleAuthManager(context, mockSettingsManager, { store }, com.google.firebase.auth.FirebaseAuth.getInstance())

    SettingsScreen(
        settingsManager = mockSettingsManager,
        authManager = mockAuthManager,
        errorLog = emptyList(),
        onDismiss = {}
    )
}
