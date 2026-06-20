package fr.geoking.julius.ui.v3

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
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
) {
    val settings by deps.settingsManager.settings.collectAsState()
    var isRefreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // On open: background reconcile features from DB + conversations from agents.
    LaunchedEffect(sourceName) {
        isRefreshing = true
        val apiKeys = deps.settingsManager.settings.value.julesApiKeys()
        val githubToken = deps.settingsManager.settings.value.githubApiKey
        try {
            deps.featureRepository.refreshFeatures(sourceName, apiKeys, githubToken)
        } finally {
            isRefreshing = false
        }
    }

    val featuresFlow = remember { deps.featureRepository.getAllFeatures() }
    val all by featuresFlow.collectAsState(initial = emptyList())
    var query by remember { mutableStateOf("") }
    var bucket by remember { mutableStateOf(FeatureBucket.ALL) }

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
                isRefreshing = true
                val apiKeys = deps.settingsManager.settings.value.julesApiKeys()
                val githubToken = deps.settingsManager.settings.value.githubApiKey
                try {
                    deps.featureRepository.refreshFeatures(sourceName, apiKeys, githubToken)
                } finally {
                    isRefreshing = false
                }
            }
        },
        modifier = Modifier.fillMaxSize()
    ) {
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 18.dp).padding(top = 16.dp)) {
            OutlinedTextField(
                value = query, onValueChange = { query = it },
                placeholder = { Text("Rechercher…", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                leadingIcon = { Icon(Icons.Filled.Search, null, tint = V3.Faint) },
                singleLine = true,
                shape = RoundedCornerShape(13.dp),
                modifier = Modifier.fillMaxWidth(),
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
}
