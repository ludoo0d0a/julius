package fr.geoking.julius.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import fr.geoking.julius.SettingsManager
import fr.geoking.julius.providers.JulesChatItem
import fr.geoking.julius.providers.JulesClient
import fr.geoking.julius.shared.NetworkException
import kotlinx.coroutines.launch

private val JulesBg = Color(0xFF0F172A)
private val JulesCardUser = Color(0xFF334155)
private val JulesCardAgent = Color(0xFF1E293B)
private val JulesAccent = Color(0xFF818CF8)
private val JulesErrorBg = Color(0xFF7F1D1D)
private val JulesHeaderBg = Color(0xFF1E293B)
private val JulesListBg = Color(0xFF1E293B)

@Composable
fun JulesScreen(
    onBack: () -> Unit,
    julesClient: JulesClient,
    settingsManager: SettingsManager
) {
    val settings by settingsManager.settings.collectAsState()
    val apiKey = settings.julesKey

    var sources by remember { mutableStateOf<List<JulesClient.JulesSource>>(emptyList()) }
    var sessions by remember { mutableStateOf<List<JulesClient.JulesSession>>(emptyList()) }
    var currentSession by remember { mutableStateOf<JulesClient.JulesSession?>(null) }
    val chatItems = remember { mutableStateListOf<JulesChatItem>() }
    var loading by remember { mutableStateOf(false) }
    var loadingSessions by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var inputText by remember { mutableStateOf("") }
    var newSessionPrompt by remember { mutableStateOf("") }
    var selectedSourceName by remember { mutableStateOf<String?>(null) }
    var sourcesLoaded by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val sessionsListState = rememberLazyListState()

    fun clearError() { error = null }

    fun loadSources() {
        if (apiKey.isBlank()) return
        scope.launch {
            loading = true
            clearError()
            try {
                val resp = julesClient.listSources(apiKey)
                sources = resp.sources
                sourcesLoaded = true
                if (resp.sources.isNotEmpty() && selectedSourceName == null) {
                    selectedSourceName = resp.sources.first().name
                }
            } catch (e: Exception) {
                sourcesLoaded = true
                error = when {
                    e is NetworkException && e.httpCode == 401 -> "Invalid or wrong Jules API key. Check Settings → Jules API Key."
                    e is NetworkException && e.httpCode != null -> "Jules API error (${e.httpCode}): ${e.message}"
                    else -> "Could not load repositories: ${e.message ?: "Unknown error"}"
                }
            }
            loading = false
        }
    }

    fun loadSessions() {
        if (apiKey.isBlank() || selectedSourceName == null) return
        scope.launch {
            loadingSessions = true
            clearError()
            try {
                val resp = julesClient.listSessions(apiKey, pageSize = 50)
                val list = resp.sessions.orEmpty().filter { s ->
                    s.sourceContext?.source == selectedSourceName
                }
                sessions = list
            } catch (e: Exception) {
                error = when {
                    e is NetworkException && e.httpCode == 401 -> "Invalid or wrong Jules API key."
                    e is NetworkException && e.httpCode != null -> "Failed to load conversations (${e.httpCode}): ${e.message}"
                    else -> "Failed to load conversations: ${e.message ?: "Unknown error"}"
                }
            }
            loadingSessions = false
        }
    }

    fun refreshActivities() {
        val session = currentSession ?: return
        if (apiKey.isBlank()) return
        scope.launch {
            loading = true
            clearError()
            try {
                val resp = julesClient.listActivities(apiKey, session.id, pageSize = 50)
                chatItems.clear()
                chatItems.addAll(julesClient.activitiesToChatItems(resp.activities))
                listState.animateScrollToItem(chatItems.size.coerceAtLeast(0))
            } catch (e: Exception) {
                error = when {
                    e is NetworkException && e.httpCode == 401 -> "Invalid API key."
                    else -> "Failed to load messages: ${e.message ?: "Unknown error"}"
                }
            }
            loading = false
        }
    }

    LaunchedEffect(apiKey) {
        if (apiKey.isNotBlank()) loadSources()
        else sourcesLoaded = true
    }

    LaunchedEffect(selectedSourceName) {
        if (selectedSourceName != null && apiKey.isNotBlank()) loadSessions()
        else sessions = emptyList()
    }

    LaunchedEffect(currentSession?.id) {
        currentSession?.let { refreshActivities() }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = JulesBg) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(JulesHeaderBg)
                    .padding(horizontal = 8.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Spacer(modifier = Modifier.size(8.dp))
                Text(
                    text = "Jules",
                    color = Color.White,
                    fontSize = 22.sp,
                    modifier = Modifier.weight(1f)
                )
            }

            when {
                apiKey.isBlank() -> {
                    ErrorCard(
                        title = "Jules API key not set",
                        message = "Go to Settings → Jules API Key and add your key from jules.google.com Settings."
                    )
                }
                else -> {
                    if (error != null) {
                        ErrorCard(
                            title = "Error",
                            message = error!!,
                            onDismiss = { clearError() }
                        )
                    }

                    if (currentSession != null) {
                        InConversationContent(
                            currentSession = currentSession!!,
                            chatItems = chatItems,
                            listState = listState,
                            inputText = inputText,
                            onInputChange = { inputText = it },
                            onSend = {
                                val prompt = inputText.takeIf { it.isNotBlank() } ?: return@InConversationContent
                                inputText = ""
                                scope.launch {
                                    loading = true
                                    clearError()
                                    try {
                                        julesClient.sendMessage(apiKey, currentSession!!.id, prompt)
                                        kotlinx.coroutines.delay(1500)
                                        refreshActivities()
                                    } catch (e: Exception) {
                                        error = e.message ?: "Failed to send"
                                    }
                                    loading = false
                                }
                            },
                            onBackToList = { currentSession = null },
                            loading = loading
                        )
                    } else {
                        RepoAndSessionsContent(
                            apiKey = apiKey,
                            sources = sources,
                            sessions = sessions,
                            selectedSourceName = selectedSourceName,
                            onSelectSource = { selectedSourceName = it },
                            onRefreshSources = { loadSources() },
                            loading = loading,
                            loadingSessions = loadingSessions,
                            sourcesLoaded = sourcesLoaded,
                            newSessionPrompt = newSessionPrompt,
                            onNewSessionPromptChange = { newSessionPrompt = it },
                            onCreateSession = {
                                val source = selectedSourceName ?: return@RepoAndSessionsContent
                                if (newSessionPrompt.isBlank()) return@RepoAndSessionsContent
                                scope.launch {
                                    loading = true
                                    clearError()
                                    try {
                                        val session = julesClient.createSession(
                                            apiKey = apiKey,
                                            prompt = newSessionPrompt,
                                            source = source,
                                            title = newSessionPrompt.take(80)
                                        )
                                        newSessionPrompt = ""
                                        currentSession = session
                                        loadSessions()
                                    } catch (e: Exception) {
                                        error = when {
                                            e is NetworkException && e.httpCode == 401 -> "Invalid API key."
                                            e is NetworkException && e.httpCode != null -> "API error ${e.httpCode}: ${e.message}"
                                            else -> "Failed to create conversation: ${e.message ?: "Unknown error"}"
                                        }
                                    }
                                    loading = false
                                }
                            },
                            onOpenSession = { currentSession = it }
                        )
                    }

                    if (loading || loadingSessions) {
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
    }
}

@Composable
private fun ErrorCard(
    title: String,
    message: String,
    onDismiss: (() -> Unit)? = null
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        colors = CardDefaults.cardColors(containerColor = JulesErrorBg),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = title, color = Color.White, fontSize = 16.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = message, color = Color.White.copy(alpha = 0.9f), fontSize = 14.sp)
            if (onDismiss != null) {
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(onClick = onDismiss) {
                    Text("Dismiss", color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun InConversationContent(
    currentSession: JulesClient.JulesSession,
    chatItems: List<JulesChatItem>,
    listState: androidx.compose.foundation.lazy.LazyListState,
    inputText: String,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onBackToList: () -> Unit,
    loading: Boolean
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(JulesListBg)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = currentSession.title.ifBlank { currentSession.prompt.take(40) }.ifBlank { "Conversation" },
                color = Color.White.copy(alpha = 0.9f),
                fontSize = 14.sp,
                modifier = Modifier.weight(1f)
            )
            OutlinedButton(onClick = onBackToList) {
                Text("Back to list", fontSize = 12.sp)
            }
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
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
                onValueChange = onInputChange,
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
            OutlinedButton(onClick = onSend, enabled = inputText.isNotBlank() && !loading) {
                Text("Send")
            }
        }
    }
}

@Composable
private fun RepoAndSessionsContent(
    apiKey: String,
    sources: List<JulesClient.JulesSource>,
    sessions: List<JulesClient.JulesSession>,
    selectedSourceName: String?,
    onSelectSource: (String) -> Unit,
    onRefreshSources: () -> Unit,
    loading: Boolean,
    loadingSessions: Boolean,
    sourcesLoaded: Boolean,
    newSessionPrompt: String,
    onNewSessionPromptChange: (String) -> Unit,
    onCreateSession: () -> Unit,
    onOpenSession: (JulesClient.JulesSession) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text("Repository", color = JulesAccent, fontSize = 14.sp)
        Spacer(modifier = Modifier.height(8.dp))
        if (!sourcesLoaded) {
            Text("Loading repositories…", color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
        } else if (sources.isEmpty()) {
            Text(
                "No repositories connected. Install the Jules GitHub app and connect a repo at jules.google.com.",
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(onClick = onRefreshSources) { Text("Retry") }
        } else {
            sources.forEach { src ->
                val selected = selectedSourceName == src.name
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectSource(src.name) }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .background(
                                if (selected) JulesAccent else Color.White.copy(alpha = 0.2f),
                                RoundedCornerShape(4.dp)
                            )
                    )
                    Spacer(modifier = Modifier.size(12.dp))
                    Text(
                        text = src.githubRepo?.let { "${it.owner}/${it.repo}" } ?: src.name,
                        color = if (selected) Color.White else Color.White.copy(alpha = 0.8f),
                        fontSize = 16.sp
                    )
                }
            }
            OutlinedButton(onClick = onRefreshSources, modifier = Modifier.padding(top = 8.dp)) {
                Text("Refresh repositories")
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        HorizontalDivider(color = Color.White.copy(alpha = 0.2f))
        Spacer(modifier = Modifier.height(16.dp))

        if (selectedSourceName == null) {
            Text(
                "Select a repository above to see conversations and start a new one.",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 14.sp
            )
            return@Column
        }

        Text("Conversations", color = JulesAccent, fontSize = 14.sp)
        Spacer(modifier = Modifier.height(8.dp))

        var showNewForm by remember { mutableStateOf(false) }
        if (showNewForm) {
            Card(
                colors = CardDefaults.cardColors(containerColor = JulesListBg),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("New conversation", color = Color.White, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = newSessionPrompt,
                        onValueChange = onNewSessionPromptChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("What should Jules do? (e.g. Add dark mode)") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = JulesAccent,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.5f),
                            focusedLabelColor = JulesAccent,
                            cursorColor = JulesAccent,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.End) {
                        OutlinedButton(onClick = { showNewForm = false }) { Text("Cancel") }
                        Spacer(modifier = Modifier.size(8.dp))
                        OutlinedButton(
                            onClick = {
                                if (newSessionPrompt.isNotBlank()) {
                                    onCreateSession()
                                    showNewForm = false
                                }
                            },
                            enabled = newSessionPrompt.isNotBlank() && !loading
                        ) { Text("Create") }
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        FilledTonalButton(
            onClick = { showNewForm = !showNewForm },
            modifier = Modifier.fillMaxWidth(),
            colors = androidx.compose.material3.ButtonDefaults.filledTonalButtonColors(containerColor = JulesAccent.copy(alpha = 0.3f))
        ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(20.dp), tint = Color.White)
            Spacer(modifier = Modifier.size(8.dp))
            Text(if (showNewForm) "Cancel new conversation" else "New conversation")
        }
        Spacer(modifier = Modifier.height(16.dp))

        if (loadingSessions) {
            Text("Loading conversations…", color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
        } else if (sessions.isEmpty()) {
            Text(
                "No conversations yet for this repository. Tap \"New conversation\" to start.",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 14.sp
            )
        } else {
            sessions.forEach { session ->
                SessionRow(
                    session = session,
                    onClick = { onOpenSession(session) }
                )
            }
        }
    }
}

@Composable
private fun SessionRow(
    session: JulesClient.JulesSession,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = JulesListBg),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = session.title.ifBlank { session.prompt.take(60) }.ifBlank { "Conversation" },
                color = Color.White,
                fontSize = 16.sp
            )
            if (session.prompt.isNotBlank() && session.title != session.prompt) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = session.prompt.take(120) + if (session.prompt.length > 120) "…" else "",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 13.sp
                )
            }
            val hasOutput = !session.outputs.isNullOrEmpty()
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = if (hasOutput) "Has output (e.g. PR)" else "In progress",
                color = if (hasOutput) JulesAccent else Color.White.copy(alpha = 0.6f),
                fontSize = 12.sp
            )
        }
    }
}
