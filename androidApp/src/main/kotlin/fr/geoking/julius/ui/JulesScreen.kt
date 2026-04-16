package fr.geoking.julius.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Merge
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import androidx.compose.ui.platform.LocalUriHandler
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.Duration
import java.time.Instant
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.geoking.julius.SettingsManager
import fr.geoking.julius.api.github.GitHubClient
import fr.geoking.julius.api.jules.JulesChatItem
import fr.geoking.julius.api.jules.JulesClient
import fr.geoking.julius.persistence.JulesSessionEntity
import fr.geoking.julius.repository.JulesQuota
import fr.geoking.julius.repository.JulesRepository
import fr.geoking.julius.shared.network.NetworkException
import fr.geoking.julius.shared.voice.VoiceManager
import kotlinx.coroutines.delay
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
    voiceManager: VoiceManager
) {
    val settings by settingsManager.settings.collectAsState()
    val networkStatus by julesRepository.getNetworkService().status.collectAsState()
    val isOnline = networkStatus.isConnected
    val apiKey = settings.julesKey
    val githubToken = settings.githubApiKey

    var sources by remember { mutableStateOf<List<JulesClient.JulesSource>>(emptyList()) }
    var sessions by remember { mutableStateOf<List<JulesSessionEntity>>(emptyList()) }
    var currentSession by remember { mutableStateOf<JulesSessionEntity?>(null) }
    var quota by remember { mutableStateOf<JulesQuota?>(null) }
    val chatItems = remember { mutableStateListOf<JulesChatItem>() }
    var loading by remember { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }
    var loadingSessions by remember { mutableStateOf(false) }
    var refreshingSessions by remember { mutableStateOf(false) }
    var hideCompleted by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var inputText by remember { mutableStateOf("") }
    var newSessionPrompt by remember { mutableStateOf("") }
    var selectedSourceName by remember { mutableStateOf<String?>(null) }
    var selectedSourceDisplayName by remember { mutableStateOf("Select repository") }
    var sourcesLoaded by remember { mutableStateOf(false) }
    var showRepoSheet by remember { mutableStateOf(false) }
    var showActivitiesSheet by remember { mutableStateOf(false) }
    var showConflictSheet by remember { mutableStateOf(false) }
    var conflictingFiles by remember { mutableStateOf<List<String>>(emptyList()) }
    var rawActivities by remember { mutableStateOf<List<JulesClient.JulesActivity>>(emptyList()) }
    val sheetState = rememberModalBottomSheetState()
    val activitiesSheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val sessionsListState = rememberLazyListState()

    val handleBack = {
        if (currentSession != null) {
            currentSession = null
        } else if (selectedSourceName != null) {
            selectedSourceName = null
        } else {
            onBack()
        }
    }

    BackHandler(onBack = handleBack)

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

    fun loadSessions(isRefresh: Boolean = false) {
        val sourceName = selectedSourceName ?: return
        if (apiKey.isBlank()) return
        scope.launch {
            if (isRefresh) refreshingSessions = true else loadingSessions = true
            clearError()
            quota = julesRepository.getUsageQuota(apiKey)
            try {
                julesRepository.getSessions(apiKey, sourceName, githubToken).collectLatest { list ->
                    sessions = list
                    // If we are in a session, update currentSession object from the list to get refreshed PR state
                    currentSession?.let { curr ->
                    val updated = list.find { it.id == curr.id }
                    if (updated != null) {
                        currentSession = updated
                    } else if (curr.id.startsWith("offline_")) {
                        // Check if it was promoted to a real ID
                        val promoted = list.find { it.prompt == curr.prompt && !it.id.startsWith("offline_") }
                        if (promoted != null) {
                            currentSession = promoted
                        }
                    }
                    }
                }
            } finally {
                loadingSessions = false
                refreshingSessions = false
            }
        }
    }

    suspend fun refreshActivitiesInternal(isRefresh: Boolean = false) {
        val session = currentSession ?: return
        if (apiKey.isBlank()) return
        if (isRefresh) refreshing = true else loading = true
        clearError()
        try {
            julesRepository.getActivities(apiKey, session.id).collectLatest { list ->
                // Don't clear if list is already populated from cache to avoid flicker
                if (chatItems.isEmpty() || isRefresh) {
                    chatItems.clear()
                    chatItems.addAll(list)
                } else {
                    // Smart update: add only new items
                    val existingIds = chatItems.map { if (it is JulesChatItem.UserMessage) it.id else (it as JulesChatItem.AgentMessage).id }.toSet()
                    val newItems = list.filter {
                        val id = if (it is JulesChatItem.UserMessage) it.id else (it as JulesChatItem.AgentMessage).id
                        !existingIds.contains(id)
                    }
                    if (newItems.isNotEmpty()) {
                        chatItems.addAll(newItems)
                    }
                }
                if (chatItems.isNotEmpty()) {
                    listState.animateScrollToItem(chatItems.size - 1)
                }
            }
        } finally {
            loading = false
            refreshing = false
        }
    }

    fun refreshActivities(isRefresh: Boolean = false) {
        scope.launch { refreshActivitiesInternal(isRefresh) }
    }

    LaunchedEffect(apiKey) {
        if (apiKey.isNotBlank()) {
            if (sources.isEmpty()) loadSources()
        } else {
            sourcesLoaded = true
        }
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

    // Polling logic for PR status/creation while on Jules screen
    LaunchedEffect(apiKey, githubToken, selectedSourceName, currentSession?.id) {
        if (apiKey.isBlank() || selectedSourceName == null) return@LaunchedEffect
        while (true) {
            delay(30_000)
            val sessionToPoll = currentSession
            if (sessionToPoll != null) {
                julesRepository.pollSessionStatus(apiKey, sessionToPoll.id, githubToken)
                // loadSessions will refresh the list and update currentSession in its collectLatest block
                loadSessions()
            } else {
                // Poll any session that is "In progress" (null prState but has no PR URL yet)
                val inProgress = sessions.filter { it.prState == null && it.prUrl == null }
                if (inProgress.isNotEmpty()) {
                    for (s in inProgress) {
                        julesRepository.pollSessionStatus(apiKey, s.id, githubToken)
                    }
                    loadSessions()
                } else {
                    // Even if none in progress, refresh to check for updates
                    loadSessions()
                }
            }
        }
    }

    LaunchedEffect(currentSession?.id) {
        currentSession?.let { refreshActivities() }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = JulesBg) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                if (loading || loadingSessions) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = JulesAccent,
                        trackColor = Color.Transparent
                    )
                }
                Row(
                    modifier = Modifier
                    .fillMaxWidth()
                    .background(JulesHeaderBg)
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = handleBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Spacer(modifier = Modifier.size(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = when {
                            currentSession != null -> currentSession!!.title.ifBlank { currentSession!!.prompt.take(40) }.ifBlank { "Conversation" }
                            selectedSourceName != null -> selectedSourceDisplayName
                            else -> "Jules"
                        },
                        color = Color.White,
                        fontSize = 20.sp,
                        maxLines = 1
                    )
                    if (currentSession != null) {
                        val hasOutput = !currentSession!!.prUrl.isNullOrBlank()
                        val (statusText, statusColor) = when {
                            currentSession!!.prState == "merged" -> "Merged" to Color.Magenta
                            currentSession!!.prState == "closed" -> "Closed" to Color.Red
                            currentSession!!.prState == "open" -> "Open PR" to Color.Green
                            currentSession!!.sessionState == "COMPLETED" -> "Completed" to Color.Green
                            currentSession!!.sessionState == "FAILED" -> "Failed" to Color.Red
                            currentSession!!.sessionState == "AWAITING_PLAN_APPROVAL" -> "Waiting for approval" to JulesAccent
                            currentSession!!.sessionState == "AWAITING_USER_FEEDBACK" -> "Waiting for you" to JulesAccent
                            currentSession!!.sessionState == "PLANNING" -> "Planning…" to JulesAccent
                            currentSession!!.sessionState == "QUEUED" -> "Queued…" to Color.White.copy(alpha = 0.6f)
                            currentSession!!.sessionState == "QUEUED_OFFLINE" -> "Queued (Offline)" to Color.White.copy(alpha = 0.5f)
                            currentSession!!.sessionState == "PAUSED" -> "Paused" to Color.Yellow
                            else -> (if (hasOutput) "Output available" else "In progress") to (if (hasOutput) JulesAccent else Color.White.copy(alpha = 0.6f))
                        }
                        val mergeabilityText = if (currentSession!!.prState == "open" && currentSession!!.prMergeable == false) " (Conflicts)" else ""

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(6.dp).background(statusColor, RoundedCornerShape(3.dp)))
                            Spacer(modifier = Modifier.size(6.dp))
                            Text(
                                text = "$statusText$mergeabilityText",
                                color = statusColor,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
                if (currentSession != null) {
                    IconButton(onClick = {
                        scope.launch {
                            loading = true
                            try {
                                val resp = julesClient.listActivities(apiKey, currentSession!!.id)
                                rawActivities = resp.activities
                                showActivitiesSheet = true
                            } catch (e: Exception) {
                                error = "Could not load activities: ${e.message}"
                            }
                            loading = false
                        }
                    }) {
                        Icon(Icons.Default.History, contentDescription = "Activities", tint = Color.White)
                    }
                }
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

                    when {
                        currentSession != null -> {
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

                                            julesRepository.sendMessage(apiKey, currentSession!!.id, prompt)

                                            if (isOnline && !currentSession!!.id.startsWith("offline_")) {
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
                                            } else {
                                                // Refresh immediately to show local user message
                                                refreshActivitiesInternal()
                                            }
                                        } catch (e: Exception) {
                                            error = e.message ?: "Failed to send"
                                        }
                                        loading = false
                                    }
                                },
                                onSolveConflicts = {
                                    scope.launch {
                                        loading = true
                                        val res = julesRepository.getConflictingFiles(githubToken, currentSession!!.prUrl!!)
                                        if (res.isSuccess) {
                                            conflictingFiles = res.getOrDefault(emptyList())
                                            showConflictSheet = true
                                        } else {
                                            error = "Could not find conflicting files: ${res.exceptionOrNull()?.message}"
                                        }
                                        loading = false
                                    }
                                },
                                onAutoSolveConflicts = {
                                    scope.launch {
                                        loading = true
                                        try {
                                            julesRepository.sendMessage(apiKey, currentSession!!.id, "@jules resolve the conflicts in this PR")
                                            currentSession?.let { refreshActivities() }
                                        } catch (e: Exception) {
                                            error = "Auto solve failed: ${e.message}"
                                        }
                                        loading = false
                                    }
                                },
                                loading = loading,
                                onMergePr = {
                                    scope.launch {
                                        loading = true
                                        val res = julesRepository.mergePr(githubToken, currentSession!!.prUrl!!)
                                        if (res.isFailure) error = "Merge failed: ${res.exceptionOrNull()?.message}"
                                        else loadSessions()
                                        loading = false
                                    }
                                },
                                isRefreshing = refreshing,
                                onRefresh = { refreshActivities(isRefresh = true) }
                            )
                        }
                        selectedSourceName != null -> {
                            val sourceName = selectedSourceName!!
                            SessionsListContent(
                                apiKey = apiKey,
                                isOnline = isOnline,
                                sessions = sessions,
                                selectedSourceName = sourceName,
                                loading = loading,
                                loadingSessions = loadingSessions,
                                newSessionPrompt = newSessionPrompt,
                                onNewSessionPromptChange = { newSessionPrompt = it },
                                onCreateSession = {
                                    val source = selectedSourceName ?: return@SessionsListContent
                                    if (newSessionPrompt.isBlank()) return@SessionsListContent
                                    scope.launch {
                                        loading = true
                                        clearError()
                                        try {
                                            val sessionId = julesRepository.createSession(
                                                apiKey = apiKey,
                                                prompt = newSessionPrompt,
                                                source = source,
                                                title = newSessionPrompt.take(80)
                                            )

                                            val entity = julesRepository.getSession(sessionId)

                                            newSessionPrompt = ""
                                            currentSession = entity
                                            loadSessions()
                                        } catch (e: Exception) {
                                            error = when {
                                                e is NetworkException && e.httpCode == 401 -> "Invalid API key."
                                                e is NetworkException && e.httpCode == 429 -> "Daily session limit reached (5 sessions/day during beta)."
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
                                onSolveConflicts = { session ->
                                    scope.launch {
                                        loading = true
                                        val res = julesRepository.getConflictingFiles(githubToken, session.prUrl!!)
                                        if (res.isSuccess) {
                                            currentSession = session
                                            conflictingFiles = res.getOrDefault(emptyList())
                                            showConflictSheet = true
                                        } else {
                                            error = "Could not find conflicting files: ${res.exceptionOrNull()?.message}"
                                        }
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
                                        julesRepository.archiveSession(apiKey, session.id)
                                        loadSessions()
                                        loading = false
                                    }
                                },
                                quota = quota,
                                isRefreshingSessions = refreshingSessions,
                                onRefreshSessions = { loadSessions(isRefresh = true) },
                                hideCompleted = hideCompleted,
                                onHideCompletedChange = { hideCompleted = it }
                            )
                        }
                        else -> {
                            ProjectsListContent(
                                sources = sources,
                                loading = loading,
                                sourcesLoaded = sourcesLoaded,
                                onSourceSelected = { src ->
                                    selectedSourceName = src.name
                                    selectedSourceDisplayName = src.githubRepo?.let { "${it.owner}/${it.repo}" } ?: src.name
                                },
                                onRefresh = { loadSources() }
                            )
                        }
                    }

                }
            }
        }

        }
    }

    if (showActivitiesSheet) {
        ModalBottomSheet(
            onDismissRequest = { showActivitiesSheet = false },
            sheetState = activitiesSheetState,
            containerColor = JulesBg,
            contentColor = Color.White
        ) {
            ActivitiesSheet(activities = rawActivities)
        }
    }

    if (showConflictSheet) {
        ModalBottomSheet(
            onDismissRequest = { showConflictSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = JulesBg,
            contentColor = Color.White,
            modifier = Modifier.fillMaxSize()
        ) {
            ConflictResolutionSheet(
                session = currentSession!!,
                files = conflictingFiles,
                githubToken = githubToken,
                julesRepository = julesRepository,
                onDismiss = {
                    showConflictSheet = false
                    loadSessions()
                }
            )
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

private enum class MessageType {
    PLAN, CODE_REVIEWED, SEARCHING, STANDARD
}

private data class ParsedMessage(
    val type: MessageType,
    val title: String,
    val subtitle: String
)

private fun parseMessage(text: String, overrideTitle: String? = null): ParsedMessage {
    return when {
        overrideTitle != null -> {
            ParsedMessage(MessageType.STANDARD, overrideTitle, text)
        }
        text.startsWith("**Plan", ignoreCase = true) -> {
            val lines = text.lines()
            val firstLine = lines.firstOrNull() ?: ""
            val title = firstLine.replace("**", "").trim()
            val subtitle = lines.drop(1).joinToString("\n").trim()
            ParsedMessage(MessageType.PLAN, title, subtitle)
        }
        text.startsWith("Code reviewed", ignoreCase = true) -> {
            val title = "Code reviewed"
            val subtitle = text.removePrefix("Code reviewed").trim().removePrefix(":").trim()
            ParsedMessage(MessageType.CODE_REVIEWED, title, subtitle)
        }
        text.startsWith("Searching for", ignoreCase = true) -> {
            val lines = text.lines()
            val title = lines.firstOrNull()?.trim() ?: ""
            val subtitle = lines.drop(1).joinToString("\n").trim()
            ParsedMessage(MessageType.SEARCHING, title, subtitle)
        }
        else -> {
            val lines = text.lines()
            val title = lines.firstOrNull()?.trim() ?: ""
            val subtitle = lines.drop(1).joinToString("\n").trim()
            ParsedMessage(MessageType.STANDARD, title, subtitle)
        }
    }
}

@Composable
private fun MessageText(
    item: JulesChatItem,
    baseFontSize: Int,
    onSpeak: () -> Unit
) {
    val text = when (item) {
        is JulesChatItem.UserMessage -> item.text
        is JulesChatItem.AgentMessage -> if (item.subItems.size == 1) item.subItems.first().text else item.text
    }
    val titleOverride = (item as? JulesChatItem.AgentMessage)?.let { if (it.subItems.size > 1) it.title else null }
    val parsed = remember(text, titleOverride) { parseMessage(text, titleOverride) }
    var expanded by remember { mutableStateOf(false) }

    val isExpandable = parsed.type != MessageType.STANDARD || (item is JulesChatItem.AgentMessage && item.subItems.size > 1)

    val timeFormatter = remember { DateTimeFormatter.ofPattern("HH:mm") }

    @Composable
    fun RenderAnnotatedText(content: String, isTitle: Boolean, modifier: Modifier = Modifier, maxLines: Int = Int.MAX_VALUE, colorAlpha: Float = if (isTitle) 1f else 0.6f) {
        val annotatedString = buildAnnotatedString {
            val style = if (isTitle) {
                SpanStyle(color = Color.White, fontWeight = FontWeight.Bold)
            } else {
                SpanStyle(color = Color.White.copy(alpha = colorAlpha))
            }

            withStyle(style) {
                // Apply Markdown-like bolding for **text**
                val parts = content.split("**")
                parts.forEachIndexed { pIndex, part ->
                    if (pIndex % 2 == 1) {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Color.White)) {
                            append(part)
                        }
                    } else {
                        // Apply bullet point conversion
                        val bulletedPart = part.replace(Regex("(?m)^\\s*[-*]\\s+"), " • ")
                        append(bulletedPart)
                    }
                }
            }
        }
        Text(
            text = annotatedString,
            modifier = modifier,
            fontSize = (if (isTitle && parsed.type == MessageType.PLAN) baseFontSize + 2 else baseFontSize).sp,
            maxLines = maxLines,
            overflow = if (maxLines != Int.MAX_VALUE) TextOverflow.Ellipsis else TextOverflow.Clip
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                @OptIn(ExperimentalFoundationApi::class)
                Modifier.combinedClickable(
                    onClick = { if (isExpandable) expanded = !expanded },
                    onDoubleClick = onSpeak
                )
            )
            .padding(vertical = 8.dp, horizontal = 12.dp)
    ) {
        if (item is JulesChatItem.AgentMessage && item.subItems.size > 1) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RenderAnnotatedText(parsed.title, isTitle = true, maxLines = 1, modifier = Modifier.weight(1f))
                Icon(
                    if (expanded) Icons.Default.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = JulesAccent,
                    modifier = Modifier.size(20.dp)
                )
            }
            if (expanded) {
                item.subItems.forEach { sub ->
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.weight(1f)) {
                            RenderAnnotatedText(sub.text, isTitle = false)
                            val subTime = remember(sub.createTime) {
                                try { OffsetDateTime.parse(sub.createTime).format(timeFormatter) } catch (e: Exception) { "" }
                            }
                            if (subTime.isNotBlank()) {
                                Text(subTime, color = Color.White.copy(alpha = 0.4f), fontSize = 10.sp)
                            }
                        }
                    }
                }
            } else {
                val lastSub = item.subItems.last()
                RenderAnnotatedText(lastSub.text, isTitle = false, maxLines = 1)
            }
        } else {
            val itemTime = when (item) {
                is JulesChatItem.UserMessage -> item.createTime
                is JulesChatItem.AgentMessage -> item.createTime
            }
            val displayTime = remember(itemTime) {
                try { OffsetDateTime.parse(itemTime).format(timeFormatter) } catch (e: Exception) { "" }
            }

            when (parsed.type) {
                MessageType.SEARCHING -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (expanded) {
                            Column(modifier = Modifier.weight(1f)) {
                                RenderAnnotatedText(parsed.title, isTitle = true)
                                if (parsed.subtitle.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    RenderAnnotatedText(parsed.subtitle, isTitle = false)
                                }
                                if (displayTime.isNotBlank()) {
                                    Text(displayTime, color = Color.White.copy(alpha = 0.4f), fontSize = 10.sp)
                                }
                            }
                        } else {
                            Column(modifier = Modifier.weight(1f)) {
                                RenderAnnotatedText(parsed.title, isTitle = true, maxLines = 1)
                                if (displayTime.isNotBlank()) {
                                    Text(displayTime, color = Color.White.copy(alpha = 0.4f), fontSize = 10.sp)
                                }
                            }
                        }
                        if (isExpandable) {
                            Icon(
                                if (expanded) Icons.Default.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = JulesAccent,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
                MessageType.PLAN -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            RenderAnnotatedText(parsed.title, isTitle = true, maxLines = if (expanded) Int.MAX_VALUE else 1)
                            if (expanded) {
                                if (parsed.subtitle.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    RenderAnnotatedText(parsed.subtitle, isTitle = false)
                                }
                            } else {
                                if (parsed.subtitle.isNotBlank()) {
                                    RenderAnnotatedText(parsed.subtitle, isTitle = false, maxLines = 1)
                                }
                            }
                            if (displayTime.isNotBlank()) {
                                Text(displayTime, color = Color.White.copy(alpha = 0.4f), fontSize = 10.sp)
                            }
                        }
                        Icon(
                            if (expanded) Icons.Default.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = JulesAccent,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                MessageType.CODE_REVIEWED -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            RenderAnnotatedText(parsed.title, isTitle = true, maxLines = if (expanded) Int.MAX_VALUE else 1)
                            if (expanded && parsed.subtitle.isNotBlank()) {
                                Spacer(modifier = Modifier.height(2.dp))
                                RenderAnnotatedText(parsed.subtitle, isTitle = false)
                            }
                            if (displayTime.isNotBlank()) {
                                Text(displayTime, color = Color.White.copy(alpha = 0.4f), fontSize = 10.sp)
                            }
                        }
                        Icon(
                            if (expanded) Icons.Default.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = JulesAccent,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                MessageType.STANDARD -> {
                    RenderAnnotatedText(parsed.title, isTitle = true)
                    if (parsed.subtitle.isNotBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        RenderAnnotatedText(parsed.subtitle, isTitle = false)
                    }
                    if (displayTime.isNotBlank()) {
                        Text(displayTime, color = Color.White.copy(alpha = 0.4f), fontSize = 10.sp)
                    }
                }
            }
        }
    }
}

