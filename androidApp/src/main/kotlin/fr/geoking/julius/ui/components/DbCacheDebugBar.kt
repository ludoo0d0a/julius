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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.geoking.julius.debug.DbCacheCounts
import fr.geoking.julius.debug.DbCacheDebugState
import fr.geoking.julius.debug.DbCacheDebugTracker
import fr.geoking.julius.debug.DbEntityKind

/**
 * Non-intrusive overlay showing Room cache write stats during background refreshes.
 * Collapsed by default; tap the chip to expand details. Dismiss hides until restored.
 */
@Composable
fun DbCacheDebugBar(
    tracker: DbCacheDebugTracker,
    modifier: Modifier = Modifier,
    bottomPadding: Dp = 0.dp,
) {
    val state by tracker.state.collectAsState()
    var dismissed by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }

    if (dismissed) {
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
                Icon(Icons.Default.Storage, contentDescription = "Show DB debug", modifier = Modifier.size(18.dp))
            }
        }
        return
    }

    val showBar = state.loading || state.current.isNotEmpty() || state.lifetime.isNotEmpty()
    if (!showBar) return

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
                    if (state.loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = Color.Cyan,
                        )
                    } else {
                        Icon(Icons.Default.Storage, null, tint = Color.Cyan, modifier = Modifier.size(16.dp))
                    }
                    Spacer(Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = state.currentOp ?: "DB cache",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                        )
                        Text(
                            text = summaryLine(state),
                            color = Color.White.copy(alpha = 0.75f),
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
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

                AnimatedVisibility(
                    visible = expanded,
                    enter = expandVertically(),
                    exit = shrinkVertically(),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.35f))
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                    ) {
                        Text("Cycle", color = Color.Cyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        DbEntityKind.entries.forEach { kind ->
                            val counts = state.current[kind]
                            if (counts != null && !counts.isEmpty()) {
                                CountRow(kind.label, counts)
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
                                CountRow(kind.label, counts)
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
                            Text(
                                "Error: $err",
                                color = Color(0xFFFF8A80),
                                fontSize = 10.sp,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CountRow(label: String, counts: DbCacheCounts) {
    Text(
        text = "$label: +${counts.saved} saved · ${counts.updated} upd · ${counts.deleted} del",
        color = Color.White.copy(alpha = 0.9f),
        fontSize = 10.sp,
        fontFamily = FontFamily.Monospace,
    )
}

private fun summaryLine(state: DbCacheDebugState): String {
    val cur = state.currentTotals()
    val life = state.lifetimeTotals()
    val curPart = if (cur.total > 0) "cycle +${cur.saved}/~${cur.updated}/-${cur.deleted}" else "cycle —"
    val lifePart = if (life.total > 0) " · total +${life.saved}/~${life.updated}/-${life.deleted}" else ""
    return curPart + lifePart
}
