package fr.geoking.julius.ui.jules

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.geoking.julius.api.github.GitHubClient
import fr.geoking.julius.api.jules.JulesClient
import fr.geoking.julius.persistence.JulesSessionEntity
import fr.geoking.julius.repository.JulesRepository
import fr.geoking.julius.ui.ColorHelper
import kotlinx.coroutines.launch

@Composable
internal fun JulesActivitiesSheet(activities: List<JulesClient.JulesActivity>) {
    LazyColumn(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        item {
            Text(
                "Activities",
                color = Color.White,
                fontSize = 20.sp,
                modifier = Modifier.padding(bottom = 16.dp),
                fontWeight = FontWeight.Bold,
            )
        }
        items(activities.sortedByDescending { it.createTime }) { activity ->
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Row {
                    Text(
                        text = activity.originator,
                        color = if (activity.originator == "user") ColorHelper.JulesAccent else Color.Green,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = activity.createTime.take(16),
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 10.sp,
                    )
                }
                Text(
                    text = activity.description ?: "Activity",
                    color = Color.White,
                    fontSize = 14.sp,
                )
                activity.artifacts?.forEach { artifact ->
                    artifact.bashOutput?.let { bash ->
                        Text(
                            "> ${bash.command}",
                            color = Color.Green,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(top = 8.dp), color = Color.White.copy(alpha = 0.1f))
            }
        }
    }
}

@Composable
internal fun JulesConflictResolutionSheet(
    session: JulesSessionEntity,
    files: List<String>,
    githubToken: String,
    julesRepository: JulesRepository,
    onDismiss: () -> Unit,
) {
    var selectedFile by remember { mutableStateOf<String?>(null) }
    var fileContent by remember { mutableStateOf("") }
    var fileSha by remember { mutableStateOf("") }
    var conflicts by remember { mutableStateOf<List<JulesRepository.Conflict>>(emptyList()) }
    var currentConflictIndex by remember { mutableStateOf(0) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        Text(
            "Resolve Conflicts",
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
        )

        error?.let {
            JulesErrorCard(title = "Error", message = it, onDismiss = { error = null })
        }

        if (loading) {
            Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = ColorHelper.JulesAccent)
            }
        } else if (selectedFile == null) {
            LazyColumn {
                items(files) { file ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                scope.launch {
                                    loading = true
                                    val res = julesRepository.getFileContent(githubToken, session.prUrl!!, file)
                                    if (res.isSuccess) {
                                        val (content, sha) = res.getOrThrow()
                                        selectedFile = file
                                        fileContent = content
                                        fileSha = sha
                                        conflicts = julesRepository.parseConflicts(content)
                                        currentConflictIndex = 0
                                    } else {
                                        error = "Failed to load file"
                                    }
                                    loading = false
                                }
                            }
                            .padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = ColorHelper.JulesHeaderBg),
                    ) {
                        Text(file, color = Color.White, modifier = Modifier.padding(16.dp))
                    }
                }
            }
        } else {
            val file = selectedFile ?: ""
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { selectedFile = null }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Text(file, color = ColorHelper.JulesAccent, fontWeight = FontWeight.Bold)
            }

            if (conflicts.isNotEmpty()) {
                val conflict = conflicts.getOrNull(currentConflictIndex)
                if (conflict != null) {
                    Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                        JulesConflictBlock(
                            title = "MINE",
                            content = conflict.mine,
                            color = ColorHelper.JulesAccent,
                            onSelect = {
                                fileContent = fileContent.replace(conflict.fullMatch, conflict.mine + "\n")
                                conflicts = julesRepository.parseConflicts(fileContent)
                            },
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        JulesConflictBlock(
                            title = "INCOMING",
                            content = conflict.incoming,
                            color = Color.Green,
                            onSelect = {
                                fileContent = fileContent.replace(conflict.fullMatch, conflict.incoming + "\n")
                                conflicts = julesRepository.parseConflicts(fileContent)
                            },
                        )
                    }
                }
            } else {
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text("Resolved!", color = Color.Green)
                }
                val fileToSave = selectedFile
                val prUrl = session.prUrl
                if (fileToSave != null && prUrl != null) {
                    FilledTonalButton(
                        onClick = {
                            scope.launch {
                                loading = true
                                val res = julesRepository.saveResolvedFile(githubToken, prUrl, fileToSave, fileContent, fileSha)
                                if (res.isSuccess) {
                                    selectedFile = null
                                } else {
                                    error = "Failed to save"
                                }
                                loading = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Save Resolved File", color = Color.Green)
                    }
                }
            }
        }
    }
}

@Composable
private fun JulesConflictBlock(title: String, content: String, color: Color, onSelect: () -> Unit) {
    Column {
        Text(title, color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onSelect)
                .padding(vertical = 4.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.3f)),
        ) {
            Text(
                content,
                color = Color.White,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(8.dp),
            )
        }
    }
}

@Composable
internal fun JulesPrDetailsDialog(pr: GitHubClient.GitHubPullRequestDetail, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ColorHelper.JulesListBg,
        title = { Text(pr.title, color = Color.White) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(pr.body ?: "No description.", color = Color.White.copy(alpha = 0.8f))
            }
        },
        confirmButton = {
            OutlinedButton(onClick = onDismiss) { Text("Close") }
        },
    )
}