private enum class ProgressStep(val label: String) {
    PLAN("Plan"),
    PLAN_APPROVED("Plan approved"),
    RUNNING_CODE_REVIEW("Running code review"),
    COMPLETE_PRE_COMMIT_STEPS("Complete pre-commit steps"),
    ALL_PLAN_STEPS_COMPLETED("All plan steps completed")
}

private fun calculateProgressStep(session: JulesSessionEntity, chatItems: List<JulesChatItem>): ProgressStep? {
    // Priority 1: sessionState from Jules API
    when (session.sessionState) {
        "PLANNING" -> return ProgressStep.PLAN
        "AWAITING_PLAN_APPROVAL" -> return ProgressStep.PLAN
        "COMPLETED" -> return ProgressStep.ALL_PLAN_STEPS_COMPLETED
    }

    // Priority 2: Look into chat items (activities)
    val agentMessages = chatItems.filterIsInstance<JulesChatItem.AgentMessage>()
    val userMessages = chatItems.filterIsInstance<JulesChatItem.UserMessage>()

    val lastAgentMsg = agentMessages.lastOrNull()?.text ?: ""
    val hasPlanApproved = agentMessages.any { it.text.contains("Plan approved", ignoreCase = true) }
    val hasCodeReviewed = agentMessages.any { it.text.startsWith("Code reviewed", ignoreCase = true) }
    val hasPreCommit = agentMessages.any { it.text.contains("pre commit", ignoreCase = true) || it.text.contains("pre-commit", ignoreCase = true) }
    val isCompleted = session.sessionState == "COMPLETED" || agentMessages.any { it.text.contains("Session completed", ignoreCase = true) }

    return when {
        isCompleted -> ProgressStep.ALL_PLAN_STEPS_COMPLETED
        hasPreCommit -> ProgressStep.COMPLETE_PRE_COMMIT_STEPS
        hasCodeReviewed -> ProgressStep.RUNNING_CODE_REVIEW
        hasPlanApproved -> ProgressStep.PLAN_APPROVED
        lastAgentMsg.startsWith("**Plan") -> ProgressStep.PLAN
        else -> null
    }
}

