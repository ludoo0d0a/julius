package fr.geoking.julius.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import fr.geoking.julius.BuildConfig
import fr.geoking.julius.feature.auth.GoogleAuthManager
import fr.geoking.julius.*
import fr.geoking.julius.repository.JulesRepository
import fr.geoking.julius.repository.GitHubBuildRepository
import fr.geoking.julius.agents.LlamatikModelHelper
import fr.geoking.julius.agents.LlamatikModelVariant
import fr.geoking.julius.feature.voice.VoskModelHelper
import fr.geoking.julius.feature.voice.VoskModelVariant
import fr.geoking.julius.api.codingagent.CodingAgentBackend
import fr.geoking.julius.queue.AgentAccount
import fr.geoking.julius.queue.QueuePolicy
import fr.geoking.julius.queue.QueueStatus
import java.util.UUID
import fr.geoking.julius.shared.voice.SttEnginePreference

enum class SettingsScreenPage { Main, ApiKeys, Agents, AgentDetails, GitHub, Jules, GitCi }

/** Tab index for [AgentsPage]: 0 = all, 1 = remote, 2 = local (on-device). */
private const val AGENTS_FILTER_ALL = 0
private const val AGENTS_FILTER_REMOTE = 1
private const val AGENTS_FILTER_LOCAL = 2

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settingsManager: SettingsManager,
    authManager: GoogleAuthManager,
    julesRepository: JulesRepository,
    buildRepository: GitHubBuildRepository,
    errorLog: List<Any>,
    onDismiss: () -> Unit,
    initialScreenStack: List<SettingsScreenPage>? = null,
    onInitialRouteConsumed: () -> Unit = {}
) {
    val settings by settingsManager.settings.collectAsState()

    var navigationStack = rememberSaveable(
        saver = listSaver(
            save = { it.map { page -> page.name } },
            restore = { it.map { name -> SettingsScreenPage.valueOf(name) }.toMutableStateList() }
        )
    ) {
        mutableStateListOf<SettingsScreenPage>().apply {
            if (initialScreenStack.isNullOrEmpty()) {
                add(SettingsScreenPage.Main)
            } else {
                addAll(initialScreenStack)
            }
            onInitialRouteConsumed()
        }
    }

    var selectedAgentForDetails by rememberSaveable {
        mutableStateOf<AgentType?>(
            if (navigationStack.contains(SettingsScreenPage.AgentDetails)) settings.selectedAgent else null
        )
    }

    var agentsFilterMode by rememberSaveable { mutableIntStateOf(AGENTS_FILTER_ALL) }

    fun goBack() {
        if (navigationStack.size > 1) {
            navigationStack.removeAt(navigationStack.size - 1)
        } else {
            onDismiss()
        }
    }

    BackHandler(enabled = true, onBack = { goBack() })

    val currentPage = navigationStack.last()

    Scaffold(
        topBar = {
            if (currentPage != SettingsScreenPage.GitCi) {
                TopAppBar(
                    title = {
                        Text(
                            when (currentPage) {
                                SettingsScreenPage.Main -> "Settings"
                                SettingsScreenPage.ApiKeys -> "API keys"
                                SettingsScreenPage.Agents -> when (agentsFilterMode) {
                                    AGENTS_FILTER_REMOTE -> "Remote agents"
                                    AGENTS_FILTER_LOCAL -> "Local agents"
                                    else -> "Agents"
                                }
                                SettingsScreenPage.AgentDetails -> selectedAgentForDetails?.name ?: "Agent Settings"
                                SettingsScreenPage.GitHub -> "GitHub"
                                SettingsScreenPage.Jules -> "Coding agent"
                                SettingsScreenPage.GitCi -> "Git & CI"
                            }
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { goBack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                )
            }
        }
    ) { padding ->
        Surface(
            modifier = Modifier.padding(padding).fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            when (currentPage) {
                SettingsScreenPage.Main -> MainSettingsPage(
                    settings = settings,
                    settingsManager = settingsManager,
                    authManager = authManager,
                    onNavigateToApiKeys = {
                        navigationStack.add(SettingsScreenPage.ApiKeys)
                        @Suppress("UNUSED_EXPRESSION") Unit
                    },
                    onNavigateToGitCi = {
                        navigationStack.add(SettingsScreenPage.GitCi)
                        @Suppress("UNUSED_EXPRESSION") Unit
                    }
                )
                SettingsScreenPage.ApiKeys -> ApiKeysPage(
                    settings = settings,
                    onNavigateToLocalAgents = {
                        agentsFilterMode = AGENTS_FILTER_LOCAL
                        navigationStack.add(SettingsScreenPage.Agents)
                        @Suppress("UNUSED_EXPRESSION") Unit
                    },
                    onNavigateToRemoteAgents = {
                        agentsFilterMode = AGENTS_FILTER_REMOTE
                        navigationStack.add(SettingsScreenPage.Agents)
                        @Suppress("UNUSED_EXPRESSION") Unit
                    },
                    onNavigateToGitHub = {
                        navigationStack.add(SettingsScreenPage.GitHub)
                        @Suppress("UNUSED_EXPRESSION") Unit
                    },
                    onNavigateToJules = {
                        navigationStack.add(SettingsScreenPage.Jules)
                        @Suppress("UNUSED_EXPRESSION") Unit
                    },
                )
                SettingsScreenPage.Agents -> AgentsPage(
                    settings = settings,
                    filterMode = agentsFilterMode,
                    onFilterModeChange = { agentsFilterMode = it },
                    onAgentSelected = { agent ->
                        settingsManager.saveSettings(settings.copy(selectedAgent = agent))
                    },
                    onNavigateToDetails = { agent ->
                        selectedAgentForDetails = agent
                        navigationStack.add(SettingsScreenPage.AgentDetails)
                        @Suppress("UNUSED_EXPRESSION") Unit
                    }
                )
                SettingsScreenPage.GitHub -> {
                    val context = LocalContext.current
                    GitHubTokenPage(
                        token = settings.githubApiKey,
                        onTokenChange = { token ->
                            settingsManager.saveSettings(settings.copy(githubApiKey = token))
                        },
                        onCreatePat = { context.openExternalUrl(GitHubPatUrls.CREATE_CLASSIC_PAT) },
                        onManagePats = { context.openExternalUrl(CredentialUrls.GITHUB_MANAGE_TOKENS) },
                    )
                }
                SettingsScreenPage.Jules -> {
                    val context = LocalContext.current
                    CodingAgentSettingsPage(
                        backend = settings.codingAgentBackend,
                        onBackendChange = { backend ->
                            settingsManager.saveSettings(settings.copy(codingAgentBackend = backend))
                        },
                        agentAccounts = settings.agentAccounts,
                        onAgentAccountsChange = { accounts ->
                            val julesKeys = accounts.filter { it.backend == CodingAgentBackend.JULES && it.enabled }
                                .map { it.apiKey }
                            val anthropic = accounts.firstOrNull { it.backend == CodingAgentBackend.CLAUDE_CODE && it.enabled }
                                ?.apiKey ?: settings.anthropicApiKey
                            settingsManager.saveSettings(
                                settings.copy(
                                    agentAccounts = accounts,
                                    julesKeys = julesKeys,
                                    anthropicApiKey = anthropic,
                                ),
                            )
                        },
                        queuePolicy = settings.queuePolicies[settings.codingAgentBackend] ?: QueuePolicy(),
                        onQueuePolicyChange = { policy ->
                            settingsManager.saveSettings(
                                settings.copy(
                                    queuePolicies = settings.queuePolicies + (settings.codingAgentBackend to policy),
                                ),
                            )
                        },
                        onGetJulesApiKey = { context.openExternalUrl(CredentialUrls.JULES_API_KEYS) },
                        onGetAnthropicApiKey = { context.openExternalUrl(CredentialUrls.ANTHROPIC_API_KEYS) },
                        onCreateGitHubPat = { context.openExternalUrl(GitHubPatUrls.CREATE_CLASSIC_PAT) },
                    )
                }
                SettingsScreenPage.AgentDetails -> {
                    selectedAgentForDetails?.let { agent ->
                        AgentDetailsPage(
                            agent = agent,
                            settings = settings,
                            settingsManager = settingsManager,
                            onSettingsChange = { settingsManager.saveSettings(it) }
                        )
                    }
                }
                SettingsScreenPage.GitCi -> {
                    GitCiPage(
                        settings = settings,
                        julesRepository = julesRepository,
                        buildRepository = buildRepository,
                        onBack = { goBack() }
                    )
                }
            }
        }
    }
}

@Composable
fun GitCiPage(
    settings: AppSettings,
    julesRepository: JulesRepository,
    buildRepository: GitHubBuildRepository,
    onBack: () -> Unit
) {
    var owner by remember { mutableStateOf<String?>(null) }
    var repo by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(settings.lastJulesRepoId) {
        loading = true
        val sources = julesRepository.getSourcesCached()
        val source = sources.find { it.name == settings.lastJulesRepoId }
        owner = source?.githubRepo?.owner?.takeIf { it.isNotBlank() }
        repo = source?.githubRepo?.repo?.takeIf { it.isNotBlank() }
        loading = false
    }

    if (loading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = ColorHelper.JulesAccent)
        }
    } else if (owner != null && repo != null) {
        BuildRunsDetailScreen(
            owner = owner!!,
            repo = repo!!,
            githubToken = settings.githubApiKey,
            buildRepository = buildRepository,
            onBack = onBack
        )
    } else {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "No project selected",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Select a project with a GitHub repository linked in the Jules screen to see CI status here.",
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                color = Color.Gray
            )
            Spacer(Modifier.height(24.dp))
            Button(onClick = onBack) {
                Text("Back")
            }
        }
    }
}

