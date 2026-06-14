package fr.geoking.julius.ui.v3

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import fr.geoking.julius.SettingsManager
import fr.geoking.julius.queue.CodingAgentQueueEngine
import fr.geoking.julius.repository.FeatureRepository
import fr.geoking.julius.repository.GitHubBuildRepository
import fr.geoking.julius.repository.JulesRepository
import kotlinx.coroutines.launch

/** Dependency bundle threaded into the v3 screens (reuses existing Koin singletons). */
class V3Deps(
    val settingsManager: SettingsManager,
    val julesRepository: JulesRepository,
    val featureRepository: FeatureRepository,
    val queueEngine: CodingAgentQueueEngine,
    val buildRepository: GitHubBuildRepository,
)

private sealed class V3Sheet {
    data class AddFeature(val sourceName: String?) : V3Sheet()
    data object AddProject : V3Sheet()
    data class Launch(val featureId: String) : V3Sheet()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JuliusV3App(deps: V3Deps, onExit: () -> Unit) {
    JuliusV3Theme {
        val nav = rememberV3NavController()
        val scope = rememberCoroutineScope()
        val snackbar = remember { SnackbarHostState() }
        var sheet by remember { mutableStateOf<V3Sheet?>(null) }

        // Hardware back: pop the v3 stack, or exit to the host dashboard.
        BackHandler(enabled = true) { if (!nav.pop()) onExit() }

        val route = nav.current
        val showBottomBar = route !is V3Route.Conversation
        val fab = fabFor(route)

        Scaffold(
            containerColor = V3.Bg,
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = { if (showBottomBar) V3BottomBar(route) { nav.selectTab(it) } },
            floatingActionButton = {
                if (fab != null && showBottomBar) {
                    ExtendedFloatingActionButton(
                        text = { Text(fab.label) },
                        icon = { Icon(Icons.Filled.Add, null) },
                        onClick = {
                            sheet = when (val r = nav.current) {
                                is V3Route.Projects -> V3Sheet.AddProject
                                is V3Route.ProjectFeatures -> V3Sheet.AddFeature(r.sourceName)
                                is V3Route.Features -> V3Sheet.AddFeature(null)
                                is V3Route.FeatureDetail -> V3Sheet.Launch(r.featureId)
                                else -> null
                            }
                        },
                        containerColor = V3.Accent,
                        contentColor = V3.AccentInk,
                    )
                }
            },
        ) { padding ->
            Box(Modifier.padding(padding)) {
                when (val r = nav.current) {
                    is V3Route.Scheduler -> SchedulerV3Screen(
                        deps = deps,
                        onOpenFeature = { nav.push(V3Route.FeatureDetail(it)) },
                        onSeeAllFeatures = { nav.selectTab(V3Route.Features) },
                    )
                    is V3Route.Features -> FeaturesV3Screen(
                        deps = deps, sourceName = null, onBack = null,
                        onOpenFeature = { nav.push(V3Route.FeatureDetail(it)) },
                    )
                    is V3Route.ProjectFeatures -> FeaturesV3Screen(
                        deps = deps, sourceName = r.sourceName, onBack = { nav.pop() },
                        onOpenFeature = { nav.push(V3Route.FeatureDetail(it)) },
                    )
                    is V3Route.Projects -> ProjectsV3Screen(
                        deps = deps,
                        onOpenProject = { nav.push(V3Route.ProjectFeatures(it)) },
                    )
                    is V3Route.FeatureDetail -> FeatureDetailV3Screen(
                        deps = deps, featureId = r.featureId, onBack = { nav.pop() },
                        onOpenConversation = { nav.push(V3Route.Conversation(it)) },
                        onLaunch = { sheet = V3Sheet.Launch(r.featureId) },
                    )
                    is V3Route.Conversation -> ConversationV3Screen(
                        deps = deps, sessionId = r.sessionId, onBack = { nav.pop() },
                    )
                    is V3Route.Settings -> SettingsV3Screen(deps = deps)
                }
            }
        }

        // ---- bottom sheets (add feature / connect repo / launch conversation) ----
        when (val s = sheet) {
            is V3Sheet.AddFeature -> AddFeatureSheet(deps, s.sourceName, onClose = { sheet = null }) { msg ->
                sheet = null; scope.launch { snackbar.showSnackbar(msg) }
            }
            is V3Sheet.AddProject -> AddProjectSheet(onClose = { sheet = null }) { msg ->
                sheet = null; scope.launch { snackbar.showSnackbar(msg) }
            }
            is V3Sheet.Launch -> LaunchConversationSheet(
                deps, s.featureId, onClose = { sheet = null },
                onStarted = { sessionId ->
                    sheet = null
                    nav.push(V3Route.Conversation(sessionId))
                    scope.launch { snackbar.showSnackbar("Session démarrée") }
                },
            )
            null -> {}
        }
    }
}

private data class FabSpec(val label: String)

private fun fabFor(route: V3Route): FabSpec? = when (route) {
    is V3Route.Projects -> FabSpec("Projet")
    is V3Route.Features, is V3Route.ProjectFeatures -> FabSpec("Feature")
    is V3Route.FeatureDetail -> FabSpec("Conversation")
    else -> null
}

@Composable
private fun V3BottomBar(route: V3Route, onSelect: (V3Route) -> Unit) {
    NavigationBar(containerColor = V3.Surface, tonalElevation = 0.dp) {
        val current = route.tabRoot
        v3TabItems().forEach { (tab, label, icon) ->
            NavigationBarItem(
                selected = current == tab,
                onClick = { onSelect(tab) },
                icon = { Icon(icon, null) },
                label = { Text(label) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = V3.AccentInk,
                    selectedTextColor = V3.Accent,
                    indicatorColor = V3.Accent,
                    unselectedIconColor = V3.Faint,
                    unselectedTextColor = V3.Faint,
                ),
            )
        }
    }
}

private fun v3TabItems(): List<Triple<V3Route, String, ImageVector>> = listOf(
    Triple(V3Route.Scheduler, "Scheduler", Icons.Filled.Schedule),
    Triple(V3Route.Features, "Features", Icons.Filled.List),
    Triple(V3Route.Projects, "Projets", Icons.Filled.FolderOpen),
    Triple(V3Route.Settings, "Réglages", Icons.Filled.Settings),
)

// ---------------------------------------------------------------------------
// Sheets
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddFeatureSheet(
    deps: V3Deps,
    sourceName: String?,
    onClose: () -> Unit,
    onDone: (String) -> Unit,
) {
    val settings by deps.settingsManager.settings.collectAsState()
    val scope = rememberCoroutineScope()
    var title by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var source by remember { mutableStateOf(sourceName ?: settings.lastJulesRepoName) }
    ModalBottomSheet(onDismissRequest = onClose, containerColor = V3.Surface) {
        Column(Modifier.padding(horizontal = 18.dp).padding(bottom = 24.dp)) {
            Text("Nouvelle feature", color = V3.Fg, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(title, { title = it }, label = { Text("Titre") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(desc, { desc = it }, label = { Text("Description / contexte") }, modifier = Modifier.fillMaxWidth().height(110.dp))
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(source, { source = it }, label = { Text("Dépôt (owner/repo)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(14.dp))
            Button(
                onClick = {
                    if (title.isNotBlank() && source.isNotBlank()) scope.launch {
                        deps.featureRepository.addFeature(title.trim(), desc.trim(), 0, source.trim())
                        deps.featureRepository.scheduleWorker()
                        onDone("Feature ajoutée à la file")
                    }
                },
                enabled = title.isNotBlank() && source.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = V3.Accent, contentColor = V3.AccentInk),
            ) { Text("Ajouter à la file") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddProjectSheet(onClose: () -> Unit, onDone: (String) -> Unit) {
    var repo by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onClose, containerColor = V3.Surface) {
        Column(Modifier.padding(horizontal = 18.dp).padding(bottom = 24.dp)) {
            Text("Connecter un dépôt", color = V3.Fg, style = MaterialTheme.typography.titleLarge)
            Text(
                "Les dépôts sont découverts via Jules / GitHub. Renseigne owner/repo pour le cibler.",
                color = V3.Muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp, bottom = 6.dp),
            )
            OutlinedTextField(
                repo, { repo = it }, label = { Text("owner/repo") }, singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(14.dp))
            Button(
                onClick = { if (repo.isNotBlank()) onDone("Dépôt ciblé : ${repo.trim()}") },
                enabled = repo.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = V3.Accent, contentColor = V3.AccentInk),
            ) { Text("Cibler le dépôt") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LaunchConversationSheet(
    deps: V3Deps,
    featureId: String,
    onClose: () -> Unit,
    onStarted: (String) -> Unit,
) {
    val settings by deps.settingsManager.settings.collectAsState()
    val scope = rememberCoroutineScope()
    val accounts = remember(settings) { settings.agentAccounts.filter { it.enabled && it.apiKey.isNotBlank() } }
    var selected by remember(accounts) { mutableStateOf(accounts.firstOrNull()) }
    var starting by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onClose, containerColor = V3.Surface) {
        Column(Modifier.padding(horizontal = 18.dp).padding(bottom = 24.dp)) {
            Text("Lancer une conversation", color = V3.Fg, style = MaterialTheme.typography.titleLarge)
            Text(
                "Choix de l'agent selon les comptes disponibles",
                color = V3.Muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp, bottom = 10.dp),
            )
            if (accounts.isEmpty()) {
                Text("Aucun compte agent activé. Ajoute-en dans Réglages.", color = V3.Warn, style = MaterialTheme.typography.bodyMedium)
            } else {
                accounts.forEach { acc ->
                    val sel = acc.id == selected?.id
                    ListItem(
                        headlineContent = { Text(acc.label, color = V3.Fg) },
                        supportingContent = { Text(acc.backend.name, color = V3.Muted, fontFamily = FontFamily.Monospace) },
                        trailingContent = { RadioButton(selected = sel, onClick = { selected = acc }) },
                        colors = ListItemDefaults.colors(containerColor = if (sel) V3.SurfaceHi else V3.Surface),
                        modifier = Modifier.clickable { selected = acc },
                    )
                }
                Spacer(Modifier.height(14.dp))
                Button(
                    onClick = {
                        val acc = selected ?: return@Button
                        starting = true
                        scope.launch {
                            try {
                                val sessionId = deps.featureRepository.startFeature(featureId, acc)
                                onStarted(sessionId)
                            } catch (_: Exception) {
                                starting = false
                            }
                        }
                    },
                    enabled = selected != null && !starting,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = V3.Accent, contentColor = V3.AccentInk),
                ) { Text(if (starting) "Démarrage…" else "Démarrer la session") }
            }
        }
    }
}
