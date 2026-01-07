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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settingsManager: SettingsManager,
    lastError: String?,
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

        // API Keys Section
        SectionTitle("API Keys")
        StyledTextField("OpenAI Key", openAiKey) { openAiKey = it }
        StyledTextField("ElevenLabs Key", elevenKey) { elevenKey = it }
        StyledTextField("Perplexity Key", pplxKey) { pplxKey = it }
        StyledTextField("Gemini Key (Free)", geminiKey) { geminiKey = it }
        StyledTextField("Deepgram Key", deepgramKey) { deepgramKey = it }

        Spacer(modifier = Modifier.height(24.dp))

        SectionTitle("Assistant")
        StyledDropdown("Agent", selectedAgent, { it.name }) { onDismiss ->
            AgentType.entries.forEach { agent ->
                DropdownMenuItem(
                    text = { Text(agent.name) },
                    onClick = {
                        selectedAgent = agent
                        onDismiss()
                    }
                )
            }
        }
        StyledDropdown("Theme", selectedTheme, { it.name }) { onDismiss ->
            AppTheme.entries.forEach { theme ->
                DropdownMenuItem(
                    text = { Text(theme.name) },
                    onClick = {
                        selectedTheme = theme
                        onDismiss()
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        SectionTitle("Advanced")
        StyledDropdown("IA Model", selectedModel, { it.displayName }) { onDismiss ->
            IaModel.entries.forEach { model ->
                DropdownMenuItem(
                    text = { Text(model.displayName) },
                    onClick = {
                        selectedModel = model
                        onDismiss()
                    }
                )
            }
        }
        Column(modifier = Modifier.padding(vertical = 4.dp)) {
            Text("Last Error Log", color = Color.White, fontSize = 12.sp, modifier = Modifier.padding(bottom = 4.dp))
            OutlinedTextField(
                value = lastError ?: "No errors",
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
                settingsManager.saveSettings(openAiKey, elevenKey, pplxKey, geminiKey, deepgramKey, selectedAgent, selectedTheme)
                onDismiss()
            },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Save & Close")
        }
    }
}

@Composable
fun <T> StyledDropdown(
    label: String,
    selectedValue: T,
    valueToString: (T) -> String,
    content: @Composable ColumnScope.(() -> Unit) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(label, color = Color.White, fontSize = 12.sp, modifier = Modifier.padding(bottom = 4.dp))
        Box {
            OutlinedTextField(
                value = valueToString(selectedValue),
                onValueChange = { },
                readOnly = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = true },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF6366F1),
                    unfocusedBorderColor = Color(0xFF334155),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                )
            )
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.fillMaxWidth()
            ) {
                content { expanded = false }
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
            lastError = "Sample error message for preview",
            onDismiss = {}
        )
    }
}
