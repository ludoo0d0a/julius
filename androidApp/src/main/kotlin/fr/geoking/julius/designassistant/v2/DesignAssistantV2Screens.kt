package fr.geoking.julius.designassistant.v2

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.geoking.julius.SettingsManager
import fr.geoking.julius.designassistant.ChatMessageKind
import fr.geoking.julius.designassistant.DesignAssistantColors
import fr.geoking.julius.designassistant.DesignAssistantMapper
import fr.geoking.julius.designassistant.DesignAssistantSampleData
import fr.geoking.julius.designassistant.DesignAssistantV2State
import fr.geoking.julius.designassistant.DesignChatMessage
import fr.geoking.julius.designassistant.DesignFeature
import fr.geoking.julius.designassistant.DesignProject
import fr.geoking.julius.designassistant.FeatureStatus
import fr.geoking.julius.designassistant.WorkspaceTab
import fr.geoking.julius.designassistant.components.CollapsibleCodeBlock
import fr.geoking.julius.designassistant.components.DesignAssistantNavyHeader
import fr.geoking.julius.designassistant.components.DesignAssistantTheme
import fr.geoking.julius.designassistant.components.DesignBreadcrumb
import fr.geoking.julius.designassistant.components.DesignChatInputBar
import fr.geoking.julius.designassistant.components.JulesAvatar
import fr.geoking.julius.designassistant.components.StatusDot
import fr.geoking.julius.designassistant.components.TechnicalStatusBanner
import fr.geoking.julius.designassistant.components.WhiteContentSheet
import fr.geoking.julius.designassistant.components.WorkspaceTabRow
import fr.geoking.julius.persistence.JulesSessionEntity
import fr.geoking.julius.repository.FeatureRepository
import fr.geoking.julius.repository.GitHubBuildRepository
import fr.geoking.julius.repository.JulesRepository
import fr.geoking.julius.ui.AddFeatureDialog
import kotlinx.coroutines.launch

enum class V2Screen { PROJECTS, FEATURES, WORKSPACE }

