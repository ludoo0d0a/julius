package fr.geoking.julius.ui.v3

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VerticalAlignBottom
import androidx.compose.material.icons.filled.VerticalAlignTop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import fr.geoking.julius.api.github.parseGitHubPullRequestUrl
import fr.geoking.julius.persistence.FeatureEntity
import fr.geoking.julius.persistence.JulesSessionEntity
import kotlinx.coroutines.CoroutineScope
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.geoking.julius.SettingsManager
import fr.geoking.julius.queue.CodingAgentQueueEngine
import fr.geoking.julius.queue.queuePolicyFor
import fr.geoking.julius.repository.FeatureRepository
import fr.geoking.julius.repository.GitHubBuildRepository
import fr.geoking.julius.repository.JulesRepository
import fr.geoking.julius.shared.voice.VoiceManager
import fr.geoking.julius.ui.components.VoiceInputIcon
import kotlinx.coroutines.launch

/** Dependency bundle threaded into the v3 screens (reuses existing Koin singletons). */
class V3Deps(
    val settingsManager: SettingsManager,
    val julesRepository: JulesRepository,
    val featureRepository: FeatureRepository,
    val queueEngine: CodingAgentQueueEngine,
    val buildRepository: GitHubBuildRepository,
    val voiceManager: VoiceManager,
)

