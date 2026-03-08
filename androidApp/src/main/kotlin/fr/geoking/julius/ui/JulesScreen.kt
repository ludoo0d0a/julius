package fr.geoking.julius.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.collectAsState
import fr.geoking.julius.SettingsManager
import fr.geoking.julius.providers.JulesChatItem
import fr.geoking.julius.providers.JulesClient
import kotlinx.coroutines.launch

private val JulesBg = Color(0xFF0F172A)
private val JulesCardUser = Color(0xFF334155)
private val JulesCardAgent = Color(0xFF1E293B)
private val JulesAccent = Color(0xFF818CF8)

@Composable
fun JulesScreen(
    onBack: () -> Unit,
    julesClient: JulesClient,
    settingsManager: SettingsManager
) {
    val settings by settingsManager.settings.collectAsState()
    val apiKey = settings.julesKey

    var sources by remember { mutableStateOf<List<JulesClient.JulesSource>>(emptyList()) }
    var currentSession by remember { mutableStateOf<JulesClient.JulesSession?>(null) }
    val chatItems = remember { mutableStateListOf<JulesChatItem>() }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var inputText by remember { mutableStateOf("") }
    var newSessionPrompt by remember { mutableStateOf("") }
    var selectedSourceName by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    fun loadSources() {
        if (apiKey.isBlank()) return
        scope.launch {
            loading = true
            error = null
            try {
                val resp = julesClient.listSources(apiKey)
                sources = resp.sources
                if (resp.sources.isNotEmpty() && selectedSourceName == null) selectedSourceName = resp.sources.first().name
            } catch (e: Exception) {
                error = e.message ?: "Failed to load sources"
            }
            loading = false
        }
    }

    fun refreshActivities() {
        val session = currentSession ?: return
        if (apiKey.isBlank()) return
        scope.launch {
            loading = true
            error = null
            try {
                val resp = julesClient.listActivities(apiKey, session.id, pageSize = 50)
                chatItems.clear()
                chatItems.addAll(julesClient.activitiesToChatItems(resp.activities))
                listState.animateScrollToItem(chatItems.size)
            } catch (e: Exception) {
                error = e.message ?: "Failed to load activities"
            }
            loading = false
        }
    }

    LaunchedEffect(apiKey) {
        if (apiKey.isNotBlank()) loadSources()
    }

    LaunchedEffect(currentSession?.id) {
        currentSession?.let { refreshActivities() }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = JulesBg
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1E293B))
                    .padding(horizontal = 8.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }
                Spacer(modifier = Modifier.size(8.dp))
                Text(
                    text = "Jules",
                    color = Color.White,
                    fontSize = 22.sp,
                    modifier = Modifier.weight(1f)
                )
            }

            if (apiKey.isBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Set your Jules API key in Settings → Jules API Key to use this screen.",
                        color = Color.White.copy(alpha = 0.8f),
                        textAlign = TextAlign.Center
                    )
                }
                return@Column
            }

            error?.let { err ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF7F1D1D)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = err,
                        color = Color.White,
                        modifier = Modifier.padding(16.dp),
                        fontSize = 14.sp
                    )
                }
            }

            if (currentSession == null) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        "Start a session with a prompt and a connected repo (source).",
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 16.sp,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    if (sources.isEmpty() && !loading) {
                        OutlinedButton(onClick = { loadSources() }) {
                            Text("Load sources")
                        }
                    }
                    if (sources.isNotEmpty()) {
                        Text("Source", color = JulesAccent, fontSize = 14.sp, modifier = Modifier.padding(bottom = 8.dp))
                        sources.forEach { src ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedButton(
                                    onClick = { selectedSourceName = src.name },
                                    colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                                        contentColor = if (selectedSourceName == src.name) JulesAccent else Color.White)
                                ) {
                                    Text(src.githubRepo?.let { "${it.owner}/${it.repo}" } ?: src.name, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = newSessionPrompt,
                        onValueChange = { newSessionPrompt = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Prompt (e.g. Add a dark mode)") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = JulesAccent,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.5f),
                            focusedLabelColor = JulesAccent,
                            cursorColor = JulesAccent,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedButton(
                        onClick = {
                            val source = selectedSourceName ?: sources.firstOrNull()?.name
                            if (source == null || newSessionPrompt.isBlank()) return@OutlinedButton
                            scope.launch {
                                loading = true
                                error = null
                                try {
                                    currentSession = julesClient.createSession(
                                        apiKey = apiKey,
                                        prompt = newSessionPrompt,
                                        source = source,
                                        title = newSessionPrompt.take(80)
                                    )
                                    newSessionPrompt = ""
                                    refreshActivities()
                                } catch (e: Exception) {
                                    error = e.message ?: "Failed to create session"
                                }
                                loading = false
                            }
                        },
                        enabled = selectedSourceName != null && newSessionPrompt.isNotBlank() && !loading
                    ) {
                        Text("Create session")
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(chatItems, key = {
                        when (it) {
                            is JulesChatItem.UserMessage -> it.id
                            is JulesChatItem.AgentMessage -> it.id
                        }
                    }) { item ->
                        when (item) {
                            is JulesChatItem.UserMessage -> Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = JulesCardUser),
                                    shape = RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp),
                                    modifier = Modifier.widthIn(max = 280.dp)
                                ) {
                                    Text(
                                        text = item.text,
                                        color = Color.White,
                                        modifier = Modifier.padding(12.dp),
                                        fontSize = 15.sp
                                    )
                                }
                            }
                            is JulesChatItem.AgentMessage -> Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Start
                            ) {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = JulesCardAgent),
                                    shape = RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp),
                                    modifier = Modifier.widthIn(max = 320.dp)
                                ) {
                                    Text(
                                        text = item.text,
                                        color = Color.White.copy(alpha = 0.95f),
                                        modifier = Modifier.padding(12.dp),
                                        fontSize = 14.sp
                                    )
                                }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Message Jules…", color = Color.White.copy(alpha = 0.5f)) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = JulesAccent,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                            cursorColor = JulesAccent,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    OutlinedButton(
                        onClick = {
                            val prompt = inputText.takeIf { it.isNotBlank() } ?: return@OutlinedButton
                            val session = currentSession ?: return@OutlinedButton
                            inputText = ""
                            scope.launch {
                                loading = true
                                error = null
                                try {
                                    julesClient.sendMessage(apiKey, session.id, prompt)
                                    kotlinx.coroutines.delay(1500)
                                    refreshActivities()
                                } catch (e: Exception) {
                                    error = e.message ?: "Failed to send message"
                                }
                                loading = false
                            }
                        },
                        enabled = inputText.isNotBlank() && !loading
                    ) {
                        Text("Send")
                    }
                }
            }

            if (loading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = JulesAccent, modifier = Modifier.size(24.dp))
                }
            }
        }
    }
}
