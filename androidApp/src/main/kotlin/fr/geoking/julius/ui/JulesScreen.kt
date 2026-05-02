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
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Merge
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import androidx.compose.ui.platform.LocalUriHandler
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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import fr.geoking.julius.ui.components.JulesMessageContent
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

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
    val apiKeys = settings.julesKeys
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
    var showActivitiesSheet by remember { mutableStateOf(false) }
    var showConflictSheet by remember { mutableStateOf(false) }
    var conflictingFiles by remember { mutableStateOf<List<String>>(emptyList()) }
    var rawActivities by remember { mutableStateOf<List<JulesClient.JulesActivity>>(emptyList()) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val sessionsListState = rememberLazyListState()
    val uriHandler = LocalUriHandler.current

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

    fun loadSources(isRefresh: Boolean = false) {
        if (apiKeys.isEmpty()) {
            sourcesLoaded = true
            return
        }
        scope.launch {
            if (isRefresh) refreshing = true else loading = true
            clearError()
            try {
                val allSources = mutableMapOf<String, JulesClient.JulesSource>()
                coroutineScope {
                    apiKeys.map { key ->
                        async {
                            try {
                                val resp = julesClient.listSources(key)
                                synchronized(allSources) {
                                    resp.sources.forEach { src ->
                                        allSources[src.name] = src
                                    }
                                }
                            } catch (e: Exception) {
                                android.util.Log.e("JulesScreen", "Failed to load sources for a key", e)
                            }
                        }
                    }.awaitAll()
                }
                sources = allSources.values.toList()
                sourcesLoaded = true

                selectedSourceName?.let { id ->
                    val found = sources.find { it.name == id }
                    if (found != null) {
                        selectedSourceDisplayName = found.githubRepo?.let { "${it.owner}/${it.repo}" } ?: found.name
                    }
                }
            } catch (e: Exception) {
                sourcesLoaded = true
                error = "Could not load repositories: ${e.message ?: "Unknown error"}"
            } finally {
                loading = false
                refreshing = false
            }
        }
    }

    fun loadSessions(isRefresh: Boolean = false) {
        val sourceName = selectedSourceName ?: return
        if (apiKeys.isEmpty()) return
        scope.launch {
            if (isRefresh) refreshingSessions = true else loadingSessions = true
            clearError()
            try {
                quota = julesRepository.getUsageQuota(apiKeys)
                julesRepository.getSessions(apiKeys, sourceName, githubToken).collectLatest { list ->
                    sessions = list
                    currentSession?.let { curr ->
                        val updated = list.find { it.id == curr.id }
                        if (updated != null) {
                            currentSession = updated
                        }
                    }
                }
            } catch (e: Exception) {
                error = "Could not load sessions: ${e.message ?: "Unknown error"}"
            } finally {
                loadingSessions = false
                refreshingSessions = false
            }
        }
    }

    suspend fun refreshActivitiesInternal(isRefresh: Boolean = false) {
        val session = currentSession ?: return
        if (apiKeys.isEmpty()) return
        if (isRefresh) refreshing = true else loading = true
        clearError()
        try {
            julesRepository.getActivities(session.id).collectLatest { list ->
                if (chatItems.isEmpty() || isRefresh) {
                    chatItems.clear()
                    chatItems.addAll(list)
                } else {
                    val existingIds = chatItems.map {
                        when (it) {
                            is JulesChatItem.UserMessage -> it.id
                            is JulesChatItem.AgentMessage -> it.id
                        }
                    }.toSet()
                    val newItems = list.filter {
                        val id = when (it) {
                            is JulesChatItem.UserMessage -> it.id
                            is JulesChatItem.AgentMessage -> it.id
                        }
                        !existingIds.contains(id)
                    }
                    if (newItems.isNotEmpty()) {
                        chatItems.addAll(newItems)
                    }
                }
                if (chatItems.isNotEmpty()) {
                    try {
                        listState.animateScrollToItem(chatItems.size - 1)
                    } catch (e: Exception) {
                        // Ignore scroll errors
                    }
                }
            }
        } catch (e: Exception) {
            error = "Could not load activities: ${e.message ?: "Unknown error"}"
        } finally {
            loading = false
            refreshing = false
        }
    }

    fun refreshActivities(isRefresh: Boolean = false) {
        scope.launch { refreshActivitiesInternal(isRefresh) }
    }

    LaunchedEffect(apiKeys) {
        if (apiKeys.isNotEmpty()) loadSources()
        else sourcesLoaded = true
    }

    // If the user previously selected a repo, jump directly into it.
    LaunchedEffect(sourcesLoaded, sources, settings.lastJulesRepoId) {
        if (!sourcesLoaded) return@LaunchedEffect
        if (selectedSourceName != null) return@LaunchedEffect
        val lastId = settings.lastJulesRepoId.trim()
        if (lastId.isEmpty()) return@LaunchedEffect
        if (sources.any { it.name == lastId }) {
            selectedSourceName = lastId
        }
    }

    LaunchedEffect(selectedSourceName) {
        if (selectedSourceName != null && apiKeys.isNotEmpty()) {
            loadSessions()
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
    LaunchedEffect(apiKeys, githubToken, selectedSourceName, currentSession?.id) {
        if (apiKeys.isEmpty() || selectedSourceName == null) return@LaunchedEffect
        while (true) {
            delay(30_000)
            val sessionToPoll = currentSession
            if (sessionToPoll != null) {
                julesRepository.pollSessionStatus(sessionToPoll.id, githubToken)
                loadSessions()
            } else {
                val inProgress = sessions.filter { it.prState == null && it.prUrl == null }
                if (inProgress.isNotEmpty()) {
                    for (s in inProgress) {
                        julesRepository.pollSessionStatus(s.id, githubToken)
                    }
                    loadSessions()
                } else {
                    loadSessions()
                }
            }
        }
    }


    LaunchedEffect(currentSession?.id) {
        if (currentSession != null) {
            refreshActivitiesInternal()
        } else {
            chatItems.clear()
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = ColorHelper.JulesBg
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = handleBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = when {
                            currentSession != null -> currentSession?.title ?: ""
                            selectedSourceName != null -> selectedSourceDisplayName
                            else -> "Repositories"
                        },
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val session = currentSession
                    if (session != null) {
                        val hasOutput = !session.prUrl.isNullOrBlank()
                        val (statusText, statusColor) = when {
                            session.prState == "merged" -> "Merged" to Color.Green
                            session.prState == "closed" -> "Closed" to Color.Red
                            session.sessionState == "COMPLETED" -> "Completed" to Color.Green
                            session.sessionState == "FAILED" -> "Failed" to Color.Red
                            session.sessionState == "AWAITING_PLAN_APPROVAL" -> "Waiting for approval" to ColorHelper.JulesAccent
                            session.sessionState == "PLANNING" -> "Planning…" to ColorHelper.JulesAccent
                            else -> (if (hasOutput) "Output available" else "In progress") to (if (hasOutput) ColorHelper.JulesAccent else Color.White.copy(alpha = 0.6f))
                        }
                        val mergeabilityText = if (session.prState == "open" && session.prMergeable == false) " (Conflicts)" else ""
                        val prIdText = if (!session.prId.isNullOrBlank()) " #${session.prId}" else ""

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(6.dp).background(statusColor, RoundedCornerShape(3.dp)))
                            Spacer(modifier = Modifier.size(6.dp))
                            Text(
                                text = "$statusText$prIdText$mergeabilityText",
                                color = statusColor,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
                val sess = currentSession
                if (sess != null || selectedSourceName != null) {
                    // Allow switching repository from sessions or conversation.
                    IconButton(onClick = {
                        currentSession = null
                        selectedSourceName = null
                    }) {
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Switch repository", tint = Color.White)
                    }
                }
                if (sess != null) {
                    if (!sess.url.isNullOrBlank()) {
                        IconButton(onClick = {
                            sess.url?.let { uriHandler.openUri(it) }
                        }) {
                            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "Open in web", tint = Color.White)
                        }
                    }
                    IconButton(onClick = {
                        scope.launch {
                            loading = true
                            try {
                                val json = Json { ignoreUnknownKeys = true }
                                val cached = julesRepository.getActivitiesBySession(sess.id)
                                val activities = cached.mapNotNull {
                                    it.activityJson?.let { aj -> json.decodeFromString(JulesClient.JulesActivity.serializer(), aj) }
                                }

                                if (activities.isNotEmpty()) {
                                    rawActivities = activities
                                    showActivitiesSheet = true
                                } else {
                                    val key = sess.apiKey ?: apiKeys.firstOrNull()
                                    if (key != null) {
                                        val resp = julesClient.listActivities(key, sess.id)
                                        rawActivities = resp.activities
                                        showActivitiesSheet = true
                                    }
                                }
                            } catch (e: Exception) {
                                error = "Could not load activities: ${e.message ?: "Unknown error"}"
                            } finally {
                                loading = false
                            }
                        }
                    }) {
                        Icon(Icons.Default.History, contentDescription = "Activities", tint = Color.White)
                    }
                }
            }


            when {
                apiKeys.isEmpty() -> {
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

                    val activeSession = currentSession
                    if (activeSession != null) {
                        InConversationContent(
                            currentSession = activeSession,
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
                                        julesRepository.sendMessage(activeSession.id, prompt)
                                        refreshActivitiesInternal()
                                    } catch (e: Exception) {
                                        error = e.message ?: "Failed to send"
                                    } finally {
                                        loading = false
                                    }
                                }
                            },
                            loading = loading,
                            onMergePr = {
                                val prUrl = activeSession.prUrl ?: return@InConversationContent
                                scope.launch {
                                    loading = true
                                    val res = julesRepository.mergePr(githubToken, prUrl)
                                    if (res.isFailure) error = "Merge failed: ${res.exceptionOrNull()?.message}"
                                    else loadSessions()
                                    loading = false
                                }
                            },
                            onSolveConflicts = {
                                val prUrl = activeSession.prUrl ?: return@InConversationContent
                                scope.launch {
                                    loading = true
                                    val res = julesRepository.getConflictingFiles(githubToken, prUrl)
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
                                        julesRepository.sendMessage(activeSession.id, "@jules resolve the conflicts in this PR")
                                        refreshActivities()
                                    } catch (e: Exception) {
                                        error = "Auto solve failed: ${e.message ?: "Unknown error"}"
                                    } finally {
                                        loading = false
                                    }
                                }
                            },
                            isRefreshing = refreshing,
                            onRefresh = { refreshActivities(isRefresh = true) }
                        )
                    } else if (selectedSourceName != null) {
                        RepoAndSessionsContent(
                            apiKeys = apiKeys,
                            isOnline = isOnline,
                            sessions = sessions,
                            selectedSourceName = selectedSourceName ?: "",
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
                                        val sessionId = julesRepository.createSession(
                                            apiKeys = apiKeys,
                                            prompt = newSessionPrompt,
                                            source = source,
                                            title = newSessionPrompt.take(80)
                                        )
                                        val entity = julesRepository.getSession(sessionId)
                                        newSessionPrompt = ""
                                        currentSession = entity
                                        loadSessions()
                                    } catch (e: Exception) {
                                        error = "Failed: ${e.message}"
                                    }
                                    loading = false
                                }
                            },
                            onOpenSession = { currentSession = it },
                            onMergePr = { session ->
                                scope.launch {
                                    loading = true
                                    val res = julesRepository.mergePr(githubToken, session.prUrl!!)
                                    if (res.isFailure) error = "Merge failed"
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
                                    }
                                    loading = false
                                }
                            },
                            onClosePr = { session ->
                                scope.launch {
                                    loading = true
                                    julesRepository.closePr(githubToken, session.prUrl!!)
                                    loadSessions()
                                    loading = false
                                }
                            },
                            onGetPrDetails = { session, onResult ->
                                scope.launch {
                                    val res = julesRepository.getPrDetails(githubToken, session.prUrl!!)
                                    if (res.isSuccess) onResult(res.getOrNull())
                                }
                            },
                            onArchive = { session ->
                                scope.launch {
                                    julesRepository.archiveSession(session.id)
                                    loadSessions()
                                }
                            },
                            onArchiveCompleted = {
                                scope.launch {
                                    julesRepository.archiveCompletedSessions(selectedSourceName ?: "")
                                    loadSessions()
                                }
                            },
                            isRefreshingSessions = refreshingSessions,
                            onRefreshSessions = { loadSessions(isRefresh = true) },
                            hideCompleted = hideCompleted,
                            onHideCompletedChange = { hideCompleted = it }
                        )
                    } else {
                        RepositoriesListContent(
                            sources = sources,
                            onSelect = { src ->
                                selectedSourceName = src.name
                                selectedSourceDisplayName = src.githubRepo?.let { "${it.owner}/${it.repo}" } ?: src.name
                            },
                            loading = loading && sources.isEmpty(),
                            refreshing = refreshing,
                            onRefresh = { loadSources(isRefresh = true) }
                        )
                    }
                }
            }
        }

        if (showActivitiesSheet) {
            ModalBottomSheet(
                onDismissRequest = { showActivitiesSheet = false },
                sheetState = rememberModalBottomSheetState(),
                containerColor = ColorHelper.JulesListBg
            ) {
                ActivitiesSheet(rawActivities)
            }
        }

        val conflictSession = currentSession
        if (showConflictSheet && conflictSession != null) {
            ModalBottomSheet(
                onDismissRequest = { showConflictSheet = false },
                containerColor = ColorHelper.JulesListBg,
                modifier = Modifier.fillMaxSize()
            ) {
                ConflictResolutionSheet(
                    session = conflictSession,
                    files = conflictingFiles,
                    githubToken = githubToken,
                    julesRepository = julesRepository,
                    onDismiss = { showConflictSheet = false }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RepositoriesListContent(
    sources: List<JulesClient.JulesSource>,
    onSelect: (JulesClient.JulesSource) -> Unit,
    loading: Boolean,
    refreshing: Boolean,
    onRefresh: () -> Unit
) {
    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize()
    ) {
        if (loading && sources.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = ColorHelper.JulesAccent)
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                item {
                    Text(
                        "Repositories",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(16.dp, 8.dp)
                    )
                }
                items(sources) { source ->
                    val displayName = source.githubRepo?.let { "${it.owner}/${it.repo}" } ?: source.name
                    androidx.compose.material3.ListItem(
                        headlineContent = { Text(displayName) },
                        trailingContent = {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                        },
                        modifier = Modifier.clickable { onSelect(source) },
                        colors = androidx.compose.material3.ListItemDefaults.colors(
                            containerColor = Color.Transparent,
                            headlineColor = Color.White,
                            trailingIconColor = Color.White.copy(alpha = 0.3f)
                        )
                    )
                    HorizontalDivider(color = Color.White.copy(alpha = 0.05f))
                }
            }
        }
    }
}