/** V2 — Projets → Features (dashboard) → Conception & Chat (onglets swipe). */
@Composable
fun DesignAssistantV2Host(
    onBack: () -> Unit,
    onSwitchToV1: () -> Unit,
    julesRepository: JulesRepository? = null,
    featureRepository: FeatureRepository? = null,
    settingsManager: SettingsManager? = null,
    buildRepository: GitHubBuildRepository? = null,
) {
    val scope = rememberCoroutineScope()
    val hasRepos = julesRepository != null && featureRepository != null &&
        settingsManager != null && buildRepository != null

    val controller = if (hasRepos) {
        remember(julesRepository, featureRepository, settingsManager, buildRepository) {
            DesignAssistantV2State(
                scope = scope,
                julesRepository = julesRepository!!,
                featureRepository = featureRepository!!,
                settingsManager = settingsManager!!,
                buildRepository = buildRepository!!,
            )
        }
    } else {
        null
    }

    DisposableEffect(controller) {
        onDispose { controller?.dispose() }
    }

    LaunchedEffect(controller) {
        controller?.loadProjects()
    }

    var screen by remember { mutableStateOf(V2Screen.PROJECTS) }
    var project by remember { mutableStateOf<DesignProject?>(null) }
    var feature by remember { mutableStateOf<DesignFeature?>(null) }

    when (screen) {
        V2Screen.PROJECTS -> {
            if (controller != null) {
                ProjectsHomeScreen(
                    projects = controller.projects,
                    loading = controller.projectsLoading,
                    error = controller.projectsError,
                    onBack = onBack,
                    onSwitchToV1 = onSwitchToV1,
                    onProjectClick = { p ->
                        project = p
                        controller.loadFeaturesForProject(p.id)
                        screen = V2Screen.FEATURES
                    },
                )
            } else {
                ProjectsHomeScreen(
                    projects = DesignAssistantSampleData.projects,
                    onBack = onBack,
                    onSwitchToV1 = onSwitchToV1,
                    onProjectClick = { p ->
                        project = p
                        screen = V2Screen.FEATURES
                    },
                )
            }
        }
        V2Screen.FEATURES -> {
            val p = project ?: return
            if (controller != null) {
                val liveProject = controller.projectWithFeatures(p.id) ?: p
                ProjectFeaturesScreen(
                    project = liveProject,
                    searchQuery = controller.featureSearchQuery,
                    onSearchChange = { controller.featureSearchQuery = it },
                    onAddClick = { controller.showAddFeatureDialog = true },
                    onBack = { screen = V2Screen.PROJECTS },
                    onFeatureClick = { f ->
                        feature = f
                        controller.openWorkspace(liveProject, f)
                        screen = V2Screen.WORKSPACE
                    },
                )
                if (controller.showAddFeatureDialog) {
                    AddFeatureDialog(
                        sources = controller.sources,
                        initialSourceName = p.id,
                        onDismiss = { controller.showAddFeatureDialog = false },
                        onConfirm = { title, desc, _, source ->
                            controller.addFeature(title, desc, source)
                        },
                    )
                }
            } else {
                ProjectFeaturesScreen(
                    project = p,
                    onBack = { screen = V2Screen.PROJECTS },
                    onFeatureClick = { f ->
                        feature = f
                        screen = V2Screen.WORKSPACE
                    },
                )
            }
        }
        V2Screen.WORKSPACE -> {
            val p = project ?: return
            val f = feature ?: return
            if (controller != null) {
                ConceptionWorkspaceScreen(
                    project = p,
                    feature = f,
                    messages = controller.chatMessages.toList(),
                    codeContent = controller.workspaceCode,
                    modifiedFiles = controller.workspaceFiles,
                    loading = controller.workspaceLoading,
                    workspaceError = controller.workspaceError,
                    activeSession = controller.activeSession,
                    pickerSessions = controller.pickerSessions,
                    buildDeployLine = DesignAssistantMapper.deployStatusLabel(
                        controller.buildSummary,
                        controller.buildLoading,
                        controller.buildError,
                    ),
                    buildLoading = controller.buildLoading,
                    messageDraft = controller.messageDraft,
                    onMessageDraftChange = { controller.messageDraft = it },
                    onSend = { controller.sendMessage(p, f) },
                    sendingEnabled = controller.apiKeys.isNotEmpty() && !controller.sendingMessage,
                    onSelectSession = { session ->
                        controller.selectWorkspaceSession(session, p, f)
                    },
                    onBack = { screen = V2Screen.FEATURES },
                    onBreadcrumbClick = { index ->
                        when (index) {
                            0 -> screen = V2Screen.PROJECTS
                            1 -> screen = V2Screen.FEATURES
                        }
                    },
                )
            } else {
                ConceptionWorkspaceScreen(
                    project = p,
                    feature = f,
                    messages = if (f.id == "oauth") {
                        DesignAssistantSampleData.oauthChatMessages
                    } else {
                        DesignAssistantSampleData.catalogChatMessages
                    },
                    onBack = { screen = V2Screen.FEATURES },
                    onBreadcrumbClick = { index ->
                        when (index) {
                            0 -> screen = V2Screen.PROJECTS
                            1 -> screen = V2Screen.FEATURES
                        }
                    },
                )
            }
        }
    }
}

