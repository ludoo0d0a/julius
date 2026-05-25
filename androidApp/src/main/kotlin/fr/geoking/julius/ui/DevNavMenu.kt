package fr.geoking.julius.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * Global dev overlay to jump between main screens without leaving the app.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevNavMenu(
    current: MainDestination,
    onNavigate: (MainDestination) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var sheetOpen by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    Box(modifier = modifier.fillMaxSize()) {
        FloatingActionButton(
            onClick = { sheetOpen = true },
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 8.dp, top = 8.dp),
            containerColor = Color(0xFF334155),
            contentColor = Color.White,
        ) {
            Icon(Icons.Default.Apps, contentDescription = "Dev navigation")
        }
    }

    if (sheetOpen) {
        ModalBottomSheet(
            onDismissRequest = { sheetOpen = false },
            sheetState = sheetState,
            containerColor = Color(0xFF1E293B),
        ) {
            Text(
                text = "Dev navigation",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            LazyColumn {
                items(MainDestination.entries) { destination ->
                    val selected = destination == current
                    ListItem(
                        headlineContent = {
                            Text(destination.label, color = Color.White)
                        },
                        trailingContent = {
                            if (selected) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    tint = Color(0xFF94A3B8),
                                )
                            }
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onNavigate(destination)
                                scope.launch {
                                    sheetState.hide()
                                    sheetOpen = false
                                }
                            }
                            .padding(horizontal = 8.dp),
                    )
                }
                item {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        color = Color(0xFF334155),
                    )
                    ListItem(
                        headlineContent = {
                            Text("Settings", color = Color.White)
                        },
                        leadingContent = {
                            Icon(Icons.Default.Settings, contentDescription = null, tint = Color(0xFF94A3B8))
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onOpenSettings()
                                scope.launch {
                                    sheetState.hide()
                                    sheetOpen = false
                                }
                            }
                            .padding(horizontal = 8.dp),
                    )
                }
            }
        }
    }
}
