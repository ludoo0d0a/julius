package fr.geoking.julius.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.geoking.julius.AgentType
import fr.geoking.julius.AppSettings
import fr.geoking.julius.AppTheme
import fr.geoking.julius.FractalColorIntensity
import fr.geoking.julius.FractalQuality
import fr.geoking.julius.IaModel
import fr.geoking.julius.SettingsManager
import fr.geoking.julius.shared.DetailedError
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private enum class Screen {
    Main,
    Theme,
    Model,
    Agent,
    AgentConfig,
    FractalConfig,
    ErrorLog
}

private val Lavender = Color(0xFFD1D5FF)
private val DeepPurple = Color(0xFF21004C)
private val DarkBackground = Color(0xFF0A0A0A)
private val SeparatorColor = Color(0xFF2D2D44)

@Composable
fun SettingsScreen(
    settingsManager: SettingsManager,
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
                    Screen.Model -> "IA Model"
                    Screen.Agent -> "Agent"
                    Screen.AgentConfig -> "${current.selectedAgent.name} Config"
                    Screen.FractalConfig -> "Fractal Settings"
                    Screen.ErrorLog -> "Error Log"
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
                    Screen.Model -> ModelSelection(
                        selected = current.selectedModel,
                        onSelect = {
                            save(settingsManager, current.copy(selectedModel = it))
                        }
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
                    Screen.FractalConfig -> FractalConfig(
                        settings = current,
                        onUpdate = { save(settingsManager, it) }
                    )
                    Screen.ErrorLog -> ErrorLog(errorLog)
                }
            }
        }
    }
}

private fun save(settingsManager: SettingsManager, s: AppSettings) {
    settingsManager.saveSettings(
        s.openAiKey, s.elevenLabsKey, s.perplexityKey, s.geminiKey, s.deepgramKey,
        s.genkitApiKey, s.genkitEndpoint, s.firebaseAiKey, s.firebaseAiModel,
        s.selectedAgent, s.selectedTheme, s.selectedModel, s.fractalQuality, s.fractalColorIntensity,
        s.extendedActionsEnabled
    )
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
    onNavigate: (Screen) -> Unit,
    onToggleExtendedActions: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = 16.dp)
    ) {
        SettingsItem(
            label = "Theme",
            value = settings.selectedTheme.name,
            onClick = { onNavigate(Screen.Theme) }
        )
        SettingsItem(
            label = "IA Model",
            value = settings.selectedModel.displayName,
            onClick = { onNavigate(Screen.Model) }
        )
        SettingsItem(
            label = "Agent",
            value = settings.selectedAgent.name,
            onClick = { onNavigate(Screen.Agent) }
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
            label = "Error Log",
            value = "View recent errors",
            onClick = { onNavigate(Screen.ErrorLog) }
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
private fun ModelSelection(
    selected: IaModel,
    onSelect: (IaModel) -> Unit
) {
    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        IaModel.entries.forEach { model ->
            SelectionItem(
                label = model.displayName,
                isSelected = model == selected,
                onSelect = { onSelect(model) }
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
            }
            AgentType.ElevenLabs -> {
                ConfigTextField("ElevenLabs Key", settings.elevenLabsKey) { onUpdate(settings.copy(elevenLabsKey = it)) }
                ConfigTextField("Perplexity Key", settings.perplexityKey) { onUpdate(settings.copy(perplexityKey = it)) }
            }
            AgentType.Native -> {
                ConfigTextField("Perplexity Key", settings.perplexityKey) { onUpdate(settings.copy(perplexityKey = it)) }
            }
            AgentType.Gemini -> {
                ConfigTextField("Gemini Key", settings.geminiKey) { onUpdate(settings.copy(geminiKey = it)) }
            }
            AgentType.Deepgram -> {
                ConfigTextField("Deepgram Key", settings.deepgramKey) { onUpdate(settings.copy(deepgramKey = it)) }
            }
            AgentType.Genkit -> {
                ConfigTextField("Genkit Endpoint", settings.genkitEndpoint) { onUpdate(settings.copy(genkitEndpoint = it)) }
                ConfigTextField("Genkit API Key", settings.genkitApiKey) { onUpdate(settings.copy(genkitApiKey = it)) }
            }
            AgentType.FirebaseAI -> {
                ConfigTextField("Firebase AI Key", settings.firebaseAiKey) { onUpdate(settings.copy(firebaseAiKey = it)) }
                ConfigTextField("Firebase AI Model", settings.firebaseAiModel) { onUpdate(settings.copy(firebaseAiModel = it)) }
            }
            AgentType.Embedded -> {
                Text(
                    "This agent runs offline using an embedded model in assets/models/",
                    color = Lavender,
                    fontSize = 18.sp
                )
            }
        }
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
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(24.dp)
    ) {
        if (errorLog.isEmpty()) {
            Text("No errors recorded", color = Lavender, fontSize = 18.sp)
        } else {
            errorLog.reversed().forEach { error ->
                val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(error.timestamp))
                val httpCode = error.httpCode?.let { "HTTP $it" } ?: "Generic"

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                        .background(Color.Black.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                        .padding(16.dp)
                ) {
                    Text(text = "[$timestamp] $httpCode", color = Lavender, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = error.message, color = Color.White, fontSize = 16.sp)
                }
            }
        }
    }
}

@Composable
fun SettingsScreenPreview() {
    // Create a mock SettingsManager for preview
    val mockSettingsManager = remember {
        object : SettingsManager(null as android.content.Context) {
            private val mockSettings = MutableStateFlow(
                AppSettings(
                    openAiKey = "sk-preview-key-123",
                    elevenLabsKey = "preview-eleven-key",
                    perplexityKey = "preview-perplexity-key",
                    geminiKey = "preview-gemini-key",
                    deepgramKey = "preview-deepgram-key",
                    genkitApiKey = "preview-genkit-key",
                    genkitEndpoint = "https://example.com/genkit",
                    firebaseAiKey = "preview-firebase-key",
                    firebaseAiModel = "gemini-1.5-flash-latest",
                    selectedAgent = AgentType.OpenAI,
                    selectedTheme = AppTheme.Particles,
                    selectedModel = IaModel.LLAMA_3_1_SONAR_SMALL,
                    fractalQuality = FractalQuality.Medium,
                    fractalColorIntensity = FractalColorIntensity.Medium,
                    extendedActionsEnabled = false
                )
            )
            override val settings: StateFlow<AppSettings> = mockSettings.asStateFlow()
            override fun saveSettings(
                openAiKey: String,
                elevenLabsKey: String,
                perplexityKey: String,
                geminiKey: String,
                deepgramKey: String,
                genkitApiKey: String,
                genkitEndpoint: String,
                firebaseAiKey: String,
                firebaseAiModel: String,
                agent: AgentType,
                theme: AppTheme,
                model: IaModel,
                fractalQuality: FractalQuality,
                fractalColorIntensity: FractalColorIntensity,
                extendedActionsEnabled: Boolean
            ) {
                mockSettings.value = AppSettings(
                    openAiKey,
                    elevenLabsKey,
                    perplexityKey,
                    geminiKey,
                    deepgramKey,
                    genkitApiKey,
                    genkitEndpoint,
                    firebaseAiKey,
                    firebaseAiModel,
                    agent, theme, model,
                    fractalQuality,
                    fractalColorIntensity,
                    extendedActionsEnabled
                )
            }
        }
    }
    
    SettingsScreen(
        settingsManager = mockSettingsManager,
        errorLog = emptyList(),
        onDismiss = {}
    )
}
