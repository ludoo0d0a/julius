package fr.geoking.julius.ui

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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import fr.geoking.julius.providers.PoiProviderType
import fr.geoking.julius.TextAnimation
import fr.geoking.julius.BuildConfig
import fr.geoking.julius.shared.DetailedError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    GoogleAccount,
    AgentConfig,
    FractalConfig,
    JulesConfig,
    ErrorLog,
    About
}

private val Lavender = Color(0xFFD1D5FF)
private val DeepPurple = Color(0xFF21004C)
private val DarkBackground = Color(0xFF0A0A0A)
private val SeparatorColor = Color(0xFF2D2D44)

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
                    Screen.AgentConfig -> "${current.selectedAgent.name} Config"
                    Screen.FractalConfig -> "Fractal Settings"
                    Screen.JulesConfig -> "Jules API"
                    Screen.ErrorLog -> "Error Log"
                    Screen.About -> "About"
                    Screen.GoogleAccount -> "Google Account"
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
                        }
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
                    Screen.AgentConfig -> AgentConfig(
                        settings = current,
                        onUpdate = { save(settingsManager, it) }
                    )
                    Screen.JulesConfig -> JulesConfig(
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
    onToggleExtendedActions: (Boolean) -> Unit
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
            label = "Google Account",
            value = settings.googleUserName ?: "Not connected",
            onClick = { onNavigate(Screen.GoogleAccount) }
        )
        
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

@Composable
private fun AboutContent() {
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
    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        AgentType.entries.forEach { agent ->
            SelectionItem(
                label = agent.name,
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
            AgentType.Local -> {
                val context = LocalContext.current
                val helper = remember(context) { LocalModelHelper(context) }
                val scope = rememberCoroutineScope()
                var downloadProgress by remember { mutableStateOf<Pair<Long, Long?>?>(null) }
                var downloadError by remember { mutableStateOf<String?>(null) }

                val selectedVariant = remember(settings.selectedLocalModelVariant) {
                    LocalModelVariant.entries.find { it.name == settings.selectedLocalModelVariant } ?: LocalModelVariant.Phi2Q4_0
                }
                val downloaded = helper.isModelDownloaded(settings)
                val variantDownloaded = helper.isVariantDownloaded(selectedVariant)
                val displayPath = helper.getDisplayPath(settings)

                Text(
                    "This agent runs offline using a local GGUF model. Choose a variant below and download, or use a model in assets.",
                    color = Lavender,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                Text(
                    text = "Model variant",
                    color = Lavender,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                LocalModelVariant.entries.forEach { variant ->
                    val isSelected = variant == selectedVariant
                    val isVariantDown = helper.isVariantDownloaded(variant)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable { onUpdate(settings.copy(selectedLocalModelVariant = variant.name)) }
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = { onUpdate(settings.copy(selectedLocalModelVariant = variant.name)) },
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
                        if (!variantDownloaded) {
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
                                            onSuccess = { path -> withContext(Dispatchers.Main) { onUpdate(settings.copy(localModelPath = path)) } },
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
    val clipboardManager = LocalClipboardManager.current
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
                        clipboardManager.setText(AnnotatedString(allErrors))
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
                                    clipboardManager.setText(AnnotatedString(errorText))
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
    // Create a mock SettingsManager for preview
    val mockSettingsManager = remember {
        object : SettingsManager(null as android.content.Context) {
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
    
    val context = LocalContext.current
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
    val mockAuthManager = remember { GoogleAuthManager(context, mockSettingsManager, store) }

    SettingsScreen(
        settingsManager = mockSettingsManager,
        authManager = mockAuthManager,
        errorLog = emptyList(),
        onDismiss = {}
    )
}
