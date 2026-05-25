package fr.geoking.julius.designassistant.v2

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.geoking.julius.designassistant.ChatMessageKind
import fr.geoking.julius.designassistant.DesignAssistantColors
import fr.geoking.julius.designassistant.DesignAssistantSampleData
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
import fr.geoking.julius.designassistant.components.PromptShortcutsRow
import fr.geoking.julius.designassistant.components.StatusDot
import fr.geoking.julius.designassistant.components.TechnicalStatusBanner
import fr.geoking.julius.designassistant.components.WhiteContentSheet
import fr.geoking.julius.designassistant.components.WorkspaceTabRow
import kotlinx.coroutines.launch

enum class V2Screen { PROJECTS, FEATURES, WORKSPACE }

/** V2 — Projets → Features (dashboard) → Conception & Chat (onglets swipe). */
@Composable
fun DesignAssistantV2Host(
    onBack: () -> Unit,
    onSwitchToV1: () -> Unit,
) {
    var screen by remember { mutableStateOf(V2Screen.PROJECTS) }
    var project by remember { mutableStateOf<DesignProject?>(null) }
    var feature by remember { mutableStateOf<DesignFeature?>(null) }

    when (screen) {
        V2Screen.PROJECTS -> ProjectsHomeScreen(
            projects = DesignAssistantSampleData.projects,
            onBack = onBack,
            onSwitchToV1 = onSwitchToV1,
            onProjectClick = { p ->
                project = p
                screen = V2Screen.FEATURES
            },
        )
        V2Screen.FEATURES -> {
            val p = project ?: return
            ProjectFeaturesScreen(
                project = p,
                onBack = { screen = V2Screen.PROJECTS },
                onFeatureClick = { f ->
                    feature = f
                    screen = V2Screen.WORKSPACE
                },
            )
        }
        V2Screen.WORKSPACE -> {
            val p = project ?: return
            val f = feature ?: return
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

/** Écran 1 — Mes Projets (nouvel accueil). */
@Composable
fun ProjectsHomeScreen(
    projects: List<DesignProject>,
    onBack: () -> Unit,
    onSwitchToV1: () -> Unit,
    onProjectClick: (DesignProject) -> Unit,
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
            LazyColumn(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(projects) { project ->
                    ProjectCard(project, onClick = { onProjectClick(project) })
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
) {
    Column(Modifier.fillMaxSize().background(DesignAssistantColors.Navy)) {
        DesignAssistantNavyHeader(
            onBack = onBack,
            title = project.name,
            content = {
                DesignBreadcrumb(listOf("Projets", project.name))
                Spacer(Modifier.height(8.dp))
            },
        )
        WhiteContentSheet(Modifier.weight(1f)) {
            ProjectSummaryDashboard(project)
            Text(
                "Features",
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                fontWeight = FontWeight.Bold,
                color = DesignAssistantColors.Navy,
                fontSize = 18.sp,
            )
            LazyColumn(Modifier.padding(horizontal = 16.dp)) {
                items(project.features) { f ->
                    FeatureRowV2(feature = f, onClick = { onFeatureClick(f) })
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun ProjectSummaryDashboard(project: DesignProject) {
    val inProgress = project.features.count { it.status == FeatureStatus.IN_PROGRESS }
    val ready = project.features.count { it.status == FeatureStatus.READY || it.status == FeatureStatus.DONE }
    val waiting = project.features.count { it.status == FeatureStatus.IDEA || it.status == FeatureStatus.TODO }
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
) {
    val pagerState = rememberPagerState(pageCount = { WorkspaceTab.entries.size })
    val scope = rememberCoroutineScope()
    var selectedTab by remember { mutableStateOf(WorkspaceTab.CHAT) }

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
        feature.branch?.let { branch ->
            TechnicalStatusBanner(
                branch = branch,
                prNumber = feature.prNumber,
                prTitle = feature.prTitle,
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
                WorkspaceTab.CHAT -> WorkspaceChatTab(messages, feature.name)
                WorkspaceTab.GENERATED_CODE -> WorkspaceCodeTab()
                WorkspaceTab.MODIFIED_FILES -> WorkspaceFilesTab()
            }
        }
        PromptShortcutsRow(
            shortcuts = DesignAssistantSampleData.promptShortcuts,
            onShortcutClick = {},
        )
        DesignChatInputBar("Message Jules sur ${feature.name}…")
    }
}

@Composable
private fun WorkspaceChatTab(messages: List<DesignChatMessage>, featureName: String) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(messages) { msg -> V2ChatBubble(msg) }
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
private fun WorkspaceCodeTab() {
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        color = DesignAssistantColors.CodeBlockBg,
        shape = RoundedCornerShape(8.dp),
    ) {
        Text(
            DesignAssistantSampleData.generatedCodeSample,
            Modifier.padding(16.dp),
            color = Color(0xFF80CBC4),
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun WorkspaceFilesTab() {
    LazyColumn(Modifier.padding(16.dp)) {
        items(DesignAssistantSampleData.modifiedFilesSample) { path ->
            Text(path, color = DesignAssistantColors.Navy, modifier = Modifier.padding(vertical = 8.dp))
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
