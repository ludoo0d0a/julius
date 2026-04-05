package fr.geoking.julius.ui.map

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import androidx.compose.ui.zIndex
import fr.geoking.julius.shared.logging.DebugLogStore
import fr.geoking.julius.shared.logging.NetworkLog
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun DebugLogOverlay(
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        DebugLogOverlayContent()
    }
}

@Composable
private fun DebugLogOverlayContent() {
    var isExpanded by remember { mutableStateOf(false) }
    val logs by DebugLogStore.logs.collectAsState()
    var selectedLog by remember { mutableStateOf<NetworkLog?>(null) }

    Box(modifier = Modifier.padding(16.dp).zIndex(2f)) {
        if (!isExpanded) {
            FloatingActionButton(
                onClick = { isExpanded = true },
                containerColor = Color(0xFF334155).copy(alpha = 0.8f),
                contentColor = Color.White,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(Icons.Default.BugReport, contentDescription = "Show Logs")
            }
        } else {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.6f),
                color = Color(0xFF0F172A).copy(alpha = 0.95f),
                shape = RoundedCornerShape(16.dp),
                border = BoxShadow(Color.White.copy(alpha = 0.2f))
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Network Debug Logs (${logs.size})",
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        Row {
                            IconButton(onClick = { DebugLogStore.clearLogs() }) {
                                Icon(Icons.Default.Delete, "Clear", tint = Color.White)
                            }
                            IconButton(onClick = { isExpanded = false }) {
                                Icon(Icons.Default.Close, "Close", tint = Color.White)
                            }
                        }
                    }

                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(logs, key = { it.id }) { log ->
                            LogItem(log, onClick = { selectedLog = log })
                        }
                    }
                }
            }
        }
    }

    if (selectedLog != null) {
        LogDetailsDialog(log = selectedLog!!, onDismiss = { selectedLog = null })
    }
}

@Composable
private fun LogItem(log: NetworkLog, onClick: () -> Unit) {
    val time = remember(log.timestamp) {
        SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date(log.timestamp))
    }
    val statusColor = when (log.statusCode) {
        in 200..299 -> Color(0xFF4ADE80)
        in 400..499 -> Color(0xFFFACC15)
        in 500..599 -> Color(0xFFF87171)
        else -> Color.Gray
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = log.method,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                modifier = Modifier
                    .background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = log.statusCode?.toString() ?: "ERR",
                color = statusColor,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "${log.durationMs}ms",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 11.sp
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = time,
                color = Color.White.copy(alpha = 0.4f),
                fontSize = 10.sp
            )
        }
        Text(
            text = log.url,
            color = Color.White.copy(alpha = 0.8f),
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp)
        )
        HorizontalDivider(
            modifier = Modifier.padding(top = 8.dp),
            color = Color.White.copy(alpha = 0.1f)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LogDetailsDialog(log: NetworkLog, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
        title = {
            Text("Request Details", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        },
        text = {
            SelectionContainer {
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    item {
                        DetailSection("General")
                        DetailItem("URL", log.url)
                        DetailItem("Method", log.method)
                        DetailItem("Status", log.statusCode?.toString() ?: "N/A")
                        DetailItem("Duration", "${log.durationMs}ms")
                        DetailItem("Timestamp", Date(log.timestamp).toString())

                        Spacer(modifier = Modifier.height(16.dp))
                        DetailSection("Request Headers")
                        log.requestHeaders.forEach { (k, v) ->
                            DetailItem(k, v.joinToString(", "))
                        }

                        val reqBody = log.safeRequestBody
                        if (reqBody.isNotBlank()) {
                            Spacer(modifier = Modifier.height(16.dp))
                            DetailSection("Request Body")
                            BodyContent(reqBody)
                        }

                        log.responseHeaders?.let { headers ->
                            Spacer(modifier = Modifier.height(16.dp))
                            DetailSection("Response Headers")
                            headers.forEach { (k, v) ->
                                DetailItem(k, v.joinToString(", "))
                            }
                        }

                        val respBody = log.safeResponseBody
                        if (respBody.isNotBlank()) {
                            Spacer(modifier = Modifier.height(16.dp))
                            DetailSection("Response Body")
                            BodyContent(respBody)
                        }
                    }
                }
            }
        },
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.fillMaxWidth(0.95f).fillMaxHeight(0.8f)
    )
}

@Composable
private fun DetailSection(title: String) {
    Text(
        text = title,
        color = MaterialTheme.colorScheme.primary,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(vertical = 4.dp)
    )
}

@Composable
private fun DetailItem(label: String, value: String) {
    Row(modifier = Modifier.padding(vertical = 2.dp)) {
        Text(
            text = "$label: ",
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            modifier = Modifier.width(100.dp)
        )
        Text(text = value, fontSize = 12.sp)
    }
}

@Composable
private fun BodyContent(body: String) {
    Surface(
        color = Color.Black.copy(alpha = 0.05f),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = body,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(8.dp)
        )
    }
}

private fun BoxShadow(color: Color) = androidx.compose.foundation.BorderStroke(1.dp, color)