@Composable
private fun PRDetailsDialog(pr: GitHubClient.GitHubPullRequestDetail, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ColorHelper.JulesListBg,
        title = { Text(pr.title, color = Color.White) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(pr.body ?: "No description.", color = Color.White.copy(alpha = 0.8f))
            }
        },
        confirmButton = {
            OutlinedButton(onClick = onDismiss) { Text("Close") }
        }
    )
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
        Text(
            "Resolve Conflicts",
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )

        error?.let {
            ErrorCard(title = "Error", message = it, onDismiss = { error = null })
        }

        if (loading) {
            Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = ColorHelper.JulesAccent)
            }
        } else if (selectedFile == null) {
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
                                        error = "Failed to load file"
                                    }
                                    loading = false
                                }
                            }
                            .padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = ColorHelper.JulesHeaderBg)
                    ) {
                        Text(file, color = Color.White, modifier = Modifier.padding(16.dp))
                    }
                }
            }
        } else {
            val file = selectedFile ?: ""
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { selectedFile = null }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Text(file, color = ColorHelper.JulesAccent, fontWeight = FontWeight.Bold)
            }

            if (conflicts.isNotEmpty()) {
                val conflict = conflicts.getOrNull(currentConflictIndex)
                if (conflict != null) {
                    Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                        ConflictBlock(
                            title = "MINE",
                            content = conflict.mine,
                            color = ColorHelper.JulesAccent,
                            onSelect = {
                                fileContent = fileContent.replace(conflict.fullMatch, conflict.mine + "\n")
                                conflicts = julesRepository.parseConflicts(fileContent)
                            }
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        ConflictBlock(
                            title = "INCOMING",
                            content = conflict.incoming,
                            color = Color.Green,
                            onSelect = {
                                fileContent = fileContent.replace(conflict.fullMatch, conflict.incoming + "\n")
                                conflicts = julesRepository.parseConflicts(fileContent)
                            }
                        )
                    }
                }
            } else {
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text("Resolved!", color = Color.Green)
                }
                val fileToSave = selectedFile
                val prUrl = session.prUrl
                if (fileToSave != null && prUrl != null) {
                    FilledTonalButton(
                        onClick = {
                            scope.launch {
                                loading = true
                                val res = julesRepository.saveResolvedFile(githubToken, prUrl, fileToSave, fileContent, fileSha)
                                if (res.isSuccess) {
                                    selectedFile = null
                                } else {
                                    error = "Failed to save"
                                }
                                loading = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Save Resolved File", color = Color.Green)
                    }
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
            colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.3f))
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
    LazyColumn(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        item {
            Text(
                "Activities",
                color = Color.White,
                fontSize = 20.sp,
                modifier = Modifier.padding(bottom = 16.dp),
                fontWeight = FontWeight.Bold
            )
        }
        items(activities.sortedByDescending { it.createTime }) { activity ->
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Row {
                    Text(
                        text = activity.originator,
                        color = if (activity.originator == "user") ColorHelper.JulesAccent else Color.Green,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = activity.createTime.take(16),
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 10.sp
                    )
                }
                Text(
                    text = activity.description ?: "Activity",
                    color = Color.White,
                    fontSize = 14.sp
                )
                activity.artifacts?.forEach { artifact ->
                    artifact.bashOutput?.let { bash ->
                        Text("> ${bash.command}", color = Color.Green, fontSize = 11.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(top = 8.dp), color = Color.White.copy(alpha = 0.1f))
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
    voiceManager: VoiceManager,
    onSend: () -> Unit,
    loading: Boolean,
    onMergePr: () -> Unit,
    onSolveConflicts: () -> Unit,
    onAutoSolveConflicts: () -> Unit,
    isRefreshing: Boolean,
    onRefresh: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        val progressStep = remember(currentSession, chatItems.size) {
            calculateProgressStep(currentSession, chatItems)
        }
        MiniProgressBar(currentStep = progressStep)

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
            items(chatItems, key = { it.id }) { item ->
                val isUser = item is JulesChatItem.UserMessage
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
                ) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = if (isUser) ColorHelper.JulesCardUser else ColorHelper.JulesCardAgent),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        JulesMessageContent(
                            item = item,
                            baseFontSize = 14,
                            onSpeak = { voiceManager.speak(if (item is JulesChatItem.UserMessage) item.text else (item as JulesChatItem.AgentMessage).text) }
                        )
                    }
                }
            }

            if (currentSession.prState == "open") {
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (currentSession.prMergeable == true) {
                            FilledTonalButton(onClick = onMergePr) {
                                Text("Merge Pull Request", color = Color.Green)
                            }
                        } else if (currentSession.prMergeable == false) {
                            Text("This PR has merge conflicts.", color = Color.Red)
                            Row {
                                FilledTonalButton(onClick = onAutoSolveConflicts) { Text("Auto solve") }
                                Spacer(modifier = Modifier.size(8.dp))
                                FilledTonalButton(onClick = onSolveConflicts) { Text("Solve manually") }
                            }
                        }
                    }
                }
            }
        }
    }
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = inputText,
                onValueChange = onInputChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Message Jules…") }
            )
            IconButton(onClick = onSend, enabled = inputText.isNotBlank() && !loading) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = ColorHelper.JulesAccent)
            }
        }
    }
}

