package fr.geoking.julius.designassistant.v1

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.geoking.julius.designassistant.DesignAssistantColors
import fr.geoking.julius.designassistant.DesignAssistantSampleData
import fr.geoking.julius.designassistant.DesignChatMessage
import fr.geoking.julius.designassistant.DesignFeature
import fr.geoking.julius.designassistant.DesignProject
import fr.geoking.julius.designassistant.ChatMessageKind
import fr.geoking.julius.designassistant.FeatureStatus
import fr.geoking.julius.designassistant.components.CollapsibleCodeBlock
import fr.geoking.julius.designassistant.components.DesignAssistantBottomNav
import fr.geoking.julius.designassistant.components.DesignAssistantNavyHeader
import fr.geoking.julius.designassistant.components.DesignAssistantTheme
import fr.geoking.julius.designassistant.components.DesignBreadcrumb
import fr.geoking.julius.designassistant.components.DesignBottomTab
import fr.geoking.julius.designassistant.components.DesignChatInputBar
import fr.geoking.julius.designassistant.components.JulesAvatar
import fr.geoking.julius.designassistant.components.StatusDot
import fr.geoking.julius.designassistant.components.WhiteContentSheet

/** V1 — UI actuelle reproduite depuis le mockup (Features + Chat Jules). */
@Composable
fun DesignAssistantV1Host(
    onBack: () -> Unit,
    project: DesignProject = DesignAssistantSampleData.aeroFlow,
) {
    var bottomTab by remember { mutableStateOf(DesignBottomTab.FEATURES) }
    var selectedFeature by remember { mutableStateOf<DesignFeature?>(project.features.find { it.id == "catalog" }) }

    Column(Modifier.fillMaxSize().background(DesignAssistantColors.Navy)) {
        when (bottomTab) {
            DesignBottomTab.CHAT -> {
                if (selectedFeature != null) {
                    V1ChatScreen(
                        projectName = project.name,
                        feature = selectedFeature!!,
                        messages = DesignAssistantSampleData.catalogChatMessages,
                        onBack = { bottomTab = DesignBottomTab.FEATURES },
                    )
                } else {
                    V1FeaturesScreen(
                        project = project,
                        selectedFeatureId = null,
                        onSelectFeature = { f ->
                            selectedFeature = f
                            bottomTab = DesignBottomTab.CHAT
                        },
                        onBack = onBack,
                    )
                }
            }
            else -> {
                V1FeaturesScreen(
                    project = project,
                    selectedFeatureId = selectedFeature?.id,
                    onSelectFeature = { f ->
                        selectedFeature = f
                        bottomTab = DesignBottomTab.CHAT
                    },
                    onBack = onBack,
                )
            }
        }
        DesignAssistantBottomNav(selected = bottomTab, onSelect = { bottomTab = it })
    }
}

@Composable
fun V1FeaturesScreen(
    project: DesignProject,
    selectedFeatureId: String?,
    onSelectFeature: (DesignFeature) -> Unit,
    onBack: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        DesignAssistantNavyHeader(
            onBack = onBack,
            title = project.name,
            subtitle = "Projet actif",
            trailing = {
                IconButton(onClick = {}) {
                    Icon(Icons.Default.Settings, contentDescription = "Réglages", tint = Color.White)
                }
            },
            content = {
                Row(
                    Modifier.padding(start = 16.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Flight, contentDescription = null, tint = Color.White, modifier = Modifier.padding(end = 8.dp))
                    Text(project.description, color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
                }
                DesignBreadcrumb(listOf("Projets", project.name))
                Spacer(Modifier.height(8.dp))
            },
        )
        WhiteContentSheet(Modifier.weight(1f)) {
            Text(
                "Features",
                modifier = Modifier.padding(20.dp),
                color = DesignAssistantColors.Navy,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
            )
            LazyColumn(Modifier.padding(horizontal = 16.dp)) {
                items(project.features) { feature ->
                    val selected = feature.id == selectedFeatureId
                    FeatureCardV1(
                        feature = feature,
                        selected = selected,
                        onClick = { onSelectFeature(feature) },
                    )
                    Spacer(Modifier.height(10.dp))
                }
            }
        }
    }
}

