package fr.geoking.julius.ui

import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Merge
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.text.font.FontWeight
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.Duration
import java.time.Instant
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.geoking.julius.SettingsManager
import fr.geoking.julius.api.github.GitHubClient
import fr.geoking.julius.api.jules.JulesChatItem
import fr.geoking.julius.api.jules.JulesClient
import fr.geoking.julius.persistence.JulesSessionEntity
import fr.geoking.julius.repository.JulesRepository
import fr.geoking.julius.shared.NetworkException
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

private val JulesBg = Color(0xFF0F172A)
private val JulesCardUser = Color(0xFF334155)
private val JulesCardAgent = Color(0xFF1E293B)
private val JulesAccent = Color(0xFF818CF8)
private val JulesErrorBg = Color(0xFF7F1D1D)
private val JulesHeaderBg = Color(0xFF1E293B)
private val JulesListBg = Color(0xFF1E293B)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JulesScreen(
    onBack: () -> Unit,
    julesClient: JulesClient,
    julesRepository: JulesRepository,
    settingsManager: SettingsManager,
    voiceManager: fr.geoking.julius.shared.VoiceManager
) {
    BackHandler { onBack() }

    val settings by settingsManager.settings.collectAsState()
    val apiKey = settings.julesKey
    val githubToken = settings.githubApiKey

    var sources by remember { mutableStateOf<List<JulesClient.JulesSource>>(emptyList()) }
    var sessions by remember { mutableStateOf<List<JulesSessionEntity>>(emptyList()) }
    var currentSession by remember { mutableStateOf<JulesSessionEntity?>(null) }
    val chatItems = remember { mutableStateListOf<JulesChatItem>() }
    var loading by remember { mutableStateOf(false) }
    var loadingSessions by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var inputText by remember { mutableStateOf("") }
    var newSessionPrompt by remember { mutableStateOf("") }
    var selectedSourceName by remember { mutableStateOf(settings.lastJulesRepoId.takeIf { it.isNotBlank() }) }
    var selectedSourceDisplayName by remember { mutableStateOf(settings.lastJulesRepoName.takeIf { it.isNotBlank() } ?: "Select repository") }
    var sourcesLoaded by remember { mutableStateOf(false) }
    var showRepoSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()
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

                // Update display name if we already had an ID
                selectedSourceName?.let { id ->
                    val found = resp.sources.find { it.name == id }
                    if (found != null) {
                        selectedSourceDisplayName = found.githubRepo?.let { "${it.owner}/${it.repo}" } ?: found.name
                    }
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
        val sourceName = selectedSourceName ?: return
        if (apiKey.isBlank()) return
        scope.launch {
            loadingSessions = true
            clearError()
            julesRepository.getSessions(apiKey, sourceName, githubToken).collectLatest { list ->
                sessions = list
                loadingSessions = false
            }
        }
    }

    suspend fun refreshActivitiesInternal() {
        val session = currentSession ?: return
        if (apiKey.isBlank()) return
        loading = true
        clearError()
        julesRepository.getActivities(apiKey, session.id).collectLatest { list ->
            chatItems.clear()
            chatItems.addAll(list)
            if (chatItems.isNotEmpty()) {
                listState.animateScrollToItem(chatItems.size - 1)
            }
            loading = false
        }
    }

    fun refreshActivities() {
        scope.launch { refreshActivitiesInternal() }
    }

    LaunchedEffect(apiKey) {
        if (apiKey.isNotBlank()) loadSources()
        else sourcesLoaded = true
    }

    LaunchedEffect(selectedSourceName) {
        if (selectedSourceName != null && apiKey.isNotBlank()) {
            loadSessions()
            // Persist selection
            val source = sources.find { it.name == selectedSourceName }
            if (source != null) {
                val displayName = source.githubRepo?.let { "${it.owner}/${it.repo}" } ?: source.name
                selectedSourceDisplayName = displayName
                settingsManager.saveSettings(settings.copy(lastJulesRepoId = source.name, lastJulesRepoName = displayName))
            }
        } else {
            sessions = emptyList()
        }
    }

    LaunchedEffect(currentSession?.id) {
        currentSession?.let { refreshActivities() }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = JulesBg) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                    .fillMaxWidth()
                    .background(JulesHeaderBg)
                    .padding(horizontal = 8.dp, vertical = 8.dp),
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

            // Repository link row
            if (apiKey.isNotBlank() && currentSession == null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(JulesHeaderBg.copy(alpha = 0.8f))
                        .clickable {
                            showRepoSheet = true
                            if (!sourcesLoaded) loadSources()
                        }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = selectedSourceDisplayName,
                        color = JulesAccent,
                        fontSize = 14.sp,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint = JulesAccent,
                        modifier = Modifier.size(16.dp)
                    )
                }
                HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
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
                            voiceManager = voiceManager,
                            onSend = {
                                val prompt = inputText.takeIf { it.isNotBlank() } ?: return@InConversationContent
                                inputText = ""
                                scope.launch {
                                    loading = true
                                    clearError()
                                    try {
                                        val oldLastId = chatItems.lastOrNull { it is JulesChatItem.AgentMessage }?.let {
                                            (it as JulesChatItem.AgentMessage).id
                                        }

                                        julesClient.sendMessage(apiKey, currentSession!!.id, prompt)

                                        // Poll for response
                                        repeat(10) {
                                            kotlinx.coroutines.delay(2000)
                                            refreshActivitiesInternal()
                                            val newLastAgentMsg = chatItems.lastOrNull { it is JulesChatItem.AgentMessage } as? JulesChatItem.AgentMessage
                                            if (newLastAgentMsg != null && newLastAgentMsg.id != oldLastId) {
                                                voiceManager.speak(newLastAgentMsg.text)
                                                return@repeat
                                            }
                                        }
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
                            sessions = sessions,
                            selectedSourceName = selectedSourceName,
                            loading = loading,
                            loadingSessions = loadingSessions,
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
                                        val entity = JulesSessionEntity(
                                            id = session.id,
                                            title = session.title,
                                            prompt = session.prompt,
                                            sourceName = source,
                                            prUrl = session.outputs?.firstOrNull()?.pullRequest?.url,
                                            prTitle = session.outputs?.firstOrNull()?.pullRequest?.title,
                                            prState = null,
                                            isArchived = false,
                                            lastUpdated = System.currentTimeMillis()
                                        )
                                        newSessionPrompt = ""
                                        currentSession = entity
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
                            onOpenSession = { currentSession = it },
                            onMergePr = { session ->
                                scope.launch {
                                    loading = true
                                    val res = julesRepository.mergePr(githubToken, session.prUrl!!)
                                    if (res.isFailure) error = "Merge failed: ${res.exceptionOrNull()?.message}"
                                    else loadSessions()
                                    loading = false
                                }
                            },
                            onClosePr = { session ->
                                scope.launch {
                                    loading = true
                                    val res = julesRepository.closePr(githubToken, session.prUrl!!)
                                    if (res.isFailure) error = "Close failed: ${res.exceptionOrNull()?.message}"
                                    else loadSessions()
                                    loading = false
                                }
                            },
                            onGetPrDetails = { session, onResult ->
                                scope.launch {
                                    loading = true
                                    val res = julesRepository.getPrDetails(githubToken, session.prUrl!!)
                                    if (res.isSuccess) onResult(res.getOrNull())
                                    else error = "Could not get details: ${res.exceptionOrNull()?.message}"
                                    loading = false
                                }
                            },
                            onArchive = { session ->
                                scope.launch {
                                    loading = true
                                    julesRepository.archiveSession(session.id)
                                    loadSessions()
                                    loading = false
                                }
                            }
                        )
                    }

                }
            }
        }

        if (loading || loadingSessions) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.4f))
                        .clickable(enabled = true, onClick = {}),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = JulesAccent)
                }
            }
        }
    }

    if (showRepoSheet) {
        ModalBottomSheet(
            onDismissRequest = { showRepoSheet = false },
            sheetState = sheetState,
            sheetGesturesEnabled = true,
            containerColor = JulesBg,
            contentColor = Color.White
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp)
            ) {
                Text(
                    "Select Repository",
                    color = Color.White,
                    fontSize = 20.sp,
                    modifier = Modifier.padding(16.dp)
                )
                if (!sourcesLoaded && loading) {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = JulesAccent)
                    }
                } else if (sources.isEmpty() && sourcesLoaded) {
                    Text(
                        "No repositories found. Connect one at jules.google.com.",
                        color = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.padding(16.dp)
                    )
                    OutlinedButton(
                        onClick = { loadSources() },
                        modifier = Modifier.padding(horizontal = 16.dp)
                    ) {
                        Text("Retry")
                    }
                } else {
                    LazyColumn {
                        items(sources) { src ->
                            val displayName = src.githubRepo?.let { "${it.owner}/${it.repo}" } ?: src.name
                            val isSelected = src.name == selectedSourceName
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedSourceName = src.name
                                        selectedSourceDisplayName = displayName
                                        showRepoSheet = false
                                    }
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = displayName,
                                    color = if (isSelected) JulesAccent else Color.White,
                                    fontSize = 16.sp,
                                    modifier = Modifier.weight(1f)
                                )
                                if (isSelected) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = JulesAccent)
                                }
                            }
                        }
                        item {
                            OutlinedButton(
                                onClick = { loadSources() },
                                modifier = Modifier.padding(16.dp)
                            ) {
                                Text("Refresh list")
                            }
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
    currentSession: JulesSessionEntity,
    chatItems: List<JulesChatItem>,
    listState: androidx.compose.foundation.lazy.LazyListState,
    inputText: String,
    onInputChange: (String) -> Unit,
    voiceManager: fr.geoking.julius.shared.VoiceManager,
    onSend: () -> Unit,
    onBackToList: () -> Unit,
    loading: Boolean
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(JulesListBg.copy(alpha = 0.5f))
                .clickable { onBackToList() }
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = null,
                tint = JulesAccent,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.size(8.dp))
            Text(
                text = currentSession.title.ifBlank { currentSession.prompt.take(40) }.ifBlank { "Conversation" },
                color = JulesAccent,
                fontSize = 13.sp,
                modifier = Modifier.weight(1f)
            )
        }
        val timeFormatter = remember { DateTimeFormatter.ofPattern("HH:mm") }
        val nowInstant = remember { Instant.now() }

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
                val createTime = when (item) {
                    is JulesChatItem.UserMessage -> item.createTime
                    is JulesChatItem.AgentMessage -> item.createTime
                }

                val itemTime = remember(createTime) {
                    try {
                        OffsetDateTime.parse(createTime)
                    } catch (e: Exception) {
                        null
                    }
                }

                if (itemTime != null) {
                    val isOlderThanOneHour = remember(itemTime, nowInstant) {
                        Duration.between(itemTime.toInstant(), nowInstant).toHours() >= 1
                    }

                    if (isOlderThanOneHour) {
                        Text(
                            text = itemTime.format(timeFormatter),
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 11.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 4.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                }

                when (item) {
                    is JulesChatItem.UserMessage -> Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = JulesCardUser),
                            shape = RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp),
                            modifier = Modifier
                                .widthIn(max = 280.dp)
                                .clickable { voiceManager.speak(item.text) }
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
                            modifier = Modifier
                                .widthIn(max = 320.dp)
                                .clickable { voiceManager.speak(item.text) }
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
    sessions: List<JulesSessionEntity>,
    selectedSourceName: String?,
    loading: Boolean,
    loadingSessions: Boolean,
    newSessionPrompt: String,
    onNewSessionPromptChange: (String) -> Unit,
    onCreateSession: () -> Unit,
    onOpenSession: (JulesSessionEntity) -> Unit,
    onMergePr: (JulesSessionEntity) -> Unit,
    onClosePr: (JulesSessionEntity) -> Unit,
    onGetPrDetails: (JulesSessionEntity, (GitHubClient.GitHubPullRequestDetail?) -> Unit) -> Unit,
    onArchive: (JulesSessionEntity) -> Unit
) {
    var showPrDetails by remember { mutableStateOf<GitHubClient.GitHubPullRequestDetail?>(null) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        if (selectedSourceName == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "Select a repository above to see conversations and start a new one.",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(32.dp)
                )
            }
            return@Column
        }

        var showNewForm by remember { mutableStateOf(false) }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp)
        ) {
            item {
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
                            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
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
                    Spacer(modifier = Modifier.height(16.dp))
                } else {
                    FilledTonalButton(
                        onClick = { showNewForm = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = androidx.compose.material3.ButtonDefaults.filledTonalButtonColors(containerColor = JulesAccent.copy(alpha = 0.3f))
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(20.dp), tint = Color.White)
                        Spacer(modifier = Modifier.size(8.dp))
                        Text("New conversation")
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

            if (loadingSessions && sessions.isEmpty()) {
                item {
                    Text("Loading conversations…", color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
                }
            } else if (sessions.isEmpty()) {
                item {
                    Text(
                        "No conversations yet for this repository. Tap \"New conversation\" to start.",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 14.sp
                    )
                }
            } else {
                items(sessions) { session ->
                    SessionRow(
                        session = session,
                        onClick = { onOpenSession(session) },
                        onMerge = { onMergePr(session) },
                        onClose = { onClosePr(session) },
                        onArchive = { onArchive(session) },
                        onDetails = {
                            onGetPrDetails(session) { showPrDetails = it }
                        }
                    )
                }
            }
        }
    }

    if (showPrDetails != null) {
        val pr = showPrDetails!!
        AlertDialog(
            onDismissRequest = { showPrDetails = null },
            title = { Text(pr.title, color = Color.White) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text("State: ${pr.state.uppercase()}", color = JulesAccent, fontWeight = FontWeight.Bold)
                    if (pr.merged) Text("Status: MERGED", color = Color.Green)
                    if (pr.mergeable == false) Text("Status: CONFLICTS", color = Color.Red)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(pr.body ?: "No description.", color = Color.White.copy(alpha = 0.8f))
                }
            },
            confirmButton = {
                OutlinedButton(onClick = { showPrDetails = null }) { Text("Close") }
            },
            containerColor = JulesBg
        )
    }
}