private sealed class V3Sheet {
    data object AddProject : V3Sheet()
    data class Launch(val featureId: String) : V3Sheet()
    data class EditFeature(val featureId: String) : V3Sheet()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JuliusV3App(deps: V3Deps, onExit: () -> Unit) {
    JuliusV3Theme {
        val nav = rememberV3NavController()
        val scope = rememberCoroutineScope()
        val snackbar = remember { SnackbarHostState() }
        var sheet by remember { mutableStateOf<V3Sheet?>(null) }
        var showMenu by remember { mutableStateOf(false) }
        var convActionTick by remember { mutableStateOf(0) }

        // Scroll triggers for conversation
        var scrollTrigger by remember { mutableStateOf<Int?>(null) } // 0 for Top, 1 for Bottom
        var getActivitiesJson by remember { mutableStateOf< (suspend () -> String)?>(null) }

        // Hardware back: pop the v3 stack, or exit to the host dashboard.
        BackHandler(enabled = true) { if (!nav.pop()) onExit() }

        val route = nav.current
        val showBottomBar = route !is V3Route.Conversation && route !is V3Route.GitCi && route !is V3Route.PrConflict

        var showDeleteConfirm by remember { mutableStateOf(false) }

        val uriHandler = LocalUriHandler.current

        Scaffold(
            containerColor = V3.Bg,
            snackbarHost = { SnackbarHost(snackbar) },
            topBar = {
                val sourceName = (route as? V3Route.ProjectFeatures)?.sourceName
                val featureId = (route as? V3Route.FeatureDetail)?.featureId
                val sessionId = (route as? V3Route.Conversation)?.sessionId

                val session by produceState<fr.geoking.julius.persistence.JulesSessionEntity?>(initialValue = null, sessionId, convActionTick) {
                    value = sessionId?.let { deps.julesRepository.getSession(it) }
                }
                val feature by produceState<fr.geoking.julius.persistence.FeatureEntity?>(initialValue = null, featureId) {
                    value = featureId?.let { deps.featureRepository.getFeature(it) }
                }

                TopAppBar(
                    title = {
                        Column {
                            val title = when (route) {
                                is V3Route.Scheduler -> "Dashboard"
                                is V3Route.Features -> "Features"
                                is V3Route.Projects -> "Projets"
                                is V3Route.Settings -> "Réglages"
                                is V3Route.ProjectFeatures -> route.sourceName
                                is V3Route.AddFeature -> "Nouvelle feature"
                                is V3Route.AgentDetail -> if (route.accountId == null) "Nouvel agent" else "Agent"
                                is V3Route.FeatureDetail -> "Détail Feature"
                                is V3Route.Conversation -> session?.prTitle ?: session?.title?.ifBlank { session?.prompt?.take(48) } ?: "Conversation"
                                is V3Route.GitCi -> "Git & CI"
                                is V3Route.PrConflict -> "Conflits PR"
                                is V3Route.JsonDebug -> route.title
                            }
                            Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)

                            val subtitle = when (route) {
                                is V3Route.Conversation -> {
                                    val backend = if (sessionId?.startsWith("sesn_") == true) "CLAUDE_CODE" else "JULES"
                                    "$backend${if (session?.prUrl.isNullOrBlank()) "" else " · PR"} · ${session?.sourceName ?: "…"}"
                                }
                                is V3Route.GitCi -> "${route.owner}/${route.repo}"
                                is V3Route.PrConflict -> "Résolution de conflits"
                                else -> null
                            }
                            if (subtitle != null) {
                                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = V3.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    },
                    navigationIcon = {
                        if (nav.canPop) {
                            IconButton(onClick = {
                                // PrConflict has its own back handling for selected file
                                // But TopAppBar back should probably always pop the route
                                nav.pop()
                            }) {
                                Icon(Icons.Filled.ArrowBack, "Retour")
                            }
                        }
                    },
                    actions = {
                        if (route is V3Route.Scheduler) {
                            val st by deps.settingsManager.settings.collectAsState()
                            val backend = st.codingAgentBackend
                            val policy = st.queuePolicyFor(backend)
                            TooltipIconButton(
                                hint = if (policy.queuePaused) "Reprendre la file" else "Mettre la file en pause",
                                icon = if (policy.queuePaused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                                tint = if (policy.queuePaused) V3.Warn else V3.Fg,
                            ) {
                                deps.settingsManager.saveSettings(
                                    st.copy(queuePolicies = st.queuePolicies + (backend to policy.copy(queuePaused = !policy.queuePaused))),
                                )
                                scope.launch { deps.queueEngine.tick() }
                            }
                        }
                        if (route is V3Route.FeatureDetail && feature != null) {
                            val clipboard = LocalClipboard.current
                            TooltipIconButton("Éditer", Icons.Filled.Edit) {
                                sheet = V3Sheet.EditFeature(route.featureId)
                            }
                            TooltipIconButton("Copier le texte", Icons.Filled.ContentCopy) {
                                scope.launch {
                                    clipboard.setClipEntry(androidx.compose.ui.platform.ClipEntry(
                                        android.content.ClipData.newPlainText("feature_prompt", feature!!.description.ifBlank { feature!!.title })
                                    ))
                                    snackbar.showSnackbar("Texte copié")
                                }
                            }
                        }

                        if (route is V3Route.Conversation && session != null) {
                            TooltipIconButton("Ouvrir dans une page web", Icons.AutoMirrored.Filled.OpenInNew) {
                                session?.url?.let { uriHandler.openUri(it) }
                            }
                            TooltipIconButton("Début", Icons.Filled.VerticalAlignTop) {
                                scrollTrigger = 0
                            }
                            TooltipIconButton("Fin", Icons.Filled.VerticalAlignBottom) {
                                scrollTrigger = 1
                            }
                            TooltipIconButton("Debug API", Icons.Filled.BugReport) {
                                scope.launch {
                                    val json = getActivitiesJson?.invoke() ?: "{}"
                                    nav.push(V3Route.JsonDebug("Debug Activités", json))
                                }
                            }
                        }

                        if (route is V3Route.Features || route is V3Route.ProjectFeatures) {
                            val scopedSource = (route as? V3Route.ProjectFeatures)?.sourceName
                            IconButton(onClick = { deps.featureRepository.scheduleWorker() }) {
                                Icon(Icons.Filled.Refresh, "Refresh")
                            }
                            IconButton(onClick = { scope.launch { deps.featureRepository.retryFailedFeatures(scopedSource) } }) {
                                Icon(Icons.Filled.Replay, "Retry FAILED", tint = V3.Warn)
                            }
                            Box {
                                IconButton(onClick = { showMenu = true }) {
                                    Icon(Icons.Filled.MoreVert, "Plus")
                                }
                                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                                    DropdownMenuItem(
                                        text = { Text(if (scopedSource != null) "Archiver terminées ($scopedSource)" else "Archiver toutes les terminées") },
                                        onClick = {
                                            showMenu = false
                                            scope.launch { deps.featureRepository.archiveCompletedFeatures(scopedSource) }
                                        },
                                        leadingIcon = { Icon(Icons.Filled.Archive, null) }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(if (scopedSource != null) "Supprimer TOUT ($scopedSource)" else "Supprimer TOUT", color = V3.Danger) },
                                        onClick = {
                                            showMenu = false
                                            showDeleteConfirm = true
                                        },
                                        leadingIcon = { Icon(Icons.Filled.Delete, null, tint = V3.Danger) }
                                    )
                                }
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = V3.Surface,
                        titleContentColor = V3.Fg,
                        navigationIconContentColor = V3.Fg,
                        actionIconContentColor = V3.Fg,
                    )
                )
            },
            bottomBar = { if (showBottomBar) V3BottomBar(nav.activeTab) { nav.selectTab(it) } },
            floatingActionButton = {
                if (showBottomBar) {
                    val r = nav.current
                    if (r is V3Route.FeatureDetail) {
                        // Feature detail: icon-only speed dial (launch / retry / close).
                        FeatureDetailSpeedDial(
                            onLaunch = { sheet = V3Sheet.Launch(r.featureId) },
                            onRetry = {
                                scope.launch {
                                    deps.featureRepository.updateFeatureStatus(r.featureId, "PENDING")
                                    deps.featureRepository.scheduleWorker()
                                    snackbar.showSnackbar("Feature relancée")
                                }
                            },
                            onClose = {
                                scope.launch {
                                    deps.featureRepository.updateFeatureStatus(r.featureId, "COMPLETED")
                                    snackbar.showSnackbar("Feature fermée")
                                }
                            },
                        )
                    } else if (r is V3Route.Scheduler) {
                        // Dashboard: icon-only FAB (Features icon) to add a feature.
                        FloatingActionButton(
                            onClick = { nav.push(V3Route.AddFeature(null)) },
                            containerColor = V3.Accent, contentColor = V3.AccentInk,
                        ) { Icon(Icons.Filled.List, "Ajouter une feature") }
                    } else if (fabFor(r) != null) {
                        ExtendedFloatingActionButton(
                            text = { Text(fabFor(r)!!.label) },
                            icon = { Icon(Icons.Filled.Add, null) },
                            onClick = {
                                when (r) {
                                    is V3Route.Projects -> sheet = V3Sheet.AddProject
                                    is V3Route.ProjectFeatures -> nav.push(V3Route.AddFeature(r.sourceName))
                                    is V3Route.Features -> nav.push(V3Route.AddFeature(null))
                                    is V3Route.Scheduler -> nav.push(V3Route.AddFeature(null))
                                    else -> {}
                                }
                            },
                            containerColor = V3.Accent,
                            contentColor = V3.AccentInk,
                        )
                    }
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
                        onOpenProject = { nav.selectTab(V3Route.Features); nav.push(V3Route.ProjectFeatures(it)) },
                    )
                    is V3Route.FeatureDetail -> FeatureDetailV3Screen(
                        deps = deps, featureId = r.featureId, onBack = { nav.pop() },
                        onOpenConversation = { nav.push(V3Route.Conversation(it)) },
                        onLaunch = { sheet = V3Sheet.Launch(r.featureId) },
                    )
                    is V3Route.Conversation -> ConversationV3Screen(
                        deps = deps, sessionId = r.sessionId, onBack = { nav.pop() },
                        onOpenGitCi = { owner, repo -> nav.push(V3Route.GitCi(owner, repo)) },
                        onOpenConflict = { prUrl -> nav.push(V3Route.PrConflict(r.sessionId, prUrl)) },
                        scrollTrigger = scrollTrigger,
                        onScrolled = { scrollTrigger = null },
                        onProvideJson = { getActivitiesJson = it as (suspend () -> String) }
                    )
                    is V3Route.GitCi -> GitCiV3Screen(
                        deps = deps, owner = r.owner, repo = r.repo, onBack = { nav.pop() },
                    )
                    is V3Route.PrConflict -> PrConflictV3Screen(
                        deps = deps, prUrl = r.prUrl, onBack = { nav.pop() },
                    )
                    is V3Route.AddFeature -> AddFeatureV3Screen(
                        deps = deps, sourceName = r.sourceName, onBack = { nav.pop() },
                        onDone = { msg -> nav.pop(); scope.launch { snackbar.showSnackbar(msg) } }
                    )
                    is V3Route.Settings -> SettingsV3Screen(deps = deps, onOpenAgent = { nav.push(V3Route.AgentDetail(it)) })
                    is V3Route.AgentDetail -> AgentDetailV3Screen(deps = deps, accountId = r.accountId, onBack = { nav.pop() })
                    is V3Route.JsonDebug -> JsonDebugScreen(title = r.title, json = r.json, onBack = { nav.pop() })
                }
            }
        }

        // ---- bottom sheets (add feature / connect repo / launch conversation) ----
        when (val s = sheet) {
            is V3Sheet.AddProject -> AddProjectSheet(deps, onClose = { sheet = null }) { msg ->
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
            is V3Sheet.EditFeature -> EditFeatureSheet(deps, s.featureId, onClose = { sheet = null }) { msg ->
                sheet = null; scope.launch { snackbar.showSnackbar(msg) }
            }
            null -> {}
        }

        if (showDeleteConfirm) {
            val scopedSource = (route as? V3Route.ProjectFeatures)?.sourceName
            AlertDialog(
                onDismissRequest = { showDeleteConfirm = false },
                title = { Text("Supprimer TOUT") },
                text = { Text(if (scopedSource != null) "Voulez-vous vraiment supprimer toutes les features du projet $scopedSource ?" else "Voulez-vous vraiment supprimer TOUTES les features de TOUS les projets ?") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showDeleteConfirm = false
                            scope.launch { deps.featureRepository.deleteAllFeatures(scopedSource) }
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = V3.Danger)
                    ) { Text("Supprimer") }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirm = false }) { Text("Annuler") }
                },
                containerColor = V3.Surface,
                titleContentColor = V3.Fg,
                textContentColor = V3.Muted,
            )
        }
    }
}

