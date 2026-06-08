package fr.geoking.julius.ui.jules

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.geoking.julius.api.jules.JulesClient
import fr.geoking.julius.ui.ColorHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun JulesRepositoriesList(
    sources: List<JulesClient.JulesSource>,
    onSelect: (JulesClient.JulesSource) -> Unit,
    loading: Boolean,
    refreshing: Boolean,
    onRefresh: () -> Unit,
) {
    JulesPullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize(),
    ) {
        when {
            loading && sources.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = ColorHelper.JulesAccent)
                }
            }
            else -> {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    item {
                        Text(
                            "Projects",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(16.dp, 8.dp),
                        )
                    }
                    if (sources.isEmpty()) {
                        item {
                            Text(
                                "No projects found. Pull down to refresh.",
                                color = Color.White.copy(alpha = 0.5f),
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                        }
                    } else {
                        items(sources, key = { it.name }) { source ->
                            val displayName = source.githubRepo?.let { "${it.owner}/${it.repo}" } ?: source.name
                            ListItem(
                                headlineContent = { Text(displayName) },
                                trailingContent = {
                                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                                },
                                modifier = Modifier.clickable { onSelect(source) },
                                colors = ListItemDefaults.colors(
                                    containerColor = Color.Transparent,
                                    headlineColor = Color.White,
                                    trailingIconColor = Color.White.copy(alpha = 0.3f),
                                ),
                            )
                            HorizontalDivider(color = Color.White.copy(alpha = 0.05f))
                        }
                    }
                }
            }
        }
    }
}