@Composable
private fun SessionRow(
    session: JulesSessionEntity,
    onClick: () -> Unit,
    onMerge: () -> Unit,
    onClose: () -> Unit,
    onArchive: () -> Unit,
    onDetails: () -> Unit
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
            val hasOutput = !session.prUrl.isNullOrBlank()
            Spacer(modifier = Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                val (statusText, statusColor) = when (session.prState) {
                    "merged" -> "Merged" to Color.Magenta
                    "closed" -> "Closed" to Color.Red
                    "open" -> "Open PR" to Color.Green
                    else -> (if (hasOutput) "Output available" else "In progress") to (if (hasOutput) JulesAccent else Color.White.copy(alpha = 0.6f))
                }
                Box(modifier = Modifier.size(8.dp).background(statusColor, RoundedCornerShape(4.dp)))
                Spacer(modifier = Modifier.size(6.dp))
                Text(
                    text = statusText,
                    color = statusColor,
                    fontSize = 12.sp,
                    modifier = Modifier.weight(1f)
                )

                IconButton(onClick = onArchive, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Archive, contentDescription = "Archive", tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(16.dp))
                }

                if (hasOutput && session.prState == "open") {
                    IconButton(onClick = onDetails, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Description, contentDescription = "Details", tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                    Spacer(modifier = Modifier.size(4.dp))
                    IconButton(onClick = onMerge, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Merge, contentDescription = "Merge", tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                    Spacer(modifier = Modifier.size(4.dp))
                    IconButton(onClick = onClose, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}
