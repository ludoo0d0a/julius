package fr.geoking.julius.designassistant.v3

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.drawscope.Stroke
import fr.geoking.julius.designassistant.ChatMessageKind
import fr.geoking.julius.designassistant.DesignAssistantJarvisColors
import fr.geoking.julius.designassistant.DesignChatMessage
import fr.geoking.julius.designassistant.DesignFeature
import fr.geoking.julius.designassistant.DesignProject
import fr.geoking.julius.designassistant.FeatureStatus
import fr.geoking.julius.designassistant.components.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.tooling.preview.Preview
import fr.geoking.julius.designassistant.DesignAssistantSampleData
import fr.geoking.julius.ui.components.RenderMessageBlock
import fr.geoking.julius.ui.components.parseJulesMessage

@Composable
fun V3ProjectsScreen(
    projects: List<DesignProject>,
    loading: Boolean,
    error: String?,
    onProjectClick: (DesignProject) -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DesignAssistantJarvisColors.Background)
    ) {
        JarvisHUDHeader(
            title = "System Core",
            subtitle = "Active Projects: ${projects.size}",
            onBack = onBack,
            trailing = {
                Row {
                    IconButton(onClick = {}) {
                        Icon(Icons.Default.Add, contentDescription = "Add", tint = DesignAssistantJarvisColors.CyanNeon)
                    }
                    IconButton(onClick = {}) {
                        Icon(Icons.Default.Settings, contentDescription = "Config", tint = DesignAssistantJarvisColors.CyanNeon)
                    }
                }
            }
        )

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(projects) { project ->
                ProjectCardV3(project, onClick = { onProjectClick(project) })
            }
        }
    }
}

