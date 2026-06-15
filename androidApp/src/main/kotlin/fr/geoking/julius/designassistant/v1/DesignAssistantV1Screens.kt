package fr.geoking.julius.designassistant.v1

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.geoking.julius.SettingsManager
import fr.geoking.julius.designassistant.ChatMessageKind
import fr.geoking.julius.designassistant.DesignAssistantColors
import fr.geoking.julius.designassistant.DesignAssistantMapper
import fr.geoking.julius.designassistant.DesignAssistantSampleData
import fr.geoking.julius.designassistant.DesignAssistantState
import fr.geoking.julius.designassistant.DesignChatMessage
import fr.geoking.julius.designassistant.DesignFeature
import fr.geoking.julius.designassistant.DesignProject
import fr.geoking.julius.designassistant.FeatureStatus
import fr.geoking.julius.designassistant.components.CollapsibleCodeBlock
import fr.geoking.julius.designassistant.components.DesignAssistantBottomNav
import fr.geoking.julius.designassistant.components.DesignAssistantNavyHeader
import fr.geoking.julius.designassistant.components.DesignAssistantTheme
import fr.geoking.julius.designassistant.components.DesignBreadcrumb
import fr.geoking.julius.designassistant.components.DesignBottomTab
import fr.geoking.julius.designassistant.components.DesignChatInputBar
import fr.geoking.julius.designassistant.components.JulesAvatar
import fr.geoking.julius.designassistant.components.StatusDot
import fr.geoking.julius.designassistant.components.WhiteContentSheet
import fr.geoking.julius.persistence.JulesSessionEntity
import fr.geoking.julius.repository.FeatureRepository
import fr.geoking.julius.repository.GitHubBuildRepository
import fr.geoking.julius.repository.JulesRepository
import fr.geoking.julius.ui.components.RenderMessageBlock
import fr.geoking.julius.ui.components.parseJulesMessage

enum class V1Screen { PROJECTS, FEATURES, SESSIONS, CHAT }

