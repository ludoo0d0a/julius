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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antigravity.voiceai.AgentType
import com.antigravity.voiceai.AppTheme
import com.antigravity.voiceai.SettingsManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settingsManager: SettingsManager,
    onDismiss: () -> Unit
) {
    val current by settingsManager.settings.collectAsState()
    
    var openAiKey by remember { mutableStateOf(current.openAiKey) }
    var elevenKey by remember { mutableStateOf(current.elevenLabsKey) }
    var pplxKey by remember { mutableStateOf(current.perplexityKey) }
    var selectedAgent by remember { mutableStateOf(current.selectedAgent) }
    var selectedTheme by remember { mutableStateOf(current.selectedTheme) }

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

        Spacer(modifier = Modifier.height(24.dp))

        // Agent Selector
        SectionTitle("Select Agent")
        AgentType.entries.forEach { agent ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { selectedAgent = agent }
                    .padding(vertical = 8.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = (agent == selectedAgent),
                    onClick = { selectedAgent = agent },
                    colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF6366F1), unselectedColor = Color.Gray)
                )
                Text(text = agent.name, color = Color.White, modifier = Modifier.padding(start = 8.dp))
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Theme Selector
        SectionTitle("Select Theme")
        AppTheme.entries.forEach { theme ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { selectedTheme = theme }
                    .padding(vertical = 8.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = (theme == selectedTheme),
                    onClick = { selectedTheme = theme },
                    colors = RadioButtonDefaults.colors(selectedColor = Color(0xFFEC4899), unselectedColor = Color.Gray)
                )
                Text(text = theme.name, color = Color.White, modifier = Modifier.padding(start = 8.dp))
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = {
                settingsManager.saveSettings(openAiKey, elevenKey, pplxKey, selectedAgent, selectedTheme)
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
