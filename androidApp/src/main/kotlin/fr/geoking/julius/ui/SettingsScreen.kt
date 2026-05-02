package fr.geoking.julius.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import fr.geoking.julius.BuildConfig
import fr.geoking.julius.feature.auth.GoogleAuthManager
import fr.geoking.julius.*
import fr.geoking.julius.agents.LlamatikModelHelper
import fr.geoking.julius.agents.LlamatikModelVariant

enum class SettingsScreenPage { Main, Agents, AgentDetails, ModelDownload }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settingsManager: SettingsManager,
    authManager: GoogleAuthManager,
    errorLog: List<Any>,
    onDismiss: () -> Unit,
    initialScreenStack: List<SettingsScreenPage>? = null,
    onInitialRouteConsumed: () -> Unit = {}
) {
    var navigationStack = rememberSaveable(
        saver = listSaver(
            save = { it.map { page -> page.name } },
            restore = { it.map { name -> SettingsScreenPage.valueOf(name as String) }.toMutableStateList() }
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

    var selectedAgentForDetails by rememberSaveable { mutableStateOf<AgentType?>(null) }

    fun goBack() {
        if (navigationStack.size > 1) {
            navigationStack.removeAt(navigationStack.size - 1)
        } else {
            onDismiss()
        }
    }

    BackHandler(enabled = true, onBack = { goBack() })

    val settings by settingsManager.settings.collectAsState()
    val currentPage = navigationStack.last()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when (currentPage) {
                            SettingsScreenPage.Main -> "Settings"
                            SettingsScreenPage.Agents -> "Agents"
                            SettingsScreenPage.AgentDetails -> selectedAgentForDetails?.name ?: "Agent Settings"
                            SettingsScreenPage.ModelDownload -> "Download Model"
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
    ) { padding ->
        Surface(
            modifier = Modifier.padding(padding).fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            when (currentPage) {
                SettingsScreenPage.Main -> MainSettingsPage(
                    settings = settings,
                    authManager = authManager,
                    onNavigateToAgents = {
                        navigationStack.add(SettingsScreenPage.Agents)
                        @Suppress("UNUSED_EXPRESSION") Unit
                    }
                )
                SettingsScreenPage.Agents -> AgentsPage(
                    settings = settings,
                    onAgentSelected = { agent ->
                        settingsManager.saveSettings(settings.copy(selectedAgent = agent))
                    },
                    onNavigateToDetails = { agent ->
                        selectedAgentForDetails = agent
                        navigationStack.add(SettingsScreenPage.AgentDetails)
                        @Suppress("UNUSED_EXPRESSION") Unit
                    }
                )
                SettingsScreenPage.AgentDetails -> {
                    selectedAgentForDetails?.let { agent ->
                        AgentDetailsPage(
                            agent = agent,
                            settings = settings,
                            onSettingsChange = { settingsManager.saveSettings(it) },
                            onNavigateToModelDownload = {
                                navigationStack.add(SettingsScreenPage.ModelDownload)
                                @Suppress("UNUSED_EXPRESSION") Unit
                            }
                        )
                    }
                }
                SettingsScreenPage.ModelDownload -> {
                    selectedAgentForDetails?.let { agent ->
                        ModelDownloadPage(
                            agent = agent,
                            settingsManager = settingsManager
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MainSettingsPage(
    settings: AppSettings,
    authManager: GoogleAuthManager,
    onNavigateToAgents: () -> Unit,
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
                title = "Agents",
                subtitle = "Manage AI providers and on-device models",
                onClick = { onNavigateToAgents() }
            )
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
fun AgentsPage(
    settings: AppSettings,
    onAgentSelected: (AgentType) -> Unit,
    onNavigateToDetails: (AgentType) -> Unit
) {
    var filterMode by remember { mutableIntStateOf(0) } // 0: All, 1: Remote, 2: Embedded

    Column {
        TabRow(selectedTabIndex = filterMode) {
            Tab(selected = filterMode == 0, onClick = { filterMode = 0 }) {
                Text("All", modifier = Modifier.padding(12.dp))
            }
            Tab(selected = filterMode == 1, onClick = { filterMode = 1 }) {
                Text("Remote", modifier = Modifier.padding(12.dp))
            }
            Tab(selected = filterMode == 2, onClick = { filterMode = 2 }) {
                Text("Embedded", modifier = Modifier.padding(12.dp))
            }
        }

        val allAgents = AgentType.entries.filter { it.enabled }
        val filteredAgents = when (filterMode) {
            1 -> allAgents.filter { !it.isEmbedded }
            2 -> allAgents.filter { it.isEmbedded }
            else -> allAgents
        }

        LazyColumn {
            val remote = filteredAgents.filter { !it.isEmbedded }
            val embedded = filteredAgents.filter { it.isEmbedded }

            if (remote.isNotEmpty()) {
                item { SettingsHeader("Remote Agents") }
                items(remote) { agent ->
                    AgentListItem(
                        agent = agent,
                        isSelected = settings.selectedAgent == agent,
                        onSelect = { onAgentSelected(agent) },
                        onNavigateToDetails = { onNavigateToDetails(agent) }
                    )
                }
            }

            if (embedded.isNotEmpty()) {
                item { SettingsHeader("Embedded Agents") }
                items(embedded) { agent ->
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
    onSettingsChange: (AppSettings) -> Unit,
    onNavigateToModelDownload: () -> Unit
) {
    Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
        if (agent.isEmbedded) {
            SettingsListItem(
                title = "Download Model",
                subtitle = "Select and download on-device model variants",
                onClick = onNavigateToModelDownload
            )
            Spacer(Modifier.height(16.dp))
        }

        when (agent) {
            AgentType.OpenAI -> {
                OutlinedTextField(
                    value = settings.openAiKey,
                    onValueChange = { onSettingsChange(settings.copy(openAiKey = it)) },
                    label = { Text("OpenAI API Key") },
                    modifier = Modifier.fillMaxWidth()
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
                OutlinedTextField(
                    value = settings.geminiKey,
                    onValueChange = { onSettingsChange(settings.copy(geminiKey = it)) },
                    label = { Text("Gemini API Key") },
                    modifier = Modifier.fillMaxWidth()
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
                OutlinedTextField(
                    value = settings.perplexityKey,
                    onValueChange = { onSettingsChange(settings.copy(perplexityKey = it)) },
                    label = { Text("Perplexity API Key") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = settings.elevenLabsKey,
                    onValueChange = { onSettingsChange(settings.copy(elevenLabsKey = it)) },
                    label = { Text("ElevenLabs API Key") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            AgentType.DeepSeek -> {
                OutlinedTextField(
                    value = settings.deepSeekKey,
                    onValueChange = { onSettingsChange(settings.copy(deepSeekKey = it)) },
                    label = { Text("DeepSeek API Key") },
                    modifier = Modifier.fillMaxWidth()
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
                OutlinedTextField(
                    value = settings.groqKey,
                    onValueChange = { onSettingsChange(settings.copy(groqKey = it)) },
                    label = { Text("Groq API Key") },
                    modifier = Modifier.fillMaxWidth()
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
                OutlinedTextField(
                    value = settings.openRouterKey,
                    onValueChange = { onSettingsChange(settings.copy(openRouterKey = it)) },
                    label = { Text("OpenRouter API Key") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = settings.openRouterModel,
                    onValueChange = { onSettingsChange(settings.copy(openRouterModel = it)) },
                    label = { Text("OpenRouter Model") },
                    modifier = Modifier.fillMaxWidth()
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
                if (agent.isEmbedded) {
                    Text("${agent.name} is an on-device agent.", fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    Text("Configuration for this agent is handled automatically or via shared embedded settings.")
                } else {
                    Text("Configuration for ${agent.name} coming soon.", color = Color.Gray)
                }
            }
        }
    }
}

@Composable
fun ModelDownloadPage(
    agent: AgentType,
    settingsManager: SettingsManager
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val helper = remember { LlamatikModelHelper(context) }
    val scope = rememberCoroutineScope()
    val settings by settingsManager.settings.collectAsState()

    var downloadVariant by remember { mutableStateOf<LlamatikModelVariant?>(null) }
    var downloadBytes by remember { mutableLongStateOf(0L) }
    var downloadTotal by remember { mutableStateOf<Long?>(null) }
    var downloadError by remember { mutableStateOf<String?>(null) }

    val variants = remember(agent) {
        LlamatikModelVariant.entries.filter { it.forAgentName == agent.name }
    }

    LazyColumn(Modifier.fillMaxSize()) {
        if (downloadVariant != null) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
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
                            val pct = (progress * 100).toInt()
                            Text("$pct% (${downloadBytes / 1024 / 1024} MB / ${downloadTotal?.let { it / 1024 / 1024 } ?: "?"} MB)")
                        }
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = { downloadVariant = null; downloadError = null }) {
                            Text("Cancel")
                        }
                    }
                }
            }
        }

        items(variants) { variant ->
            val isDownloaded = helper.isVariantDownloaded(variant)
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
                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
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
                Text(if (agent.isEmbedded) "Embedded" else "Remote", color = Color.Gray, fontSize = 12.sp)
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