@Composable
fun V3SourceScreen(
    project: DesignProject,
    feature: DesignFeature,
    onBack: () -> Unit,
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DesignAssistantJarvisColors.Background)
    ) {
        JarvisHUDHeader(
            title = "Source Control",
            subtitle = "${project.name} / ${feature.name}",
            onBack = onBack
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(16.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Branch Info
            JarvisCard {
                Text("TARGET_BRANCH", color = DesignAssistantJarvisColors.CyanNeon, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Text(feature.branch ?: project.mainBranch, color = DesignAssistantJarvisColors.TextPrimary, fontSize = 16.sp, fontFamily = FontFamily.Monospace)
                Spacer(Modifier.height(8.dp))
                JarvisButton(text = "SWITCH BRANCH", onClick = {}, color = DesignAssistantJarvisColors.OrangeNeon)
            }

            // PR Status
            JarvisCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("PULL_REQUEST", color = DesignAssistantJarvisColors.CyanNeon, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    if (feature.prNumber != null) {
                        Text("OPEN", color = DesignAssistantJarvisColors.StatusSuccess, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.height(4.dp))
                if (feature.prNumber != null) {
                    Text("#${feature.prNumber} - ${feature.prTitle}", color = DesignAssistantJarvisColors.TextPrimary, fontSize = 14.sp)
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        JarvisButton(text = "MERGE PR", onClick = {}, modifier = Modifier.weight(1f))
                        JarvisButton(text = "VIEW GITHUB", onClick = {}, modifier = Modifier.weight(1f), color = DesignAssistantJarvisColors.TextSecondary)
                    }
                } else {
                    Text("NO ACTIVE PULL REQUEST", color = DesignAssistantJarvisColors.TextSecondary, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                    Spacer(Modifier.height(12.dp))
                    JarvisButton(text = "CREATE PR", onClick = {})
                }
            }

            // File Stream (Mockup)
            JarvisCard {
                Text("FILE_STREAM_CHANGES", color = DesignAssistantJarvisColors.CyanNeon, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                listOf(
                    "src/core/engine.kt" to "MODIFIED",
                    "src/ui/hud_view.kt" to "ADDED",
                    "assets/config.json" to "MODIFIED"
                ).forEach { (file, status) ->
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Text(file, color = DesignAssistantJarvisColors.TextSecondary, fontSize = 11.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
                        Text(status, color = if (status == "ADDED") DesignAssistantJarvisColors.StatusSuccess else DesignAssistantJarvisColors.OrangeNeon, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun V3ConversationScreen(
    projectName: String,
    feature: DesignFeature,
    messages: List<DesignChatMessage>,
    onBack: () -> Unit,
) {
    var draft by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DesignAssistantJarvisColors.Background)
    ) {
        JarvisHUDHeader(
            title = feature.name,
            subtitle = "Engine Activity: Connected",
            onBack = onBack
        )

        // Engine Workflow Visualization
        EngineWorkflowView(modifier = Modifier.padding(16.dp))

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(messages) { msg ->
                JarvisChatBubble(msg)
            }
        }

        JarvisChatInput(
            value = draft,
            onValueChange = { draft = it },
            onSend = { draft = "" },
            placeholder = "INPUT OVERRIDE..."
        )
    }
}

@Composable
fun EngineWorkflowView(modifier: Modifier = Modifier) {
    JarvisCard(modifier = modifier) {
        Text(
            text = "ENGINE WORKFLOW CONTROL",
            color = DesignAssistantJarvisColors.CyanNeon,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            WorkflowNode("ANALYZER", true)
            WorkflowArrow()
            WorkflowNode("ARCHITECT", true)
            WorkflowArrow()
            WorkflowNode("CODER", false)
            WorkflowArrow()
            WorkflowNode("REVIEWER", false)
        }
    }
}

@Composable
private fun WorkflowNode(name: String, isActive: Boolean) {
    val color = if (isActive) DesignAssistantJarvisColors.CyanNeon else DesignAssistantJarvisColors.TextSecondary
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .border(1.dp, color, CircleShape)
                .background(color.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            if (isActive) {
                Canvas(modifier = Modifier.size(12.dp)) {
                    drawCircle(color = color, radius = size.minDimension / 2)
                }
            }
        }
        Text(
            text = name,
            color = color,
            fontSize = 8.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
private fun WorkflowArrow() {
    Text(
        text = "→",
        color = DesignAssistantJarvisColors.CyanNeon.copy(alpha = 0.5f),
        fontSize = 14.sp
    )
}

@Composable
private fun JarvisChatBubble(message: DesignChatMessage) {
    val isUser = message.kind == ChatMessageKind.USER

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        Row(verticalAlignment = Alignment.Top) {
            if (!isUser) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .border(1.dp, DesignAssistantJarvisColors.CyanNeon, CircleShape)
                        .background(DesignAssistantJarvisColors.CyanNeon.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("J", color = DesignAssistantJarvisColors.CyanNeon, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(8.dp))
            }

            val bubbleColor = if (isUser) DesignAssistantJarvisColors.OrangeNeon else DesignAssistantJarvisColors.CyanNeon

            Column(
                modifier = Modifier
                    .widthIn(max = 280.dp)
                    .border(1.dp, bubbleColor.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                    .background(bubbleColor.copy(alpha = 0.05f))
                    .padding(12.dp)
            ) {
                if (message.kind == ChatMessageKind.CODE) {
                    Text(
                        text = message.codeSnippet ?: "",
                        color = Color(0xFF80CBC4),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp
                    )
                } else if (message.kind == ChatMessageKind.CI) {
                    Text(
                        text = message.text,
                        color = DesignAssistantJarvisColors.StatusSuccess,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                } else {
                    val blocks = remember(message.text) { parseJulesMessage(message.text) }
                    blocks.forEach { block ->
                        RenderMessageBlock(
                            block = block,
                            baseFontSize = 13,
                            textColor = if (isUser) DesignAssistantJarvisColors.OrangeNeon else DesignAssistantJarvisColors.TextPrimary
                        )
                    }
                }
            }
        }

        Text(
            text = if (isUser) "USER_DATA_LINK" else "JULES_AI_STREAM",
            color = (if (isUser) DesignAssistantJarvisColors.OrangeNeon else DesignAssistantJarvisColors.CyanNeon).copy(alpha = 0.4f),
            fontSize = 8.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(top = 4.dp, start = if (isUser) 0.dp else 32.dp)
        )
    }
}

@Composable
fun V3FeaturesScreen(
    project: DesignProject,
    onFeatureClick: (DesignFeature) -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DesignAssistantJarvisColors.Background)
    ) {
        JarvisHUDHeader(
            title = project.name,
            subtitle = "Mission Objectives",
            onBack = onBack
        )

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(project.features) { feature ->
                FeatureCardV3(feature = feature, onClick = { onFeatureClick(feature) })
            }
        }
    }
}

@Composable
private fun FeatureCardV3(
    feature: DesignFeature,
    onClick: () -> Unit,
) {
    JarvisCard(onClick = onClick) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = feature.name.uppercase(),
                    color = DesignAssistantJarvisColors.CyanNeon,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    letterSpacing = 1.sp
                )
                Spacer(Modifier.height(4.dp))
                JarvisStatusIndicator(feature.status)
            }

            if (feature.branch != null) {
                Text(
                    text = feature.branch,
                    color = DesignAssistantJarvisColors.OrangeNeon.copy(alpha = 0.7f),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(end = 8.dp)
                )
            }

            Text(
                text = ">",
                color = DesignAssistantJarvisColors.CyanNeon,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF00050A)
@Composable
fun V3ProjectsScreenPreview() {
    JarvisTheme {
        V3ProjectsScreen(
            projects = DesignAssistantSampleData.projects,
            loading = false,
            error = null,
            onProjectClick = {},
            onBack = {}
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF00050A)
@Composable
fun V3FeaturesScreenPreview() {
    JarvisTheme {
        V3FeaturesScreen(
            project = DesignAssistantSampleData.eCommerce,
            onFeatureClick = {},
            onBack = {}
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF00050A)
@Composable
fun V3ConversationScreenPreview() {
    JarvisTheme {
        V3ConversationScreen(
            projectName = "E-Commerce App",
            feature = DesignAssistantSampleData.eCommerce.features[0],
            messages = DesignAssistantSampleData.oauthChatMessages,
            onBack = {}
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF00050A)
@Composable
fun V3SourceScreenPreview() {
    JarvisTheme {
        V3SourceScreen(
            project = DesignAssistantSampleData.eCommerce,
            feature = DesignAssistantSampleData.eCommerce.features[0],
            onBack = {}
        )
    }
}

@Composable
private fun ProjectCardV3(project: DesignProject, onClick: () -> Unit) {
    JarvisCard(onClick = onClick) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = project.emoji,
                fontSize = 32.sp,
                modifier = Modifier.padding(end = 16.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = project.name.uppercase(),
                    color = DesignAssistantJarvisColors.CyanNeon,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "${project.activeFeaturesCount} MODULES DETECTED",
                    color = DesignAssistantJarvisColors.TextSecondary,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
            Text(
                text = ">>",
                color = DesignAssistantJarvisColors.CyanNeon.copy(alpha = 0.5f),
                fontWeight = FontWeight.Black
            )
        }

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "BRANCH: ${project.mainBranch}",
                color = DesignAssistantJarvisColors.OrangeNeon.copy(alpha = 0.8f),
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = "UPTIME: ${project.lastModifiedLabel}",
                color = DesignAssistantJarvisColors.TextSecondary,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}
