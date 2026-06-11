package fr.geoking.julius.designassistant.v3

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import fr.geoking.julius.SettingsManager
import fr.geoking.julius.designassistant.*
import fr.geoking.julius.designassistant.components.DesignAssistantBottomNav
import fr.geoking.julius.designassistant.components.DesignBottomTab
import fr.geoking.julius.designassistant.components.JarvisTheme
import fr.geoking.julius.persistence.JulesSessionEntity
import fr.geoking.julius.repository.FeatureRepository
import fr.geoking.julius.repository.GitHubBuildRepository
import fr.geoking.julius.repository.JulesRepository

enum class V3Screen { PROJECTS, FEATURES, SESSIONS, CHAT, SOURCE }

@Composable
fun DesignAssistantV3Host(
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

    val screenStack = remember { mutableStateListOf(V3Screen.PROJECTS) }
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

    JarvisTheme {
        Column(Modifier.fillMaxSize().background(DesignAssistantJarvisColors.Background)) {
            Box(Modifier.weight(1f)) {
                when (currentScreen) {
                    V3Screen.PROJECTS -> V3ProjectsScreen(
                        projects = controller?.projects ?: DesignAssistantSampleData.projects,
                        loading = controller?.projectsLoading ?: false,
                        error = controller?.projectsError,
                        onProjectClick = { p ->
                            project = p
                            controller?.loadFeaturesForProject(p.id)
                            screenStack.add(V3Screen.FEATURES)
                            bottomTab = DesignBottomTab.FEATURES
                        },
                        onBack = ::navigateBack,
                    )
                    V3Screen.FEATURES -> {
                        val p = project ?: return@Box
                        val liveProject = controller?.projectWithFeatures(p.id) ?: p
                        V3FeaturesScreen(
                            project = liveProject,
                            onFeatureClick = { f ->
                                feature = f
                                screenStack.add(V3Screen.CHAT)
                                bottomTab = DesignBottomTab.CHAT
                            },
                            onBack = ::navigateBack,
                        )
                    }
                    V3Screen.CHAT -> {
                        val p = project ?: return@Box
                        val f = feature ?: return@Box
                        V3ConversationScreen(
                            projectName = p.name,
                            feature = f,
                            messages = controller?.chatMessages?.toList() ?: DesignAssistantSampleData.catalogChatMessages,
                            onBack = ::navigateBack,
                        )
                    }
                    V3Screen.SOURCE -> {
                        val p = project ?: return@Box
                        val f = feature ?: return@Box
                        V3SourceScreen(
                            project = p,
                            feature = f,
                            onBack = ::navigateBack,
                        )
                    }
                    else -> {}
                }
            }
            DesignAssistantBottomNav(
                selected = bottomTab,
                onSelect = { tab ->
                    bottomTab = tab
                    when (tab) {
                        DesignBottomTab.PROJECTS -> {
                            screenStack.clear()
                            screenStack.add(V3Screen.PROJECTS)
                        }
                        DesignBottomTab.FEATURES -> {
                            if (project != null) {
                                while (screenStack.last() != V3Screen.FEATURES && screenStack.contains(V3Screen.FEATURES)) {
                                    screenStack.removeAt(screenStack.size - 1)
                                }
                                if (!screenStack.contains(V3Screen.FEATURES)) screenStack.add(V3Screen.FEATURES)
                            }
                        }
                        DesignBottomTab.CHAT -> {
                            if (feature != null && !screenStack.contains(V3Screen.CHAT)) {
                                screenStack.add(V3Screen.CHAT)
                            }
                        }
                        DesignBottomTab.BRANCHES -> {
                            if (feature != null && !screenStack.contains(V3Screen.SOURCE)) {
                                screenStack.add(V3Screen.SOURCE)
                            }
                        }
                        else -> {}
                    }
                }
            )
        }
    }
}
