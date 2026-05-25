package fr.geoking.julius.designassistant.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.geoking.julius.designassistant.DesignAssistantColors
import fr.geoking.julius.designassistant.FeatureStatus

@Composable
fun DesignAssistantNavyHeader(
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    title: String,
    subtitle: String? = null,
    trailing: @Composable (() -> Unit)? = null,
    content: @Composable (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(DesignAssistantColors.Navy)
            .padding(horizontal = 4.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour", tint = Color.White)
                }
            } else {
                Spacer(Modifier.width(48.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                subtitle?.let {
                    Text(it, color = Color.White.copy(alpha = 0.75f), fontSize = 12.sp)
                }
            }
            trailing?.invoke()
        }
        content?.invoke()
    }
}

@Composable
fun DesignBreadcrumb(path: List<String>, onSegmentClick: ((Int) -> Unit)? = null) {
    val scroll = rememberScrollState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scroll)
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        path.forEachIndexed { index, segment ->
            if (index > 0) {
                Text(" › ", color = DesignAssistantColors.TextSecondary, fontSize = 12.sp)
            }
            val clickable = onSegmentClick != null && index < path.lastIndex
            Text(
                text = segment,
                color = if (index == path.lastIndex) DesignAssistantColors.Navy else DesignAssistantColors.TextSecondary,
                fontSize = 12.sp,
                fontWeight = if (index == path.lastIndex) FontWeight.SemiBold else FontWeight.Normal,
                modifier = if (clickable) {
                    Modifier.clickable { onSegmentClick?.invoke(index) }
                } else {
                    Modifier
                },
            )
        }
    }
}

@Composable
fun StatusDot(status: FeatureStatus, modifier: Modifier = Modifier) {
    val color = when (status) {
        FeatureStatus.DONE, FeatureStatus.READY -> DesignAssistantColors.StatusDone
        FeatureStatus.IN_PROGRESS -> DesignAssistantColors.StatusInProgress
        FeatureStatus.TODO, FeatureStatus.IDEA -> DesignAssistantColors.StatusTodo
    }
    Box(
        modifier = modifier
            .size(10.dp)
            .clip(CircleShape)
            .background(color),
    )
}

@Composable
fun DesignAssistantBottomNav(
    selected: DesignBottomTab,
    onSelect: (DesignBottomTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Color.White,
        shadowElevation = 8.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            DesignBottomTab.entries.forEach { tab ->
                val selectedTab = tab == selected
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onSelect(tab) }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Icon(
                        tab.icon,
                        contentDescription = tab.labelFr,
                        tint = if (selectedTab) DesignAssistantColors.Navy else DesignAssistantColors.TextSecondary,
                        modifier = Modifier.size(22.dp),
                    )
                    Text(
                        tab.labelFr,
                        fontSize = 10.sp,
                        color = if (selectedTab) DesignAssistantColors.Navy else DesignAssistantColors.TextSecondary,
                    )
                }
            }
        }
    }
}

enum class DesignBottomTab(val labelFr: String, val icon: ImageVector) {
    PROJECTS("Projets", Icons.Default.Folder),
    FEATURES("Features", Icons.Default.RocketLaunch),
    CHAT("Chat", Icons.Default.Chat),
    BRANCHES("Branches", Icons.Default.AccountTree),
    SETTINGS("Réglages", Icons.Default.Settings),
}

@Composable
fun WhiteContentSheet(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = DesignAssistantColors.Surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    ) {
        content()
    }
}

