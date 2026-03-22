package fr.geoking.julius.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
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
import fr.geoking.julius.AgentType
import fr.geoking.julius.AppSettings
import fr.geoking.julius.AppTheme
import fr.geoking.julius.FractalColorIntensity
import fr.geoking.julius.FractalQuality
import fr.geoking.julius.PerplexityModel
import fr.geoking.julius.OpenAiModel
import fr.geoking.julius.GeminiModel
import fr.geoking.julius.SettingsManager
import fr.geoking.julius.GoogleAuthManager
import fr.geoking.julius.poi.PoiProviderType
import fr.geoking.julius.TextAnimation
import fr.geoking.julius.BuildConfig
import fr.geoking.julius.shared.DetailedError
import fr.geoking.julius.shared.SttEnginePreference
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

private enum class Screen {
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
    About
}

private val Lavender = Color(0xFFD1D5FF)
private val DeepPurple = Color(0xFF21004C)
private val DarkBackground = Color(0xFF0A0A0A)
private val SeparatorColor = Color(0xFF2D2D44)

/** Used in About screen: API/service name, website URL, optional logo URL. */
private data class UsedApi(val name: String, val url: String, val logoUrl: String? = null)

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
    UsedApi("Jules (Google)", "https://jules.google.com", null),
    // Routing & maps
    UsedApi("OSRM", "https://project-osrm.org", "https://project-osrm.org/favicon.ico"),
    UsedApi("Overpass API (OpenStreetMap)", "https://wiki.openstreetmap.org/wiki/Overpass_API", "https://www.openstreetmap.org/favicon.ico"),
    // POI & fuel / charging
    UsedApi("Open Charge Map", "https://openchargemap.org", "https://openchargemap.org/favicon.ico"),
    UsedApi("data.gouv.fr", "https://www.data.gouv.fr", "https://www.data.gouv.fr/favicon.ico"),
    UsedApi("ODRE (bornes IRVE)", "https://odre.opendatasoft.com", null),
    UsedApi("Gas API (prix carburants)", "https://gas-api.ovh", null),
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
    onDismiss: () -> Unit
) {
    val current by settingsManager.settings.collectAsState()
    var currentScreen by remember { mutableStateOf(Screen.Main) }

    BackHandler {
        if (currentScreen == Screen.Main) {
            onDismiss()
        } else {
            currentScreen = Screen.Main
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
                    Screen.Main -> "Julius Settings"
                    Screen.Theme -> "Theme"
                    Screen.Agent -> "Agent"
                    Screen.TextAnimation -> "Text animation"
                    Screen.SttEngine -> "STT engine (car mic)"
                    Screen.AgentConfig -> "${current.selectedAgent.name} Config"
                    Screen.FractalConfig -> "Fractal Settings"
                    Screen.JulesConfig -> "Jules API"
                    Screen.TollData -> "Highway toll (OpenTollData)"
                    Screen.ErrorLog -> "Error Log"
                    Screen.About -> "About"
                    Screen.GoogleAccount -> "Google Account"
                    Screen.VehicleConfig -> "Vehicle"
                },
                onBack = {
                    if (currentScreen == Screen.Main) onDismiss()
                    else currentScreen = Screen.Main
                }
            )

            Box(modifier = Modifier.weight(1f)) {
                when (currentScreen) {
                    Screen.Main -> MainMenu(
                        settings = current,
                        authManager = authManager,
                        onNavigate = { currentScreen = it },
                        onToggleExtendedActions = {
                            save(settingsManager, current.copy(extendedActionsEnabled = it))
                        },
                        onToggleMuteMediaOnCar = {
                            save(settingsManager, current.copy(muteMediaOnCar = it))
                        },
                        onToggleHeyJuliusDuringSpeaking = {
                            save(settingsManager, current.copy(heyJuliusDuringSpeakingEnabled = it))
                        },
                        onSttEnginePreferenceChange = {
                            save(settingsManager, current.copy(sttEnginePreference = it))
                        }
                    )
                    Screen.VehicleConfig -> VehicleConfig(
                        settings = current,
                        onUpdate = { save(settingsManager, it) }
                    )
                    Screen.Theme -> ThemeSelection(
                        selected = current.selectedTheme,
                        onSelect = {
                            save(settingsManager, current.copy(selectedTheme = it))
                        },
                        onConfigureFractal = { currentScreen = Screen.FractalConfig }
                    )
                    Screen.Agent -> AgentSelection(
                        selected = current.selectedAgent,
                        onSelect = {
                            save(settingsManager, current.copy(selectedAgent = it))
                        },
                        onConfigure = { currentScreen = Screen.AgentConfig }
                    )
                    Screen.TextAnimation -> TextAnimationSelection(
                        selected = current.textAnimation,
                        onSelect = {
                            save(settingsManager, current.copy(textAnimation = it))
                        }
                    )
                    Screen.SttEngine -> SttEngineSelection(
                        selected = current.sttEnginePreference,
                        onSelect = {
                            save(settingsManager, current.copy(sttEnginePreference = it))
                        }
                    )
                    Screen.AgentConfig -> AgentConfig(
                        settings = current,
                        onUpdate = { save(settingsManager, it) }
                    )
                    Screen.JulesConfig -> JulesConfig(
                        settings = current,
                        onUpdate = { save(settingsManager, it) }
                    )
                    Screen.TollData -> TollDataSection(
                        settings = current,
                        onUpdate = { save(settingsManager, it) }
                    )
                    Screen.FractalConfig -> FractalConfig(
                        settings = current,
                        onUpdate = { save(settingsManager, it) }
                    )
                    Screen.ErrorLog -> ErrorLog(errorLog)
                    Screen.About -> AboutContent()
                    Screen.GoogleAccount -> GoogleAccount(
                        settings = current,
                        settingsManager = settingsManager,
                        authManager = authManager
                    )
                }
            }
        }
    }
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
    onNavigate: (Screen) -> Unit,
    onToggleExtendedActions: (Boolean) -> Unit,
    onToggleMuteMediaOnCar: (Boolean) -> Unit,
    onToggleHeyJuliusDuringSpeaking: (Boolean) -> Unit,
    onSttEnginePreferenceChange: (SttEnginePreference) -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

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
            onClick = { onNavigate(Screen.Theme) }
        )
        SettingsItem(
            label = "Agent",
            value = settings.selectedAgent.name,
            onClick = { onNavigate(Screen.Agent) }
        )
        SettingsItem(
            label = "Text animation",
            value = settings.textAnimation.name,
            onClick = { onNavigate(Screen.TextAnimation) }
        )
        SettingsItem(
            label = "Vehicle",
            value = if (settings.vehicleBrand.isNotEmpty()) "${settings.vehicleBrand} ${settings.vehicleModel}" else "Not configured",
            onClick = { onNavigate(Screen.VehicleConfig) }
        )
        SettingsItem(
            label = "STT engine (car)",
            value = sttEnginePreferenceLabel(settings.sttEnginePreference),
            onClick = { onNavigate(Screen.SttEngine) }
        )

        SettingsItem(
            label = "Google Account",
            value = settings.googleUserName ?: "Not connected",
            onClick = { onNavigate(Screen.GoogleAccount) }
        )

        // Mute Radio (Car) Toggle
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
                        text = "Mute Radio (Car)",
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Mute media in Android Auto when Julius is active",
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

        // "Hey Julius" during speaking toggle
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
                        text = "Hey Julius (during speaking)",
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Say \"hey julius\" to interrupt and start listening",
                        color = Lavender.copy(alpha = 0.7f),
                        fontSize = 16.sp
                    )
                }
                Switch(
                    checked = settings.heyJuliusDuringSpeakingEnabled,
                    onCheckedChange = onToggleHeyJuliusDuringSpeaking,
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
            label = "Jules API Key",
            value = if (settings.julesKey.isNotEmpty()) "••••••••" else "Not set",
            onClick = { onNavigate(Screen.JulesConfig) }
        )
        SettingsItem(
            label = "Highway toll (OpenTollData)",
            value = if (!settings.tollDataPath.isNullOrBlank()) "Downloaded" else "Not downloaded",
            onClick = { onNavigate(Screen.TollData) }
        )
        SettingsItem(
            label = "Error Log",
            value = "View recent errors",
            onClick = { onNavigate(Screen.ErrorLog) }
        )
        SettingsItem(
            label = "About",
            value = "Version & build info",
            onClick = { onNavigate(Screen.About) }
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
    val localAgents = listOf(
        AgentType.Llamatik, AgentType.GeminiNano, AgentType.RunAnywhere,
        AgentType.MlcLlm, AgentType.LlamaCpp, AgentType.MediaPipe,
        AgentType.AiEdge, AgentType.PocketPal, AgentType.Offline
    )
    val remoteAgents = AgentType.entries.filter { it !in localAgents }

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
            text = "On-Device AI (Local)",
            color = Lavender,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
        )
        localAgents.forEach { agent ->
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
    val isLocal = agent in listOf(
        AgentType.Llamatik, AgentType.GeminiNano, AgentType.RunAnywhere,
        AgentType.MlcLlm, AgentType.LlamaCpp, AgentType.MediaPipe,
        AgentType.AiEdge, AgentType.PocketPal, AgentType.Offline
    )
    val label = if (isLocal && agent != AgentType.Offline) "${agent.name} (local)" else agent.name

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
                ConfigTextField("ElevenLabs Key", settings.elevenLabsKey) { onUpdate(settings.copy(elevenLabsKey = it)) }
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
            AgentType.Native -> {
                ConfigTextField("Perplexity Key", settings.perplexityKey) { onUpdate(settings.copy(perplexityKey = it)) }
                Spacer(modifier = Modifier.height(16.dp))
                Text("Model", color = Lavender, fontSize = 16.sp, modifier = Modifier.padding(bottom = 8.dp))
                PerplexityModel.entries.forEach { model ->
                    SelectionItem(
                        label = model.displayName,
                        isSelected = model == settings.selectedModel,
                        onSelect = { onUpdate(settings.copy(selectedModel = model)) }
                    )
                }
            }
            AgentType.Gemini -> {
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
                ConfigTextField("Deepgram Key", settings.deepgramKey) { onUpdate(settings.copy(deepgramKey = it)) }
            }
            AgentType.FirebaseAI -> {
                ConfigTextField("Firebase AI Key", settings.firebaseAiKey) { onUpdate(settings.copy(firebaseAiKey = it)) }
                ConfigTextField("Firebase AI Model", settings.firebaseAiModel) { onUpdate(settings.copy(firebaseAiModel = it)) }
            }
            AgentType.OpenCodeZen -> {
                ConfigTextField("OpenCode Zen Key", settings.opencodeZenKey) { onUpdate(settings.copy(opencodeZenKey = it)) }
                ConfigTextField("Model (e.g. minimax-m2.5-free, big-pickle, gpt-5-nano)", settings.opencodeZenModel) { onUpdate(settings.copy(opencodeZenModel = it)) }
            }
            AgentType.CompletionsMe -> {
                ConfigTextField("Completions.me Key", settings.completionsMeKey) { onUpdate(settings.copy(completionsMeKey = it)) }
                ConfigTextField("Model (e.g. claude-sonnet-4.5, gpt-5.2)", settings.completionsMeModel) { onUpdate(settings.copy(completionsMeModel = it)) }
            }
            AgentType.ApiFreeLLM -> {
                ConfigTextField("ApiFreeLLM Key (sign in with Google at apifreellm.com)", settings.apifreellmKey) { onUpdate(settings.copy(apifreellmKey = it)) }
            }
            AgentType.Llamatik, AgentType.GeminiNano, AgentType.RunAnywhere,
            AgentType.MlcLlm, AgentType.LlamaCpp, AgentType.MediaPipe,
            AgentType.AiEdge, AgentType.PocketPal -> {
                val agent = settings.selectedAgent
                val context = LocalContext.current
                val helper = remember(context) { LocalModelHelper(context) }
                val scope = rememberCoroutineScope()
                var downloadProgress by remember { mutableStateOf<Pair<Long, Long?>?>(null) }
                var downloadError by remember { mutableStateOf<String?>(null) }

                val selectedVariant = remember(settings.selectedLlamatikModelVariant, agent) {
                    LocalModelVariant.entries
                        .filter { it.agentType == agent }
                        .find { it.name == settings.selectedLlamatikModelVariant }
                        ?: LocalModelVariant.entries.firstOrNull { it.agentType == agent }
                }
                val downloaded = helper.isModelDownloaded(settings, agent)
                val variantDownloaded = selectedVariant?.let { helper.isVariantDownloaded(it) } ?: false
                val displayPath = helper.getDisplayPath(settings, agent)

                Text(
                    "This agent runs offline using a local model. Choose a variant below and download, or use a model in assets.",
                    color = Lavender,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                val variants = LocalModelVariant.entries.filter { it.agentType == agent }
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
        Text(
            "Jules (jules.google.com) suggests code changes via the Jules screen. Get an API key in Jules Settings.",
            color = Lavender,
            fontSize = 14.sp,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        ConfigTextField("Jules API Key", settings.julesKey) { onUpdate(settings.copy(julesKey = it)) }
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
    authManager: GoogleAuthManager
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (settings.googleUserName != null) {
            Text(
                "Connected as ${settings.googleUserName}",
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
                fr.geoking.julius.ui.MAP_ENERGY_OPTIONS.filter { it.first != "electric" }.forEach { (id, label) ->
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
            fr.geoking.julius.FuelCard.entries.forEach { card ->
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
                fr.geoking.julius.ui.MAP_IRVE_POWER_OPTIONS.forEach { (id, label) ->
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
                    selectedPoiProvider = PoiProviderType.Routex,
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
                    extendedActionsEnabled = false,
                    textAnimation = TextAnimation.Fade
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
        fr.geoking.julius.shared.ConversationStore(
            scope = scope,
            agent = object : fr.geoking.julius.agents.ConversationalAgent {
                override suspend fun process(input: String) = fr.geoking.julius.agents.AgentResponse("Mock", null, null)
            },
            voiceManager = object : fr.geoking.julius.shared.VoiceManager {
                override val events = MutableStateFlow(fr.geoking.julius.shared.VoiceEvent.Silence)
                override val transcribedText = MutableStateFlow("")
                override val partialText = MutableStateFlow("")
                override fun startListening() {}
                override fun stopListening() {}
                override fun speak(text: String, languageTag: String?) {}
                override fun playAudio(bytes: ByteArray) {}
                override fun stopSpeaking() {}
                override fun setTranscriber(transcriber: suspend (ByteArray) -> String?) {}
            },
            actionExecutor = null,
            initialSpeechLanguageTag = null
        )
    }
    val mockAuthManager = remember { GoogleAuthManager(context, mockSettingsManager, { store }) }

    SettingsScreen(
        settingsManager = mockSettingsManager,
        authManager = mockAuthManager,
        errorLog = emptyList(),
        onDismiss = {}
    )
}