@Composable
private fun FeatureCardV1(
    feature: DesignFeature,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val bg = if (selected) DesignAssistantColors.Navy else DesignAssistantColors.SurfaceCard
    val textColor = if (selected) Color.White else DesignAssistantColors.Navy
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = bg),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatusDot(feature.status)
            Spacer(Modifier.padding(horizontal = 8.dp))
            Column(Modifier.weight(1f)) {
                Text(feature.name, color = textColor, fontWeight = FontWeight.SemiBold)
                Text(feature.status.labelFr, color = textColor.copy(alpha = 0.7f), fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun V1ChatScreen(
    projectName: String,
    feature: DesignFeature,
    messages: List<DesignChatMessage>,
    onBack: () -> Unit,
) {
    Column(Modifier.fillMaxSize().background(Color.White)) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour", tint = DesignAssistantColors.Navy)
            }
            JulesAvatar()
            Spacer(Modifier.padding(horizontal = 8.dp))
            Column {
                Text("Jules | AI Assistant", fontWeight = FontWeight.Bold, color = DesignAssistantColors.Navy)
                Text("Jules Design Assistant · $projectName › ${feature.name}", fontSize = 11.sp, color = DesignAssistantColors.TextSecondary)
            }
        }
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(messages) { msg ->
                V1ChatBubble(msg)
            }
        }
        DesignChatInputBar(placeholder = "Demander à Jules à propos de ${feature.name}…")
    }
}

@Composable
private fun V1ChatBubble(message: DesignChatMessage) {
    when (message.kind) {
        ChatMessageKind.USER -> {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Surface(color = DesignAssistantColors.UserBubble, shape = RoundedCornerShape(16.dp)) {
                    Text(message.text, Modifier.padding(12.dp), color = DesignAssistantColors.Navy)
                }
            }
        }
        ChatMessageKind.CODE -> {
            var expanded by remember { mutableStateOf(true) }
            Row {
                JulesAvatar()
                Spacer(Modifier.padding(4.dp))
                CollapsibleCodeBlock(message.codeSnippet.orEmpty(), expanded) { expanded = !expanded }
            }
        }
        ChatMessageKind.PR_ACTION, ChatMessageKind.AGENT -> {
            Row(Modifier.fillMaxWidth()) {
                JulesAvatar()
                Spacer(Modifier.padding(4.dp))
                Column(Modifier.weight(1f)) {
                    Surface(shape = RoundedCornerShape(12.dp), color = DesignAssistantColors.Surface) {
                        Text(message.text, Modifier.padding(12.dp), color = DesignAssistantColors.Navy)
                    }
                    if (message.prNumber != null) {
                        Spacer(Modifier.height(8.dp))
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = DesignAssistantColors.Navy,
                            shape = RoundedCornerShape(8.dp),
                        ) {
                            Text(
                                "Voir PR #${message.prNumber} (${message.prTitle}) sur GitHub",
                                Modifier.padding(12.dp),
                                color = Color.White,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                            PrActionChip("Voir branche")
                            PrActionChip("Pull code")
                        }
                    }
                }
            }
        }
        ChatMessageKind.CI -> {
            Text(
                message.text,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                color = DesignAssistantColors.CiSuccess,
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun PrActionChip(label: String) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = DesignAssistantColors.UserBubble,
    ) {
        Text(label, Modifier.padding(horizontal = 12.dp, vertical = 8.dp), color = DesignAssistantColors.Navy, fontSize = 12.sp)
    }
}

@Preview(showBackground = true, heightDp = 800, name = "V1 Features")
@Composable
private fun V1FeaturesPreview() {
    DesignAssistantTheme {
        V1FeaturesScreen(
            project = DesignAssistantSampleData.aeroFlow,
            selectedFeatureId = "catalog",
            onSelectFeature = {},
            onBack = {},
        )
    }
}

@Preview(showBackground = true, heightDp = 800, name = "V1 Chat")
@Composable
private fun V1ChatPreview() {
    DesignAssistantTheme {
        V1ChatScreen(
            projectName = "AeroFlow Website",
            feature = DesignAssistantSampleData.aeroFlow.features[1],
            messages = DesignAssistantSampleData.catalogChatMessages,
            onBack = {},
        )
    }
}