private data class FabSpec(val label: String)

private fun fabFor(route: V3Route): FabSpec? = when (route) {
    is V3Route.Projects -> FabSpec("Projet")
    is V3Route.Scheduler, is V3Route.Features, is V3Route.ProjectFeatures -> FabSpec("Feature")
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
    Triple(V3Route.Scheduler, "Dashboard", Icons.Filled.Schedule),
    Triple(V3Route.Projects, "Projets", Icons.Filled.FolderOpen),
    Triple(V3Route.Features, "Features", Icons.Filled.List),
    Triple(V3Route.Settings, "Réglages", Icons.Filled.Settings),
)

// ---------------------------------------------------------------------------
// Conversation top-bar actions (Material 3: primary icon w/ tooltip + overflow)
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TooltipIconButton(hint: String, icon: ImageVector, tint: Color = V3.Fg, enabled: Boolean = true, onClick: () -> Unit) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(hint) } },
        state = rememberTooltipState(),
    ) {
        IconButton(onClick = onClick, enabled = enabled) { Icon(icon, hint, tint = tint) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConversationTopBarActions(
    deps: V3Deps,
    session: JulesSessionEntity,
    sessionId: String,
    onResolve: (String) -> Unit,
    onGitCi: (String, String) -> Unit,
    snackbar: SnackbarHostState,
    scope: CoroutineScope,
    onActed: () -> Unit,
) {
    val token = deps.settingsManager.settings.collectAsState().value.githubApiKey
    val uriHandler = LocalUriHandler.current
    var busy by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    val prUrl = session.prUrl
    val hasPr = !prUrl.isNullOrBlank()
    val conflict = session.prMergeable == false
    val merged = session.prState == "merged"

    // Primary action: merge when clean, resolve when in conflict.
    if (hasPr && !merged) {
        if (conflict) {
            TooltipIconButton("Résoudre les conflits", Icons.Filled.Warning, tint = V3.Danger) { onResolve(prUrl!!) }
        } else {
            TooltipIconButton("Merger", Icons.Filled.Done, tint = V3.Accent, enabled = !busy) {
                busy = true
                scope.launch {
                    val res = deps.julesRepository.mergePr(token, sessionId, prUrl!!)
                    busy = false; onActed()
                    snackbar.showSnackbar(if (res.isSuccess) "PR mergée" else "Échec du merge")
                }
            }
        }
    }

    Box {
        IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Plus") }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            if (hasPr) {
                DropdownMenuItem(
                    text = { Text("Voir la PR") }, onClick = { menu = false; uriHandler.openUri(prUrl!!) },
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.OpenInNew, null) },
                )
                DropdownMenuItem(
                    text = { Text("Git & CI") },
                    onClick = { menu = false; parseGitHubPullRequestUrl(prUrl!!)?.let { onGitCi(it.owner, it.repo) } },
                    leadingIcon = { Icon(Icons.Filled.Build, null) },
                )
                if (conflict) {
                    DropdownMenuItem(
                        text = { Text("Résoudre les conflits") }, onClick = { menu = false; onResolve(prUrl!!) },
                        leadingIcon = { Icon(Icons.Filled.Warning, null, tint = V3.Danger) },
                    )
                }
            }
            session.url?.takeIf { it.isNotBlank() }?.let { url ->
                DropdownMenuItem(
                    text = { Text("Ouvrir la session") }, onClick = { menu = false; uriHandler.openUri(url) },
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.OpenInNew, null) },
                )
            }
            DropdownMenuItem(
                text = { Text("Relancer l'agent") },
                onClick = {
                    menu = false
                    scope.launch {
                        runCatching { deps.julesRepository.sendMessage(sessionId, "Corrige les problèmes et repousse la PR.") }
                        snackbar.showSnackbar("Relance envoyée à l'agent")
                    }
                },
                leadingIcon = { Icon(Icons.Filled.Replay, null) },
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Feature-detail speed dial (icon-only FAB with launch / retry / close)
// ---------------------------------------------------------------------------

@Composable
private fun FeatureDetailSpeedDial(onLaunch: () -> Unit, onRetry: () -> Unit, onClose: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
        if (open) {
            MiniAction("Fermer", Icons.Filled.Done) { open = false; onClose() }
            MiniAction("Relancer", Icons.Filled.Replay) { open = false; onRetry() }
            MiniAction("Conversation", Icons.AutoMirrored.Filled.Chat) { open = false; onLaunch() }
        }
        FloatingActionButton(
            onClick = { open = !open },
            containerColor = V3.Accent, contentColor = V3.AccentInk,
        ) { Icon(if (open) Icons.Filled.Close else Icons.Filled.Add, "Actions") }
    }
}

