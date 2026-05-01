package fr.geoking.julius.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.geoking.julius.*

enum class SettingsScreenPage { Main, Agents, AgentDetails }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settingsManager: SettingsManager,
    authManager: fr.geoking.julius.feature.auth.GoogleAuthManager,
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
                            onSettingsChange = { settingsManager.saveSettings(it) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MainSettingsPage(onNavigateToAgents: () -> Unit) {
    LazyColumn {
        item {
            SettingsListItem(
                title = "Agents",
                subtitle = "Manage AI providers and on-device models",
                onClick = { onNavigateToAgents() }
            )
        }
    }
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
    onSettingsChange: (AppSettings) -> Unit
) {
    Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
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
                Spacer(Modifier.height(16.dp))
                Text("Select model variant for download:", color = Color.Gray, fontSize = 14.sp)
                // In a real app, this would be a dropdown or list of variants
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