/** V1 — Réimplémenté avec la structure logique (Projets -> Features -> Sessions -> Chat). */
@Composable
fun DesignAssistantV1Host(
    onBack: () -> Unit,
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
            DesignAssistantState(
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

    val screenStack = remember { mutableStateListOf(V1Screen.PROJECTS) }
    val currentScreen = screenStack.last()
    var project by remember { mutableStateOf<DesignProject?>(null) }
    var feature by remember { mutableStateOf<DesignFeature?>(null) }

    var bottomTab by remember { mutableStateOf(DesignBottomTab.PROJECTS) }

    fun navigateBack() {
        if (screenStack.size > 1) {
            screenStack.removeAt(screenStack.size - 1)
        } else {
            onBack()
        }
    }

    BackHandler(onBack = ::navigateBack)

    Column(Modifier.fillMaxSize().background(DesignAssistantColors.Navy)) {
        Box(Modifier.weight(1f)) {
            when (currentScreen) {
                V1Screen.PROJECTS -> V1ProjectsScreen(
                    projects = controller?.projects ?: DesignAssistantSampleData.projects,
                    loading = controller?.projectsLoading ?: false,
                    error = controller?.projectsError,
                    onProjectClick = { p ->
                        project = p
                        controller?.loadFeaturesForProject(p.id)
                        screenStack.add(V1Screen.FEATURES)
                        bottomTab = DesignBottomTab.FEATURES
                    },
                    onBack = ::navigateBack,
                )
                V1Screen.FEATURES -> {
                    val p = project ?: return@Box
                    val liveProject = controller?.projectWithFeatures(p.id) ?: p
                    V1FeaturesScreen(
                        project = liveProject,
                        onFeatureClick = { f ->
                            feature = f
                            screenStack.add(V1Screen.SESSIONS)
                        },
                        onBack = ::navigateBack,
                    )
                }
                V1Screen.SESSIONS -> {
                    val p = project ?: return@Box
                    val f = feature ?: return@Box
                    val sessions = if (controller != null) {
                        val entity = controller.allFeatures.find { it.id == f.id }
                        DesignAssistantMapper.resolveSessionsForFeature(
                            feature = f,
                            sourceName = p.id,
                            sessions = controller.sessions,
                            featureSessionId = entity?.sessionId
                        )
                    } else emptyList()

                    V1SessionsScreen(
                        project = p,
                        feature = f,
                        sessions = sessions,
                        onSessionClick = { s ->
                            controller?.openWorkspaceForSession(p, s)
                            screenStack.add(V1Screen.CHAT)
                            bottomTab = DesignBottomTab.CHAT
                        },
                        onBack = ::navigateBack,
                    )
                }
                V1Screen.CHAT -> {
                    val p = project ?: return@Box
                    val f = feature ?: return@Box
                    V1ChatScreen(
                        projectName = p.name,
                        feature = f,
                        session = controller?.activeSession,
                        messages = controller?.chatMessages?.toList() ?: emptyList(),
                        messageDraft = controller?.messageDraft ?: "",
                        onMessageDraftChange = { controller?.messageDraft = it },
                        onSend = { controller?.sendMessage(p, f) },
                        onTogglePause = {
                            if (controller?.activeSession?.sessionState == "PAUSED") {
                                controller.resumeSession()
                            } else {
                                controller?.pauseSession()
                            }
                        },
                        onArchive = { controller?.archiveSession() },
                        onStop = { controller?.pauseSession() },
                        onBack = ::navigateBack,
                    )
                }
            }
        }
        DesignAssistantBottomNav(
            selected = bottomTab,
            onSelect = { tab ->
                bottomTab = tab
                when (tab) {
                    DesignBottomTab.PROJECTS -> {
                        screenStack.clear()
                        screenStack.add(V1Screen.PROJECTS)
                    }
                    DesignBottomTab.FEATURES -> {
                        if (project != null) {
                            while (screenStack.last() != V1Screen.FEATURES && screenStack.contains(V1Screen.FEATURES)) {
                                screenStack.removeAt(screenStack.size - 1)
                            }
                            if (!screenStack.contains(V1Screen.FEATURES)) screenStack.add(V1Screen.FEATURES)
                        }
                    }
                    else -> {}
                }
            }
        )
    }
}

@Composable
fun V1ProjectsScreen(
    projects: List<DesignProject>,
    loading: Boolean,
    error: String?,
    onProjectClick: (DesignProject) -> Unit,
    onBack: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        DesignAssistantNavyHeader(
            onBack = onBack,
            title = "Mes Projets",
            trailing = {
                Row {
                    IconButton(onClick = {}) {
                        Icon(Icons.Default.Person, contentDescription = "Profil", tint = Color.White)
                    }
                    IconButton(onClick = {}) {
                        Icon(Icons.Default.Add, contentDescription = "Nouveau", tint = Color.White)
                    }
                }
            }
        )
        WhiteContentSheet(Modifier.weight(1f)) {
            if (loading && projects.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = DesignAssistantColors.Navy)
                }
            } else {
                LazyColumn(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(projects) { project ->
                        ProjectCardV1(project, onClick = { onProjectClick(project) })
                    }
                }
            }
        }
    }
}

@Composable
private fun ProjectCardV1(project: DesignProject, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(project.emoji, fontSize = 24.sp)
            Spacer(Modifier.padding(horizontal = 8.dp))
            Column {
                Text(project.name, fontWeight = FontWeight.Bold, color = DesignAssistantColors.Navy)
                Text("${project.activeFeaturesCount} features actives", fontSize = 12.sp, color = DesignAssistantColors.TextSecondary)
            }
        }
    }
}

@Composable
fun V1FeaturesScreen(
    project: DesignProject,
    onFeatureClick: (DesignFeature) -> Unit,
    onBack: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        DesignAssistantNavyHeader(
            onBack = onBack,
            title = project.name,
            subtitle = "Features du projet",
            content = {
                DesignBreadcrumb(listOf("Projets", project.name))
                Spacer(Modifier.height(8.dp))
            }
        )
        WhiteContentSheet(Modifier.weight(1f)) {
            LazyColumn(Modifier.padding(16.dp)) {
                items(project.features) { feature ->
                    FeatureCardV1(feature = feature, onClick = { onFeatureClick(feature) })
                    Spacer(Modifier.height(10.dp))
                }
            }
        }
    }
}