@Composable
fun TechnicalStatusBanner(
    branch: String?,
    prNumber: Int?,
    prTitle: String?,
    prStateEmoji: String = "🟢",
    deployStatusLine: String? = null,
    deployLoading: Boolean = false,
    onCopyBranch: () -> Unit = {},
    onOpenPr: () -> Unit = {},
    onDeployClick: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(DesignAssistantColors.Surface)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        if (!branch.isNullOrBlank()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(prStateEmoji, fontSize = 14.sp)
                Spacer(Modifier.width(6.dp))
                Text("Branch: ", color = DesignAssistantColors.TextSecondary, fontSize = 13.sp)
                Text(branch, color = DesignAssistantColors.Navy, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                IconButton(onClick = onCopyBranch, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Copier", tint = DesignAssistantColors.Accent)
                }
            }
        }
        if (prNumber != null) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onOpenPr)
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("🚀", fontSize = 14.sp)
                Spacer(Modifier.width(6.dp))
                Text(
                    "Ouvrir PR #$prNumber${prTitle?.let { " ($it)" } ?: ""}",
                    color = DesignAssistantColors.Accent,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp,
                )
            }
        }
        when {
            deployLoading -> {
                Text(
                    "Deploy: chargement…",
                    color = DesignAssistantColors.TextSecondary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            !deployStatusLine.isNullOrBlank() -> {
                val deployModifier = if (onDeployClick != null) {
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onDeployClick)
                        .padding(top = 4.dp)
                } else {
                    Modifier.padding(top = 4.dp)
                }
                Row(modifier = deployModifier, verticalAlignment = Alignment.CenterVertically) {
                    Text("⚙️", fontSize = 14.sp)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        deployStatusLine,
                        color = DesignAssistantColors.Accent,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}

@Composable
fun PromptShortcutsRow(
    shortcuts: List<String>,
    onShortcutClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scroll = rememberScrollState()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(scroll)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        shortcuts.forEach { label ->
            FilterChip(
                selected = false,
                onClick = { onShortcutClick(label) },
                label = { Text(label, fontSize = 12.sp) },
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = DesignAssistantColors.UserBubble,
                    labelColor = DesignAssistantColors.Navy,
                ),
            )
        }
    }
}

@Composable
fun DesignChatInputBar(
    placeholder: String,
    onSend: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    DesignChatInputBar(
        value = "",
        onValueChange = {},
        placeholder = placeholder,
        onSend = onSend,
        enabled = false,
        modifier = modifier,
    )
}

@Composable
fun DesignChatInputBar(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    onSend: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = {}, enabled = false) {
            Icon(Icons.Default.Add, contentDescription = "Pièces jointes", tint = DesignAssistantColors.Navy.copy(alpha = 0.4f))
        }
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text(placeholder, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            enabled = enabled,
            singleLine = true,
            shape = RoundedCornerShape(24.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = DesignAssistantColors.Surface,
                unfocusedContainerColor = DesignAssistantColors.Surface,
                focusedBorderColor = DesignAssistantColors.Accent.copy(alpha = 0.5f),
                unfocusedBorderColor = Color.Transparent,
            ),
        )
        Spacer(Modifier.width(8.dp))
        IconButton(
            onClick = onSend,
            enabled = enabled && value.isNotBlank(),
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(
                    if (enabled && value.isNotBlank()) DesignAssistantColors.Navy
                    else DesignAssistantColors.Navy.copy(alpha = 0.35f)
                ),
        ) {
            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Envoyer", tint = Color.White)
        }
    }
}

@Composable
fun JulesAvatar(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(DesignAssistantColors.Navy),
        contentAlignment = Alignment.Center,
    ) {
        Text("J", color = Color.White, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun CollapsibleCodeBlock(code: String, expanded: Boolean, onToggle: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DesignAssistantColors.CodeBlockBg),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Code", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
                Text(if (expanded) "Réduire" else "Développer", color = DesignAssistantColors.Accent, fontSize = 12.sp)
            }
            if (expanded) {
                Spacer(Modifier.height(8.dp))
                Text(
                    code,
                    color = Color(0xFF80CBC4),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                )
            }
        }
    }
}

@Composable
fun WorkspaceTabRow(
    selected: fr.geoking.julius.designassistant.WorkspaceTab,
    onSelect: (fr.geoking.julius.designassistant.WorkspaceTab) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        fr.geoking.julius.designassistant.WorkspaceTab.entries.forEach { tab ->
            val active = tab == selected
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { onSelect(tab) }
                    .background(if (active) DesignAssistantColors.Navy else Color.Transparent)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    tab.icon,
                    contentDescription = tab.labelFr,
                    tint = if (active) Color.White else DesignAssistantColors.TextSecondary,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    tab.labelFr,
                    color = if (active) Color.White else DesignAssistantColors.TextSecondary,
                    fontSize = 13.sp,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }
    }
}

@Composable
fun DesignAssistantTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DesignAssistantColors.MaterialTheme, content = content)
}