@Composable
private fun MiniProgressBar(currentStep: ProgressStep?) {
    if (currentStep == null) return

    val steps = ProgressStep.values()
    val currentIndex = steps.indexOf(currentStep)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        steps.forEachIndexed { index, _ ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(4.dp)
                    .background(
                        if (index <= currentIndex) JulesAccent else Color.White.copy(alpha = 0.1f),
                        RoundedCornerShape(2.dp)
                    )
            )
        }
    }
}

@Composable
private fun ConflictResolutionSheet(
    session: JulesSessionEntity,
    files: List<String>,
    githubToken: String,
    julesRepository: JulesRepository,
    onDismiss: () -> Unit
) {
    var selectedFile by remember { mutableStateOf<String?>(null) }
    var fileContent by remember { mutableStateOf("") }
    var fileSha by remember { mutableStateOf("") }
    var conflicts by remember { mutableStateOf<List<JulesRepository.Conflict>>(emptyList()) }
    var currentConflictIndex by remember { mutableStateOf(0) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Resolve Conflicts",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
            }
        }

        if (error != null) {
            ErrorCard(title = "Error", message = error!!, onDismiss = { error = null })
        }

        if (loading) {
            Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = JulesAccent)
            }
        } else if (selectedFile == null) {
            Text(
                "Conflicting files:",
                color = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.padding(vertical = 8.dp)
            )
            LazyColumn {
                items(files) { file ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                scope.launch {
                                    loading = true
                                    val res = julesRepository.getFileContent(githubToken, session.prUrl!!, file)
                                    if (res.isSuccess) {
                                        val (content, sha) = res.getOrThrow()
                                        selectedFile = file
                                        fileContent = content
                                        fileSha = sha
                                        conflicts = julesRepository.parseConflicts(content)
                                        currentConflictIndex = 0
                                    } else {
                                        error = "Failed to load file: ${res.exceptionOrNull()?.message}"
                                    }
                                    loading = false
                                }
                            }
                            .padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = JulesHeaderBg)
                    ) {
                        Text(file, color = Color.White, modifier = Modifier.padding(16.dp))
                    }
                }
            }
        } else {
            // File conflict resolution view
            val conflict = conflicts.getOrNull(currentConflictIndex)
            Column(modifier = Modifier.fillMaxSize()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { selectedFile = null }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                    Text(selectedFile!!, color = JulesAccent, fontSize = 16.sp, modifier = Modifier.weight(1f))
                    if (conflicts.isNotEmpty()) {
                        Text(
                            "Conflict ${currentConflictIndex + 1} of ${conflicts.size}",
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 14.sp
                        )
                    }
                }

                if (conflict != null) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                    ) {
                        ConflictBlock("Mine (PR Branch)", conflict.mine, JulesAccent) {
                            val newContent = fileContent.replace(conflict.fullMatch, conflict.mine + "\n")
                            fileContent = newContent
                            conflicts = julesRepository.parseConflicts(newContent)
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        ConflictBlock("Incoming (Base Branch)", conflict.incoming, Color.Green) {
                            val newContent = fileContent.replace(conflict.fullMatch, conflict.incoming + "\n")
                            fileContent = newContent
                            conflicts = julesRepository.parseConflicts(newContent)
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        ConflictBlock("Both", conflict.mine + "\n" + conflict.incoming, Color.Yellow) {
                            val newContent = fileContent.replace(conflict.fullMatch, conflict.mine + "\n" + conflict.incoming + "\n")
                            fileContent = newContent
                            conflicts = julesRepository.parseConflicts(newContent)
                        }

                        Spacer(modifier = Modifier.height(24.dp))
                        Text("Current Result:", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
                        OutlinedTextField(
                            value = conflict.fullMatch, // This is just for display, editing should happen on the whole file or specifically here
                            onValueChange = { newValue ->
                                val newContent = fileContent.replace(conflict.fullMatch, newValue)
                                fileContent = newContent
                                conflicts = julesRepository.parseConflicts(newContent)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = androidx.compose.ui.text.TextStyle(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, fontSize = 12.sp),
                            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        OutlinedButton(
                            onClick = { currentConflictIndex-- },
                            enabled = currentConflictIndex > 0
                        ) {
                            Text("Previous")
                        }
                        OutlinedButton(
                            onClick = { currentConflictIndex++ },
                            enabled = currentConflictIndex < conflicts.size - 1
                        ) {
                            Text("Next")
                        }
                    }
                } else {
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Text("All conflicts resolved in this file!", color = Color.Green)
                    }
                }

                FilledTonalButton(
                    onClick = {
                        scope.launch {
                            loading = true
                            val res = julesRepository.saveResolvedFile(githubToken, session.prUrl!!, selectedFile!!, fileContent, fileSha)
                            if (res.isSuccess) {
                                selectedFile = null
                            } else {
                                error = "Failed to save: ${res.exceptionOrNull()?.message}"
                            }
                            loading = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = conflicts.isEmpty() && !loading,
                    colors = androidx.compose.material3.ButtonDefaults.filledTonalButtonColors(containerColor = Color.Green.copy(alpha = 0.2f))
                ) {
                    Text("Save Resolved File", color = Color.Green)
                }
            }
        }
    }
}

@Composable
private fun ConflictBlock(title: String, content: String, color: Color, onSelect: () -> Unit) {
    Column {
        Text(title, color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onSelect)
                .padding(vertical = 4.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.3f)),
            border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.5f))
        ) {
            Text(
                content,
                color = Color.White,
                fontSize = 11.sp,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                modifier = Modifier.padding(8.dp)
            )
        }
    }
}