/** Écran 1 — Mes Projets (nouvel accueil). */
@Composable
fun ProjectsHomeScreen(
    projects: List<DesignProject>,
    onBack: () -> Unit,
    onSwitchToV1: () -> Unit,
    onProjectClick: (DesignProject) -> Unit,
    loading: Boolean = false,
    error: String? = null,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(DesignAssistantColors.Navy)) {
        DesignAssistantNavyHeader(
            onBack = onBack,
            title = "Mes Projets",
            trailing = {
                Row {
                    IconButton(onClick = {}) {
                        Icon(Icons.Default.Person, contentDescription = "Profil", tint = Color.White)
                    }
                    IconButton(onClick = {}) {
                        Icon(Icons.Default.Add, contentDescription = "Nouveau projet", tint = Color.White)
                    }
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Options", tint = Color.White)
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("Voir la version mockup (V1)") },
                                onClick = {
                                    menuExpanded = false
                                    onSwitchToV1()
                                },
                            )
                        }
                    }
                }
            },
        )
        WhiteContentSheet(Modifier.weight(1f)) {
            when {
                loading && projects.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = DesignAssistantColors.Navy)
                    }
                }
                error != null && projects.isEmpty() -> {
                    Text(
                        error,
                        modifier = Modifier.padding(24.dp),
                        color = DesignAssistantColors.TextSecondary,
                    )
                }
                else -> {
                    LazyColumn(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (error != null) {
                            item {
                                Text(error, color = Color(0xFFC62828), fontSize = 13.sp, modifier = Modifier.padding(bottom = 8.dp))
                            }
                        }
                        items(projects, key = { it.id }) { project ->
                            ProjectCard(project, onClick = { onProjectClick(project) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProjectCard(project: DesignProject, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "${project.emoji} ${project.name}",
                fontWeight = FontWeight.Bold,
                color = DesignAssistantColors.Navy,
                fontSize = 16.sp,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "${project.activeFeaturesCount} Features • ${project.promptCount} Prompts • Branch: ${project.mainBranch}",
                color = DesignAssistantColors.TextSecondary,
                fontSize = 13.sp,
            )
            Text(
                "Dernière modification : ${project.lastModifiedLabel}",
                color = DesignAssistantColors.TextSecondary,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

/** Écran 2 — Features avec mini-dashboard projet. */
@Composable
fun ProjectFeaturesScreen(
    project: DesignProject,
    onBack: () -> Unit,
    onFeatureClick: (DesignFeature) -> Unit,
    searchQuery: String = "",
    onSearchChange: (String) -> Unit = {},
    onAddClick: (() -> Unit)? = null,
) {
    Column(Modifier.fillMaxSize().background(DesignAssistantColors.Navy)) {
        DesignAssistantNavyHeader(
            onBack = onBack,
            title = project.name,
            trailing = onAddClick?.let { add ->
                {
                    IconButton(onClick = add) {
                        Icon(Icons.Default.Add, contentDescription = "Ajouter une feature", tint = Color.White)
                    }
                }
            },
            content = {
                DesignBreadcrumb(listOf("Projets", project.name))
                Spacer(Modifier.height(8.dp))
            },
        )
        WhiteContentSheet(Modifier.weight(1f)) {
            ProjectSummaryDashboard(project)
            if (onAddClick != null) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    placeholder = { Text("Rechercher une feature…") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { onSearchChange("") }) {
                                Icon(Icons.Default.Close, contentDescription = "Effacer")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = DesignAssistantColors.Accent,
                        unfocusedBorderColor = DesignAssistantColors.TextSecondary.copy(alpha = 0.3f),
                    ),
                )
            }
            Text(
                "Features",
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                fontWeight = FontWeight.Bold,
                color = DesignAssistantColors.Navy,
                fontSize = 18.sp,
            )
            LazyColumn(Modifier.padding(horizontal = 16.dp)) {
                items(project.features, key = { it.id }) { f ->
                    FeatureRowV2(feature = f, onClick = { onFeatureClick(f) })
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun ProjectSummaryDashboard(project: DesignProject) {
    val realFeatures = project.features.filter { !DesignAssistantMapper.isAllOthers(it) }
    val inProgress = realFeatures.count { it.status == FeatureStatus.IN_PROGRESS }
    val ready = realFeatures.count { it.status == FeatureStatus.READY || it.status == FeatureStatus.DONE }
    val waiting = realFeatures.count { it.status == FeatureStatus.IDEA || it.status == FeatureStatus.TODO }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SummaryChip("$inProgress En cours", DesignAssistantColors.StatusInProgress)
        SummaryChip("$ready Prêtes", DesignAssistantColors.StatusReady)
        SummaryChip("$waiting En attente", DesignAssistantColors.StatusTodo)
    }
}

@Composable
private fun RowScope.SummaryChip(label: String, color: Color) {
    Surface(
        modifier = Modifier.weight(1f),
        color = color.copy(alpha = 0.15f),
        shape = RoundedCornerShape(8.dp),
    ) {
        Text(label, Modifier.padding(10.dp), fontSize = 11.sp, color = DesignAssistantColors.Navy, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun FeatureRowV2(feature: DesignFeature, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Color.White),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            StatusDot(feature.status)
            Spacer(Modifier.padding(horizontal = 10.dp))
            Column(Modifier.weight(1f)) {
                Text(feature.name, fontWeight = FontWeight.SemiBold, color = DesignAssistantColors.Navy)
                Text(feature.status.labelFr, fontSize = 12.sp, color = DesignAssistantColors.TextSecondary)
            }
        }
    }
}

/** Écran 3 — Panneau contexte persistant + chat + onglets swipe. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ConceptionWorkspaceScreen(
    project: DesignProject,
    feature: DesignFeature,
    messages: List<DesignChatMessage>,
    onBack: () -> Unit,
    onBreadcrumbClick: (Int) -> Unit,
    codeContent: String = DesignAssistantSampleData.generatedCodeSample,
    modifiedFiles: List<String> = DesignAssistantSampleData.modifiedFilesSample,
    loading: Boolean = false,
    workspaceError: String? = null,
    activeSession: JulesSessionEntity? = null,
    pickerSessions: List<JulesSessionEntity> = emptyList(),
    buildDeployLine: String? = null,
    buildLoading: Boolean = false,
    messageDraft: String = "",
    onMessageDraftChange: (String) -> Unit = {},
    onSend: () -> Unit = {},
    sendingEnabled: Boolean = false,
    onSelectSession: (JulesSessionEntity) -> Unit = {},
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val pagerState = rememberPagerState(pageCount = { WorkspaceTab.entries.size })
    val scope = rememberCoroutineScope()
    var selectedTab by remember { mutableStateOf(WorkspaceTab.CHAT) }

    val branch = activeSession?.prBranch ?: feature.branch
    val prNumber = DesignAssistantMapper.prNumberFromSession(activeSession) ?: feature.prNumber
    val prTitle = activeSession?.prTitle ?: feature.prTitle
    val prUrl = activeSession?.prUrl
    val prEmoji = DesignAssistantMapper.prStateEmoji(activeSession?.prState)

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { page ->
            selectedTab = WorkspaceTab.entries[page]
        }
    }

    Column(Modifier.fillMaxSize().background(Color.White)) {
        DesignAssistantNavyHeader(
            onBack = onBack,
            title = "Mode Conception",
            subtitle = feature.name,
        )
        DesignBreadcrumb(
            path = listOf("Projets", project.name, "Feature: ${feature.name}"),
            onSegmentClick = onBreadcrumbClick,
        )
        if (branch != null || prNumber != null || buildDeployLine != null || buildLoading) {
            TechnicalStatusBanner(
                branch = branch,
                prNumber = prNumber,
                prTitle = prTitle,
                prStateEmoji = prEmoji,
                deployStatusLine = buildDeployLine,
                deployLoading = buildLoading,
                onCopyBranch = {
                    branch?.let { b ->
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("branch", b))
                    }
                },
                onOpenPr = {
                    prUrl?.let { uriHandler.openUri(it) }
                },
            )
        }
        if (pickerSessions.size > 1) {
            SessionPickerRow(
                sessions = pickerSessions,
                selectedId = activeSession?.id,
                onSelect = onSelectSession,
            )
        }
        WorkspaceTabRow(selected = selectedTab, onSelect = { tab ->
            selectedTab = tab
            scope.launch { pagerState.animateScrollToPage(tab.ordinal) }
        })
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f),
            userScrollEnabled = true,
        ) { page ->
            when (WorkspaceTab.entries[page]) {
                WorkspaceTab.CHAT -> WorkspaceChatTab(
                    messages = messages,
                    loading = loading,
                    error = workspaceError,
                    emptyHint = if (activeSession == null) {
                        "Aucune conversation — envoyez un message pour démarrer."
                    } else {
                        null
                    },
                )
                WorkspaceTab.GENERATED_CODE -> WorkspaceCodeTab(codeContent)
                WorkspaceTab.MODIFIED_FILES -> WorkspaceFilesTab(modifiedFiles)
            }
        }
        if (sendingEnabled) {
            DesignChatInputBar(
                value = messageDraft,
                onValueChange = onMessageDraftChange,
                placeholder = "Message Jules sur ${feature.name}…",
                onSend = onSend,
                enabled = !loading,
            )
        } else {
            DesignChatInputBar("Message Jules sur ${feature.name}…")
        }
    }
}

@Composable
private fun SessionPickerRow(
    sessions: List<JulesSessionEntity>,
    selectedId: String?,
    onSelect: (JulesSessionEntity) -> Unit,
) {
    val scroll = rememberScrollState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scroll)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        sessions.forEach { session ->
            val selected = session.id == selectedId
            Surface(
                modifier = Modifier.clickable { onSelect(session) },
                color = if (selected) DesignAssistantColors.Navy else DesignAssistantColors.Surface,
                shape = RoundedCornerShape(16.dp),
            ) {
                Text(
                    session.title.ifBlank { session.prompt.take(24) },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    color = if (selected) Color.White else DesignAssistantColors.Navy,
                    fontSize = 12.sp,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun WorkspaceChatTab(
    messages: List<DesignChatMessage>,
    loading: Boolean = false,
    error: String? = null,
    emptyHint: String? = null,
) {
    Box(Modifier.fillMaxSize()) {
        when {
            loading && messages.isEmpty() -> {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = DesignAssistantColors.Navy,
                )
            }
            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    error?.let {
                        item {
                            Text(it, color = Color(0xFFC62828), fontSize = 13.sp)
                        }
                    }
                    if (messages.isEmpty() && emptyHint != null) {
                        item {
                            Text(emptyHint, color = DesignAssistantColors.TextSecondary, fontSize = 14.sp)
                        }
                    }
                    items(messages, key = { it.id }) { msg -> V2ChatBubble(msg) }
                }
            }
        }
    }
}

@Composable
private fun V2ChatBubble(message: DesignChatMessage) {
    when (message.kind) {
        ChatMessageKind.USER -> {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Surface(color = DesignAssistantColors.UserBubble, shape = RoundedCornerShape(16.dp)) {
                    Text(message.text, Modifier.padding(12.dp), color = DesignAssistantColors.Navy)
                }
            }
        }
        ChatMessageKind.CODE -> {
            var expanded by remember { mutableStateOf(false) }
            Row {
                JulesAvatar()
                Spacer(Modifier.padding(4.dp))
                CollapsibleCodeBlock(message.codeSnippet.orEmpty(), expanded) { expanded = !expanded }
            }
        }
        ChatMessageKind.CI -> {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = DesignAssistantColors.CiSuccess.copy(alpha = 0.12f),
                shape = RoundedCornerShape(8.dp),
            ) {
                Text(message.text, Modifier.padding(10.dp), color = DesignAssistantColors.CiSuccess, fontSize = 12.sp)
            }
        }
        else -> {
            Row {
                JulesAvatar()
                Spacer(Modifier.padding(4.dp))
                Text(message.text, color = DesignAssistantColors.Navy, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun WorkspaceCodeTab(code: String) {
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        color = DesignAssistantColors.CodeBlockBg,
        shape = RoundedCornerShape(8.dp),
    ) {
        Text(
            code.ifBlank { "Aucun code généré pour cette session." },
            Modifier.padding(16.dp),
            color = Color(0xFF80CBC4),
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun WorkspaceFilesTab(files: List<String>) {
    LazyColumn(Modifier.padding(16.dp)) {
        if (files.isEmpty()) {
            item {
                Text("Aucun fichier modifié.", color = DesignAssistantColors.TextSecondary)
            }
        } else {
            items(files) { path ->
                Text(path, color = DesignAssistantColors.Navy, modifier = Modifier.padding(vertical = 8.dp))
            }
        }
    }
}

@Preview(showBackground = true, heightDp = 800, name = "V2 Projets")
@Composable
private fun ProjectsHomePreview() {
    DesignAssistantTheme {
        ProjectsHomeScreen(
            projects = DesignAssistantSampleData.projects,
            onBack = {},
            onSwitchToV1 = {},
            onProjectClick = {},
        )
    }
}

@Preview(showBackground = true, heightDp = 800, name = "V2 Features")
@Composable
private fun ProjectFeaturesPreview() {
    DesignAssistantTheme {
        ProjectFeaturesScreen(DesignAssistantSampleData.eCommerce, onBack = {}, onFeatureClick = {})
    }
}

@Preview(showBackground = true, heightDp = 900, name = "V2 Workspace")
@Composable
private fun ConceptionWorkspacePreview() {
    DesignAssistantTheme {
        ConceptionWorkspaceScreen(
            project = DesignAssistantSampleData.eCommerce,
            feature = DesignAssistantSampleData.eCommerce.features.first(),
            messages = DesignAssistantSampleData.oauthChatMessages,
            onBack = {},
            onBreadcrumbClick = {},
        )
    }
}
