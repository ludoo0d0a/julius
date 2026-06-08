package fr.geoking.julius.ui.jules

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Build
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.geoking.julius.persistence.FeatureEntity
import fr.geoking.julius.persistence.JulesSessionEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun JulesFeaturesContent(
    selectedSourceName: String,
    features: List<FeatureEntity>,
    sessions: List<JulesSessionEntity>,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onOpenGitDetails: () -> Unit,
    onSelectFeature: (featureId: String, title: String) -> Unit,
) {
    val repoFeatures = features.filter { it.sourceName == selectedSourceName }.sortedBy { it.position }
    val orphanCount = JulesNavigation.sessionCountForFeature(
        sessions,
        selectedSourceName,
        JulesNavigation.ORPHAN_FEATURE_ID,
    )

    JulesPullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item {
                Text(
                    "Features",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(16.dp, 8.dp),
                )
            }

            item(key = "git_details") {
                ListItem(
                    headlineContent = { Text("Git & CI") },
                    supportingContent = { Text("Workflow runs, deploy status", fontSize = 12.sp) },
                    trailingContent = {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                    },
                    leadingContent = {
                        Icon(Icons.Default.Build, contentDescription = null, tint = Color.White.copy(alpha = 0.7f))
                    },
                    modifier = Modifier.clickable(onClick = onOpenGitDetails),
                    colors = ListItemDefaults.colors(
                        containerColor = Color.Transparent,
                        headlineColor = Color.White,
                        supportingColor = Color.White.copy(alpha = 0.6f),
                    ),
                )
                HorizontalDivider(color = Color.White.copy(alpha = 0.05f))
            }

            item(key = JulesNavigation.ORPHAN_FEATURE_ID) {
                FeatureRow(
                    title = JulesNavigation.ORPHAN_FEATURE_TITLE,
                    subtitle = "$orphanCount conversation${if (orphanCount == 1) "" else "s"}",
                    onClick = {
                        onSelectFeature(JulesNavigation.ORPHAN_FEATURE_ID, JulesNavigation.ORPHAN_FEATURE_TITLE)
                    },
                )
            }

            items(repoFeatures, key = { it.id }) { feature ->
                val count = JulesNavigation.sessionCountForFeature(sessions, selectedSourceName, feature.id)
                FeatureRow(
                    title = feature.title,
                    subtitle = "$count conversation${if (count == 1) "" else "s"} · ${feature.status}",
                    onClick = { onSelectFeature(feature.id, feature.title) },
                )
            }
        }
    }
}

@Composable
private fun FeatureRow(title: String, subtitle: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle, fontSize = 12.sp) },
        trailingContent = {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
        },
        modifier = Modifier.clickable(onClick = onClick),
        colors = ListItemDefaults.colors(
            containerColor = Color.Transparent,
            headlineColor = Color.White,
            supportingColor = Color.White.copy(alpha = 0.6f),
            trailingIconColor = Color.White.copy(alpha = 0.3f),
        ),
    )
    HorizontalDivider(color = Color.White.copy(alpha = 0.05f))
}