@Composable
private fun FeatureCardV1(
    feature: DesignFeature,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            StatusDot(feature.status)
            Spacer(Modifier.padding(horizontal = 8.dp))
            Column(Modifier.weight(1f)) {
                Text(feature.name, color = DesignAssistantColors.Navy, fontWeight = FontWeight.SemiBold)
                Text(feature.status.labelFr, color = DesignAssistantColors.TextSecondary, fontSize = 12.sp)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = DesignAssistantColors.TextSecondary)
        }
    }
}

@Composable
fun V1SessionsScreen(
    project: DesignProject,
    feature: DesignFeature,
    sessions: List<JulesSessionEntity>,
    onSessionClick: (JulesSessionEntity) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    Column(Modifier.fillMaxSize()) {
        DesignAssistantNavyHeader(
            onBack = onBack,
            title = feature.name,
            subtitle = "Conversations",
            content = {
                DesignBreadcrumb(listOf(project.name, feature.name))
                Spacer(Modifier.height(8.dp))
            }
        )
        WhiteContentSheet(Modifier.weight(1f)) {
            LazyColumn(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        StatusDot(feature.status)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = feature.status.labelFr,
                            color = DesignAssistantColors.TextSecondary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                if (sessions.isEmpty()) {
                    item {
                        Text("Aucune conversation trouvée.", modifier = Modifier.padding(16.dp), color = DesignAssistantColors.TextSecondary)
                    }
                }
                items(sessions) { session ->
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { onSessionClick(session) },
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(1.dp)
                    ) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null, tint = DesignAssistantColors.Accent, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.padding(horizontal = 10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    session.title.ifBlank { session.prompt.take(50) },
                                    fontWeight = FontWeight.SemiBold,
                                    color = DesignAssistantColors.Navy,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    DesignAssistantMapper.formatRelativeTime(session.lastUpdated),
                                    fontSize = 12.sp,
                                    color = DesignAssistantColors.TextSecondary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun V1ChatScreen(
    projectName: String,
    feature: DesignFeature,
    session: JulesSessionEntity?,
    messages: List<DesignChatMessage>,
    messageDraft: String,
    onMessageDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    onTogglePause: () -> Unit,
    onArchive: () -> Unit,
    onStop: () -> Unit,
    onBack: () -> Unit,
) {
    Column(Modifier.fillMaxSize().background(Color.White)) {
        TopAppBar(
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    JulesAvatar()
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Jules | AI Assistant", color = DesignAssistantColors.Navy, fontSize = 16.sp)
                        Text("$projectName › ${feature.name}", fontSize = 11.sp, color = DesignAssistantColors.TextSecondary)
                    }
                }
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour", tint = DesignAssistantColors.Navy)
                }
            },
            actions = {
                if (session != null) {
                    val isPaused = session.sessionState == "PAUSED"
                    IconButton(onClick = onTogglePause) {
                        Icon(
                            if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                            contentDescription = if (isPaused) "Reprendre" else "Pause",
                            tint = DesignAssistantColors.Navy
                        )
                    }
                    IconButton(onClick = onStop) {
                        Icon(Icons.Default.Stop, contentDescription = "Arrêter", tint = DesignAssistantColors.Navy)
                    }
                    IconButton(onClick = onArchive) {
                        Icon(Icons.Default.Archive, contentDescription = "Archiver", tint = DesignAssistantColors.Navy)
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.White,
                titleContentColor = DesignAssistantColors.Navy,
                navigationIconContentColor = DesignAssistantColors.Navy
            )
        )
        LazyColumn(
            modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(messages) { msg ->
                V1ChatBubble(msg)
            }
        }
        DesignChatInputBar(
            value = messageDraft,
            onValueChange = onMessageDraftChange,
            placeholder = "Demander à Jules…",
            onSend = onSend,
            enabled = true
        )
    }
}

@Composable
private fun V1ChatBubble(message: DesignChatMessage) {
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
            val blocks = remember(message.text) { parseJulesMessage(message.text) }
            Row {
                JulesAvatar()
                Spacer(Modifier.padding(4.dp))
                Column(Modifier.weight(1f)) {
                    blocks.forEach { block ->
                        RenderMessageBlock(
                            block = block,
                            baseFontSize = 14,
                            textColor = DesignAssistantColors.Navy
                        )
                        Spacer(Modifier.height(4.dp))
                    }
                }
            }
        }
    }
}
