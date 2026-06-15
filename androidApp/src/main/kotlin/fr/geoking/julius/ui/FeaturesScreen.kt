package fr.geoking.julius.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import fr.geoking.julius.SettingsManager
import fr.geoking.julius.queue.AgentAccount
import fr.geoking.julius.queue.enabledAccountsFor
import fr.geoking.julius.api.jules.JulesClient
import fr.geoking.julius.persistence.FeatureEntity
import fr.geoking.julius.persistence.JulesSessionEntity
import fr.geoking.julius.repository.FeatureRepository
import fr.geoking.julius.repository.JulesRepository
import kotlinx.coroutines.launch
import java.util.*

private fun List<JulesClient.JulesSource>.resolveProjectName(sourceName: String): String {
    return this.find { it.name == sourceName }?.let {
        it.githubRepo?.let { gr -> "${gr.owner}/${gr.repo}" } ?: it.name
    } ?: sourceName
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun FeaturesScreen(
    onBack: () -> Unit,
    featureRepository: FeatureRepository,
    julesRepository: JulesRepository,
    julesClient: JulesClient,
    settingsManager: SettingsManager,
    onOpenConversation: (JulesSessionEntity) -> Unit
) {
    val features by featureRepository.getAllFeatures().collectAsState(initial = emptyList())
    val settings by settingsManager.settings.collectAsState()
    val apiKeys = settings.julesKeys

    var searchQuery by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }
    var selectedFeature by remember { mutableStateOf<FeatureEntity?>(null) }
    var isEditingFeature by remember { mutableStateOf(false) }
    var sources by remember { mutableStateOf<List<JulesClient.JulesSource>>(emptyList()) }
    var refreshingList by remember { mutableStateOf(false) }
    var refreshSourcesTrigger by remember { mutableIntStateOf(0) }

    val scope = rememberCoroutineScope()

    LaunchedEffect(apiKeys, refreshSourcesTrigger) {
        if (apiKeys.isEmpty()) return@LaunchedEffect
        if (refreshSourcesTrigger > 0) refreshingList = true
        try {
            julesRepository.getSources(apiKeys).collect { list ->
                sources = list
                refreshingList = false
            }
        } catch (_: Exception) {
            refreshingList = false
        }
    }

    var localFeatures by remember { mutableStateOf<List<FeatureEntity>>(emptyList()) }
    LaunchedEffect(features) {
        localFeatures = features
    }

    val filteredFeatures = localFeatures.filter {
        it.title.contains(searchQuery, ignoreCase = true) ||
        it.description.contains(searchQuery, ignoreCase = true)
    }

    BackHandler {
        if (isSearching) {
            isSearching = false
            searchQuery = ""
        } else if (selectedFeature != null) {
            selectedFeature = null
            isEditingFeature = false
        } else {
            onBack()
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = ColorHelper.JulesBg
    ) {
        Column {
            // Header
            if (isSearching && selectedFeature == null) {
                TopAppBar(
                    title = {
                        TextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("Search features...", color = Color.White.copy(alpha = 0.5f)) },
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                cursorColor = ColorHelper.JulesAccent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            ),
                            singleLine = true
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            isSearching = false
                            searchQuery = ""
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }
                    },
                    actions = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear", tint = Color.White)
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = Color.White,
                        navigationIconContentColor = Color.White,
                        actionIconContentColor = Color.White
                    )
                )
            } else {
                TopAppBar(
                    title = {
                        Text(
                            text = if (selectedFeature != null) selectedFeature!!.title else "Features",
                            color = Color.White,
                            fontSize = 18.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            if (selectedFeature != null) {
                                selectedFeature = null
                                isEditingFeature = false
                            } else onBack()
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }
                    },
                    actions = {
                        if (selectedFeature == null) {
                            IconButton(onClick = { isSearching = true }) {
                                Icon(Icons.Default.Search, contentDescription = "Search", tint = Color.White)
                            }
                            IconButton(onClick = { showAddDialog = true }) {
                                Icon(Icons.Default.Add, contentDescription = "Add Feature", tint = Color.White)
                            }
                        } else {
                            var showDetailMenu by remember { mutableStateOf(false) }
                            val clipboard = LocalClipboard.current

                            IconButton(onClick = {
                                scope.launch {
                                    clipboard.setClipEntry(androidx.compose.ui.platform.ClipEntry(
                                        android.content.ClipData.newPlainText("feature_prompt", selectedFeature!!.description.ifBlank { selectedFeature!!.title })
                                    ))
                                }
                            }) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy Prompt", tint = Color.White)
                            }

                            Box {
                                IconButton(onClick = { showDetailMenu = true }) {
                                    Icon(Icons.Default.MoreVert, contentDescription = "More", tint = Color.White)
                                }
                                DropdownMenu(
                                    expanded = showDetailMenu,
                                    onDismissRequest = { showDetailMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Retry") },
                                        onClick = {
                                            scope.launch {
                                                try {
                                                    val account = settings.enabledAccountsFor(settings.codingAgentBackend).firstOrNull() ?: return@launch
                                                    featureRepository.replayFeature(selectedFeature!!.id, account)
                                                } catch (_: Exception) {}
                                            }
                                            showDetailMenu = false
                                        },
                                        leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Delete", color = Color.Red) },
                                        onClick = {
                                            scope.launch {
                                                featureRepository.deleteFeature(selectedFeature!!.id)
                                                selectedFeature = null
                                            }
                                            showDetailMenu = false
                                        },
                                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = Color.Red) }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Close") },
                                        onClick = {
                                            selectedFeature = null
                                            isEditingFeature = false
                                            showDetailMenu = false
                                        },
                                        leadingIcon = { Icon(Icons.Default.Close, contentDescription = null) }
                                    )
                                }
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = Color.White,
                        navigationIconContentColor = Color.White,
                        actionIconContentColor = Color.White
                    )
                )
            }

            if (selectedFeature == null) {
                // List View
                var newFeatureTitle by remember { mutableStateOf("") }
                PullToRefreshBox(
                    isRefreshing = refreshingList,
                    onRefresh = { refreshSourcesTrigger++ },
                    modifier = Modifier.weight(1f),
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(filteredFeatures, key = { it.id }) { feature ->
                                val clipboard = LocalClipboard.current
                                FeatureItem(
                                    feature = feature,
                                    sources = sources,
                                    onClick = { selectedFeature = feature },
                                    onDelete = {
                                        scope.launch { featureRepository.deleteFeature(feature.id) }
                                    },
                                    onRetry = {
                                        scope.launch {
                                            try {
                                                val account = settings.enabledAccountsFor(settings.codingAgentBackend).firstOrNull() ?: return@launch
                                                featureRepository.replayFeature(feature.id, account)
                                            } catch (_: Exception) {}
                                        }
                                    },
                                    onCopyPrompt = {
                                        scope.launch {
                                            clipboard.setClipEntry(androidx.compose.ui.platform.ClipEntry(
                                                android.content.ClipData.newPlainText("feature_prompt", feature.description.ifBlank { feature.title })
                                            ))
                                        }
                                    },
                                    onMoveUp = {
                                        val idx = localFeatures.indexOf(feature)
                                        if (idx > 0) {
                                            val newList = localFeatures.toMutableList()
                                            newList.removeAt(idx)
                                            newList.add(idx - 1, feature)
                                            localFeatures = newList
                                            scope.launch { featureRepository.updatePositions(newList) }
                                        }
                                    },
                                    onMoveDown = {
                                        val idx = localFeatures.indexOf(feature)
                                        if (idx < localFeatures.size - 1) {
                                            val newList = localFeatures.toMutableList()
                                            newList.removeAt(idx)
                                            newList.add(idx + 1, feature)
                                            localFeatures = newList
                                            scope.launch { featureRepository.updatePositions(newList) }
                                        }
                                    },
                                    isFirst = localFeatures.firstOrNull()?.id == feature.id,
                                    isLast = localFeatures.lastOrNull()?.id == feature.id,
                                )
                            }
                        }

                        // Quick Add Bar (WhatsApp style)
                        Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val currentProjectName = remember(settings.lastJulesRepoId, sources) {
                            val resolved = sources.resolveProjectName(settings.lastJulesRepoId)
                            if (resolved == settings.lastJulesRepoId) {
                                settings.lastJulesRepoName.takeIf { it.isNotBlank() } ?: resolved
                            } else {
                                resolved
                            }
                        }

                        OutlinedTextField(
                            value = newFeatureTitle,
                            onValueChange = { newFeatureTitle = it },
                            modifier = Modifier.weight(1f),
                            placeholder = {
                                Text(
                                    if (currentProjectName != null) "Add to $currentProjectName..." else "Add a new feature...",
                                    color = Color.White.copy(alpha = 0.5f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            shape = RoundedCornerShape(24.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                cursorColor = ColorHelper.JulesAccent,
                                focusedBorderColor = ColorHelper.JulesAccent,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.1f)
                            ),
                            maxLines = 5
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(
                            onClick = {
                                if (newFeatureTitle.isNotBlank()) {
                                    scope.launch {
                                        val sourceName = settings.lastJulesRepoId.takeIf { id ->
                                            id.isNotBlank() && sources.any { it.name == id }
                                        } ?: sources.firstOrNull()?.name ?: ""
                                        featureRepository.addFeature(
                                            title = newFeatureTitle,
                                            description = "",
                                            priority = 0,
                                            sourceName = sourceName
                                        )
                                        newFeatureTitle = ""
                                    }
                                }
                            },
                            enabled = newFeatureTitle.isNotBlank()
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Add",
                                tint = if (newFeatureTitle.isNotBlank()) ColorHelper.JulesAccent else Color.Gray
                            )
                        }
                    }
                    }
                }
            } else {
                // Detail View
                FeatureDetailContent(
                    feature = selectedFeature!!,
                    sources = sources,
                    julesRepository = julesRepository,
                    featureRepository = featureRepository,
                    settingsManager = settingsManager,
                    apiKeys = apiKeys,
                    onOpenConversation = onOpenConversation,
                    onUpdateFeature = { selectedFeature = it },
                    isEditing = isEditingFeature,
                    onEditChange = { isEditingFeature = it }
                )
            }
        }
    }

    if (showAddDialog) {
        AddFeatureDialog(
            sources = sources,
            initialSourceName = settings.lastJulesRepoId,
            onDismiss = { showAddDialog = false },
            onConfirm = { title, desc, priority, source ->
                scope.launch {
                    featureRepository.addFeature(title, desc, priority, source)
                    showAddDialog = false
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FeatureItem(
    feature: FeatureEntity,
    sources: List<JulesClient.JulesSource>,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onRetry: () -> Unit,
    onCopyPrompt: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    isFirst: Boolean,
    isLast: Boolean
) {
    val projectName = remember(feature.sourceName, sources) {
        sources.resolveProjectName(feature.sourceName)
    }
    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = { showMenu = true }
            ),
        colors = CardDefaults.cardColors(containerColor = ColorHelper.JulesCardAgent)
    ) {
        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
            DropdownMenuItem(
                text = { Text("Copy Prompt") },
                onClick = { onCopyPrompt(); showMenu = false },
                leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) }
            )
            DropdownMenuItem(
                text = { Text("Retry") },
                onClick = { onRetry(); showMenu = false },
                leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) }
            )
            DropdownMenuItem(
                text = { Text("Delete", color = Color.Red) },
                onClick = { onDelete(); showMenu = false },
                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = Color.Red) }
            )
        }
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                if (projectName.isNotBlank()) {
                    Text(
                        text = projectName,
                        color = ColorHelper.JulesAccent,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(feature.title, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(feature.description, color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Spacer(modifier = Modifier.width(8.dp))
            StatusBadge(feature.status)

            Spacer(modifier = Modifier.width(8.dp))
            Column {
                IconButton(onClick = onMoveUp, enabled = !isFirst, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Move Up", tint = if (isFirst) Color.Gray else Color.White)
                }
                IconButton(onClick = onMoveDown, enabled = !isLast, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Move Down", tint = if (isLast) Color.Gray else Color.White)
                }
            }
        }
    }
}

@Composable
fun FeatureDetailContent(
    feature: FeatureEntity,
    sources: List<JulesClient.JulesSource>,
    julesRepository: JulesRepository,
    featureRepository: FeatureRepository,
    settingsManager: SettingsManager,
    apiKeys: List<String>,
    onOpenConversation: (JulesSessionEntity) -> Unit,
    onUpdateFeature: (FeatureEntity) -> Unit,
    isEditing: Boolean,
    onEditChange: (Boolean) -> Unit
) {
    val scope = rememberCoroutineScope()
    var editedTitle by remember(feature) { mutableStateOf(feature.title) }
    var editedDescription by remember(feature) { mutableStateOf(feature.description) }
    var sessions by remember { mutableStateOf<List<JulesSessionEntity>>(emptyList()) }
    var messageDraft by remember { mutableStateOf("") }
    var showInProgressOnly by remember { mutableStateOf(false) }
    var showUnmergedOnly by remember { mutableStateOf(false) }
    var refreshingSessions by remember { mutableStateOf(false) }
    var refreshSessionsTrigger by remember { mutableIntStateOf(0) }
    val settings by settingsManager.settings.collectAsState()

    val filteredSessions = remember(sessions, showInProgressOnly, showUnmergedOnly) {
        sessions.filter { session ->
            val matchesInProgress = !showInProgressOnly || !session.isFinished
            val matchesUnmerged = !showUnmergedOnly || (session.prUrl != null && session.prState == "open")
            matchesInProgress && matchesUnmerged
        }
    }

    LaunchedEffect(feature.id, settings.githubApiKey, refreshSessionsTrigger) {
        if (refreshSessionsTrigger > 0) refreshingSessions = true
        julesRepository.getSessions(apiKeys, feature.sourceName, settings.githubApiKey).collect { list ->
            sessions = list.filter { it.featureId == feature.id }
            refreshingSessions = false
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        if (isEditing) {
            OutlinedTextField(
                value = editedTitle,
                onValueChange = { editedTitle = it },
                label = { Text("Title") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = editedDescription,
                onValueChange = { editedDescription = it },
                label = { Text("Description") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedLabelColor = ColorHelper.JulesAccent,
                    unfocusedLabelColor = Color.White.copy(alpha = 0.5f),
                    focusedBorderColor = ColorHelper.JulesAccent,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.2f)
                )
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = {
                    editedTitle = feature.title
                    editedDescription = feature.description
                    onEditChange(false)
                }) { Text("Cancel") }
                Button(onClick = {
                    val updated = feature.copy(title = editedTitle, description = editedDescription)
                    scope.launch {
                        featureRepository.updateFeature(updated)
                        onUpdateFeature(updated)
                        onEditChange(false)
                    }
                }, colors = ButtonDefaults.buttonColors(containerColor = ColorHelper.JulesAccent)) { Text("Save") }
            }
        } else {
            val projectName = remember(feature.sourceName, sources) {
                sources.resolveProjectName(feature.sourceName)
            }
            if (projectName.isNotBlank()) {
                Text(
                    text = projectName,
                    color = ColorHelper.JulesAccent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(feature.title, color = Color.White, fontSize = 20.sp, modifier = Modifier.weight(1f))
                IconButton(onClick = { onEditChange(true) }) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit", tint = Color.White)
                }
            }
            StatusBadge(feature.status)
            if (feature.description.isNotBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(feature.description, color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp)
            }

            Spacer(modifier = Modifier.height(16.dp))

            PullToRefreshBox(
                isRefreshing = refreshingSessions,
                onRefresh = { refreshSessionsTrigger++ },
                modifier = Modifier.weight(1f),
            ) {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(filteredSessions, key = { it.id }) { session ->
                    androidx.compose.material3.ListItem(
                        headlineContent = { Text(session.title.ifBlank { session.prompt }, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        supportingContent = {
                            Column {
                                val backend = if (session.id.startsWith("sesn_")) "CLAUDE_CODE" else "JULES"
                                Text("$backend · ${session.sourceName}", fontSize = 11.sp, color = Color.White.copy(alpha = 0.5f), modifier = Modifier.padding(bottom = 2.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    StatusBadge(session.sessionState ?: "Active")
                                    Spacer(modifier = Modifier.size(8.dp))
                                    Text(session.updateTime?.take(16) ?: "", fontSize = 12.sp)
                                }
                            }
                        },
                        trailingContent = {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                        },
                        modifier = Modifier.clickable { onOpenConversation(session) },
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

            FeatureActionBar(
                onStartFeature = {
                    scope.launch {
                        try {
                            val account = settings.enabledAccountsFor(settings.codingAgentBackend).firstOrNull()
                                ?: return@launch
                            val sessionId = featureRepository.startFeatureWithTitle(feature.id, account)
                            val session = julesRepository.getSession(sessionId)
                            if (session != null) onOpenConversation(session)
                        } catch (e: Exception) {
                            // Show error
                        }
                    }
                },
                onArchiveCompleted = {
                    val completed = sessions.filter { it.isFinished }
                    scope.launch {
                        completed.forEach { julesRepository.archiveSession(it.id) }
                        // Refresh will happen via the LaunchedEffect if getSessions is observing
                    }
                },
                onReplayPrompts = {
                    scope.launch {
                        try {
                            val account = settings.enabledAccountsFor(settings.codingAgentBackend).firstOrNull()
                                ?: return@launch
                            featureRepository.replayFeature(feature.id, account)
                        } catch (e: Exception) {
                            // Show error
                        }
                    }
                },
                onFinishFeature = {
                    // Find session with open PR
                    val sessionWithPr = sessions.find { it.prUrl != null && it.prState == "open" }
                    if (sessionWithPr != null) {
                        scope.launch {
                            julesRepository.mergePr(
                                settings.githubApiKey,
                                sessionWithPr.id,
                                sessionWithPr.prUrl!!,
                                deleteBranch = true
                            )
                        }
                    }
                },
                hasOpenPr = sessions.any { it.prUrl != null && it.prState == "open" },
                showInProgressOnly = showInProgressOnly,
                onShowInProgressOnlyChange = { showInProgressOnly = it },
                showUnmergedOnly = showUnmergedOnly,
                onShowUnmergedOnlyChange = { showUnmergedOnly = it }
            )

            FeatureChatInput(
                value = messageDraft,
                onValueChange = { messageDraft = it },
                placeholder = androidx.compose.ui.res.stringResource(fr.geoking.julius.R.string.message_jules_on, feature.title),
                onSend = {
                    val prompt = messageDraft.trim()
                    if (prompt.isEmpty()) return@FeatureChatInput
                    scope.launch {
                        val sessionId = julesRepository.createSession(
                            apiKeys = apiKeys,
                            prompt = prompt,
                            source = feature.sourceName,
                            title = prompt.take(80),
                            featureId = feature.id
                        )
                        messageDraft = ""
                        val session = julesRepository.getSession(sessionId)
                        if (session != null) onOpenConversation(session)
                    }
                },
                enabled = apiKeys.isNotEmpty()
            )
        }
    }
}

@Composable
fun FeatureActionBar(
    onStartFeature: () -> Unit = {},
    onArchiveCompleted: () -> Unit,
    onReplayPrompts: () -> Unit = {},
    onFinishFeature: () -> Unit = {},
    hasOpenPr: Boolean = false,
    showInProgressOnly: Boolean,
    onShowInProgressOnlyChange: (Boolean) -> Unit,
    showUnmergedOnly: Boolean,
    onShowUnmergedOnlyChange: (Boolean) -> Unit
) {
    val scrollState = rememberScrollState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Start Button
        Surface(
            modifier = Modifier.clickable(onClick = onStartFeature),
            color = ColorHelper.JulesAccent.copy(alpha = 0.1f),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, ColorHelper.JulesAccent.copy(alpha = 0.3f))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = ColorHelper.JulesAccent, modifier = Modifier.size(16.dp))
                Text(
                    "Start",
                    color = ColorHelper.JulesAccent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Archive Button
        Surface(
            modifier = Modifier.clickable(onClick = onArchiveCompleted),
            color = Color.White.copy(alpha = 0.05f),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(Icons.Default.Archive, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                Text(
                    androidx.compose.ui.res.stringResource(fr.geoking.julius.R.string.archive_completed),
                    color = Color.White,
                    fontSize = 12.sp
                )
            }
        }

        // In Progress Filter
        FilterChip(
            selected = showInProgressOnly,
            onClick = { onShowInProgressOnlyChange(!showInProgressOnly) },
            label = { Text(androidx.compose.ui.res.stringResource(fr.geoking.julius.R.string.in_progress)) },
            colors = FilterChipDefaults.filterChipColors(
                labelColor = Color.White.copy(alpha = 0.6f),
                selectedLabelColor = Color.White,
                selectedContainerColor = ColorHelper.JulesAccent.copy(alpha = 0.2f)
            ),
            border = FilterChipDefaults.filterChipBorder(
                enabled = true,
                selected = showInProgressOnly,
                borderColor = Color.White.copy(alpha = 0.1f),
                selectedBorderColor = ColorHelper.JulesAccent.copy(alpha = 0.5f)
            )
        )

        // Replay Button
        Surface(
            modifier = Modifier.clickable(onClick = onReplayPrompts),
            color = Color.White.copy(alpha = 0.05f),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                Text(
                    "Replay Prompts",
                    color = Color.White,
                    fontSize = 12.sp
                )
            }
        }

        // Finish/Merge Button
        if (hasOpenPr) {
            Surface(
                modifier = Modifier.clickable(onClick = onFinishFeature),
                color = Color.Green.copy(alpha = 0.1f),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Color.Green.copy(alpha = 0.3f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(Icons.Default.Merge, contentDescription = null, tint = Color.Green, modifier = Modifier.size(16.dp))
                    Text(
                        "Finish & Merge",
                        color = Color.Green,
                        fontSize = 12.sp
                    )
                }
            }
        }

        // Unmerged Filter
        FilterChip(
            selected = showUnmergedOnly,
            onClick = { onShowUnmergedOnlyChange(!showUnmergedOnly) },
            label = { Text(androidx.compose.ui.res.stringResource(fr.geoking.julius.R.string.unmerged_code)) },
            colors = FilterChipDefaults.filterChipColors(
                labelColor = Color.White.copy(alpha = 0.6f),
                selectedLabelColor = Color.White,
                selectedContainerColor = ColorHelper.JulesAccent.copy(alpha = 0.2f)
            ),
            border = FilterChipDefaults.filterChipBorder(
                enabled = true,
                selected = showUnmergedOnly,
                borderColor = Color.White.copy(alpha = 0.1f),
                selectedBorderColor = ColorHelper.JulesAccent.copy(alpha = 0.5f)
            )
        )
    }
}

@Composable
fun FeatureChatInput(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    onSend: () -> Unit,
    enabled: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text(placeholder, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            enabled = enabled,
            shape = RoundedCornerShape(24.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = ColorHelper.JulesAccent,
                unfocusedBorderColor = Color.White.copy(alpha = 0.1f),
                cursorColor = ColorHelper.JulesAccent
            )
        )
        IconButton(
            onClick = onSend,
            enabled = enabled && value.isNotBlank(),
            modifier = Modifier
                .size(48.dp)
                .background(
                    if (enabled && value.isNotBlank()) ColorHelper.JulesAccent else Color.Gray.copy(alpha = 0.2f),
                    CircleShape
                )
        ) {
            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = Color.White)
        }
    }
}

@Composable
fun StatusBadge(status: String) {
    val color = when (status) {
        "COMPLETED" -> Color.Green
        "FAILED" -> Color.Red
        "IN_PROGRESS" -> ColorHelper.JulesAccent
        "QUEUED" -> Color.Yellow
        else -> Color.Gray
    }
    Surface(
        color = color.copy(alpha = 0.1f),
        shape = RoundedCornerShape(4.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.5f))
    ) {
        Text(
            text = status,
            color = color,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddFeatureDialog(
    sources: List<JulesClient.JulesSource>,
    onDismiss: () -> Unit,
    onConfirm: (String, String, Int, String) -> Unit,
    initialSourceName: String? = null,
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var priority by remember { mutableStateOf(0) }
    var selectedSource by remember {
        mutableStateOf(
            initialSourceName?.takeIf { name -> sources.any { it.name == name } }
                ?: sources.firstOrNull()?.name
                ?: ""
        )
    }
    var expanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Feature") },
        text = {
            Column {
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Title") })
                OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("Description") })

                Spacer(modifier = Modifier.height(8.dp))

                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded }
                ) {
                    OutlinedTextField(
                        value = selectedSource,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Source Repository") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier.menuAnchor(
                            type = ExposedDropdownMenuAnchorType.PrimaryNotEditable,
                            enabled = true,
                        )
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        sources.forEach { source ->
                            DropdownMenuItem(
                                text = { Text(source.githubRepo?.let { "${it.owner}/${it.repo}" } ?: source.name) },
                                onClick = {
                                    selectedSource = source.name
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(title, description, priority, selectedSource) }, enabled = title.isNotBlank() && selectedSource.isNotBlank()) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
