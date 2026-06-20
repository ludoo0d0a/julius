package fr.geoking.julius.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.geoking.julius.shared.logging.NetworkLog

/**
 * Reusable network request log list with expandable request/response JSON payloads.
 * Reads from [DebugLogStore] by default; pass any [List] of [NetworkLog] for tests/previews.
 */
@Composable
fun NetworkDebugSection(
    logs: List<NetworkLog>,
    modifier: Modifier = Modifier,
    maxListHeight: androidx.compose.ui.unit.Dp = 280.dp,
    emptyHint: String = "No network logs — enable “Journaux de debug” in Settings.",
) {
    if (logs.isEmpty()) {
        Text(emptyHint, color = Color.White.copy(alpha = 0.5f), fontSize = 10.sp, modifier = modifier)
        return
    }

    var selectedHost by remember { mutableStateOf<String?>(null) }
    val hosts = remember(logs) { logs.map { it.host }.distinct().sorted() }

    val filteredLogs = remember(logs, selectedHost) {
        if (selectedHost == null) logs else logs.filter { it.host == selectedHost }
    }

    Column(modifier = modifier) {
        if (hosts.size > 1) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            ) {
                item {
                    FilterChip(
                        selected = selectedHost == null,
                        onClick = { selectedHost = null },
                        label = { Text("All", fontSize = 10.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = Color.Transparent,
                            labelColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = selectedHost == null,
                            borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                            selectedBorderColor = MaterialTheme.colorScheme.primary,
                            borderWidth = 1.dp,
                            selectedBorderWidth = 1.dp,
                        )
                    )
                }
                items(hosts) { host ->
                    FilterChip(
                        selected = selectedHost == host,
                        onClick = { selectedHost = if (selectedHost == host) null else host },
                        label = { Text(host, fontSize = 10.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = Color.Transparent,
                            labelColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = selectedHost == host,
                            borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                            selectedBorderColor = MaterialTheme.colorScheme.primary,
                            borderWidth = 1.dp,
                            selectedBorderWidth = 1.dp,
                        )
                    )
                }
            }
        }

        LazyColumn(
            modifier = Modifier.heightIn(max = maxListHeight),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(filteredLogs, key = { it.id }) { log ->
                NetworkLogRow(log)
            }
        }
    }
}

@Composable
private fun NetworkLogRow(log: NetworkLog) {
    var expanded by remember(log.id) { mutableStateOf(false) }
    val status = log.statusCode
    val statusColor = when {
        log.errorMessage != null && status == null -> Color(0xFFFF8A80)
        status == null -> Color(0xFFFFB74D)
        status in 200..299 -> Color(0xFF81C784)
        status in 400..499 -> Color(0xFFFFB74D)
        status >= 500 -> Color(0xFFFF8A80)
        else -> Color.White.copy(alpha = 0.7f)
    }
    val path = remember(log.url) {
        log.url.substringAfter("://").substringAfter('/').let { p ->
            if (p.isBlank()) log.url else "/$p"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                if (expanded) Icons.Default.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                null,
                tint = Color.White.copy(alpha = 0.5f),
                modifier = Modifier.size(14.dp),
            )
            Text(
                text = log.method,
                color = Color.Cyan,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(end = 6.dp),
            )
            Text(
                text = status?.toString() ?: "…",
                color = statusColor,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(end = 6.dp),
            )
            Text(
                text = path,
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (log.durationMs > 0) {
                Text(
                    text = "${log.durationMs}ms",
                    color = Color.White.copy(alpha = 0.45f),
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }

        AnimatedVisibility(expanded, enter = expandVertically(), exit = shrinkVertically()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 18.dp, bottom = 6.dp),
            ) {
                Text(
                    log.url,
                    color = Color.White.copy(alpha = 0.55f),
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                log.errorMessage?.let { err ->
                    Text(
                        "Error: $err",
                        color = Color(0xFFFF8A80),
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                log.requestBody?.takeIf { it.isNotBlank() }?.let { body ->
                    Spacer(Modifier.height(6.dp))
                    JsonDebugPanel(
                        jsonString = body,
                        label = "Request",
                        maxHeight = 160.dp,
                        initialExpandDepth = 1,
                    )
                }
                log.responseBody?.takeIf { it.isNotBlank() }?.let { body ->
                    Spacer(Modifier.height(6.dp))
                    JsonDebugPanel(
                        jsonString = body,
                        label = "Response",
                        maxHeight = 180.dp,
                        initialExpandDepth = 1,
                    )
                }
                if (log.requestBody.isNullOrBlank() && log.responseBody.isNullOrBlank() && log.errorMessage == null) {
                    Text("Pending…", color = Color.White.copy(alpha = 0.4f), fontSize = 10.sp)
                }
            }
        }
    }
}
