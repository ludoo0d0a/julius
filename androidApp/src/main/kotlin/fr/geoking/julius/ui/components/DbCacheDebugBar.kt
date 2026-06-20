package fr.geoking.julius.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Http
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.UnfoldLess
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.geoking.julius.debug.DbCacheCounts
import fr.geoking.julius.debug.DbCacheDebugState
import fr.geoking.julius.debug.DbCacheDebugTracker
import fr.geoking.julius.debug.DbEntityKind
import fr.geoking.julius.shared.logging.DebugLogStore
import fr.geoking.julius.shared.logging.NetworkLog
import kotlinx.coroutines.flow.StateFlow

private enum class DebugPanelTab { Database, Network }

/**
 * Reusable harness debug overlay: Room cache stats + HTTP request/response logs with JSON tree viewer.
 * Use in V3 app, legacy Jules harness, Design Assistant, or any screen loading projects/features/sessions.
 */
@Composable
fun HarnessDebugBar(
    dbTracker: DbCacheDebugTracker,
    modifier: Modifier = Modifier,
    bottomPadding: Dp = 0.dp,
    networkLogs: StateFlow<List<NetworkLog>> = DebugLogStore.logs,
) {
    val dbState by dbTracker.state.collectAsState()
    val netLogs by networkLogs.collectAsState()
    var dismissed by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }
    var tab by remember { mutableStateOf(DebugPanelTab.Database) }

    val dbVisible = dbState.loading || dbState.current.isNotEmpty() || dbState.lifetime.isNotEmpty()
    val netVisible = netLogs.isNotEmpty()
    val showBar = dbVisible || netVisible

    if (dismissed) {
        if (!showBar) return
        Box(
            modifier = modifier
                .fillMaxWidth()
                .padding(end = 12.dp, bottom = 12.dp + bottomPadding),
            contentAlignment = Alignment.BottomEnd,
        ) {
            SmallFloatingActionButton(
                onClick = { dismissed = false },
                containerColor = Color.Black.copy(alpha = 0.65f),
                contentColor = Color.White,
            ) {
                Icon(Icons.Default.Storage, contentDescription = "Show debug panel", modifier = Modifier.size(18.dp))
            }
        }
        return
    }

    if (!showBar) return

    LaunchedEffect(netVisible, dbVisible) {
        if (!dbVisible && netVisible) tab = DebugPanelTab.Network
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp)
            .padding(bottom = 10.dp + bottomPadding),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = Color.Black.copy(alpha = 0.82f),
            shadowElevation = 6.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { expanded = !expanded }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    when {
                        dbState.loading -> CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = Color.Cyan)
                        netVisible && !dbVisible -> Icon(Icons.Default.Http, null, tint = Color(0xFF81C784), modifier = Modifier.size(16.dp))
                        else -> Icon(Icons.Default.Storage, null, tint = Color.Cyan, modifier = Modifier.size(16.dp))
                    }
                    Spacer(Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = headerTitle(dbState, netLogs),
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                        )
                        Text(
                            text = headerSummary(dbState, netLogs),
                            color = Color.White.copy(alpha = 0.75f),
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 2,
                        )
                    }
                    IconButton(onClick = { expanded = !expanded }, modifier = Modifier.size(28.dp)) {
                        Icon(
                            if (expanded) Icons.Default.UnfoldLess else Icons.Default.UnfoldMore,
                            contentDescription = if (expanded) "Collapse" else "Expand",
                            tint = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.size(16.dp),
                        )
                    }
                    IconButton(onClick = { dismissed = true; expanded = false }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Hide", tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
                    }
                }

                AnimatedVisibility(expanded, enter = expandVertically(), exit = shrinkVertically()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.35f))
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                    ) {
                        if (dbVisible && netVisible) {
                            TabRow(
                                selectedTabIndex = tab.ordinal,
                                containerColor = Color.Transparent,
                                contentColor = Color.Cyan,
                                modifier = Modifier.height(36.dp),
                            ) {
                                Tab(
                                    selected = tab == DebugPanelTab.Database,
                                    onClick = { tab = DebugPanelTab.Database },
                                    text = { Text("Database", fontSize = 10.sp) },
                                )
                                Tab(
                                    selected = tab == DebugPanelTab.Network,
                                    onClick = { tab = DebugPanelTab.Network },
                                    text = { Text("Network (${netLogs.size})", fontSize = 10.sp) },
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                        }

                        when {
                            tab == DebugPanelTab.Network || (!dbVisible && netVisible) -> {
                                NetworkDebugSection(logs = netLogs)
                            }
                            else -> DatabaseDebugSection(dbState)
                        }
                    }
                }
            }
        }
    }
}

