package fr.geoking.julius.ui.v3

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeaturesV3Screen(
    deps: V3Deps,
    sourceName: String?,
    onBack: (() -> Unit)?,
    onOpenFeature: (String) -> Unit,
) {
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

    Column(Modifier.fillMaxSize()) {
        // header
        if (onBack != null) {
            Row(Modifier.fillMaxWidth().padding(start = 4.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Retour", tint = V3.Fg) }
                Text(sourceName ?: "", color = V3.Muted, fontSize = 13.sp)
            }
        }
        V3LargeTitle(
            eyebrow = "Features",
            title = "Features",
            subtitle = "${filtered.size} feature(s)" + if (sourceName != null) " · $sourceName" else " · tous projets",
        )

        Column(Modifier.padding(horizontal = 18.dp)) {
            OutlinedTextField(
                value = query, onValueChange = { query = it },
                placeholder = { Text("Rechercher…", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                leadingIcon = { Icon(Icons.Filled.Search, null, tint = V3.Faint) },
                singleLine = true,
                shape = RoundedCornerShape(13.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                chips.forEach { b ->
                    FilterChip(
                        selected = bucket == b,
                        onClick = { bucket = b },
                        label = { Text(b.label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = V3.Accent,
                            selectedLabelColor = V3.AccentInk,
                            containerColor = V3.Surface,
                            labelColor = V3.Muted,
                        ),
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        if (filtered.isEmpty()) {
            Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                Text("Aucune feature ne correspond.", color = V3.Faint, fontSize = 13.sp)
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
