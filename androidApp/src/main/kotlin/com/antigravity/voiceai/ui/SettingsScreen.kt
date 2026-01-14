package com.antigravity.voiceai.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antigravity.voiceai.AgentType
import com.antigravity.voiceai.AppSettings
import com.antigravity.voiceai.AppTheme
import com.antigravity.voiceai.IaModel
import com.antigravity.voiceai.SettingsManager
import com.antigravity.voiceai.shared.DetailedError
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settingsManager: SettingsManager,
    errorLog: List<DetailedError>,
    onDismiss: () -> Unit
) {
    val current by settingsManager.settings.collectAsState()

    var openAiKey by remember { mutableStateOf(current.openAiKey) }
    var elevenKey by remember { mutableStateOf(current.elevenLabsKey) }
    var pplxKey by remember { mutableStateOf(current.perplexityKey) }
    var geminiKey by remember { mutableStateOf(current.geminiKey) }
    var deepgramKey by remember { mutableStateOf(current.deepgramKey) }
    var selectedAgent by remember { mutableStateOf(current.selectedAgent) }
    var selectedTheme by remember { mutableStateOf(current.selectedTheme) }
    var selectedModel by remember { mutableStateOf(current.selectedModel) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A))
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text("Settings", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(24.dp))

        SectionTitle("Assistant")
        StyledExposedDropdownMenuBox(
            label = "Agent",
            selectedValue = selectedAgent,
            options = AgentType.entries.toList(),
            onValueChange = { selectedAgent = it },
            getDisplayName = { it.name }
        )
        
        // Show API key field only for the selected agent
        when (selectedAgent) {
            AgentType.OpenAI -> {
                StyledTextField("OpenAI Key", openAiKey) { openAiKey = it }
            }
            AgentType.ElevenLabs -> {
                StyledTextField("ElevenLabs Key", elevenKey) { elevenKey = it }
            }
            AgentType.Native -> {
                StyledTextField("Perplexity Key", pplxKey) { pplxKey = it }
            }
            AgentType.Gemini -> {
                StyledTextField("Gemini Key (Free)", geminiKey) { geminiKey = it }
            }
            AgentType.Deepgram -> {
                StyledTextField("Deepgram Key", deepgramKey) { deepgramKey = it }
            }
        }
        StyledExposedDropdownMenuBox(
            label = "Theme",
            selectedValue = selectedTheme,
            options = AppTheme.entries.toList(),
            onValueChange = { selectedTheme = it },
            getDisplayName = { it.name }
        )

        Spacer(modifier = Modifier.height(24.dp))

        SectionTitle("Advanced")
        StyledExposedDropdownMenuBox(
            label = "IA Model",
            selectedValue = selectedModel,
            options = IaModel.entries.toList(),
            onValueChange = { selectedModel = it },
            getDisplayName = { it.displayName }
        )
        Column(modifier = Modifier.padding(vertical = 4.dp)) {
            Text("Last Error Log", color = Color.White, fontSize = 12.sp, modifier = Modifier.padding(bottom = 4.dp))
            val errorText = if (errorLog.isEmpty()) {
                "No errors"
            } else {
                errorLog.joinToString("\n") {
                    val httpCode = it.httpCode?.let { " ($it)" } ?: ""
                    "Error: ${it.message}$httpCode"
                }
            }
            OutlinedTextField(
                value = errorText,
                onValueChange = {},
                readOnly = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF6366F1),
                    unfocusedBorderColor = Color(0xFF334155),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                )
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = {
                settingsManager.saveSettings(openAiKey, elevenKey, pplxKey, geminiKey, deepgramKey, selectedAgent, selectedTheme, selectedModel)
                onDismiss()
            },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Save & Close")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> StyledExposedDropdownMenuBox(
    label: String,
    selectedValue: T,
    options: List<T>,
    onValueChange: (T) -> Unit,
    getDisplayName: (T) -> String
) {
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(label, color = Color.White, fontSize = 12.sp, modifier = Modifier.padding(bottom = 4.dp))
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded }
        ) {
            OutlinedTextField(
                value = getDisplayName(selectedValue),
                onValueChange = { },
                readOnly = true,
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF6366F1),
                    unfocusedBorderColor = Color(0xFF334155),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedTrailingIconColor = Color(0xFF6366F1),
                    unfocusedTrailingIconColor = Color(0xFF94A3B8)
                )
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.background(Color(0xFF1E293B))
            ) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { 
                            Text(
                                text = getDisplayName(option),
                                color = if (option == selectedValue) Color(0xFF6366F1) else Color.White
                            )
                        },
                        onClick = {
                            onValueChange(option)
                            expanded = false
                        },
                        colors = MenuDefaults.itemColors(
                            textColor = if (option == selectedValue) Color(0xFF6366F1) else Color.White
                        )
                    )
                }
            }
        }
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(text, color = Color(0xFF94A3B8), fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
}

@Composable
fun StyledTextField(label: String, value: String, onValueChange: (String) -> Unit) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(label, color = Color.White, fontSize = 12.sp, modifier = Modifier.padding(bottom = 4.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(0xFF6366F1),
                unfocusedBorderColor = Color(0xFF334155),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            )
        )
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
                    openAiKey = "sk-preview-key-123",
                    elevenLabsKey = "preview-eleven-key",
                    perplexityKey = "preview-perplexity-key",
                    geminiKey = "preview-gemini-key",
                    deepgramKey = "preview-deepgram-key",
                    selectedAgent = AgentType.OpenAI,
                    selectedTheme = AppTheme.Particles,
                    selectedModel = IaModel.LLAMA_3_1_SONAR_SMALL
                )
            )
            override val settings: StateFlow<AppSettings> = mockSettings.asStateFlow()
            override fun saveSettings(
                openAiKey: String,
                elevenLabsKey: String,
                perplexityKey: String,
                geminiKey: String,
                deepgramKey: String,
                agent: AgentType,
                theme: AppTheme,
                model: IaModel
            ) {
                mockSettings.value = AppSettings(
                    openAiKey, elevenLabsKey, perplexityKey, geminiKey, deepgramKey,
                    agent, theme, model
                )
            }
        }
    }
    
    MaterialTheme(
        colorScheme = darkColorScheme(background = Color(0xFF0F172A))
    ) {
        SettingsScreen(
            settingsManager = mockSettingsManager,
            errorLog = listOf(
                DetailedError(401, "Unauthorized"),
                DetailedError(500, "Internal Server Error")
            ),
            onDismiss = {}
        )
    }
}
