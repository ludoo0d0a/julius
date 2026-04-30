package fr.geoking.julius.ui

import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.geoking.julius.logging.LogcatStore
import fr.geoking.julius.logging.ShizukuLogcatStore
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku

private val Lavender = Color(0xFFD1D5FF)
private val DeepPurple = Color(0xFF21004C)
private val SeparatorColor = Color(0xFF2D2D44)

private enum class LogSource {
    AppOnly,
    DeviceShizuku
}

@Composable
fun LogViewerScreen() {
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()

    var source by remember { mutableStateOf(LogSource.AppOnly) }
    var shizukuArgs by remember { mutableStateOf("*:V") }

    var shizukuPermissionGranted by remember { mutableStateOf(false) }
    val shizukuAvailable = ShizukuLogcatStore.canUseShizuku()

    val appLines by LogcatStore.lines.collectAsState()
    val appIsRunning by LogcatStore.isRunning.collectAsState()
    val appLastError by LogcatStore.lastError.collectAsState()

    val deviceLines by ShizukuLogcatStore.lines.collectAsState()
    val deviceIsRunning by ShizukuLogcatStore.isRunning.collectAsState()
    val deviceLastError by ShizukuLogcatStore.lastError.collectAsState()

    var query by remember { mutableStateOf("") }
    var paused by remember { mutableStateOf(false) }
    var pausedSnapshot by remember { mutableStateOf<List<String>>(emptyList()) }

    val listState = rememberLazyListState()

    DisposableEffect(Unit) {
        val permissionListener = Shizuku.OnRequestPermissionResultListener { _, grantResult ->
            shizukuPermissionGranted = grantResult == PackageManager.PERMISSION_GRANTED
        }
        try {
            Shizuku.addRequestPermissionResultListener(permissionListener)
        } catch (_: Throwable) {
        }

        shizukuPermissionGranted = ShizukuLogcatStore.hasPermission()

        onDispose {
            try {
                Shizuku.removeRequestPermissionResultListener(permissionListener)
            } catch (_: Throwable) {
            }
        }
    }

    DisposableEffect(source, shizukuPermissionGranted, shizukuArgs) {
        // Make sure we don't keep both logcat readers running.
        when (source) {
            LogSource.AppOnly -> {
                LogcatStore.start(scope)
                scope.launch { ShizukuLogcatStore.stop() }
            }
            LogSource.DeviceShizuku -> {
                scope.launch { LogcatStore.stop() }
                if (shizukuAvailable && shizukuPermissionGranted) {
                    val args = shizukuArgs
                        .split(Regex("\\s+"))
                        .filter { it.isNotBlank() }
                    ShizukuLogcatStore.start(scope, extraArgs = args)
                }
            }
        }

        onDispose {
            // Stop only the currently selected source, to avoid flapping.
            scope.launch {
                when (source) {
                    LogSource.AppOnly -> LogcatStore.stop()
                    LogSource.DeviceShizuku -> ShizukuLogcatStore.stop()
                }
            }
        }
    }

    // Auto-scroll to bottom when live and unfiltered.
    val activeLines = if (source == LogSource.AppOnly) appLines else deviceLines
    LaunchedEffect(activeLines.size, paused, query, source) {
        if (!paused && query.isBlank() && activeLines.isNotEmpty()) {
            listState.scrollToItem(activeLines.lastIndex)
        }
    }

    val display: List<String> = remember(activeLines, paused, pausedSnapshot, query) {
        val base = if (paused) pausedSnapshot else activeLines.map { it.raw }
        if (query.isBlank()) base else base.filter { it.contains(query, ignoreCase = true) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterChip(
                selected = source == LogSource.AppOnly,
                onClick = { source = LogSource.AppOnly },
                label = { Text("This app") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Lavender,
                    selectedLabelColor = DeepPurple,
                    containerColor = Color.White.copy(alpha = 0.10f),
                    labelColor = Color.White
                )
            )
            FilterChip(
                selected = source == LogSource.DeviceShizuku,
                onClick = { source = LogSource.DeviceShizuku },
                label = { Text("Device (Shizuku)") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Lavender,
                    selectedLabelColor = DeepPurple,
                    containerColor = Color.White.copy(alpha = 0.10f),
                    labelColor = Color.White
                )
            )

            Spacer(modifier = Modifier.weight(1f))

            if (source == LogSource.DeviceShizuku && (!shizukuAvailable || !shizukuPermissionGranted)) {
                Button(
                    onClick = {
                        if (!shizukuAvailable) {
                            context.startActivity(
                                Intent(
                                    Intent.ACTION_VIEW,
                                    android.net.Uri.parse("https://shizuku.rikka.app/download/")
                                )
                            )
                        } else {
                            ShizukuLogcatStore.requestPermission(901)
                        }
                    },
                    enabled = !shizukuAvailable || !shizukuPermissionGranted,
                    colors = ButtonDefaults.buttonColors(containerColor = Lavender, contentColor = DeepPurple)
                ) {
                    Text(if (!shizukuAvailable) "Install/Start Shizuku" else "Grant Shizuku permission")
                }
            }
        }

        if (source == LogSource.DeviceShizuku) {
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedTextField(
                value = shizukuArgs,
                onValueChange = { shizukuArgs = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("logcat filters (e.g. \"MyTag:D *:S\" or \"*:V\")") },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Lavender,
                    unfocusedBorderColor = SeparatorColor,
                    focusedContainerColor = Color.Black.copy(alpha = 0.25f),
                    unfocusedContainerColor = Color.Black.copy(alpha = 0.20f),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                )
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                label = { Text("Search") },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Lavender,
                    unfocusedBorderColor = SeparatorColor,
                    focusedContainerColor = Color.Black.copy(alpha = 0.25f),
                    unfocusedContainerColor = Color.Black.copy(alpha = 0.20f),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                )
            )

            IconButton(
                onClick = {
                    paused = !paused
                    if (!paused) pausedSnapshot = emptyList()
                    else pausedSnapshot = activeLines.map { it.raw }
                }
            ) {
                Icon(
                    imageVector = if (paused) Icons.Default.PlayArrow else Icons.Default.Pause,
                    contentDescription = if (paused) "Resume" else "Pause",
                    tint = Lavender
                )
            }

            IconButton(
                onClick = {
                    when (source) {
                        LogSource.AppOnly -> LogcatStore.clear()
                        LogSource.DeviceShizuku -> ShizukuLogcatStore.clear()
                    }
                }
            ) {
                Icon(Icons.Default.Delete, contentDescription = "Clear", tint = Lavender)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val exportText = remember(display) {
                val tail = if (display.size > 800) display.takeLast(800) else display
                tail.joinToString("\n")
            }

            Button(
                onClick = {
                    scope.launch {
                        clipboard.setClipEntry(
                            androidx.compose.ui.platform.ClipEntry(
                                android.content.ClipData.newPlainText("logcat", exportText)
                            )
                        )
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Lavender, contentColor = DeepPurple)
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Copy")
            }

            Button(
                onClick = {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, "Julius logs")
                        putExtra(Intent.EXTRA_TEXT, exportText)
                    }
                    context.startActivity(Intent.createChooser(intent, "Share logs"))
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.10f), contentColor = Color.White)
            ) {
                Icon(Icons.Default.Share, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Share")
            }

            Spacer(modifier = Modifier.weight(1f))

            Text(
                text = when {
                    source == LogSource.AppOnly && appLastError != null -> "logcat error: ${appLastError}"
                    source == LogSource.DeviceShizuku && deviceLastError != null -> "logcat error: ${deviceLastError}"
                    source == LogSource.AppOnly && appIsRunning -> "live • ${appLines.size} lines"
                    source == LogSource.DeviceShizuku && deviceIsRunning -> "live • ${deviceLines.size} lines"
                    source == LogSource.AppOnly -> "stopped • ${appLines.size} lines"
                    else -> "stopped • ${deviceLines.size} lines"
                },
                color = if (
                    (source == LogSource.AppOnly && appLastError != null) ||
                    (source == LogSource.DeviceShizuku && deviceLastError != null)
                ) Color(0xFFFF6B6B) else Lavender.copy(alpha = 0.8f),
                fontSize = 12.sp
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.20f), shape = MaterialTheme.shapes.medium)
                .padding(12.dp),
            state = listState
        ) {
            itemsIndexed(display, key = { index, item -> "$index-$item" }) { _, line ->
                Text(
                    text = line,
                    color = Color.White,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }
        }
    }
}

