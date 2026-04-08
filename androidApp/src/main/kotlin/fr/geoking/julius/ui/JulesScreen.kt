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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Merge
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
    var error by remember { mutableStateOf<String?>(null) }
    var inputText by remember { mutableStateOf("") }
    var newSessionPrompt by remember { mutableStateOf("") }
    var selectedSourceName by remember { mutableStateOf(settings.lastJulesRepoId.takeIf { it.isNotBlank() }) }
    var selectedSourceDisplayName by remember { mutableStateOf(settings.lastJulesRepoName.takeIf { it.isNotBlank() } ?: "Select repository") }
    var sourcesLoaded by remember { mutableStateOf(false) }
    var showRepoSheet by remember { mutableStateOf(false) }
    var showActivitiesSheet by remember { mutableStateOf(false) }
    var rawActivities by remember { mutableStateOf<List<JulesClient.JulesActivity>>(emptyList()) }
    val sheetState = rememberModalBottomSheetState()
    val activitiesSheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val sessionsListState = rememberLazyListState()

    val handleBack = {
        if (currentSession != null) {
            currentSession = null
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
                        list.find { it.id == curr.id }?.let { currentSession = it }
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
                chatItems.clear()
                chatItems.addAll(list)
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
                        text = if (currentSession != null) {
                            currentSession!!.title.ifBlank { currentSession!!.prompt.take(40) }.ifBlank { "Conversation" }
                        } else "Jules",
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
                                        // Update lastUpdated locally/in DB when sending message
                                        julesRepository.updateSessionLastUpdated(currentSession!!.id, System.currentTimeMillis())

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
                                        // Immediately get session to have the initial state (as createSession might have it as null or QUEUED)
                                        val updatedSession = try {
                                            julesClient.getSession(apiKey, session.id)
                                        } catch (e: Exception) {
                                            session
                                        }

                                        val entity = JulesSessionEntity(
                                            id = updatedSession.id,
                                            title = updatedSession.title,
                                            prompt = updatedSession.prompt,
                                            sourceName = source,
                                            prUrl = updatedSession.outputs?.firstOrNull()?.pullRequest?.url,
                                            prTitle = updatedSession.outputs?.firstOrNull()?.pullRequest?.title,
                                            prState = null,
                                            prMergeable = null,
                                            sessionState = updatedSession.state,
                                            isArchived = false,
                                            lastUpdated = System.currentTimeMillis()
                                        )
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
                            onRefreshSessions = { loadSessions(isRefresh = true) }
                        )
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
private fun MessageText(text: String, baseFontSize: Int) {
    var expanded by remember { mutableStateOf(false) }
    val isCodeReviewed = text.startsWith("Code reviewed", ignoreCase = true)
    val isPlan = text.startsWith("**Plan", ignoreCase = true)

    Column {
        if (isCodeReviewed) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    if (expanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowRight,
                    contentDescription = null,
                    tint = JulesAccent,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.size(8.dp))
                Text(
                    text = "Code reviewed",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = baseFontSize.sp
                )
            }
        }

        if (isPlan) {
            val lines = text.split("\n")
            lines.forEach { line ->
                if (line.startsWith("**Plan")) {
                    Text(
                        text = "Plan",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = (baseFontSize + 2).sp,
                        modifier = Modifier.padding(12.dp)
                    )
                } else {
                    val stepMatch = Regex("^\\d+\\.\\s+(.*)").find(line)
                    if (stepMatch != null) {
                        val content = stepMatch.groupValues[1]
                        val parts = content.split(". ", limit = 2)
                        val title = parts[0]
                        val description = if (parts.size > 1) parts[1] else ""
                        var stepExpanded by remember { mutableStateOf(false) }

                        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = description.isNotBlank()) { stepExpanded = !stepExpanded },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(JulesAccent, RoundedCornerShape(4.dp))
                                )
                                Spacer(modifier = Modifier.size(12.dp))
                                Text(
                                    text = title,
                                    color = Color.White,
                                    fontSize = baseFontSize.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.weight(1f)
                                )
                                if (description.isNotBlank()) {
                                    Icon(
                                        if (stepExpanded) Icons.Default.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                        contentDescription = null,
                                        tint = Color.White.copy(alpha = 0.5f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                            if (stepExpanded && description.isNotBlank()) {
                                Text(
                                    text = description,
                                    color = Color.White.copy(alpha = 0.6f),
                                    fontSize = (baseFontSize - 1).sp,
                                    modifier = Modifier.padding(start = 20.dp, top = 4.dp, bottom = 8.dp)
                                )
                            }
                        }
                    }
                }
            }
        } else if (!isCodeReviewed || expanded) {
            val sentences = text.split(Regex("(?<=[.!?])\\s+"))
            val annotatedString = buildAnnotatedString {
                sentences.forEachIndexed { index, sentence ->
                    val isFirst = index == 0 && !isCodeReviewed
                    val style = if (isFirst) {
                        SpanStyle(color = Color.White, fontWeight = FontWeight.Bold)
                    } else {
                        SpanStyle(color = Color.White.copy(alpha = 0.6f))
                    }

                    withStyle(style) {
                        // Apply Markdown-like bolding for **text**
                        val parts = sentence.split("**")
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
                    if (index < sentences.size - 1) append(" ")
                }
            }
            Text(
                text = annotatedString,
                modifier = Modifier.padding(12.dp),
                fontSize = baseFontSize.sp
            )
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
    isRefreshing: Boolean,
    onRefresh: () -> Unit
) {
    val uriHandler = LocalUriHandler.current
    val progressStep = remember(currentSession, chatItems.size) {
        calculateProgressStep(currentSession, chatItems)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        MiniProgressBar(currentStep = progressStep)

        // PR Status Bar
        if (currentSession.prUrl != null || currentSession.sessionState != null) {
            val (statusText, statusColor) = when {
                currentSession.prState == "merged" -> "Merged" to Color.Magenta
                currentSession.prState == "closed" -> "Closed" to Color.Red
                currentSession.prState == "open" -> "Open PR" to Color.Green
                currentSession.sessionState == "COMPLETED" -> "Completed" to Color.Green
                currentSession.sessionState == "FAILED" -> "Failed" to Color.Red
                currentSession.sessionState == "AWAITING_PLAN_APPROVAL" -> "Waiting for approval" to JulesAccent
                currentSession.sessionState == "AWAITING_USER_FEEDBACK" -> "Waiting for you" to JulesAccent
                currentSession.sessionState == "PLANNING" -> "Planning…" to JulesAccent
                currentSession.sessionState == "QUEUED" -> "Queued…" to Color.White.copy(alpha = 0.6f)
                currentSession.sessionState == "PAUSED" -> "Paused" to Color.Yellow
                else -> (if (!currentSession.prUrl.isNullOrBlank()) "Output available" else "In progress") to (if (!currentSession.prUrl.isNullOrBlank()) JulesAccent else Color.White.copy(alpha = 0.6f))
            }

            val mergeabilityText = when (currentSession.prMergeable) {
                true -> " • Ready to merge"
                false -> " • Conflicts"
                else -> ""
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                colors = CardDefaults.cardColors(containerColor = JulesHeaderBg),
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = currentSession.prTitle ?: "Pull Request",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
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

                    if (!currentSession.prUrl.isNullOrBlank()) {
                        IconButton(onClick = { uriHandler.openUri(currentSession.prUrl) }) {
                            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "Open PR", tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(20.dp))
                        }
                    }

                    if (currentSession.prState == "open" && currentSession.prMergeable == true) {
                        FilledTonalButton(
                            onClick = onMergePr,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp),
                            colors = androidx.compose.material3.ButtonDefaults.filledTonalButtonColors(containerColor = Color.Green.copy(alpha = 0.2f))
                        ) {
                            Icon(Icons.Default.Merge, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.Green)
                            Spacer(modifier = Modifier.size(4.dp))
                            Text("Merge", color = Color.Green, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

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
                            MessageText(text = item.text, baseFontSize = 15)
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
                            MessageText(text = item.text, baseFontSize = 14)
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
    onArchive: (JulesSessionEntity) -> Unit,
    quota: JulesQuota? = null,
    isRefreshingSessions: Boolean,
    onRefreshSessions: () -> Unit
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
                                    enabled = newSessionPrompt.isNotBlank() && !loading && (quota == null || quota.used < quota.limit)
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
    val progressStep = remember(session) {
        calculateProgressStep(session, emptyList())
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = JulesListBg),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            MiniProgressBar(currentStep = progressStep)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = session.title.ifBlank { session.prompt.take(60) }.ifBlank { "Conversation" },
                color = Color.White,
                fontSize = 16.sp
            )
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
                session.sessionState == "PAUSED" -> "Paused" to Color.Yellow
                else -> (if (hasOutput) "Output available" else "In progress") to (if (hasOutput) JulesAccent else Color.White.copy(alpha = 0.6f))
            }
            val mergeabilityText = if (session.prState == "open" && session.prMergeable == false) " (Conflicts)" else ""

            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(6.dp).background(statusColor, RoundedCornerShape(3.dp)))
                Spacer(modifier = Modifier.size(6.dp))
                Text(
                    text = "$statusText$mergeabilityText",
                    color = statusColor,
                    fontSize = 12.sp
                )
                if (session.prompt.isNotBlank() && session.title != session.prompt) {
                    Text(
                        text = " • " + session.prompt.take(60) + if (session.prompt.length > 60) "…" else "",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 12.sp,
                        maxLines = 1
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(modifier = Modifier.weight(1f))

                IconButton(onClick = onArchive, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Archive, contentDescription = "Archive", tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(16.dp))
                }

                if (hasOutput && session.prState == "open") {
                    IconButton(onClick = onDetails, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Description, contentDescription = "Details", tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                    if (session.prMergeable == true) {
                        Spacer(modifier = Modifier.size(4.dp))
                        IconButton(onClick = onMerge, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Merge, contentDescription = "Merge", tint = Color.Green, modifier = Modifier.size(16.dp))
                        }
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
