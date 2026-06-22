package fr.geoking.julius.ui.v3

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Schedule
import fr.geoking.julius.ui.components.SpeechMicPlacement
import fr.geoking.julius.ui.components.SpeechTextInput
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import fr.geoking.julius.queue.julesApiKeys
import kotlinx.coroutines.launch
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private fun bucketIcon(b: FeatureBucket): ImageVector = when (b) {
    FeatureBucket.ALL -> Icons.Filled.Apps
    FeatureBucket.RUNNING -> Icons.Filled.Autorenew
    FeatureBucket.QUEUED -> Icons.Filled.Schedule
    FeatureBucket.MERGED -> Icons.Filled.CheckCircle
    FeatureBucket.FAILED -> Icons.Filled.ErrorOutline
}

private fun bucketHint(b: FeatureBucket): String = when (b) {
    FeatureBucket.ALL -> "Toutes les features"
    FeatureBucket.RUNNING -> "En cours d'exécution"
    FeatureBucket.QUEUED -> "En attente dans la file"
    FeatureBucket.MERGED -> "Terminées"
    FeatureBucket.FAILED -> "En échec"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterChipWithHint(selected: Boolean, bucket: FeatureBucket, onClick: () -> Unit) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(bucketHint(bucket)) } },
        state = rememberTooltipState(),
    ) {
        FilterChip(
            selected = selected,
            onClick = onClick,
            label = { Text(bucket.label) },
            leadingIcon = { Icon(bucketIcon(bucket), null, modifier = Modifier.size(18.dp)) },
            shape = RoundedCornerShape(10.dp),
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = V3.Accent,
                selectedLabelColor = V3.AccentInk,
                selectedLeadingIconColor = V3.AccentInk,
                containerColor = V3.Surface,
                labelColor = V3.Muted,
                iconColor = V3.Muted,
            ),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeaturesV3Screen(
    deps: V3Deps,
    sourceName: String?,
    onBack: (() -> Unit)?,
    onOpenFeature: (String) -> Unit,
    onSelectProject: (String?) -> Unit,
) {
    val settings by deps.settingsManager.settings.collectAsState()
    val apiKeys = remember(settings) { settings.julesApiKeys() }
    var isRefreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // List recent sessions, promote orphans to features, then show from Room.
    LaunchedEffect(sourceName, apiKeys) {
        if (sourceName == null) return@LaunchedEffect
        isRefreshing = true
        try {
            deps.featureRepository.refreshFeatures(sourceName, apiKeys, settings.githubApiKey)
        } finally {
            isRefreshing = false
        }
    }

    val featuresFlow = remember { deps.featureRepository.getAllFeatures() }
    val all by featuresFlow.collectAsState(initial = emptyList())
    var query by remember { mutableStateOf("") }
    var bucket by remember { mutableStateOf(FeatureBucket.ALL) }
    var showProjectPicker by remember { mutableStateOf(false) }

    val scoped = remember(all, sourceName) {
        if (sourceName == null) all else all.filter { it.sourceName == sourceName }
    }
    val filtered = remember(scoped, query, bucket) {
        val q = query.trim().lowercase()
        scoped.filter { f ->
            (bucket == FeatureBucket.ALL || bucketOf(f.status) == bucket) &&
                (q.isEmpty() || f.title.lowercase().contains(q) || f.sourceName.lowercase().contains(q))
        }
    }
    val chips = listOf(FeatureBucket.ALL, FeatureBucket.RUNNING, FeatureBucket.QUEUED, FeatureBucket.MERGED, FeatureBucket.FAILED)

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = {
            scope.launch {
                if (sourceName == null) return@launch
                isRefreshing = true
                try {
                    deps.featureRepository.refreshFeatures(sourceName, apiKeys, settings.githubApiKey)
                } finally {
                    isRefreshing = false
                }
            }
        },
        modifier = Modifier.fillMaxSize()
    ) {
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 18.dp).padding(top = 16.dp)) {
            AssistChip(
                onClick = { showProjectPicker = true },
                label = { Text(sourceName ?: "Tous les projets") },
                leadingIcon = { Icon(Icons.Filled.FolderOpen, null, modifier = Modifier.size(18.dp)) },
                trailingIcon = { Icon(Icons.Filled.ArrowDropDown, null, modifier = Modifier.size(18.dp)) },
                shape = RoundedCornerShape(10.dp),
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = V3.Surface,
                    labelColor = V3.Fg,
                    leadingIconContentColor = V3.Accent,
                    trailingIconContentColor = V3.Faint,
                ),
                border = BorderStroke(1.dp, V3.Border),
            )
            Spacer(Modifier.height(8.dp))
            SpeechTextInput(
                value = query,
                onValueChange = { query = it },
                placeholder = "Rechercher…",
                singleLine = true,
                micPlacement = SpeechMicPlacement.Inline,
                showWaveform = false,
                shape = RoundedCornerShape(13.dp),
                modifier = Modifier.fillMaxWidth(),
                micTint = V3.Accent,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = V3.Fg,
                    unfocusedTextColor = V3.Fg,
                    focusedBorderColor = V3.Accent,
                    unfocusedBorderColor = V3.Border,
                    cursorColor = V3.Accent,
                ),
            )
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                chips.forEach { b ->
                    FilterChipWithHint(selected = bucket == b, bucket = b) { bucket = b }
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        if (filtered.isEmpty()) {
            if (!isRefreshing) {
                EmptyHint(if (query.isEmpty()) "Aucune feature." else "Aucun résultat pour « $query »")
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
                item {
                    Column(Modifier.fillMaxWidth().padding(bottom = 96.dp)) {
                        V3Card {
                            filtered.forEachIndexed { i, f ->
                                if (i > 0) HorizontalDivider(color = V3.Border)
                                FeatureRow(f, onClick = { onOpenFeature(f.id) })
                            }
                        }
                    }
                }
            }
        }
    }
    }

    if (showProjectPicker) {
        ProjectPickerSheet(deps, onDismiss = { showProjectPicker = false }, onSelect = onSelectProject)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProjectPickerSheet(
    deps: V3Deps,
    onDismiss: () -> Unit,
    onSelect: (String?) -> Unit,
) {
    val sources by deps.julesRepository.getSourcesFlow().collectAsState(initial = emptyList())

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = V3.Surface) {
        Column(Modifier.padding(horizontal = 18.dp).padding(bottom = 24.dp)) {
            Text("Changer de projet", color = V3.Fg, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(16.dp))
            LazyColumn {
                item {
                    ProjectPickerRow(name = "Tous les projets", onClick = { onSelect(null); onDismiss() })
                    HorizontalDivider(color = V3.Border)
                }
                items(sources) { s ->
                    ProjectPickerRow(name = s.name, onClick = { onSelect(s.name); onDismiss() })
                }
            }
        }
    }
}

@Composable
private fun ProjectPickerRow(name: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.FolderOpen, null, tint = V3.Muted, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(name, color = V3.Fg, fontSize = 16.sp)
    }
}
