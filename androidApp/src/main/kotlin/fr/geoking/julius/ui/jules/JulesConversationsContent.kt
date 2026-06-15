package fr.geoking.julius.ui.jules

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.geoking.julius.api.github.GitHubClient
import fr.geoking.julius.persistence.FeatureEntity
import fr.geoking.julius.persistence.JulesSessionEntity
import fr.geoking.julius.ui.ColorHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun JulesConversationsContent(
    selectedSourceName: String,
    selectedFeatureId: String,
    sessions: List<JulesSessionEntity>,
    features: List<FeatureEntity>,
    loading: Boolean,
    newSessionPrompt: String,
    onNewSessionPromptChange: (String) -> Unit,
    onCreateSession: () -> Unit,
    onOpenSession: (JulesSessionEntity) -> Unit,
    onGetPrDetails: (JulesSessionEntity, (GitHubClient.GitHubPullRequestDetail?) -> Unit) -> Unit,
    onArchive: (JulesSessionEntity) -> Unit,
    onLinkToFeature: (JulesSessionEntity, String?) -> Unit,
    onCreateFeatureAndLink: (JulesSessionEntity, String) -> Unit,
    onArchiveCompleted: () -> Unit,
    archivingSessionIds: List<String> = emptyList(),
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    hideCompleted: Boolean,
    onHideCompletedChange: (Boolean) -> Unit,
) {
    var showPrDetails by remember { mutableStateOf<GitHubClient.GitHubPullRequestDetail?>(null) }
    var showLinkDialog by remember { mutableStateOf<JulesSessionEntity?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    val featureSessions = JulesNavigation.sessionsForFeature(sessions, selectedSourceName, selectedFeatureId)
    val displaySessions = featureSessions.filter {
        val matchesSearch = it.title.contains(searchQuery, ignoreCase = true) ||
            it.prompt.contains(searchQuery, ignoreCase = true)
        val matchesHideCompleted = !hideCompleted || !it.isFinished
        matchesSearch && matchesHideCompleted
    }.sortedByDescending { it.lastUpdated }

    JulesPullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item(key = "new_session") {
                OutlinedTextField(
                    value = newSessionPrompt,
                    onValueChange = onNewSessionPromptChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    placeholder = { Text("What should Jules do?") },
                    trailingIcon = {
                        IconButton(onClick = onCreateSession, enabled = newSessionPrompt.isNotBlank() && !loading) {
                            Icon(Icons.Default.Add, contentDescription = "Create")
                        }
                    },
                )
            }

            item(key = "search") {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    placeholder = { Text("Search…", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null, tint = Color.White.copy(alpha = 0.5f))
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear", tint = Color.White.copy(alpha = 0.5f))
                            }
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = ColorHelper.JulesAccent,
                        focusedBorderColor = ColorHelper.JulesAccent.copy(alpha = 0.5f),
                        unfocusedBorderColor = Color.White.copy(alpha = 0.1f),
                    ),
                    shape = RoundedCornerShape(12.dp),
                )
            }

            item(key = "header") {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "Conversations",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { onHideCompletedChange(!hideCompleted) }) {
                            Text(if (hideCompleted) "Show all" else "Hide completed")
                        }
                    }
                    if (featureSessions.any { it.isFinished }) {
                        TextButton(
                            onClick = onArchiveCompleted,
                            modifier = Modifier.align(Alignment.End),
                        ) {
                            Icon(Icons.Default.Archive, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.size(4.dp))
                            Text("Archive all completed", fontSize = 12.sp)
                        }
                    }
                }
            }

            if (displaySessions.isEmpty()) {
                item(key = "empty") {
                    Text(
                        "No conversations yet. Pull down to refresh.",
                        color = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            } else {
                items(displaySessions, key = { it.id }) { session ->
                    JulesSessionListItem(
                        session = session,
                        archivingSessionIds = archivingSessionIds,
                        onOpenSession = onOpenSession,
                        onGetPrDetails = onGetPrDetails,
                        onArchive = onArchive,
                        onLinkToFeature = { showLinkDialog = session },
                        onShowPrDetails = { showPrDetails = it },
                    )
                }
            }
        }
    }

    showPrDetails?.let { pr ->
        JulesPrDetailsDialog(pr = pr, onDismiss = { showPrDetails = null })
    }

    showLinkDialog?.let { linkSess ->
        JulesLinkToFeatureDialog(
            session = linkSess,
            features = features.filter { it.sourceName == linkSess.sourceName },
            onDismiss = { showLinkDialog = null },
            onLink = { featureId ->
                onLinkToFeature(linkSess, featureId)
                showLinkDialog = null
            },
            onCreateAndLink = { title ->
                onCreateFeatureAndLink(linkSess, title)
                showLinkDialog = null
            },
        )
    }
}
