package fr.geoking.julius.ui.jules

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import fr.geoking.julius.SettingsManager
import fr.geoking.julius.api.codingagent.CodingAgentBackend
import fr.geoking.julius.api.jules.JulesChatItem
import fr.geoking.julius.api.jules.JulesClient
import fr.geoking.julius.persistence.JulesSessionEntity
import fr.geoking.julius.repository.FeatureRepository
import fr.geoking.julius.repository.GitHubBuildRepository
import fr.geoking.julius.repository.JulesRepository
import fr.geoking.julius.shared.voice.VoiceManager
import fr.geoking.julius.ui.ColorHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JulesScreen(
    onBack: () -> Unit,
    julesClient: JulesClient,
    julesRepository: JulesRepository,
    featureRepository: FeatureRepository,
    settingsManager: SettingsManager,
    voiceManager: VoiceManager,
    buildRepository: GitHubBuildRepository,
    initialSession: JulesSessionEntity? = null,
) {
    val settings by settingsManager.settings.collectAsState()
    val networkStatus by julesRepository.getNetworkService().status.collectAsState()
    val apiKeys = settings.julesKeys
    val githubToken = settings.githubApiKey
    val codingBackend = settings.codingAgentBackend
    val isAgentConfigured = julesRepository.isCodingAgentConfigured(apiKeys)

    var sources by remember { mutableStateOf<List<JulesClient.JulesSource>>(emptyList()) }
    var sessions by remember { mutableStateOf<List<JulesSessionEntity>>(emptyList()) }
    val features by featureRepository.getAllFeatures().collectAsState(initial = emptyList())
    var currentSession by remember { mutableStateOf<JulesSessionEntity?>(initialSession) }
    val chatItems = remember { mutableStateListOf<JulesChatItem>() }
    var loading by remember { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }
    var loadingSessions by remember { mutableStateOf(false) }
    var refreshingSessions by remember { mutableStateOf(false) }
    val archivingSessionIds = remember { mutableStateListOf<String>() }
    var hideCompleted by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var inputText by remember { mutableStateOf("") }
    var newSessionPrompt by remember { mutableStateOf("") }
    var selectedSourceName by remember { mutableStateOf<String?>(null) }
    var selectedSourceDisplayName by remember { mutableStateOf("Select project") }
    var screenLevel by remember { mutableStateOf(JulesScreenLevel.Projects) }
    var selectedFeatureId by remember { mutableStateOf<String?>(null) }
    var selectedFeatureTitle by remember { mutableStateOf("") }
    var sourcesLoaded by remember { mutableStateOf(false) }
    var showActivitiesSheet by remember { mutableStateOf(false) }
    var showConflictSheet by remember { mutableStateOf(false) }
    var conflictingFiles by remember { mutableStateOf<List<String>>(emptyList()) }
    var rawActivities by remember { mutableStateOf<List<JulesClient.JulesActivity>>(emptyList()) }
    var activitiesJson by remember { mutableStateOf("") }

    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val uriHandler = LocalUriHandler.current

    fun clearError() { error = null }

    fun sourceDisplayName(source: JulesClient.JulesSource): String {
        return source.githubRepo?.let { "${it.owner}/${it.repo}" } ?: source.name
    }

    fun loadSources(isRefresh: Boolean = false) {
        if (!isAgentConfigured) {
            sourcesLoaded = true
            return
        }
        scope.launch {
            if (isRefresh) refreshing = true else loading = true
            clearError()
            try {
                julesRepository.getSources(apiKeys).collectLatest { list ->
                    sources = list
                    sourcesLoaded = true
                    selectedSourceName?.let { id ->
                        sources.find { it.name == id }?.let { found ->
                            selectedSourceDisplayName = sourceDisplayName(found)
                        }
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
        if (!isAgentConfigured) return
        scope.launch {
            if (isRefresh) refreshingSessions = true else loadingSessions = true
            clearError()
            try {
                julesRepository.getUsageQuota(apiKeys)
                julesRepository.getSessions(apiKeys, sourceName, githubToken).collectLatest { list ->
                    sessions = list
                    currentSession?.let { curr ->
                        list.find { it.id == curr.id }?.let { updated -> currentSession = updated }
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
        if (!isAgentConfigured) return
        if (isRefresh) refreshing = true else loading = true
        clearError()
        try {
            julesRepository.getActivities(session.id).collectLatest { list ->
                try {
                    val json = Json { prettyPrint = true; ignoreUnknownKeys = true }
                    val cached = julesRepository.getActivitiesBySession(session.id)
                    activitiesJson = json.encodeToString(
                        ListSerializer(JulesClient.JulesActivity.serializer()),
                        cached.mapNotNull { entity ->
                            entity.activityJson?.let { aj ->
                                try {
                                    json.decodeFromString(JulesClient.JulesActivity.serializer(), aj)
                                } catch (_: Exception) {
                                    null
                                }
                            }
                        },
                    )
                } catch (e: Exception) {
                    activitiesJson = "Error loading raw activities: ${e.message}"
                }

                if (chatItems.isEmpty() || isRefresh) {
                    chatItems.clear()
                    chatItems.addAll(list)
                } else {
                    val existingIds = chatItems.map { it.id }.toSet()
                    val newItems = list.filter { it.id !in existingIds }
                    if (newItems.isNotEmpty()) chatItems.addAll(newItems)
                }
                if (chatItems.isNotEmpty()) {
                    try {
                        listState.animateScrollToItem(chatItems.size - 1)
                    } catch (_: Exception) {
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

    fun resetToProjects() {
        currentSession = null
        selectedFeatureId = null
        selectedFeatureTitle = ""
        selectedSourceName = null
        screenLevel = JulesScreenLevel.Projects
    }

    val handleBack = {
        when {
            currentSession != null -> currentSession = null
            screenLevel == JulesScreenLevel.GitDetails -> screenLevel = JulesScreenLevel.Features
            screenLevel == JulesScreenLevel.Conversations -> {
                screenLevel = JulesScreenLevel.Features
                selectedFeatureId = null
                selectedFeatureTitle = ""
            }
            screenLevel == JulesScreenLevel.Features -> resetToProjects()
            else -> onBack()
        }
    }

    BackHandler(onBack = handleBack)

    LaunchedEffect(apiKeys) {
        if (apiKeys.isNotEmpty()) loadSources() else sourcesLoaded = true
    }

    LaunchedEffect(initialSession, features) {
        if (initialSession != null) {
            selectedSourceName = initialSession.sourceName
            val featureId = initialSession.featureId ?: JulesNavigation.ORPHAN_FEATURE_ID
            selectedFeatureId = featureId
            selectedFeatureTitle = JulesNavigation.featureTitle(featureId, features)
            screenLevel = JulesScreenLevel.Conversations
            currentSession = initialSession
        }
    }

    LaunchedEffect(sourcesLoaded, sources, settings.lastJulesRepoId) {
        if (!sourcesLoaded || selectedSourceName != null || initialSession != null) return@LaunchedEffect
        val lastId = settings.lastJulesRepoId.trim()
        if (lastId.isNotEmpty() && sources.any { it.name == lastId }) {
            selectedSourceName = lastId
            screenLevel = JulesScreenLevel.Features
        }
    }

    LaunchedEffect(selectedSourceName) {
        if (selectedSourceName != null && apiKeys.isNotEmpty()) {
            loadSessions()
            sources.find { it.name == selectedSourceName }?.let { source ->
                val displayName = sourceDisplayName(source)
                selectedSourceDisplayName = displayName
                settingsManager.saveSettings(
                    settings.copy(lastJulesRepoId = source.name, lastJulesRepoName = displayName),
                )
            }
        } else {
            sessions = emptyList()
        }
    }

    LaunchedEffect(apiKeys, githubToken, selectedSourceName, currentSession?.id) {
        if (!isAgentConfigured || selectedSourceName == null) return@LaunchedEffect
        while (true) {
            delay(30_000)
            val sessionToPoll = currentSession
            if (sessionToPoll != null) {
                julesRepository.pollSessionStatus(sessionToPoll.id, githubToken)
            } else {
                sessions.filter { it.prState == null && it.prUrl == null }.forEach { session ->
                    julesRepository.pollSessionStatus(session.id, githubToken)
                }
            }
            loadSessions()
        }
    }

    LaunchedEffect(currentSession?.id) {
        if (currentSession != null) refreshActivitiesInternal() else chatItems.clear()
    }

    val headerTitle = when {
        currentSession != null -> currentSession?.title ?: ""
        screenLevel == JulesScreenLevel.GitDetails -> "Git & CI"
        screenLevel == JulesScreenLevel.Conversations -> selectedFeatureTitle
        screenLevel == JulesScreenLevel.Features -> selectedSourceDisplayName
        else -> "Projects"
    }
    val headerSubtitle = when {
        currentSession != null -> null
        screenLevel == JulesScreenLevel.Conversations -> selectedSourceDisplayName
        screenLevel == JulesScreenLevel.Features -> "Features"
        screenLevel == JulesScreenLevel.GitDetails -> selectedSourceDisplayName
        else -> null
    }
    val selectedSource = sources.find { it.name == selectedSourceName }
    val githubOwner = selectedSource?.githubRepo?.owner?.takeIf { it.isNotBlank() }
    val githubRepo = selectedSource?.githubRepo?.repo?.takeIf { it.isNotBlank() }

    Surface(modifier = Modifier.fillMaxSize(), color = ColorHelper.JulesBg) {
        Column {
            JulesScreenHeader(
                title = headerTitle,
                subtitle = headerSubtitle,
                currentSession = currentSession,
                showSwitchProject = currentSession != null || selectedSourceName != null,
                onBack = handleBack,
                onSwitchProject = ::resetToProjects,
                onTogglePause = { session ->
                    scope.launch {
                        loading = true
                        try {
                            if (session.sessionState == "PAUSED") {
                                julesRepository.resumeSession(session.id)
                            } else {
                                julesRepository.pauseSession(session.id)
                            }
                            loadSessions()
                        } catch (e: Exception) {
                            error = "Failed to toggle pause: ${e.message}"
                        } finally {
                            loading = false
                        }
                    }
                },
                onOpenInWeb = uriHandler::openUri,
                onShowActivities = { session ->
                    scope.launch {
                        loading = true
                        try {
                            val json = Json { ignoreUnknownKeys = true }
                            val cached = julesRepository.getActivitiesBySession(session.id)
                            val activities = cached.mapNotNull { entity ->
                                entity.activityJson?.let { aj ->
                                    json.decodeFromString(JulesClient.JulesActivity.serializer(), aj)
                                }
                            }
                            if (activities.isNotEmpty()) {
                                rawActivities = activities
                                showActivitiesSheet = true
                            } else {
                                val key = session.apiKey ?: apiKeys.firstOrNull()
                                if (key != null) {
                                    rawActivities = julesClient.listActivities(key, session.id).activities
                                    showActivitiesSheet = true
                                }
                            }
                        } catch (e: Exception) {
                            error = "Could not load activities: ${e.message ?: "Unknown error"}"
                        } finally {
                            loading = false
                        }
                    }
                },
            )

            if (loading || loadingSessions || refreshing || refreshingSessions) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().height(2.dp),
                    color = ColorHelper.JulesAccent,
                    trackColor = Color.Transparent,
                )
            } else {
                Spacer(modifier = Modifier.height(2.dp))
            }

            when {
                !isAgentConfigured -> {
                    val (title, message) = when (codingBackend) {
                        CodingAgentBackend.CLAUDE_CODE -> "Claude Code not configured" to
                            "Go to Settings → API keys → Coding agent. Set backend to Claude Code, add your Anthropic API key (platform.claude.com), and a GitHub token with repo access."
                        CodingAgentBackend.JULES -> "Jules API key not set" to
                            "Go to Settings → API keys → Coding agent and add your key from jules.google.com Settings."
                    }
                    JulesErrorCard(title = title, message = message)
                }
                else -> {
                    error?.let { message ->
                        JulesErrorCard(title = "Error", message = message, onDismiss = { clearError() })
                    }

                    val activeSession = currentSession
                    when {
                        activeSession != null -> JulesConversationContent(
                            currentSession = activeSession,
                            chatItems = chatItems,
                            listState = listState,
                            inputText = inputText,
                            onInputChange = { inputText = it },
                            voiceManager = voiceManager,
                            onSend = {
                                val prompt = inputText.takeIf { it.isNotBlank() } ?: return@JulesConversationContent
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
                            onMergePr = { prUrlOverride ->
                                val prUrl = prUrlOverride ?: activeSession.prUrl ?: return@JulesConversationContent
                                scope.launch {
                                    loading = true
                                    val res = julesRepository.mergePr(githubToken, activeSession.id, prUrl)
                                    if (res.isFailure) {
                                        error = "Merge failed: ${res.exceptionOrNull()?.message}"
                                    } else {
                                        loadSessions()
                                        refreshActivitiesInternal(isRefresh = true)
                                    }
                                    loading = false
                                }
                            },
                            onSolveConflicts = { prUrlOverride ->
                                val prUrl = prUrlOverride ?: activeSession.prUrl ?: return@JulesConversationContent
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
                                        julesRepository.sendMessage(
                                            activeSession.id,
                                            "@jules resolve the conflicts in this PR",
                                        )
                                        refreshActivities()
                                    } catch (e: Exception) {
                                        error = "Auto solve failed: ${e.message ?: "Unknown error"}"
                                    } finally {
                                        loading = false
                                    }
                                }
                            },
                            onCreatePr = { branchRef ->
                                scope.launch {
                                    loading = true
                                    val res = julesRepository.createPullRequest(
                                        githubToken = githubToken,
                                        owner = branchRef.owner,
                                        repo = branchRef.repo,
                                        head = branchRef.branch,
                                        base = "main",
                                        title = "PR from ${branchRef.branch}",
                                        body = "Created from Jules",
                                    )
                                    if (res.isFailure) {
                                        error = "Failed to create PR: ${res.exceptionOrNull()?.message}"
                                    } else {
                                        loadSessions()
                                        refreshActivities(isRefresh = true)
                                    }
                                    loading = false
                                }
                            },
                            julesRepository = julesRepository,
                            githubToken = githubToken,
                            isRefreshing = refreshing,
                            onRefresh = { refreshActivities(isRefresh = true) },
                            activitiesJson = activitiesJson,
                        )
                        selectedSourceName != null &&
                            screenLevel == JulesScreenLevel.Conversations &&
                            selectedFeatureId != null -> {
                            val featureId = selectedFeatureId!!
                            JulesConversationsContent(
                                selectedSourceName = selectedSourceName ?: "",
                                selectedFeatureId = featureId,
                                sessions = sessions,
                                features = features,
                                loading = loading,
                                newSessionPrompt = newSessionPrompt,
                                onNewSessionPromptChange = { newSessionPrompt = it },
                                onCreateSession = {
                                    val source = selectedSourceName ?: return@JulesConversationsContent
                                    if (newSessionPrompt.isBlank()) return@JulesConversationsContent
                                    scope.launch {
                                        loading = true
                                        clearError()
                                        try {
                                            val linkedFeatureId = featureId.takeUnless { JulesNavigation.isOrphanFeature(it) }
                                            val sessionId = julesRepository.createSession(
                                                apiKeys = apiKeys,
                                                prompt = newSessionPrompt,
                                                source = source,
                                                title = newSessionPrompt.take(80),
                                                featureId = linkedFeatureId,
                                            )
                                            currentSession = julesRepository.getSession(sessionId)
                                            newSessionPrompt = ""
                                            loadSessions()
                                        } catch (e: Exception) {
                                            error = "Failed: ${e.message}"
                                        }
                                        loading = false
                                    }
                                },
                                onOpenSession = { currentSession = it },
                                onGetPrDetails = { session, onResult ->
                                    scope.launch {
                                        val res = julesRepository.getPrDetails(githubToken, session.prUrl!!)
                                        if (res.isSuccess) onResult(res.getOrNull())
                                    }
                                },
                                onArchive = { session ->
                                    scope.launch {
                                        archivingSessionIds.add(session.id)
                                        julesRepository.archiveSession(session.id)
                                        loadSessions()
                                        archivingSessionIds.remove(session.id)
                                    }
                                },
                                onLinkToFeature = { session, linkedId ->
                                    scope.launch {
                                        julesRepository.linkSessionToFeature(session.id, linkedId)
                                        loadSessions()
                                    }
                                },
                                onCreateFeatureAndLink = { session, title ->
                                    scope.launch {
                                        val newFeatureId = featureRepository.addFeature(title, "", 0, session.sourceName)
                                        julesRepository.linkSessionToFeature(session.id, newFeatureId)
                                        loadSessions()
                                    }
                                },
                                onArchiveCompleted = {
                                    scope.launch {
                                        val sourceName = selectedSourceName ?: ""
                                        val completed = JulesNavigation.sessionsForFeature(
                                            sessions,
                                            sourceName,
                                            featureId,
                                        ).filter { it.isFinished }
                                        archivingSessionIds.addAll(completed.map { it.id })
                                        completed.forEach { julesRepository.archiveSession(it.id) }
                                        loadSessions()
                                        archivingSessionIds.removeAll(completed.map { it.id })
                                    }
                                },
                                archivingSessionIds = archivingSessionIds,
                                isRefreshing = refreshingSessions,
                                onRefresh = { loadSessions(isRefresh = true) },
                                hideCompleted = hideCompleted,
                                onHideCompletedChange = { hideCompleted = it },
                            )
                        }
                        selectedSourceName != null && screenLevel == JulesScreenLevel.GitDetails -> {
                            JulesGitDetailsContent(
                                githubToken = githubToken,
                                githubOwner = githubOwner,
                                githubRepo = githubRepo,
                                buildRepository = buildRepository,
                                isRefreshing = refreshingSessions,
                                onRefresh = { loadSessions(isRefresh = true) },
                            )
                        }
                        selectedSourceName != null && screenLevel == JulesScreenLevel.Features -> {
                            JulesFeaturesContent(
                                selectedSourceName = selectedSourceName ?: "",
                                features = features,
                                sessions = sessions,
                                isRefreshing = refreshingSessions,
                                onRefresh = { loadSessions(isRefresh = true) },
                                onOpenGitDetails = { screenLevel = JulesScreenLevel.GitDetails },
                                onSelectFeature = { featureId, title ->
                                    selectedFeatureId = featureId
                                    selectedFeatureTitle = title
                                    screenLevel = JulesScreenLevel.Conversations
                                },
                            )
                        }
                        else -> JulesRepositoriesList(
                            sources = sources,
                            onSelect = { src ->
                                selectedSourceName = src.name
                                selectedSourceDisplayName = sourceDisplayName(src)
                                screenLevel = JulesScreenLevel.Features
                            },
                            loading = loading && sources.isEmpty(),
                            refreshing = refreshing,
                            onRefresh = { loadSources(isRefresh = true) },
                        )
                    }
                }
            }
        }

        if (showActivitiesSheet) {
            ModalBottomSheet(
                onDismissRequest = { showActivitiesSheet = false },
                sheetState = rememberModalBottomSheetState(),
                containerColor = ColorHelper.JulesListBg,
            ) {
                JulesActivitiesSheet(rawActivities)
            }
        }

        val conflictSession = currentSession
        if (showConflictSheet && conflictSession != null) {
            ModalBottomSheet(
                onDismissRequest = { showConflictSheet = false },
                containerColor = ColorHelper.JulesListBg,
                modifier = Modifier.fillMaxSize(),
            ) {
                JulesConflictResolutionSheet(
                    session = conflictSession,
                    files = conflictingFiles,
                    githubToken = githubToken,
                    julesRepository = julesRepository,
                    onDismiss = { showConflictSheet = false },
                )
            }
        }
    }
}
