package fr.geoking.julius.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.geoking.julius.shared.NetworkStatus
import fr.geoking.julius.ui.components.NetworkStatusIcon

enum class DashboardAction {
    ASSISTANT,
    JULES,
    MAP,
    POI_MAP,
    ROUTE_PLANNING,
    NETWORK_LOCATION,
    SETTINGS,
    HISTORY
}

private data class DashboardItem(
    val action: DashboardAction,
    val title: String,
    val icon: ImageVector,
    val color: Color
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    networkStatus: NetworkStatus,
    onAction: (DashboardAction) -> Unit
) {
    val items = listOf(
        DashboardItem(DashboardAction.ASSISTANT, "Assistant", Icons.Default.Mic, Color(0xFF3B82F6)),
        DashboardItem(DashboardAction.JULES, "Jules Chat", Icons.Default.Chat, Color(0xFF10B981)),
        DashboardItem(DashboardAction.MAP, "Map", Icons.Default.Map, Color(0xFFF59E0B)),
        DashboardItem(DashboardAction.POI_MAP, "POI Map", Icons.Default.Place, Color(0xFFEF4444)),
        DashboardItem(DashboardAction.ROUTE_PLANNING, "Routes", Icons.Default.Route, Color(0xFF8B5CF6)),
        DashboardItem(DashboardAction.NETWORK_LOCATION, "Info", Icons.Default.Info, Color(0xFF6366F1)),
        DashboardItem(DashboardAction.SETTINGS, "Settings", Icons.Default.Settings, Color(0xFF64748B)),
        DashboardItem(DashboardAction.HISTORY, "History", Icons.Default.History, Color(0xFFEC4899))
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Julius Dashboard", color = Color.White, fontWeight = FontWeight.Bold) },
                actions = {
                    NetworkStatusIcon(
                        status = networkStatus,
                        onClick = { onAction(DashboardAction.NETWORK_LOCATION) },
                        modifier = Modifier.padding(end = 16.dp)
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF0F172A)
                )
            )
        },
        containerColor = Color(0xFF0F172A)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            Text(
                "Welcome to Julius",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(items) { item ->
                    DashboardCard(item) { onAction(item.action) }
                }
            }
        }
    }
}

@Composable
private fun DashboardCard(
    item: DashboardItem,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        shape = MaterialTheme.shapes.large,
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = item.title,
                tint = item.color,
                modifier = Modifier.size(40.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = item.title,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
        }
    }
}