@Composable
private fun MiniAction(label: String, icon: ImageVector, onClick: () -> Unit) {
    Row(
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        modifier = Modifier.padding(bottom = 12.dp),
    ) {
        Surface(color = V3.Surface, shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, V3.Border)) {
            Text(label, color = V3.Fg, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp))
        }
        Spacer(Modifier.width(10.dp))
        SmallFloatingActionButton(onClick = onClick, containerColor = V3.SurfaceHi, contentColor = V3.Fg) {
            Icon(icon, label, modifier = Modifier.size(20.dp))
        }
    }
}

// ---------------------------------------------------------------------------
// Sheets
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditFeatureSheet(deps: V3Deps, featureId: String, onClose: () -> Unit, onDone: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var feature by remember(featureId) { mutableStateOf<FeatureEntity?>(null) }
    LaunchedEffect(featureId) { feature = deps.featureRepository.getFeature(featureId) }
    val f = feature
    var title by remember(f) { mutableStateOf(f?.title ?: "") }
    var desc by remember(f) { mutableStateOf(f?.description ?: "") }
    ModalBottomSheet(onDismissRequest = onClose, containerColor = V3.Surface) {
        Column(Modifier.padding(horizontal = 18.dp).padding(bottom = 24.dp)) {
            Text("Éditer la feature", color = V3.Fg, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(title, { title = it }, label = { Text("Titre") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(desc, { desc = it }, label = { Text("Description") }, modifier = Modifier.fillMaxWidth().height(110.dp))
            Spacer(Modifier.height(14.dp))
            Button(
                onClick = {
                    val cur = f ?: return@Button
                    if (title.isNotBlank()) scope.launch {
                        deps.featureRepository.updateFeature(cur.copy(title = title.trim(), description = desc.trim()))
                        onDone("Feature mise à jour")
                    }
                },
                enabled = f != null && title.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = V3.Accent, contentColor = V3.AccentInk),
            ) { Text("Enregistrer") }
        }
    }
}

@Composable
private fun AddFeatureV3Screen(
    deps: V3Deps,
    sourceName: String?,
    onBack: () -> Unit,
    onDone: (String) -> Unit,
) {
    val settings by deps.settingsManager.settings.collectAsState()
    val scope = rememberCoroutineScope()
    var title by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var source by remember { mutableStateOf(sourceName ?: settings.lastJulesRepoName) }
    var saving by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp).padding(top = 8.dp)) {
        OutlinedTextField(
            title, { title = it },
            label = { Text("Titre") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = {
                VoiceInputIcon(
                    voiceManager = deps.voiceManager,
                    onTranscriptionReceived = { title = (title + " " + it).trim() },
                    tint = V3.Accent
                )
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = V3.Fg,
                unfocusedTextColor = V3.Fg,
                focusedBorderColor = V3.Accent,
                unfocusedBorderColor = V3.Border,
                focusedLabelColor = V3.Accent,
                unfocusedLabelColor = V3.Muted,
            )
        )
        Spacer(Modifier.height(14.dp))
        OutlinedTextField(
            desc, { desc = it },
            label = { Text("Description / contexte") },
            modifier = Modifier.fillMaxWidth().height(150.dp),
            trailingIcon = {
                VoiceInputIcon(
                    voiceManager = deps.voiceManager,
                    onTranscriptionReceived = { desc = (desc + " " + it).trim() },
                    tint = V3.Accent
                )
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = V3.Fg,
                unfocusedTextColor = V3.Fg,
                focusedBorderColor = V3.Accent,
                unfocusedBorderColor = V3.Border,
                focusedLabelColor = V3.Accent,
                unfocusedLabelColor = V3.Muted,
            )
        )
        Spacer(Modifier.height(14.dp))
        OutlinedTextField(
            source, { source = it },
            label = { Text("Dépôt (owner/repo)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = V3.Fg,
                unfocusedTextColor = V3.Fg,
                focusedBorderColor = V3.Accent,
                unfocusedBorderColor = V3.Border,
                focusedLabelColor = V3.Accent,
                unfocusedLabelColor = V3.Muted,
            )
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = {
                if (title.isNotBlank() && source.isNotBlank()) {
                    saving = true
                    scope.launch {
                        try {
                            deps.featureRepository.addFeature(title.trim(), desc.trim(), 0, source.trim())
                            deps.featureRepository.scheduleWorker()
                            onDone("Feature ajoutée à la file")
                        } finally {
                            saving = false
                        }
                    }
                }
            },
            enabled = title.isNotBlank() && source.isNotBlank() && !saving,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = V3.Accent, contentColor = V3.AccentInk),
        ) {
            if (saving) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = V3.AccentInk, strokeWidth = 2.dp)
            } else {
                Text("Ajouter à la file")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddProjectSheet(deps: V3Deps, onClose: () -> Unit, onDone: (String) -> Unit) {
    val settings by deps.settingsManager.settings.collectAsState()
    var repo by remember { mutableStateOf(settings.lastJulesRepoName) }
    ModalBottomSheet(onDismissRequest = onClose, containerColor = V3.Surface) {
        Column(Modifier.padding(horizontal = 18.dp).padding(bottom = 24.dp)) {
            Text("Projet actif", color = V3.Fg, style = MaterialTheme.typography.titleLarge)
            Text(
                "Les dépôts sont découverts via Jules / GitHub. Renseigne owner/repo pour le définir comme projet actif (mémorisé).",
                color = V3.Muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp, bottom = 6.dp),
            )
            OutlinedTextField(
                repo, { repo = it }, label = { Text("owner/repo") }, singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(14.dp))
            Button(
                onClick = {
                    val r = repo.trim()
                    if (r.isNotBlank()) {
                        deps.settingsManager.saveSettings(settings.copy(lastJulesRepoName = r, lastJulesRepoId = r))
                        onDone("Projet actif : $r")
                    }
                },
                enabled = repo.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = V3.Accent, contentColor = V3.AccentInk),
            ) { Text("Définir comme projet actif") }
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
    val status by deps.queueEngine.status.collectAsState()
    val scope = rememberCoroutineScope()
    val accounts = remember(settings) { settings.agentAccounts.filter { it.enabled && it.apiKey.isNotBlank() } }
    val rows = remember(status) { status.accounts.associateBy { it.accountId } }
    fun atLimit(id: String): Boolean { val r = rows[id]; return r != null && r.dailyLimit > 0 && r.usedToday >= r.dailyLimit }
    // Strategy: prefer accounts under quota, fewest active sessions, then lowest daily usage.
    val recommended = remember(accounts, rows) {
        accounts.filterNot { atLimit(it.id) }
            .minWithOrNull(compareBy({ rows[it.id]?.activeSessions ?: 0 }, { rows[it.id]?.usedToday ?: 0 }))
    }
    var selected by remember(recommended) { mutableStateOf(recommended ?: accounts.firstOrNull { !atLimit(it.id) } ?: accounts.firstOrNull()) }
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
                    val r = rows[acc.id]
                    val full = atLimit(acc.id)
                    val quota = when {
                        r == null -> acc.backend.name
                        r.dailyLimit > 0 -> "${acc.backend.name} · ${r.usedToday}/${r.dailyLimit}" + if (r.activeSessions > 0) " · ${r.activeSessions} act." else ""
                        else -> "${acc.backend.name} · ${r.usedToday} aujourd'hui"
                    }
                    ListItem(
                        headlineContent = {
                            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                Text(acc.label, color = if (full) V3.Faint else V3.Fg)
                                if (acc.id == recommended?.id && !full) {
                                    Spacer(Modifier.width(8.dp))
                                    Text("recommandé", color = V3.Success, fontSize = 11.sp)
                                }
                                if (full) {
                                    Spacer(Modifier.width(8.dp))
                                    Text("quota atteint", color = V3.Danger, fontSize = 11.sp)
                                }
                            }
                        },
                        supportingContent = { Text(quota, color = V3.Muted, fontFamily = FontFamily.Monospace, fontSize = 11.5.sp) },
                        trailingContent = { RadioButton(selected = sel, onClick = { if (!full) selected = acc }, enabled = !full) },
                        colors = ListItemDefaults.colors(containerColor = if (sel) V3.SurfaceHi else V3.Surface),
                        modifier = Modifier.clickable(enabled = !full) { selected = acc },
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
