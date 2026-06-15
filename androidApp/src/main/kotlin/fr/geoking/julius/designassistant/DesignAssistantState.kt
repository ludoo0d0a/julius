package fr.geoking.julius.designassistant

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import fr.geoking.julius.SettingsManager
import fr.geoking.julius.api.jules.JulesClient
import fr.geoking.julius.persistence.FeatureEntity
import fr.geoking.julius.persistence.JulesSessionEntity
import fr.geoking.julius.repository.BuildStatusSummary
import fr.geoking.julius.repository.FeatureRepository
import fr.geoking.julius.repository.GitHubBuildRepository
import fr.geoking.julius.repository.JulesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class DesignAssistantState(
    private val scope: CoroutineScope,
    private val julesRepository: JulesRepository,
    private val featureRepository: FeatureRepository,
    private val settingsManager: SettingsManager,
    private val buildRepository: GitHubBuildRepository,
) {
    var projects by mutableStateOf<List<DesignProject>>(emptyList())
        private set
    var projectsLoading by mutableStateOf(false)
        private set
    var projectsError by mutableStateOf<String?>(null)
        private set

    var allFeatures by mutableStateOf<List<FeatureEntity>>(emptyList())
        private set
    var sessions by mutableStateOf<List<JulesSessionEntity>>(emptyList())
        private set
    var sources by mutableStateOf<List<JulesClient.JulesSource>>(emptyList())
        private set

    var featureSearchQuery by mutableStateOf("")
    var showAddFeatureDialog by mutableStateOf(false)

    val chatMessages = mutableStateListOf<DesignChatMessage>()
    var workspaceCode by mutableStateOf("")
        private set
    var workspaceFiles by mutableStateOf<List<String>>(emptyList())
        private set
    var workspaceLoading by mutableStateOf(false)
        private set
    var workspaceError by mutableStateOf<String?>(null)
        private set

    var activeSession by mutableStateOf<JulesSessionEntity?>(null)
        private set
    var pickerSessions by mutableStateOf<List<JulesSessionEntity>>(emptyList())
        private set

    var buildSummary by mutableStateOf<BuildStatusSummary?>(null)
        private set
    var buildLoading by mutableStateOf(false)
        private set
    var buildError by mutableStateOf<String?>(null)
        private set

    var messageDraft by mutableStateOf("")
    var sendingMessage by mutableStateOf(false)

    private var sourcesJob: Job? = null
    private var featuresJob: Job? = null
    private var sessionsJob: Job? = null
    private var activitiesJob: Job? = null
    private val defaultBranchBySource = mutableMapOf<String, String>()

    val apiKeys: List<String>
        get() = settingsManager.settings.value.julesKeys

    val githubToken: String
        get() = settingsManager.settings.value.githubApiKey

    fun loadProjects() {
        sourcesJob?.cancel()
        featuresJob?.cancel()
        if (apiKeys.isEmpty()) {
            projects = emptyList()
            projectsError = "Ajoutez une clé Jules dans les réglages."
            projectsLoading = false
            return
        }
        projectsLoading = true
        projectsError = null
        featuresJob = scope.launch {
            featureRepository.getAllFeatures().collectLatest { feats ->
                allFeatures = feats
                refreshProjectList(sources, feats, sessions)
            }
        }
        sourcesJob = scope.launch {
            try {
                julesRepository.getSources(apiKeys).collectLatest { list ->
                    sources = list
                    refreshDefaultBranches(list)
                    refreshProjectList(list, allFeatures, sessions)
                    projectsLoading = false
                }
            } catch (e: Exception) {
                projectsError = e.message ?: "Impossible de charger les projets"
                projectsLoading = false
            }
        }
    }

    fun loadFeaturesForProject(sourceName: String) {
        sessionsJob?.cancel()
        if (apiKeys.isEmpty()) return
        sessionsJob = scope.launch {
            try {
                julesRepository.getSessions(apiKeys, sourceName, githubToken).collectLatest { list ->
                    sessions = mergeSessionsForSource(sourceName, list)
                    refreshProjectList(sources, allFeatures, sessions)
                    featureRepository.autoPromoteOrphans(scope, sourceName, list)
                }
            } catch (e: Exception) {
                projectsError = e.message
            }
        }
    }

    fun projectWithFeatures(sourceName: String): DesignProject? {
        val source = sources.find { it.name == sourceName } ?: return null
        val repoSessions = sessions.filter { it.sourceName == sourceName && !it.isArchived }
        val features = DesignAssistantMapper.buildProjectFeatures(
            sourceName = sourceName,
            entities = allFeatures,
            sessions = repoSessions,
        )
        val filtered = filterFeatures(features, featureSearchQuery)
        return DesignAssistantMapper.sourceToProject(
            source,
            allFeatures,
            repoSessions,
            mainBranch = defaultBranchFor(source),
        ).copy(features = filtered)
    }

    fun filterFeatures(features: List<DesignFeature>, query: String): List<DesignFeature> {
        val trimmed = query.trim()
        return if (trimmed.isEmpty()) {
            features
        } else {
            features.filter { it.name.contains(trimmed, ignoreCase = true) }
        }
    }

    fun openWorkspace(
        project: DesignProject,
        feature: DesignFeature,
    ) {
        workspaceError = null
        val sourceName = project.id
        val entity = allFeatures.find { it.id == feature.id }
        val matching = DesignAssistantMapper.resolveSessionsForFeature(
            feature = feature,
            sourceName = sourceName,
            sessions = sessions,
            featureSessionId = entity?.sessionId,
        )
        pickerSessions = matching
        activeSession = DesignAssistantMapper.resolveActiveSession(
            feature = feature,
            sourceName = sourceName,
            sessions = sessions,
            featureSessionId = entity?.sessionId,
            selectedSessionId = null,
        )
        loadWorkspaceData(project, feature)
        loadBuildStatus(project)
    }

    fun openWorkspaceForSession(
        project: DesignProject,
        session: JulesSessionEntity
    ) {
        workspaceError = null
        val sourceName = project.id
        val feature = DesignAssistantMapper.resolveFeatureForSession(session, project)
        val matching = if (feature != null) {
            val entity = allFeatures.find { it.id == feature.id }
            DesignAssistantMapper.resolveSessionsForFeature(
                feature = feature,
                sourceName = sourceName,
                sessions = sessions,
                featureSessionId = entity?.sessionId,
            )
        } else {
            listOf(session)
        }

        pickerSessions = matching
        activeSession = session
        if (feature != null) {
            loadWorkspaceData(project, feature)
        }
        loadBuildStatus(project)
    }

    fun sessionsForProject(sourceName: String): List<JulesSessionEntity> {
        return sessions.filter { it.sourceName == sourceName && !it.isArchived }
            .sortedByDescending { it.lastUpdated }
    }

    fun selectWorkspaceSession(session: JulesSessionEntity, project: DesignProject, feature: DesignFeature) {
        activeSession = session
        loadWorkspaceData(project, feature)
    }

    fun pauseSession() {
        val session = activeSession ?: return
        scope.launch {
            try {
                julesRepository.pauseSession(session.id)
                refreshActiveSession()
            } catch (e: Exception) {
                workspaceError = e.message
            }
        }
    }

    fun resumeSession() {
        val session = activeSession ?: return
        scope.launch {
            try {
                julesRepository.resumeSession(session.id)
                refreshActiveSession()
            } catch (e: Exception) {
                workspaceError = e.message
            }
        }
    }

    fun archiveSession() {
        val session = activeSession ?: return
        scope.launch {
            try {
                julesRepository.archiveSession(session.id)
                activeSession = null
                // We might need to refresh the sessions list too
                sessions = sessions.filter { it.id != session.id }
            } catch (e: Exception) {
                workspaceError = e.message
            }
        }
    }

    fun refreshSessionStatus() {
        val session = activeSession ?: return
        scope.launch {
            try {
                julesRepository.pollSessionStatus(session.id, githubToken)
                refreshActiveSession()
            } catch (e: Exception) {
                workspaceError = e.message
            }
        }
    }

    private suspend fun refreshActiveSession() {
        val session = activeSession ?: return
        val updated = julesRepository.getSession(session.id)
        if (updated != null) {
            activeSession = updated
            // Update the sessions list too
            sessions = sessions.map { if (it.id == updated.id) updated else it }
        }
    }

    private fun loadWorkspaceData(project: DesignProject, feature: DesignFeature) {
        activitiesJob?.cancel()
        chatMessages.clear()
        workspaceCode = ""
        workspaceFiles = emptyList()
        val session = activeSession
        if (session == null) {
            workspaceError = null
            return
        }
        workspaceLoading = true
        activitiesJob = scope.launch {
            try {
                julesRepository.getActivities(session.id).collectLatest { items ->
                    chatMessages.clear()
                    chatMessages.addAll(DesignAssistantMapper.chatItemsToDesignMessages(items))
                    buildSummary?.let { ci ->
                        DesignAssistantMapper.buildCiMessage(ci)?.let { chatMessages.add(0, it) }
                    }
                    val cached = julesRepository.getActivitiesBySession(session.id)
                    val activities = DesignAssistantMapper.decodeActivitiesFromCache(cached)
                    val artifacts = DesignAssistantMapper.extractWorkspaceArtifacts(activities)
                    workspaceCode = artifacts.code.ifBlank { "" }
                    workspaceFiles = artifacts.files
                    workspaceError = null
                }
            } catch (e: Exception) {
                workspaceError = e.message ?: "Impossible de charger la conversation"
            } finally {
                workspaceLoading = false
            }
        }
    }

    private fun loadBuildStatus(project: DesignProject) {
        val source = sources.find { it.name == project.id }
        val gh = source?.githubRepo
        if (gh == null || githubToken.isBlank()) {
            buildSummary = null
            buildError = null
            buildLoading = false
            return
        }
        scope.launch {
            buildLoading = true
            buildError = null
            try {
                val resolved = buildRepository.resolveWorkflowId(githubToken, gh.owner, gh.repo)
                if (resolved != null) {
                    buildSummary = buildRepository.loadSummary(
                        githubToken,
                        gh.owner,
                        gh.repo,
                        resolved.first,
                        resolved.second,
                    )
                }
            } catch (e: Exception) {
                buildError = e.message ?: "Statut CI indisponible"
            } finally {
                buildLoading = false
            }
        }
    }

    fun sendMessage(project: DesignProject, feature: DesignFeature) {
        val text = messageDraft.trim()
        if (text.isBlank() || sendingMessage) return
        val sourceName = project.id
        scope.launch {
            sendingMessage = true
            try {
                var session = activeSession
                if (session == null) {
                    val entity = allFeatures.find { it.id == feature.id }
                    val isVirtual = DesignAssistantMapper.isVirtualId(feature.id)
                    val sessionId = if (isVirtual) {
                        julesRepository.createSession(
                            apiKeys = apiKeys,
                            prompt = text,
                            source = sourceName,
                            title = "Conversation",
                            featureId = null,
                        )
                    } else {
                        julesRepository.createSession(
                            apiKeys = apiKeys,
                            prompt = text,
                            source = sourceName,
                            title = entity?.title ?: feature.name,
                            featureId = feature.id,
                        )
                    }
                    session = julesRepository.getSession(sessionId)
                    activeSession = session
                    if (!isVirtual) {
                        val updated = entity!!.copy(
                            sessionId = sessionId,
                            status = "QUEUED",
                            updatedAt = System.currentTimeMillis()
                        )
                        featureRepository.updateFeature(updated)
                    }
                    loadFeaturesForProject(sourceName)
                } else {
                    julesRepository.sendMessage(session.id, text, session.apiKey)
                }
                messageDraft = ""
                if (session != null) {
                    loadWorkspaceData(project, feature)
                }
            } catch (e: Exception) {
                workspaceError = e.message ?: "Envoi impossible"
            } finally {
                sendingMessage = false
            }
        }
    }

    fun addFeature(title: String, description: String, sourceName: String) {
        scope.launch {
            featureRepository.addFeature(title, description, 0, sourceName)
            showAddFeatureDialog = false
        }
    }

    fun promoteSessionToFeature(sourceName: String, session: JulesSessionEntity, title: String) {
        scope.launch {
            try {
                val featureId = featureRepository.addFeature(
                    title = title,
                    description = session.prompt,
                    priority = 0,
                    sourceName = sourceName
                )
                julesRepository.linkSessionToFeature(session.id, featureId)
                refreshActiveSession()
                loadFeaturesForProject(sourceName)
            } catch (e: Exception) {
                workspaceError = e.message
            }
        }
    }

    fun renameFeature(featureId: String, newTitle: String) {
        scope.launch {
            try {
                val entity = allFeatures.find { it.id == featureId }
                if (entity != null) {
                    featureRepository.updateFeature(entity.copy(title = newTitle))
                }
            } catch (e: Exception) {
                workspaceError = e.message
            }
        }
    }

    fun dispose() {
        sourcesJob?.cancel()
        featuresJob?.cancel()
        sessionsJob?.cancel()
        activitiesJob?.cancel()
    }

    private suspend fun refreshDefaultBranches(sourceList: List<JulesClient.JulesSource>) {
        if (githubToken.isBlank()) return
        coroutineScope {
            sourceList.mapNotNull { source ->
                val gh = source.githubRepo ?: return@mapNotNull null
                async {
                    val branch = buildRepository.getDefaultBranch(githubToken, gh.owner, gh.repo)
                    source.name to branch
                }
            }.awaitAll().forEach { (name, branch) ->
                defaultBranchBySource[name] = branch
            }
        }
    }

    private fun defaultBranchFor(source: JulesClient.JulesSource): String {
        if (githubToken.isBlank()) return "unknown"
        defaultBranchBySource[source.name]?.let { return it }
        return "main"
    }

    private fun refreshProjectList(
        sourceList: List<JulesClient.JulesSource>,
        feats: List<FeatureEntity>,
        sess: List<JulesSessionEntity>,
    ) {
        projects = sourceList.map { source ->
            DesignAssistantMapper.sourceToProject(
                source,
                feats,
                sess,
                mainBranch = defaultBranchFor(source),
            )
        }
    }

    private fun mergeSessionsForSource(
        sourceName: String,
        incoming: List<JulesSessionEntity>,
    ): List<JulesSessionEntity> {
        val others = sessions.filter { it.sourceName != sourceName }
        return others + incoming
    }
}