/** @see HarnessDebugBar */
@Composable
fun DbCacheDebugBar(
    tracker: DbCacheDebugTracker,
    modifier: Modifier = Modifier,
    bottomPadding: Dp = 0.dp,
) = HarnessDebugBar(dbTracker = tracker, modifier = modifier, bottomPadding = bottomPadding)

@Composable
private fun DatabaseDebugSection(state: DbCacheDebugState) {
    Text("Cycle", color = Color.Cyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    DbEntityKind.entries.forEach { kind ->
        val counts = state.current[kind]
        if (counts != null && !counts.isEmpty()) {
            DbCountRow(kind.label, counts)
        }
    }
    if (state.current.values.all { it.isEmpty() }) {
        Text("—", color = Color.White.copy(alpha = 0.5f), fontSize = 10.sp)
    }

    Spacer(Modifier.height(8.dp))
    Text("Session totals", color = Color.Cyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    DbEntityKind.entries.forEach { kind ->
        val counts = state.lifetime[kind]
        if (counts != null && !counts.isEmpty()) {
            DbCountRow(kind.label, counts)
        }
    }
    val life = state.lifetimeTotals()
    if (life.total > 0) {
        Text(
            "Σ saved ${life.saved} · updated ${life.updated} · deleted ${life.deleted}",
            color = Color.White.copy(alpha = 0.85f),
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
    state.lastError?.let { err ->
        Text("Error: $err", color = Color(0xFFFF8A80), fontSize = 10.sp, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun DbCountRow(label: String, counts: DbCacheCounts) {
    Text(
        text = "$label: +${counts.saved} saved · ${counts.updated} upd · ${counts.deleted} del",
        color = Color.White.copy(alpha = 0.9f),
        fontSize = 10.sp,
        fontFamily = FontFamily.Monospace,
    )
}

private fun headerTitle(dbState: DbCacheDebugState, netLogs: List<NetworkLog>): String {
    val latest = netLogs.firstOrNull()
    return when {
        dbState.loading && dbState.currentOp != null -> dbState.currentOp!!
        latest != null && dbState.currentOp == null -> "${latest.method} ${latest.statusCode ?: "…"}"
        dbState.currentOp != null -> dbState.currentOp!!
        latest != null -> "${latest.method} ${latest.host}"
        else -> "Debug"
    }
}

private fun headerSummary(dbState: DbCacheDebugState, netLogs: List<NetworkLog>): String {
    val parts = mutableListOf<String>()
    val cur = dbState.currentTotals()
    if (cur.total > 0 || dbState.loading) {
        parts += if (cur.total > 0) "db +${cur.saved}/~${cur.updated}/-${cur.deleted}" else "db …"
    }
    netLogs.firstOrNull()?.let { log ->
        val err = log.errorMessage?.take(40)?.let { if (it.length == 40) "$it…" else it }
        val netPart = buildString {
            append("http ${log.statusCode ?: "…"}")
            if (log.durationMs > 0) append(" ${log.durationMs}ms")
            err?.let { append(" · $it") }
        }
        parts += netPart
    }
    if (netLogs.size > 1) parts += "${netLogs.size} requests"
    return parts.joinToString(" · ").ifBlank { "—" }
}