@Composable
private fun StatusBadge(session: JulesSessionEntity) {
    val (statusText, statusColor) = when {
        session.prState == "merged" -> "Merged" to Color.Green
        session.prState == "closed" -> "Closed" to Color.Red
        session.sessionState == "COMPLETED" -> "Completed" to Color.Green
        session.sessionState == "FAILED" -> "Failed" to Color.Red
        session.sessionState == "AWAITING_PLAN_APPROVAL" -> "Wait Approval" to ColorHelper.JulesAccent
        session.sessionState == "PLANNING" -> "Planning" to ColorHelper.JulesAccent
        else -> (if (!session.prUrl.isNullOrBlank()) "Output" else "Active") to (if (!session.prUrl.isNullOrBlank()) ColorHelper.JulesAccent else Color.White.copy(alpha = 0.6f))
    }

    Surface(
        color = statusColor.copy(alpha = 0.1f),
        shape = RoundedCornerShape(4.dp),
        border = BorderStroke(1.dp, statusColor.copy(alpha = 0.5f))
    ) {
        Text(
            text = statusText.uppercase(),
            color = statusColor,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RepoAndSessionsContent(
    apiKeys: List<String>,
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
    onArchiveCompleted: () -> Unit,
    isRefreshingSessions: Boolean,
    onRefreshSessions: () -> Unit,
    hideCompleted: Boolean,
    onHideCompletedChange: (Boolean) -> Unit
) {
    var showPrDetails by remember { mutableStateOf<GitHubClient.GitHubPullRequestDetail?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    val displaySessions = sessions.filter {
        val matchesSearch = it.title.contains(searchQuery, ignoreCase = true) || it.prompt.contains(searchQuery, ignoreCase = true)
        val matchesHideCompleted = !hideCompleted || !it.isFinished
        matchesSearch && matchesHideCompleted
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (selectedSourceName.isNotBlank()) {
            Column(modifier = Modifier.padding(16.dp)) {
                OutlinedTextField(
                    value = newSessionPrompt,
                    onValueChange = onNewSessionPromptChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("What should Jules do?") },
                    trailingIcon = {
                        IconButton(onClick = onCreateSession, enabled = newSessionPrompt.isNotBlank() && !loading) {
                            Icon(Icons.Default.Add, contentDescription = "Create")
                        }
                    }
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search conversations…") },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null, tint = Color.White.copy(alpha = 0.5f))
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear", tint = Color.White.copy(alpha = 0.5f))
                            }
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = ColorHelper.JulesAccent,
                        focusedBorderColor = ColorHelper.JulesAccent.copy(alpha = 0.5f),
                        unfocusedBorderColor = Color.White.copy(alpha = 0.1f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        }

        PullToRefreshBox(
            isRefreshing = isRefreshingSessions,
            onRefresh = onRefreshSessions,
            modifier = Modifier.weight(1f)
        ) {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                item {
                    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Conversations", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            androidx.compose.material3.TextButton(onClick = { onHideCompletedChange(!hideCompleted) }) {
                                Text(if (hideCompleted) "Show all" else "Hide completed")
                            }
                        }
                        if (sessions.any { it.isFinished }) {
                            androidx.compose.material3.TextButton(
                                onClick = onArchiveCompleted,
                                modifier = Modifier.align(Alignment.End)
                            ) {
                                Icon(Icons.Default.Archive, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.size(4.dp))
                                Text("Archive all completed", fontSize = 12.sp)
                            }
                        }
                    }
                }
                items(displaySessions, key = { it.id }) { session ->
                    androidx.compose.material3.ListItem(
                        headlineContent = { Text(session.title.ifBlank { session.prompt }, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        supportingContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                StatusBadge(session)
                                Spacer(modifier = Modifier.size(8.dp))
                                Text(session.sessionState ?: "In progress", fontSize = 12.sp)
                            }
                        },
                        trailingContent = {
                            Row {
                                if (session.prUrl != null) {
                                    IconButton(onClick = { onGetPrDetails(session) { showPrDetails = it } }) {
                                        Icon(Icons.Default.Description, contentDescription = "Details")
                                    }
                                }
                                IconButton(onClick = { onArchive(session) }) {
                                    Icon(Icons.Default.Archive, contentDescription = "Archive")
                                }
                            }
                        },
                        modifier = Modifier.clickable { onOpenSession(session) },
                        colors = androidx.compose.material3.ListItemDefaults.colors(
                            containerColor = Color.Transparent,
                            headlineColor = Color.White,
                            supportingColor = Color.White.copy(alpha = 0.6f)
                        )
                    )
                    HorizontalDivider(color = Color.White.copy(alpha = 0.05f))
                }
            }
        }
    }

    val pr = showPrDetails
    if (pr != null) {
        PRDetailsDialog(pr = pr, onDismiss = { showPrDetails = null })
    }
}


@Composable
private fun MiniProgressBar(currentStep: Int?) {
    if (currentStep == null) return
    LinearProgressIndicator(
        progress = { currentStep.toFloat() / 5f },
        modifier = Modifier.fillMaxWidth().height(2.dp),
        color = ColorHelper.JulesAccent,
        trackColor = Color.Transparent
    )
}

private fun calculateProgressStep(session: JulesSessionEntity, items: List<JulesChatItem>): Int? {
    return when {
        session.prState == "merged" -> 5
        session.prState == "open" -> 4
        session.sessionState == "COMPLETED" -> 4
        session.sessionState == "PLANNING" -> 1
        else -> null
    }
}

@Composable
private fun ErrorCard(title: String, message: String, onDismiss: (() -> Unit)? = null) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = ColorHelper.JulesErrorBg)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row {
                Text(title, color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                if (onDismiss != null) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = Color.White)
                    }
                }
            }
            Text(message, color = Color.White.copy(alpha = 0.8f))
        }
    }
}