@Composable
private fun ActivitiesSheet(activities: List<JulesClient.JulesActivity>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 32.dp)
    ) {
        Text(
            "Activities",
            color = Color.White,
            fontSize = 20.sp,
            modifier = Modifier.padding(16.dp),
            fontWeight = FontWeight.Bold
        )
        if (activities.isEmpty()) {
            Text(
                "No activities found.",
                color = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.padding(16.dp)
            )
        } else {
            val timeFormatter = remember { DateTimeFormatter.ofPattern("MMM dd, HH:mm:ss") }
            LazyColumn {
                items(activities.sortedByDescending { it.createTime }) { activity ->
                    val activityTime = try {
                        OffsetDateTime.parse(activity.createTime).format(timeFormatter)
                    } catch (e: Exception) {
                        activity.createTime
                    }

                    val typeText = when {
                        activity.planGenerated != null -> "Plan Generated"
                        activity.planApproved != null -> "Plan Approved"
                        activity.progressUpdated != null -> "Progress Updated"
                        activity.messageSent != null -> "Message Sent"
                        activity.sessionCompleted != null -> "Session Completed"
                        else -> "Activity"
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = activity.originator.replaceFirstChar { it.uppercase() },
                                color = if (activity.originator == "user") JulesAccent else Color.Green,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.size(8.dp))
                            Text(
                                text = typeText,
                                color = Color.White,
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            Text(
                                text = activityTime,
                                color = Color.White.copy(alpha = 0.5f),
                                fontSize = 12.sp
                            )
                        }

                        activity.artifacts?.forEach { artifact ->
                            artifact.bashOutput?.let { bash ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 8.dp)
                                        .background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                                        .padding(8.dp)
                                ) {
                                    Text(
                                        text = "> ${bash.command}",
                                        color = Color.Green,
                                        fontSize = 11.sp,
                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                    )
                                    if (bash.output.isNotBlank()) {
                                        Text(
                                            text = bash.output,
                                            color = Color.White.copy(alpha = 0.7f),
                                            fontSize = 10.sp,
                                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                            modifier = Modifier.padding(top = 4.dp)
                                        )
                                    }
                                }
                            }
                            artifact.changeSet?.let { cs ->
                                cs.gitPatch?.let { gp ->
                                    Text(
                                        text = "Changed: ${cs.source}",
                                        color = JulesAccent,
                                        fontSize = 12.sp,
                                        modifier = Modifier.padding(top = 8.dp)
                                    )
                                    gp.suggestedCommitMessage?.let { msg ->
                                        Text(
                                            text = "Commit: $msg",
                                            color = Color.White.copy(alpha = 0.6f),
                                            fontSize = 11.sp,
                                            modifier = Modifier.padding(top = 2.dp)
                                        )
                                    }
                                }
                            }
                            artifact.media?.let { media ->
                                if (media.mimeType.startsWith("image/")) {
                                    val bitmap = remember(media.data) {
                                        try {
                                            val decoded = Base64.decode(media.data, Base64.DEFAULT)
                                            BitmapFactory.decodeByteArray(decoded, 0, decoded.size)
                                        } catch (e: Exception) {
                                            null
                                        }
                                    }
                                    bitmap?.let {
                                        Image(
                                            bitmap = it.asImageBitmap(),
                                            contentDescription = "Activity Media",
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(top = 8.dp)
                                                .height(200.dp)
                                                .background(Color.Black.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                                        )
                                    }
                                }
                            }
                        }

                        activity.progressUpdated?.let {
                            Text(
                                text = it.title,
                                color = Color.White.copy(alpha = 0.8f),
                                fontSize = 13.sp,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                        HorizontalDivider(
                            modifier = Modifier.padding(top = 12.dp),
                            color = Color.White.copy(alpha = 0.1f)
                        )
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InConversationContent(
    currentSession: JulesSessionEntity,
    chatItems: List<JulesChatItem>,
    listState: androidx.compose.foundation.lazy.LazyListState,
    inputText: String,
    onInputChange: (String) -> Unit,
    voiceManager: VoiceManager,
    onSend: () -> Unit,
    loading: Boolean,
    onMergePr: () -> Unit,
    onSolveConflicts: () -> Unit,
    onAutoSolveConflicts: () -> Unit,
    isRefreshing: Boolean,
    onRefresh: () -> Unit
) {
    val uriHandler = LocalUriHandler.current
    val progressStep = remember(currentSession, chatItems.size) {
        calculateProgressStep(currentSession, chatItems)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        MiniProgressBar(currentStep = progressStep)

        val timeFormatter = remember { DateTimeFormatter.ofPattern("HH:mm") }
        val nowInstant = remember { Instant.now() }

        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier.weight(1f)
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
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
                            modifier = Modifier
                                .widthIn(max = 280.dp)
                        ) {
                            MessageText(
                                item = item,
                                baseFontSize = 15,
                                onSpeak = { voiceManager.speak(item.text) }
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
                        ) {
                            MessageText(
                                item = item,
                                baseFontSize = 14,
                                onSpeak = { voiceManager.speak(item.text) }
                            )
                        }
                    }
                }
            }

            if (currentSession.prState == "open") {
                item {
                    val mergeable = currentSession.prMergeable
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp, bottom = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (mergeable == true) {
                            Text(
                                "Conflicts resolved. Ready to merge! 🚀",
                                color = Color.Green,
                                fontSize = 14.sp,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )
                            FilledTonalButton(
                                onClick = onMergePr,
                                modifier = Modifier.fillMaxWidth(),
                                colors = androidx.compose.material3.ButtonDefaults.filledTonalButtonColors(containerColor = Color.Green.copy(alpha = 0.2f))
                            ) {
                                Icon(Icons.Default.Merge, contentDescription = null, tint = Color.Green)
                                Spacer(modifier = Modifier.size(8.dp))
                                Text("Merge Pull Request", color = Color.Green)
                            }
                        } else if (mergeable == false) {
                            Text(
                                "This PR has merge conflicts.",
                                color = Color.Red,
                                fontSize = 14.sp,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )
                            Row(modifier = Modifier.fillMaxWidth()) {
                                FilledTonalButton(
                                    onClick = onAutoSolveConflicts,
                                    modifier = Modifier.weight(1f),
                                    colors = androidx.compose.material3.ButtonDefaults.filledTonalButtonColors(containerColor = JulesAccent.copy(alpha = 0.2f))
                                ) {
                                    Text("Auto solve", color = JulesAccent)
                                }
                                Spacer(modifier = Modifier.size(8.dp))
                                FilledTonalButton(
                                    onClick = onSolveConflicts,
                                    modifier = Modifier.weight(1f),
                                    colors = androidx.compose.material3.ButtonDefaults.filledTonalButtonColors(containerColor = Color.Red.copy(alpha = 0.2f))
                                ) {
                                    Text("Solve manually", color = Color.Red)
                                }
                            }
                        }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProjectsListContent(
    sources: List<JulesClient.JulesSource>,
    loading: Boolean,
    sourcesLoaded: Boolean,
    onSourceSelected: (JulesClient.JulesSource) -> Unit,
    onRefresh: () -> Unit
) {
    LaunchedEffect(Unit) {
        if (sources.isEmpty() && !loading) {
            onRefresh()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        if (!sourcesLoaded && loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = JulesAccent)
            }
        } else if (sources.isEmpty() && sourcesLoaded) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "No repositories found. Connect one at jules.google.com.",
                        color = Color.White.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(32.dp)
                    )
                    OutlinedButton(onClick = onRefresh) {
                        Text("Retry")
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
                items(sources) { src ->
                    val displayName = src.githubRepo?.let { "${it.owner}/${it.repo}" } ?: src.name
                    val initials = displayName.split("/").last().take(1).uppercase()

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSourceSelected(src) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .background(JulesAccent.copy(alpha = 0.2f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = initials,
                                color = JulesAccent,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.size(16.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = displayName,
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (src.githubRepo != null) {
                                Text(
                                    text = "GitHub Repository",
                                    color = Color.White.copy(alpha = 0.5f),
                                    fontSize = 13.sp
                                )
                            }
                        }

                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.3f)
                        )
                    }
                    HorizontalDivider(color = Color.White.copy(alpha = 0.05f), modifier = Modifier.padding(start = 64.dp))
                }

                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedButton(
                        onClick = onRefresh,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !loading
                    ) {
                        Text("Refresh Projects")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SessionsListContent(
    apiKey: String,
    isOnline: Boolean,
    sessions: List<JulesSessionEntity>,
    selectedSourceName: String,
    loading: Boolean,
    loadingSessions: Boolean,
    newSessionPrompt: String,
    onNewSessionPromptChange: (String) -> Unit,
    onCreateSession: () -> Unit,
    onOpenSession: (JulesSessionEntity) -> Unit,
    onMergePr: (JulesSessionEntity) -> Unit,
    onSolveConflicts: (JulesSessionEntity) -> Unit,
    onClosePr: (JulesSessionEntity) -> Unit,
    onGetPrDetails: (JulesSessionEntity, (GitHubClient.GitHubPullRequestDetail?) -> Unit) -> Unit,
    onArchive: (JulesSessionEntity) -> Unit,
    quota: JulesQuota? = null,
    isRefreshingSessions: Boolean,
    onRefreshSessions: () -> Unit,
    hideCompleted: Boolean,
    onHideCompletedChange: (Boolean) -> Unit
) {
    var showPrDetails by remember { mutableStateOf<GitHubClient.GitHubPullRequestDetail?>(null) }
    val displaySessions = remember(sessions, hideCompleted) {
        sessions
            .filter { session ->
                if (!hideCompleted) return@filter true
                val isCompleted = session.prState == "merged" ||
                                 session.prState == "closed" ||
                                 session.sessionState == "COMPLETED"
                !isCompleted
            }
            .sortedByDescending { it.lastUpdated }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {

        var showNewForm by remember { mutableStateOf(false) }

        PullToRefreshBox(
            isRefreshing = isRefreshingSessions,
            onRefresh = onRefreshSessions,
            modifier = Modifier.fillMaxSize()
        ) {
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
                                    enabled = newSessionPrompt.isNotBlank() && !loading && (quota == null || (isOnline && quota.used < quota.limit) || !isOnline)
                                ) { Text("Create") }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                } else {
                    quota?.let {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Daily usage: ${it.used}/${it.limit} sessions",
                                color = if (it.used >= it.limit) Color.Red else Color.White.copy(alpha = 0.6f),
                                fontSize = 13.sp
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilledTonalButton(
                            onClick = { showNewForm = true },
                            modifier = Modifier.weight(1f),
                            colors = androidx.compose.material3.ButtonDefaults.filledTonalButtonColors(containerColor = JulesAccent.copy(alpha = 0.3f))
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(20.dp), tint = Color.White)
                            Spacer(modifier = Modifier.size(8.dp))
                            Text("New conversation")
                        }
                        Spacer(modifier = Modifier.widthIn(8.dp))
                        FilterChip(
                            selected = hideCompleted,
                            onClick = { onHideCompletedChange(!hideCompleted) },
                            label = { Text("Hide completed", fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                labelColor = Color.White.copy(alpha = 0.7f),
                                selectedLabelColor = JulesAccent,
                                selectedContainerColor = JulesAccent.copy(alpha = 0.1f)
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = hideCompleted,
                                borderColor = Color.White.copy(alpha = 0.3f),
                                selectedBorderColor = JulesAccent
                            )
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

            if (loadingSessions && sessions.isEmpty()) {
                item {
                    Text("Loading conversations…", color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
                }
            } else if (displaySessions.isEmpty()) {
                item {
                    Text(
                        if (hideCompleted && sessions.isNotEmpty()) "All conversations are completed."
                        else "No conversations yet for this repository. Tap \"New conversation\" to start.",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 14.sp
                    )
                }
            } else {
                items(displaySessions, key = { it.id }) { session ->
                    SessionRow(
                        session = session,
                        onClick = { onOpenSession(session) },
                        onMerge = { onMergePr(session) },
                        onSolve = { onSolveConflicts(session) },
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
    onSolve: () -> Unit,
    onClose: () -> Unit,
    onArchive: () -> Unit,
    onDetails: () -> Unit
) {
    val progressStep = remember(session) {
        calculateProgressStep(session, emptyList())
    }

    val timeFormatter = remember { DateTimeFormatter.ofPattern("HH:mm") }
    val dateFormatter = remember { DateTimeFormatter.ofPattern("MMM dd") }
    val lastUpdated = remember(session.lastUpdated) {
        val dt = OffsetDateTime.ofInstant(Instant.ofEpochMilli(session.lastUpdated), java.time.ZoneId.systemDefault())
        if (Duration.between(dt.toInstant(), Instant.now()).toDays() < 1) {
            dt.format(timeFormatter)
        } else {
            dt.format(dateFormatter)
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(JulesAccent.copy(alpha = 0.1f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.History, contentDescription = null, tint = JulesAccent, modifier = Modifier.size(24.dp))
        }

        Spacer(modifier = Modifier.size(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = session.title.ifBlank { session.prompt.take(40) }.ifBlank { "Conversation" },
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = lastUpdated,
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 12.sp
                )
            }

            Spacer(modifier = Modifier.height(2.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                val hasOutput = !session.prUrl.isNullOrBlank()
                val (statusText, statusColor) = when {
                    session.prState == "merged" -> "Merged" to Color.Magenta
                    session.prState == "closed" -> "Closed" to Color.Red
                    session.prState == "open" -> "Open PR" to Color.Green
                    session.sessionState == "COMPLETED" -> "Completed" to Color.Green
                    session.sessionState == "FAILED" -> "Failed" to Color.Red
                    session.sessionState == "AWAITING_PLAN_APPROVAL" -> "Waiting for approval" to JulesAccent
                    session.sessionState == "AWAITING_USER_FEEDBACK" -> "Waiting for you" to JulesAccent
                    session.sessionState == "PLANNING" -> "Planning…" to JulesAccent
                    session.sessionState == "QUEUED" -> "Queued…" to Color.White.copy(alpha = 0.6f)
                    session.sessionState == "QUEUED_OFFLINE" -> "Queued (Offline)" to Color.White.copy(alpha = 0.5f)
                    session.sessionState == "PAUSED" -> "Paused" to Color.Yellow
                    else -> (if (hasOutput) "Output available" else "In progress") to (if (hasOutput) JulesAccent else Color.White.copy(alpha = 0.6f))
                }

                Box(modifier = Modifier.size(8.dp).background(statusColor, CircleShape))
                Spacer(modifier = Modifier.size(8.dp))

                Text(
                    text = session.prompt,
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                if (hasOutput && session.prState == "open") {
                    IconButton(onClick = onDetails, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Description, contentDescription = "Details", tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(16.dp))
                    }
                }

                IconButton(onClick = onArchive, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Archive, contentDescription = "Archive", tint = Color.White.copy(alpha = 0.4f), modifier = Modifier.size(16.dp))
                }
            }

            if (progressStep != null) {
                Spacer(modifier = Modifier.height(4.dp))
                MiniProgressBar(currentStep = progressStep)
            }
        }
    }
}
