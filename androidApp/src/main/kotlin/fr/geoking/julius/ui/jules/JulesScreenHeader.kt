package fr.geoking.julius.ui.jules

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Rule
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.geoking.julius.persistence.JulesSessionEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun JulesScreenHeader(
    title: String,
    subtitle: String? = null,
    currentSession: JulesSessionEntity?,
    onBack: () -> Unit,
    onTogglePause: (JulesSessionEntity) -> Unit,
    onOpenInWeb: (String) -> Unit,
    onShowActivities: (JulesSessionEntity) -> Unit,
    onArchive: (JulesSessionEntity) -> Unit = {},
    onDelete: (JulesSessionEntity) -> Unit = {},
    onMerge: (JulesSessionEntity) -> Unit = {},
    onFixConflicts: (JulesSessionEntity) -> Unit = {},
    onRetry: (JulesSessionEntity) -> Unit = {},
) {
    var showMenu by remember { mutableStateOf(false) }

    TopAppBar(
        title = {
            Column {
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 18.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                currentSession?.let { session ->
                    val status = julesSessionStatus(session, JulesStatusStyle.Header)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(status.color, RoundedCornerShape(3.dp)),
                        )
                        Spacer(modifier = Modifier.size(6.dp))
                        Text(
                            text = "${status.text}${status.prIdSuffix}${status.mergeabilitySuffix}",
                            color = status.color,
                            fontSize = 12.sp,
                        )
                    }
                } ?: subtitle?.let { sub ->
                    Text(
                        text = sub,
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
        },
        actions = {
            currentSession?.let { session ->
                if (!session.isFinished) {
                    IconButton(onClick = { onTogglePause(session) }) {
                        Icon(
                            if (session.sessionState == "PAUSED") Icons.Default.PlayArrow else Icons.Default.Pause,
                            contentDescription = if (session.sessionState == "PAUSED") "Resume" else "Pause",
                            tint = Color.White,
                        )
                    }
                }

                // Retry
                IconButton(onClick = { onRetry(session) }) {
                    Icon(Icons.Default.Refresh, contentDescription = "Retry", tint = Color.White)
                }

                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More", tint = Color.White)
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        // Fix Conflicts
                        if (session.prUrl != null && session.prState == "open" && session.prMergeable == false) {
                            DropdownMenuItem(
                                text = { Text("Fix Conflicts") },
                                onClick = { onFixConflicts(session); showMenu = false },
                                leadingIcon = { Icon(Icons.AutoMirrored.Filled.Rule, contentDescription = null) }
                            )
                        }

                        // Merge
                        if (session.prUrl != null && session.prState == "open" && session.prMergeable == true) {
                            DropdownMenuItem(
                                text = { Text("Merge PR") },
                                onClick = { onMerge(session); showMenu = false },
                                leadingIcon = { Icon(Icons.Default.Merge, contentDescription = null) }
                            )
                        }

                        // Archive
                        DropdownMenuItem(
                            text = { Text("Archive") },
                            onClick = { onArchive(session); showMenu = false },
                            leadingIcon = { Icon(Icons.Default.Archive, contentDescription = null) }
                        )

                        // Delete
                        DropdownMenuItem(
                            text = { Text("Delete", color = Color.Red) },
                            onClick = { onDelete(session); showMenu = false },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = Color.Red) }
                        )

                        session.url?.takeIf { it.isNotBlank() }?.let { url ->
                            DropdownMenuItem(
                                text = { Text("Open in web") },
                                onClick = { onOpenInWeb(url); showMenu = false },
                                leadingIcon = { Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null) }
                            )
                        }

                        DropdownMenuItem(
                            text = { Text("Activities") },
                            onClick = { onShowActivities(session); showMenu = false },
                            leadingIcon = { Icon(Icons.Default.History, contentDescription = null) }
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
