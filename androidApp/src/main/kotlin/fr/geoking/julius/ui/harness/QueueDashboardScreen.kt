package fr.geoking.julius.ui.harness

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.geoking.julius.persistence.FeatureEntity
import fr.geoking.julius.queue.QueueStatus
import fr.geoking.julius.ui.ColorHelper
import fr.geoking.julius.ui.StatusBadge

@Composable
fun QueueDashboardScreen(
    status: QueueStatus,
    features: List<FeatureEntity>,
    onTogglePause: (Boolean) -> Unit,
    onOpenProjects: () -> Unit,
    onAddFeature: () -> Unit,
    onOpenFeature: (FeatureEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pending = features.filter { it.status == "PENDING" }
    val active = features.filter { it.status in listOf("QUEUED", "IN_PROGRESS") }

    Column(modifier = modifier.fillMaxSize()) {
        QueueStatusBanner(status)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Pause queue", color = Color.White, modifier = Modifier.weight(1f))
            Switch(checked = status.paused, onCheckedChange = onTogglePause)
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilledTonalButton(onClick = onOpenProjects, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.Folder, contentDescription = null)
                Text(" Projects", modifier = Modifier.padding(start = 4.dp))
            }
            IconButton(onClick = onAddFeature) {
                Icon(Icons.Default.Add, contentDescription = "Add feature", tint = Color.White)
            }
        }
        Text(
            "Pending (${pending.size})",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(16.dp, 8.dp, 16.dp, 4.dp),
        )
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(pending, key = { it.id }) { feature ->
                FeatureQueueRow(feature, onClick = { onOpenFeature(feature) })
            }
            if (active.isNotEmpty()) {
                item {
                    Text(
                        "Active (${active.size})",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(16.dp, 16.dp, 16.dp, 4.dp),
                    )
                }
                items(active, key = { it.id }) { feature ->
                    FeatureQueueRow(feature, onClick = { onOpenFeature(feature) })
                }
            }
        }
    }
}

@Composable
private fun FeatureQueueRow(feature: FeatureEntity, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(feature.title, color = Color.White, fontWeight = FontWeight.Medium)
            if (feature.sourceName.isNotBlank()) {
                Text(feature.sourceName, color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
            }
        }
        StatusBadge(feature.status)
    }
}
