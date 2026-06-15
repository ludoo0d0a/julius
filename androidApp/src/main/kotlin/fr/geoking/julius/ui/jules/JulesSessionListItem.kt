package fr.geoking.julius.ui.jules

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.geoking.julius.api.github.GitHubClient
import fr.geoking.julius.persistence.JulesSessionEntity
import fr.geoking.julius.ui.ColorHelper

@Composable
internal fun JulesSessionListItem(
    session: JulesSessionEntity,
    archivingSessionIds: List<String>,
    onOpenSession: (JulesSessionEntity) -> Unit,
    onGetPrDetails: (JulesSessionEntity, (GitHubClient.GitHubPullRequestDetail?) -> Unit) -> Unit,
    onArchive: (JulesSessionEntity) -> Unit,
    onLinkToFeature: () -> Unit,
    onShowPrDetails: (GitHubClient.GitHubPullRequestDetail) -> Unit,
) {
    ListItem(
        headlineContent = {
            Text(session.title.ifBlank { session.prompt }, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val backend = if (session.id.startsWith("sesn_")) "CLAUDE_CODE" else "JULES"
                    Text("$backend · ${session.sourceName}", fontSize = 11.sp, color = Color.White.copy(alpha = 0.5f), modifier = Modifier.padding(bottom = 2.dp))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    JulesSessionStatusBadge(session)
                    androidx.compose.foundation.layout.Spacer(modifier = Modifier.size(8.dp))
                    Text(session.sessionState ?: "In progress", fontSize = 12.sp)
                }
            }
        },
        trailingContent = {
            if (archivingSessionIds.contains(session.id)) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = ColorHelper.JulesAccent,
                    strokeWidth = 2.dp,
                )
            } else {
                Row {
                    IconButton(onClick = onLinkToFeature) {
                        Icon(
                            Icons.Default.Link,
                            contentDescription = "Link to feature",
                            tint = if (session.featureId != null) ColorHelper.JulesAccent else Color.White.copy(alpha = 0.5f),
                        )
                    }
                    if (session.prUrl != null) {
                        IconButton(onClick = { onGetPrDetails(session) { it?.let(onShowPrDetails) } }) {
                            Icon(Icons.Default.Description, contentDescription = "Details")
                        }
                    }
                    IconButton(onClick = { onArchive(session) }) {
                        Icon(Icons.Default.Archive, contentDescription = "Archive")
                    }
                }
            }
        },
        modifier = Modifier.clickable { onOpenSession(session) },
        colors = ListItemDefaults.colors(
            containerColor = Color.Transparent,
            headlineColor = Color.White,
            supportingColor = Color.White.copy(alpha = 0.6f),
        ),
    )
    HorizontalDivider(color = Color.White.copy(alpha = 0.05f))
}