@Composable
fun MainSettingsPage(
    settings: AppSettings,
    settingsManager: SettingsManager,
    authManager: GoogleAuthManager,
    onNavigateToApiKeys: () -> Unit,
    onNavigateToGitCi: () -> Unit,
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    var authBusy by remember { mutableStateOf(false) }
    var authMessage by remember { mutableStateOf<String?>(null) }
    val webClientConfigured = remember {
        val id = BuildConfig.GOOGLE_WEB_CLIENT_ID
        id.isNotBlank() && !id.contains("placeholder", ignoreCase = true)
    }

    LazyColumn {
        item {
            SettingsHeader("Account")
            Text(
                "Sign in with Google to sync preferences with Firebase (Firestore) when enabled.",
                color = Color.Gray,
                fontSize = 14.sp,
                modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp)
            )
            if (!webClientConfigured) {
                Text(
                    "Set GOOGLE_WEB_CLIENT_ID in local.properties (OAuth Web client from Google Cloud / Firebase) and rebuild.",
                    color = MaterialTheme.colorScheme.tertiary,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp)
                )
            }
            if (settings.isLoggedIn) {
                Text(
                    "Signed in as ${settings.googleUserName ?: "Google user"}",
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
                Button(
                    onClick = {
                        authBusy = true
                        authMessage = null
                        authManager.signOut { ok ->
                            authBusy = false
                            if (!ok) authMessage = "Sign out failed"
                        }
                    },
                    enabled = !authBusy,
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .fillMaxWidth()
                ) {
                    if (authBusy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Sign out")
                    }
                }
            } else {
                Button(
                    onClick = {
                        val act = activity
                        if (act == null) {
                            authMessage = "Cannot start sign-in (no activity context)"
                            return@Button
                        }
                        authBusy = true
                        authMessage = null
                        authManager.signIn(act) { success, err ->
                            authBusy = false
                            if (!success) authMessage = err ?: "Sign-in failed"
                        }
                    },
                    enabled = !authBusy,
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .fillMaxWidth()
                ) {
                    if (authBusy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Sign in with Google")
                    }
                }
            }
            authMessage?.let { msg ->
                Text(
                    msg,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }
        }
        item {
            SettingsListItem(
                title = "API keys",
                subtitle = "Agents (local & remote), GitHub, and coding agent",
                onClick = onNavigateToApiKeys,
            )
        }
        item {
            SettingsListItem(
                title = "Git & CI",
                subtitle = "Workflow runs and build status",
                onClick = onNavigateToGitCi,
            )
        }
        item {
            SettingsHeader("Speech-to-text")
            Text(
                "Choose which engine is used to transcribe your speech when you tap Speak.",
                color = Color.Gray,
                fontSize = 14.sp,
                modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp)
            )

            val selected = settings.sttEnginePreference

            fun setPref(p: SttEnginePreference) {
                settingsManager.saveSettings(settings.copy(sttEnginePreference = p))
            }

            var voskModelReady by remember { mutableStateOf(VoskModelHelper(context).isModelDownloaded()) }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { setPref(SttEnginePreference.LocalOnly) }
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                RadioButton(
                    selected = selected == SttEnginePreference.LocalOnly,
                    onClick = { setPref(SttEnginePreference.LocalOnly) }
                )
                Column(Modifier.padding(start = 8.dp)) {
                    Text("Vosk", fontWeight = FontWeight.SemiBold)
                    val status = if (voskModelReady) "Model ready" else "Model missing"
                    Text("Offline/local recognition ($status)", color = Color.Gray, fontSize = 12.sp)
                }
            }

            if (selected == SttEnginePreference.LocalOnly || selected == SttEnginePreference.LocalFirst) {
                VoskModelSettings(context, onModelReadyChanged = { voskModelReady = it })
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { setPref(SttEnginePreference.NativeOnly) }
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                RadioButton(
                    selected = selected == SttEnginePreference.NativeOnly,
                    onClick = { setPref(SttEnginePreference.NativeOnly) }
                )
                Column(Modifier.padding(start = 8.dp)) {
                    Text("Native", fontWeight = FontWeight.SemiBold)
                    Text("Android SpeechRecognizer / cloud (if available)", color = Color.Gray, fontSize = 12.sp)
                }
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
fun ApiKeysPage(
    settings: AppSettings,
    onNavigateToLocalAgents: () -> Unit,
    onNavigateToRemoteAgents: () -> Unit,
    onNavigateToGitHub: () -> Unit,
    onNavigateToJules: () -> Unit,
) {
    val localCount = AgentType.entries.count { it.enabled && it.isEmbedded }
    val remoteCount = AgentType.entries.count { it.enabled && !it.isEmbedded }
    val githubSubtitle = if (settings.githubApiKey.isNotBlank()) "Token configured" else "Not set"
    val julesSubtitle = when (settings.codingAgentBackend) {
        CodingAgentBackend.CLAUDE_CODE -> when {
            settings.anthropicApiKey.isNotBlank() && settings.githubApiKey.isNotBlank() -> "Claude Code configured"
            settings.anthropicApiKey.isNotBlank() -> "Claude Code — add GitHub token"
            else -> "Claude Code — not set"
        }
        CodingAgentBackend.JULES -> when {
            settings.julesKeys.isNotEmpty() -> "Jules — ${settings.julesKeys.size} key(s)"
            else -> "Jules — not set"
        }
    }

    LazyColumn {
        item {
            SettingsHeader("Agents")
            SettingsListItem(
                title = "Local",
                subtitle = "$localCount on-device agents · ${settings.selectedAgent.takeIf { it.isEmbedded }?.name ?: "none selected"}",
                onClick = onNavigateToLocalAgents,
            )
            SettingsListItem(
                title = "Remote",
                subtitle = "$remoteCount cloud agents · ${settings.selectedAgent.takeIf { !it.isEmbedded }?.name ?: "none selected"}",
                onClick = onNavigateToRemoteAgents,
            )
        }
        item {
            SettingsHeader("Other")
            SettingsListItem(
                title = "GitHub",
                subtitle = githubSubtitle,
                onClick = onNavigateToGitHub,
            )
            SettingsListItem(
                title = "Coding agent",
                subtitle = julesSubtitle,
                onClick = onNavigateToJules,
            )
        }
    }
}

@Composable
fun AgentsPage(
    settings: AppSettings,
    filterMode: Int,
    onFilterModeChange: (Int) -> Unit,
    onAgentSelected: (AgentType) -> Unit,
    onNavigateToDetails: (AgentType) -> Unit,
) {
    Column {
        PrimaryTabRow(selectedTabIndex = filterMode) {
            Tab(selected = filterMode == AGENTS_FILTER_ALL, onClick = { onFilterModeChange(AGENTS_FILTER_ALL) }) {
                Text("All", modifier = Modifier.padding(12.dp))
            }
            Tab(selected = filterMode == AGENTS_FILTER_REMOTE, onClick = { onFilterModeChange(AGENTS_FILTER_REMOTE) }) {
                Text("Remote", modifier = Modifier.padding(12.dp))
            }
            Tab(selected = filterMode == AGENTS_FILTER_LOCAL, onClick = { onFilterModeChange(AGENTS_FILTER_LOCAL) }) {
                Text("Local", modifier = Modifier.padding(12.dp))
            }
        }

        val allAgents = AgentType.entries.filter { it.enabled }
        val filteredAgents = when (filterMode) {
            AGENTS_FILTER_REMOTE -> allAgents.filter { !it.isEmbedded }
            AGENTS_FILTER_LOCAL -> allAgents.filter { it.isEmbedded }
            else -> allAgents
        }

        LazyColumn {
            val remote = filteredAgents.filter { !it.isEmbedded }
            val local = filteredAgents.filter { it.isEmbedded }

            if (remote.isNotEmpty()) {
                item { SettingsHeader("Remote") }
                items(remote) { agent ->
                    AgentListItem(
                        agent = agent,
                        isSelected = settings.selectedAgent == agent,
                        onSelect = { onAgentSelected(agent) },
                        onNavigateToDetails = { onNavigateToDetails(agent) }
                    )
                }
            }

            if (local.isNotEmpty()) {
                item { SettingsHeader("Local") }
                items(local) { agent ->
                    AgentListItem(
                        agent = agent,
                        isSelected = settings.selectedAgent == agent,
                        onSelect = { onAgentSelected(agent) },
                        onNavigateToDetails = { onNavigateToDetails(agent) }
                    )
                }
            }
        }
    }
}


@Composable
fun AgentDetailsPage(
    agent: AgentType,
    settings: AppSettings,
    settingsManager: SettingsManager,
    onSettingsChange: (AppSettings) -> Unit,
) {
    val context = LocalContext.current
    val helper = remember { LlamatikModelHelper(context) }
    val scope = rememberCoroutineScope()

    var downloadVariant by remember { mutableStateOf<LlamatikModelVariant?>(null) }
    var downloadBytes by remember { mutableLongStateOf(0L) }
    var downloadTotal by remember { mutableStateOf<Long?>(null) }
    var downloadError by remember { mutableStateOf<String?>(null) }

    val variants = remember(agent) {
        LlamatikModelVariant.entries.filter { it.forAgentName == agent.name }
    }
    var refreshTrigger by remember { mutableIntStateOf(0) }

    Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
        when (agent) {
            AgentType.OpenAI -> {
                ApiKeyTextField(
                    value = settings.openAiKey,
                    onValueChange = { onSettingsChange(settings.copy(openAiKey = it)) },
                    label = "OpenAI API Key",
                )
                Spacer(Modifier.height(16.dp))
                Text("Model Selection", fontWeight = FontWeight.SemiBold)
                OpenAiModel.entries.forEach { model ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clickable {
                            onSettingsChange(settings.copy(openAiModel = model))
                        }
                    ) {
                        RadioButton(
                            selected = settings.openAiModel == model,
                            onClick = { onSettingsChange(settings.copy(openAiModel = model)) }
                        )
                        Text(model.displayName, modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
            AgentType.Gemini -> {
                ApiKeyTextField(
                    value = settings.geminiKey,
                    onValueChange = { onSettingsChange(settings.copy(geminiKey = it)) },
                    label = "Gemini API Key",
                )
                Spacer(Modifier.height(16.dp))
                Text("Model Selection", fontWeight = FontWeight.SemiBold)
                GeminiModel.entries.forEach { model ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clickable {
                            onSettingsChange(settings.copy(geminiModel = model))
                        }
                    ) {
                        RadioButton(
                            selected = settings.geminiModel == model,
                            onClick = { onSettingsChange(settings.copy(geminiModel = model)) }
                        )
                        Text(model.displayName, modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
            AgentType.ElevenLabs -> {
                ApiKeyTextField(
                    value = settings.perplexityKey,
                    onValueChange = { onSettingsChange(settings.copy(perplexityKey = it)) },
                    label = "Perplexity API Key",
                )
                Spacer(Modifier.height(16.dp))
                ApiKeyTextField(
                    value = settings.elevenLabsKey,
                    onValueChange = { onSettingsChange(settings.copy(elevenLabsKey = it)) },
                    label = "ElevenLabs API Key",
                )
            }
            AgentType.DeepSeek -> {
                ApiKeyTextField(
                    value = settings.deepSeekKey,
                    onValueChange = { onSettingsChange(settings.copy(deepSeekKey = it)) },
                    label = "DeepSeek API Key",
                )
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = settings.deepSeekModel,
                    onValueChange = { onSettingsChange(settings.copy(deepSeekModel = it)) },
                    label = { Text("DeepSeek Model") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            AgentType.Groq -> {
                ApiKeyTextField(
                    value = settings.groqKey,
                    onValueChange = { onSettingsChange(settings.copy(groqKey = it)) },
                    label = "Groq API Key",
                )
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = settings.groqModel,
                    onValueChange = { onSettingsChange(settings.copy(groqModel = it)) },
                    label = { Text("Groq Model") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            AgentType.OpenRouter -> {
                ApiKeyTextField(
                    value = settings.openRouterKey,
                    onValueChange = { onSettingsChange(settings.copy(openRouterKey = it)) },
                    label = "OpenRouter API Key",
                )
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = settings.openRouterModel,
                    onValueChange = { onSettingsChange(settings.copy(openRouterModel = it)) },
                    label = { Text("OpenRouter Model") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            AgentType.Deepgram -> {
                ApiKeyTextField(
                    value = settings.deepgramKey,
                    onValueChange = { onSettingsChange(settings.copy(deepgramKey = it)) },
                    label = "Deepgram API Key",
                )
            }
            AgentType.FirebaseAI -> {
                ApiKeyTextField(
                    value = settings.firebaseAiKey,
                    onValueChange = { onSettingsChange(settings.copy(firebaseAiKey = it)) },
                    label = "Firebase AI API Key",
                )
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = settings.firebaseAiModel,
                    onValueChange = { onSettingsChange(settings.copy(firebaseAiModel = it)) },
                    label = { Text("Firebase AI Model") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }
            AgentType.OpenCodeZen -> {
                ApiKeyTextField(
                    value = settings.opencodeZenKey,
                    onValueChange = { onSettingsChange(settings.copy(opencodeZenKey = it)) },
                    label = "OpenCode Zen API Key",
                )
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = settings.opencodeZenModel,
                    onValueChange = { onSettingsChange(settings.copy(opencodeZenModel = it)) },
                    label = { Text("OpenCode Zen Model") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }
            AgentType.CompletionsMe -> {
                ApiKeyTextField(
                    value = settings.completionsMeKey,
                    onValueChange = { onSettingsChange(settings.copy(completionsMeKey = it)) },
                    label = "Completions.me API Key",
                )
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = settings.completionsMeModel,
                    onValueChange = { onSettingsChange(settings.copy(completionsMeModel = it)) },
                    label = { Text("Completions.me Model") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }
            AgentType.ApiFreeLLM -> {
                ApiKeyTextField(
                    value = settings.apifreellmKey,
                    onValueChange = { onSettingsChange(settings.copy(apifreellmKey = it)) },
                    label = "ApiFreeLLM API Key",
                )
            }
            AgentType.Llamatik -> {
                OutlinedTextField(
                    value = settings.llamatikModelPath,
                    onValueChange = { onSettingsChange(settings.copy(llamatikModelPath = it)) },
                    label = { Text("Model Path") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            else -> {
                if (!agent.isEmbedded) {
                    Text("Configuration for ${agent.name} coming soon.", color = Color.Gray)
                }
            }
        }

        if (agent.isEmbedded && variants.isNotEmpty()) {
            Spacer(Modifier.height(24.dp))
            SettingsHeader("Available Models")
            Spacer(Modifier.height(8.dp))

            if (downloadVariant != null) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Downloading ${downloadVariant?.displayName}", fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        if (downloadError != null) {
                            Text("Error: $downloadError", color = MaterialTheme.colorScheme.error)
                        } else {
                            val progress = if (downloadTotal != null && downloadTotal!! > 0) {
                                downloadBytes.toFloat() / downloadTotal!!
                            } else {
                                0f
                            }
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = { downloadVariant = null; downloadError = null }) {
                            Text("Cancel")
                        }
                    }
                }
            }

            variants.forEach { variant ->
                val isDownloaded = remember(variant, refreshTrigger) { helper.isVariantDownloaded(variant) }
                val isSelected = settings.selectedLlamatikModelVariant == variant.name

                Surface(
                    modifier = Modifier.fillMaxWidth().clickable {
                        if (isDownloaded) {
                            val path = helper.getDownloadDestinationPath(variant)
                            settingsManager.saveSettings(
                                settings.copy(
                                    llamatikModelPath = path,
                                    selectedLlamatikModelVariant = variant.name
                                )
                            )
                        } else if (downloadVariant == null) {
                            downloadError = null
                            downloadVariant = variant
                            downloadBytes = 0L
                            downloadTotal = null
                            scope.launch {
                                val result = helper.download(variant) { bytes, total ->
                                    downloadBytes = bytes
                                    downloadTotal = total
                                }
                                result.fold(
                                    onSuccess = { path ->
                                        downloadVariant = null
                                        refreshTrigger++
                                        settingsManager.saveSettings(
                                            settingsManager.settings.value.copy(
                                                llamatikModelPath = path,
                                                selectedLlamatikModelVariant = variant.name
                                            )
                                        )
                                    },
                                    onFailure = { e ->
                                        downloadError = e.message ?: "Download failed"
                                    }
                                )
                            }
                        }
                    },
                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(variant.displayName, fontWeight = FontWeight.SemiBold)
                            Text(variant.sizeDescription, color = Color.Gray, fontSize = 14.sp)
                            if (isDownloaded) {
                                Text("Already downloaded", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                        if (isDownloaded) {
                            IconButton(onClick = {
                                helper.deleteVariant(variant)
                                refreshTrigger++
                                if (isSelected) {
                                    onSettingsChange(
                                        settings.copy(
                                            llamatikModelPath = LlamatikModelHelper.DEFAULT_ASSET_PATH,
                                            selectedLlamatikModelVariant = ""
                                        )
                                    )
                                }
                            }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Gray)
                            }
                        }

                        if (isSelected) {
                            Icon(Icons.Default.Check, contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary)
                        } else if (!isDownloaded && downloadVariant != variant) {
                            Text("Download", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsListItem(
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                if (subtitle != null) {
                    Text(subtitle, color = Color.Gray, fontSize = 14.sp)
                }
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.Gray)
        }
    }
}

@Composable
fun AgentListItem(
    agent: AgentType,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onNavigateToDetails: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onNavigateToDetails),
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = isSelected,
                onClick = onSelect
            )
            Column(Modifier.weight(1f).padding(start = 8.dp)) {
                Text(agent.name, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Text(if (agent.isEmbedded) "Local" else "Remote", color = Color.Gray, fontSize = 12.sp)
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.Gray)
        }
    }
}

@Composable
fun SettingsHeader(title: String) {
    Text(
        text = title.uppercase(),
        modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp),
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp,
        letterSpacing = 1.sp
    )
}

@Composable
private fun ApiKeyTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        visualTransformation = PasswordVisualTransformation(),
        singleLine = true,
    )
}

@Composable
fun CodingAgentSettingsPage(
    backend: CodingAgentBackend,
    onBackendChange: (CodingAgentBackend) -> Unit,
    agentAccounts: List<AgentAccount>,
    onAgentAccountsChange: (List<AgentAccount>) -> Unit,
    queuePolicy: QueuePolicy,
    onQueuePolicyChange: (QueuePolicy) -> Unit,
    onGetJulesApiKey: () -> Unit,
    onGetAnthropicApiKey: () -> Unit,
    onCreateGitHubPat: () -> Unit,
) {
    Column(Modifier.verticalScroll(rememberScrollState())) {
        SettingsHeader("Backend")
        Text(
            "Choose the remote coding agent for the Jules screen. Both use GitHub remotely — no local git required.",
            color = Color.Gray,
            fontSize = 14.sp,
            modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = backend == CodingAgentBackend.JULES,
                onClick = { onBackendChange(CodingAgentBackend.JULES) },
                label = { Text("Jules") },
            )
            FilterChip(
                selected = backend == CodingAgentBackend.CLAUDE_CODE,
                onClick = { onBackendChange(CodingAgentBackend.CLAUDE_CODE) },
                label = { Text("Claude Code") },
            )
        }
        AgentAccountsSection(
            backend = backend,
            accounts = agentAccounts,
            onAccountsChange = onAgentAccountsChange,
            onGetJulesApiKey = onGetJulesApiKey,
            onGetAnthropicApiKey = onGetAnthropicApiKey,
            onCreateGitHubPat = onCreateGitHubPat,
        )
        QueuePolicySection(
            policy = queuePolicy,
            onPolicyChange = onQueuePolicyChange,
        )
    }
}

@Composable
private fun AgentAccountsSection(
    backend: CodingAgentBackend,
    accounts: List<AgentAccount>,
    onAccountsChange: (List<AgentAccount>) -> Unit,
    onGetJulesApiKey: () -> Unit,
    onGetAnthropicApiKey: () -> Unit,
    onCreateGitHubPat: () -> Unit,
) {
    val filtered = accounts.filter { it.backend == backend }
    SettingsHeader("Agent accounts")
    Text(
        "Multiple accounts per agent. Queue uses 3 parallel tasks and 15 daily tasks per account (configurable below).",
        color = Color.Gray,
        fontSize = 14.sp,
        modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp),
    )
    if (backend == CodingAgentBackend.JULES) {
        SettingsExternalLink(label = "Get Jules API key", onClick = onGetJulesApiKey)
    } else {
        SettingsExternalLink(label = "Get Anthropic API key", onClick = onGetAnthropicApiKey)
        SettingsExternalLink(label = "Create GitHub token", onClick = onCreateGitHubPat)
    }
    filtered.forEach { account ->
        var label by remember(account.id) { mutableStateOf(account.label) }
        var key by remember(account.id) { mutableStateOf(account.apiKey) }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(account.label.ifBlank { "Account" }, fontSize = 14.sp, modifier = Modifier.weight(1f))
                Switch(
                    checked = account.enabled,
                    onCheckedChange = { enabled ->
                        onAccountsChange(
                            accounts.map { if (it.id == account.id) it.copy(enabled = enabled) else it },
                        )
                    },
                )
                IconButton(onClick = { onAccountsChange(accounts.filter { it.id != account.id }) }) {
                    Icon(Icons.Default.Delete, contentDescription = "Remove", tint = Color.Gray)
                }
            }
            OutlinedTextField(
                value = label,
                onValueChange = { v ->
                    label = v
                    onAccountsChange(accounts.map { a -> if (a.id == account.id) a.copy(label = v) else a })
                },
                label = { Text("Account label") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = key,
                onValueChange = { v ->
                    key = v
                    onAccountsChange(accounts.map { a -> if (a.id == account.id) a.copy(apiKey = v.trim()) else a })
                },
                label = { Text("API key") },
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                singleLine = true,
            )
        }
    }
    var newLabel by remember { mutableStateOf("") }
    var newKey by remember { mutableStateOf("") }
    OutlinedTextField(
        value = newLabel,
        onValueChange = { newLabel = it },
        label = { Text("Account label") },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        singleLine = true,
    )
    OutlinedTextField(
        value = newKey,
        onValueChange = { newKey = it },
        label = { Text("API key") },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        singleLine = true,
    )
    TextButton(
        onClick = {
            if (newKey.isNotBlank() && filtered.size < 10) {
                onAccountsChange(
                    accounts + AgentAccount(
                        id = UUID.randomUUID().toString(),
                        label = newLabel.ifBlank { "${backend.name} ${filtered.size + 1}" },
                        backend = backend,
                        apiKey = newKey.trim(),
                    ),
                )
                newKey = ""
                newLabel = ""
            }
        },
        enabled = newKey.isNotBlank() && filtered.size < 10,
        modifier = Modifier.padding(horizontal = 8.dp),
    ) {
        Text("Add account")
    }
}

@Composable
private fun QueuePolicySection(
    policy: QueuePolicy,
    onPolicyChange: (QueuePolicy) -> Unit,
) {
    SettingsHeader("Queue policy")
    var parallel by remember(policy) { mutableStateOf(policy.parallelLimit.toString()) }
    var daily by remember(policy) { mutableStateOf(policy.dailyLimitPerAccount.toString()) }
    OutlinedTextField(
        value = parallel,
        onValueChange = {
            parallel = it.filter { c -> c.isDigit() }.take(2)
            parallel.toIntOrNull()?.let { n -> onPolicyChange(policy.copy(parallelLimit = n.coerceIn(1, 20))) }
        },
        label = { Text("Parallel tasks") },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        singleLine = true,
    )
    OutlinedTextField(
        value = daily,
        onValueChange = {
            daily = it.filter { c -> c.isDigit() }.take(3)
            daily.toIntOrNull()?.let { n -> onPolicyChange(policy.copy(dailyLimitPerAccount = n.coerceIn(1, 100))) }
        },
        label = { Text("Daily tasks per account") },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        singleLine = true,
    )
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Pause queue", modifier = Modifier.weight(1f))
        Switch(
            checked = policy.queuePaused,
            onCheckedChange = { onPolicyChange(policy.copy(queuePaused = it)) },
        )
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Auto-merge when CI passes", modifier = Modifier.weight(1f))
        Switch(
            checked = policy.autoMergeOnCiSuccess,
            onCheckedChange = { onPolicyChange(policy.copy(autoMergeOnCiSuccess = it)) },
        )
    }
}

@Composable
fun JulesApiKeysPage(
    keys: List<String>,
    onKeysChange: (List<String>) -> Unit,
    onGetApiKey: () -> Unit,
) {
    Column(Modifier.verticalScroll(rememberScrollState())) {
        JulesApiKeysSection(keys, onKeysChange, onGetApiKey)
    }
}

@Composable
fun GitHubTokenPage(
    token: String,
    onTokenChange: (String) -> Unit,
    onCreatePat: () -> Unit,
    onManagePats: () -> Unit,
) {
    Column(Modifier.verticalScroll(rememberScrollState())) {
        GitHubTokenSection(token, onTokenChange, onCreatePat, onManagePats)
    }
}

@Composable
private fun SettingsExternalLink(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TextButton(
        onClick = onClick,
        modifier = modifier.padding(horizontal = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(label)
        }
    }
}

@Composable
fun ClaudeCodeApiKeySection(
    apiKey: String,
    onApiKeyChange: (String) -> Unit,
    onGetApiKey: () -> Unit,
    onCreateGitHubPat: () -> Unit,
) {
    SettingsHeader("Claude Code (Managed Agents)")
    Text(
        "Anthropic API key plus a GitHub personal access token. The agent runs in Anthropic's cloud, clones your repo remotely, and pushes branches/PRs.",
        color = Color.Gray,
        fontSize = 14.sp,
        modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp),
    )
    SettingsExternalLink(label = "Get Anthropic API key", onClick = onGetApiKey)
    SettingsExternalLink(label = "Create GitHub token (scopes pre-selected)", onClick = onCreateGitHubPat)
    GitHubPatUrls.CLASSIC_SCOPES.forEach { (scope, detail) ->
        Text(
            "• $scope — $detail",
            color = Color.Gray,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
        )
    }
    OutlinedTextField(
        value = apiKey,
        onValueChange = onApiKeyChange,
        label = { Text("Anthropic API key") },
        placeholder = { Text("sk-ant-...") },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        singleLine = true,
    )
    val configured = apiKey.isNotBlank()
    Text(
        if (configured) "Anthropic API key set."
        else "No key — add one above or set ANTHROPIC_API_KEY in local.properties and rebuild.",
        color = if (configured) Color(0xFF86EFAC) else MaterialTheme.colorScheme.tertiary,
        fontSize = 13.sp,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
fun JulesApiKeysSection(
    keys: List<String>,
    onKeysChange: (List<String>) -> Unit,
    onGetApiKey: () -> Unit,
) {
    SettingsHeader("Jules")
    Text(
        "API keys from Jules Settings. Used on the Jules screen and Android Auto. You can have up to 3 active keys.",
        color = Color.Gray,
        fontSize = 14.sp,
        modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp),
    )
    SettingsExternalLink(label = "Get Jules API key", onClick = onGetApiKey)
    keys.forEachIndexed { index, key ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                maskApiKey(key),
                modifier = Modifier.weight(1f),
                fontSize = 14.sp,
            )
            IconButton(
                onClick = {
                    onKeysChange(keys.filterIndexed { i, _ -> i != index })
                },
            ) {
                Icon(Icons.Default.Delete, contentDescription = "Remove key", tint = Color.Gray)
            }
        }
    }
    var newKey by remember { mutableStateOf("") }
    OutlinedTextField(
        value = newKey,
        onValueChange = { newKey = it },
        label = { Text("Jules API key") },
        placeholder = { Text("Paste key from Jules Settings") },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        visualTransformation = PasswordVisualTransformation(),
        singleLine = true,
    )
    Button(
        onClick = {
            val trimmed = newKey.trim()
            if (trimmed.isNotEmpty() && !keys.contains(trimmed)) {
                onKeysChange(keys + trimmed)
                newKey = ""
            }
        },
        enabled = newKey.trim().isNotEmpty(),
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .fillMaxWidth(),
    ) {
        Text("Add Jules API key")
    }
    val julesConfigured = keys.isNotEmpty()
    Text(
        if (julesConfigured) "${keys.size} key(s) configured."
        else "No Jules API key — add one above or set JULES_KEY in local.properties and rebuild.",
        color = if (julesConfigured) Color(0xFF86EFAC) else MaterialTheme.colorScheme.tertiary,
        fontSize = 13.sp,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
fun GitHubTokenSection(
    token: String,
    onTokenChange: (String) -> Unit,
    onCreatePat: () -> Unit,
    onManagePats: () -> Unit,
) {
    SettingsHeader("GitHub")
    Text(
        "Personal access token for pull requests, Actions workflows, and file content on the coding agent screen.",
        color = Color.Gray,
        fontSize = 14.sp,
        modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp),
    )
    SettingsExternalLink(label = "Create GitHub token (scopes pre-selected)", onClick = onCreatePat)
    SettingsExternalLink(label = "Manage existing tokens on GitHub", onClick = onManagePats)
    GitHubPatUrls.CLASSIC_SCOPES.forEach { (scope, detail) ->
        Text(
            "• $scope — $detail",
            color = Color.Gray,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
        )
    }
    OutlinedTextField(
        value = token,
        onValueChange = onTokenChange,
        label = { Text("GitHub token") },
        placeholder = { Text("ghp_… or github_pat_…") },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        visualTransformation = PasswordVisualTransformation(),
        singleLine = true,
    )
    val tokenConfigured = token.isNotBlank()
    Text(
        if (tokenConfigured) "Token set — GitHub API calls enabled on the Jules screen."
        else "No token — create one above or set GITHUB_TOKEN in local.properties and rebuild.",
        color = if (tokenConfigured) Color(0xFF86EFAC) else MaterialTheme.colorScheme.tertiary,
        fontSize = 13.sp,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

private fun maskApiKey(key: String): String {
    if (key.length <= 8) return "••••••••"
    return "${key.take(4)}…${key.takeLast(4)}"
}

private fun Context.openExternalUrl(url: String) {
    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
}
