package fr.geoking.julius.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import fr.geoking.julius.SettingsManager

enum class SettingsScreenPage { Main }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settingsManager: SettingsManager,
    authManager: fr.geoking.julius.feature.auth.GoogleAuthManager,
    errorLog: List<Any>,
    onDismiss: () -> Unit,
    initialScreenStack: List<SettingsScreenPage>? = null,
    onInitialRouteConsumed: () -> Unit = {}
) {
    BackHandler { onDismiss() }
    val settings by settingsManager.settings.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).padding(16.dp)) {
            OutlinedTextField(
                value = settings.openAiKey,
                onValueChange = { settingsManager.saveSettings(settings.copy(openAiKey = it)) },
                label = { Text("OpenAI key") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = settings.geminiKey,
                onValueChange = { settingsManager.saveSettings(settings.copy(geminiKey = it)) },
                label = { Text("Gemini key") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = settings.perplexityKey,
                onValueChange = { settingsManager.saveSettings(settings.copy(perplexityKey = it)) },
                label = { Text("Perplexity key") },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

