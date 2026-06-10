package fr.geoking.julius.ui.harness

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.geoking.julius.queue.QueueStatus
import fr.geoking.julius.ui.ColorHelper

@Composable
fun QueueStatusBanner(status: QueueStatus, modifier: Modifier = Modifier) {
    val label = when {
        status.paused -> "Queue paused"
        status.pendingCount > 0 -> "Queue: ${status.activeCount}/${status.parallelLimit} active · ${status.pendingCount} pending"
        else -> "Queue: ${status.activeCount}/${status.parallelLimit} active"
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(ColorHelper.JulesListBg)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(label, color = Color.White, fontSize = 13.sp)
        if (status.accounts.isNotEmpty()) {
            Row(modifier = Modifier.padding(top = 4.dp)) {
                status.accounts.filter { it.enabled }.take(3).forEach { row ->
                    Text(
                        "${row.label}: ${row.usedToday}/${row.dailyLimit}",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 11.sp,
                        modifier = Modifier.padding(end = 12.dp),
                    )
                }
            }
        }
    }
}
