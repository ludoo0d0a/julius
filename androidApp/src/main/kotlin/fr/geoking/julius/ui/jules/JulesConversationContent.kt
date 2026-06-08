package fr.geoking.julius.ui.jules

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import fr.geoking.julius.api.github.GitHubBranchRef
import fr.geoking.julius.api.jules.JulesChatItem
import fr.geoking.julius.persistence.JulesSessionEntity
import fr.geoking.julius.repository.JulesRepository
import fr.geoking.julius.shared.voice.VoiceManager
import fr.geoking.julius.ui.ColorHelper
import fr.geoking.julius.ui.components.DebugBar
import fr.geoking.julius.ui.components.JulesMessageContent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun JulesConversationContent(
    currentSession: JulesSessionEntity,
    chatItems: List<JulesChatItem>,
    listState: LazyListState,
    inputText: String,
    onInputChange: (String) -> Unit,
    voiceManager: VoiceManager,
    onSend: () -> Unit,
    loading: Boolean,
    onMergePr: (String?) -> Unit,
    onSolveConflicts: (String?) -> Unit,
    onAutoSolveConflicts: (String?) -> Unit,
    onCreatePr: (GitHubBranchRef) -> Unit,
    julesRepository: JulesRepository,
    githubToken: String,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    activitiesJson: String = "",
) {
    Column(modifier = Modifier.fillMaxSize()) {
        val progressStep = remember(currentSession, chatItems.size) {
            calculateJulesProgressStep(currentSession, chatItems)
        }
        JulesMiniProgressBar(currentStep = progressStep)

        JulesPullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier.weight(1f),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(chatItems, key = { it.id }) { item ->
                    val isUser = item is JulesChatItem.UserMessage
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
                    ) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (isUser) ColorHelper.JulesCardUser else ColorHelper.JulesCardAgent,
                            ),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            JulesMessageContent(
                                item = item,
                                baseFontSize = 14,
                                onSpeak = {
                                    voiceManager.speak(
                                        if (item is JulesChatItem.UserMessage) item.text
                                        else (item as JulesChatItem.AgentMessage).text,
                                    )
                                },
                                onMergePr = { onMergePr(it) },
                                onSolveConflicts = onSolveConflicts,
                                onAutoSolveConflicts = onAutoSolveConflicts,
                                onCreatePr = onCreatePr,
                                prDetails = currentSession,
                                julesRepository = julesRepository,
                                githubToken = githubToken,
                            )
                        }
                    }
                }

                if (currentSession.prState == "open") {
                    item {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            if (currentSession.prMergeable == true) {
                                FilledTonalButton(onClick = { onMergePr(null) }) {
                                    Text("Merge Pull Request", color = Color.Green)
                                }
                            } else if (currentSession.prMergeable == false) {
                                Text("This PR has merge conflicts.", color = Color.Red)
                                Row {
                                    FilledTonalButton(onClick = { onAutoSolveConflicts(null) }) {
                                        Text("Auto solve")
                                    }
                                    Spacer(modifier = Modifier.size(8.dp))
                                    FilledTonalButton(onClick = { onSolveConflicts(null) }) {
                                        Text("Solve manually")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = inputText,
                onValueChange = onInputChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Message Jules…") },
            )
            IconButton(onClick = onSend, enabled = inputText.isNotBlank() && !loading) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = ColorHelper.JulesAccent)
            }
        }

        if (activitiesJson.isNotBlank()) {
            DebugBar(jsonString = activitiesJson)
        }
    }
}
