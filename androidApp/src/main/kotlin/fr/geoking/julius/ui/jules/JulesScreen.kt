package fr.geoking.julius.ui.jules

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import fr.geoking.julius.SettingsManager
import fr.geoking.julius.api.codingagent.CodingAgentBackend
import fr.geoking.julius.api.jules.JulesChatItem
import fr.geoking.julius.api.jules.JulesClient
import fr.geoking.julius.persistence.JulesSessionEntity
import fr.geoking.julius.navigation.HarnessNavController
import fr.geoking.julius.navigation.HarnessRoute
import fr.geoking.julius.queue.CodingAgentQueueEngine
import fr.geoking.julius.queue.QueuePolicy
import fr.geoking.julius.queue.julesApiKeys
import fr.geoking.julius.queue.queuePolicyFor
import fr.geoking.julius.repository.FeatureRepository
import fr.geoking.julius.repository.GitHubBuildRepository
import fr.geoking.julius.repository.JulesRepository
import fr.geoking.julius.ui.harness.ActivitiesDebugScreen
import fr.geoking.julius.ui.harness.AddFeatureScreen
import fr.geoking.julius.ui.harness.PrConflictResolutionScreen
import fr.geoking.julius.ui.harness.QueueDashboardScreen
import fr.geoking.julius.ui.harness.QueueStatusBanner
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
    queueEngine: CodingAgentQueueEngine,
    initialSession: JulesSessionEntity? = null,
    startAtQueueDashboard: Boolean = true,
) {
    val settings by settingsManager.settings.collectAsState()
    val networkStatus by julesRepository.getNetworkService().status.collectAsState()
    val apiKeys = settings.julesApiKeys()
    val queueStatus by queueEngine.status.collectAsState()
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
    val nav = remember(initialSession, startAtQueueDashboard) {
        HarnessNavController(
            when {
                initialSession != null -> HarnessRoute.Chat(initialSession.id)
                startAtQueueDashboard -> HarnessRoute.QueueDashboard
                else -> HarnessRoute.Projects
            },
        )
    }
    var selectedFeatureId by remember { mutableStateOf<String?>(null) }
    var selectedFeatureTitle by remember { mutableStateOf("") }
    var sourcesLoaded by remember { mutableStateOf(false) }
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
            if (isRefresh) refreshing = true else if (sources.isEmpty()) loading = true
            clearError()
            try {
                julesRepository.getSources(apiKeys).collectLatest { list ->
                    sources = list
                    sourcesLoaded = true
                    if (list.isNotEmpty()) loading = false
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
            if (isRefresh) refreshingSessions = true else if (sessions.isEmpty()) loadingSessions = true
            clearError()
            try {
                julesRepository.getUsageQuota(apiKeys)
                julesRepository.getSessions(apiKeys, sourceName, githubToken).collectLatest { list ->
                    sessions = list
                    if (list.isNotEmpty()) loadingSessions = false
                    currentSession?.let { curr ->
                        list.find { it.id == curr.id }?.let { updated -> currentSession = updated }
                    }
                    featureRepository.autoPromoteOrphans(scope, sourceName, list)
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
        if (isRefresh) refreshing = true else if (chatItems.isEmpty()) loading = true
        clearError()
        try {
            julesRepository.getActivities(session.id).collectLatest { list ->
                if (list.isNotEmpty()) loading = false
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
        nav.resetTo(HarnessRoute.Projects)
    }

    val handleBack: () -> Unit = {
        when (nav.current) {
            is HarnessRoute.Chat -> {
                currentSession = null
                nav.pop()
            }
            is HarnessRoute.PrConflict, is HarnessRoute.ActivitiesDebug -> { nav.pop() }
            is HarnessRoute.AddFeature, is HarnessRoute.EditFeature -> { nav.pop() }
            is HarnessRoute.Conversations -> {
                selectedFeatureId = null
                selectedFeatureTitle = ""
                nav.pop()
            }
            is HarnessRoute.Features -> resetToProjects()
            is HarnessRoute.Projects -> {
                if (startAtQueueDashboard) nav.resetTo(HarnessRoute.QueueDashboard) else onBack()
            }
            is HarnessRoute.QueueDashboard -> onBack()
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
            currentSession = initialSession
        }
    }

    LaunchedEffect(Unit) {
        queueEngine.refreshStatusOnly()
        featureRepository.scheduleWorker()
    }

    LaunchedEffect(sourcesLoaded, sources, settings.lastJulesRepoId) {
        if (!sourcesLoaded || selectedSourceName != null || initialSession != null) return@LaunchedEffect
        val lastId = settings.lastJulesRepoId.trim()
        if (lastId.isNotEmpty() && sources.any { it.name == lastId }) {
            selectedSourceName = lastId
            val display = sources.find { it.name == lastId }?.let { sourceDisplayName(it) } ?: lastId
            nav.push(HarnessRoute.Features(lastId, display))
        }
    }

    LaunchedEffect(nav.current) {
        val route = nav.current
        if (route is HarnessRoute.Chat) {
            currentSession = sessions.find { it.id == route.sessionId }
                ?: julesRepository.getSession(route.sessionId)
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

    val headerTitle = when (val route = nav.current) {
        is HarnessRoute.Chat -> currentSession?.title ?: ""
        is HarnessRoute.Conversations -> selectedFeatureTitle
        is HarnessRoute.Features -> selectedSourceDisplayName
        is HarnessRoute.QueueDashboard -> "Harness Queue"
        is HarnessRoute.PrConflict -> "Resolve conflicts"
        is HarnessRoute.ActivitiesDebug -> "Activities"
        is HarnessRoute.AddFeature -> "New feature"
        else -> "Projects"
    }
    val headerSubtitle = when (val route = nav.current) {
        is HarnessRoute.Chat -> null
        is HarnessRoute.Conversations -> selectedSourceDisplayName
        is HarnessRoute.Features -> "Features"
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
                onBack = handleBack,
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
                            val jsonOut = Json { prettyPrint = true; ignoreUnknownKeys = true }
                            activitiesJson = if (activities.isNotEmpty()) {
                                jsonOut.encodeToString(
                                    ListSerializer(JulesClient.JulesActivity.serializer()),
                                    activities,
                                )
                            } else {
                                val key = session.apiKey ?: apiKeys.firstOrNull()
                                if (key != null) {
                                    val listed = julesClient.listActivities(key, session.id).activities
                                    jsonOut.encodeToString(
                                        ListSerializer(JulesClient.JulesActivity.serializer()),
                                        listed,
                                    )
                                } else {
                                    "[]"
                                }
                            }
                            nav.push(HarnessRoute.ActivitiesDebug(session.id, activitiesJson))
                        } catch (e: Exception) {
                            error = "Could not load activities: ${e.message ?: "Unknown error"}"
                        } finally {
                            loading = false
                        }
                    }
                },
                onArchive = { session ->
                    scope.launch {
                        julesRepository.archiveSession(session.id)
                        loadSessions()
                        if (currentSession?.id == session.id) handleBack()
                    }
                },
                onDelete = { session ->
                    scope.launch {
                        julesRepository.deleteSessionPermanently(session.id)
                        loadSessions()
                        if (currentSession?.id == session.id) handleBack()
                    }
                },
                onMerge = { session ->
                    val prUrl = session.prUrl ?: return@JulesScreenHeader
                    scope.launch {
                        loading = true
                        val res = julesRepository.mergePr(githubToken, session.id, prUrl, deleteBranch = true)
                        if (res.isFailure) {
                            error = "Merge failed: ${res.exceptionOrNull()?.message}"
                        } else {
                            loadSessions()
                        }
                        loading = false
                    }
                },
                onFixConflicts = { session ->
                    nav.push(HarnessRoute.PrConflict(session.id))
                },
                onRetry = { session ->
                    scope.launch {
                        loading = true
                        try {
                            julesRepository.sendMessage(session.id, session.prompt)
                            refreshActivitiesInternal()
                        } catch (e: Exception) {
                            error = "Retry failed: ${e.message}"
                        } finally {
                            loading = false
                        }
                    }
                }
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

                    when (val route = nav.current) {
                        is HarnessRoute.QueueDashboard -> QueueDashboardScreen(
                            status = queueStatus,
                            features = features,
                            onTogglePause = { paused ->
                                val backend = settings.codingAgentBackend
                                val policy = settings.queuePolicyFor(backend)
                                settingsManager.saveSettings(
                                    settings.copy(
                                        queuePolicies = settings.queuePolicies + (backend to policy.copy(queuePaused = paused)),
                                    ),
                                )
                                scope.launch { queueEngine.tick() }
                            },
                            onOpenProjects = { nav.push(HarnessRoute.Projects) },
                            onAddFeature = { nav.push(HarnessRoute.AddFeature(selectedSourceName)) },
                            onOpenFeature = { feature ->
                                selectedSourceName = feature.sourceName
                                selectedFeatureId = feature.id
                                selectedFeatureTitle = feature.title
                                val display = sources.find { it.name == feature.sourceName }
                                    ?.let { sourceDisplayName(it) } ?: feature.sourceName
                                nav.push(HarnessRoute.Features(feature.sourceName, display))
                                nav.push(HarnessRoute.Conversations(feature.sourceName, feature.id, feature.title))
                                feature.sessionId?.let { sid ->
                                    scope.launch {
                                        currentSession = julesRepository.getSession(sid)
                                        nav.push(HarnessRoute.Chat(sid))
                                    }
                                }
                            },
                        )
                        is HarnessRoute.AddFeature -> AddFeatureScreen(
                            defaultSourceName = route.sourceName ?: selectedSourceName ?: "",
                            voiceManager = voiceManager,
                            onBack = { nav.pop() },
                            onSave = { title, description, source ->
                                scope.launch {
                                    featureRepository.addFeature(title, description, 0, source)
                                    nav.pop()
                                }
                            },
                        )
                        is HarnessRoute.PrConflict -> {
                            val session = sessions.find { it.id == route.sessionId } ?: currentSession
                            if (session != null) {
                                PrConflictResolutionScreen(
                                    session = session,
                                    githubToken = githubToken,
                                    julesRepository = julesRepository,
                                    onBack = { nav.pop() },
                                )
                            }
                        }
                        is HarnessRoute.ActivitiesDebug -> ActivitiesDebugScreen(
                            activitiesJson = route.activitiesJson,
                            onBack = { nav.pop() },
                        )
                        is HarnessRoute.Chat -> {
                            val activeSession = currentSession
                            if (activeSession != null) JulesConversationContent(
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
                            onSolveConflicts = { _ ->
                                nav.push(HarnessRoute.PrConflict(activeSession.id))
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
                        }
                        is HarnessRoute.Conversations -> {
                            val featureId = route.featureId
                            selectedSourceName = route.sourceName
                            selectedFeatureId = featureId
                            selectedFeatureTitle = route.featureTitle
                            QueueStatusBanner(queueStatus)
                            JulesConversationsContent(
                                selectedSourceName = selectedSourceName ?: "",
                                selectedFeatureId = featureId,
                                voiceManager = voiceManager,
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
                                            val session = julesRepository.getSession(sessionId)
                                            currentSession = session
                                            newSessionPrompt = ""
                                            loadSessions()
                                            nav.push(HarnessRoute.Chat(sessionId))
                                        } catch (e: Exception) {
                                            error = "Failed: ${e.message}"
                                        }
                                        loading = false
                                    }
                                },
                                onOpenSession = {
                                    currentSession = it
                                    nav.push(HarnessRoute.Chat(it.id))
                                },
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
                        is HarnessRoute.Features -> {
                            selectedSourceName = route.sourceName
                            selectedSourceDisplayName = route.displayName
                            QueueStatusBanner(queueStatus)
                            JulesFeaturesContent(
                                selectedSourceName = route.sourceName,
                                features = features,
                                voiceManager = voiceManager,
                                sessions = sessions,
                                isRefreshing = refreshingSessions,
                                onRefresh = { loadSessions(isRefresh = true) },
                                onSelectFeature = { featureId, title ->
                                    selectedFeatureId = featureId
                                    selectedFeatureTitle = title
                                    nav.push(HarnessRoute.Conversations(route.sourceName, featureId, title))
                                },
                                onMoveFeature = { reordered ->
                                    scope.launch {
                                        featureRepository.updatePositions(reordered)
                                    }
                                },
                                onCreateFeature = { title ->
                                    scope.launch {
                                        featureRepository.addFeature(title, "", 0, route.sourceName)
                                    }
                                },
                            )
                        }
                        is HarnessRoute.EditFeature -> Text("Edit feature", color = Color.White, modifier = Modifier.padding(16.dp))
                        is HarnessRoute.Projects -> JulesRepositoriesList(
                            sources = sources,
                            onSelect = { src ->
                                selectedSourceName = src.name
                                selectedSourceDisplayName = sourceDisplayName(src)
                                nav.push(HarnessRoute.Features(src.name, sourceDisplayName(src)))
                            },
                            loading = loading && sources.isEmpty(),
                            refreshing = refreshing,
                            onRefresh = { loadSources(isRefresh = true) },
                        )
                    }
                }
            }
        }

    }
}
