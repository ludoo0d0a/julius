package fr.geoking.julius.ui.jules

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Reorder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.geoking.julius.ui.ColorHelper
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
    onMoveFeature: (List<FeatureEntity>) -> Unit,
) {
    var repoFeatures by remember { mutableStateOf(emptyList<FeatureEntity>()) }
    var isReorderMode by remember { mutableStateOf(false) }

    LaunchedEffect(features, selectedSourceName) {
        if (!isReorderMode) {
            repoFeatures = features.filter { it.sourceName == selectedSourceName }.sortedBy { it.position }
        }
    }

    val orphanCount = JulesNavigation.sessionCountForFeature(
        sessions,
        selectedSourceName,
        JulesNavigation.ORPHAN_FEATURE_ID,
    )

    val listState = rememberLazyListState()

    JulesPullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(isReorderMode) {
                    if (!isReorderMode) return@pointerInput
                    var draggedItemIndex: Int? = null
                    detectDragGesturesAfterLongPress(
                        onDragStart = { offset ->
                            listState.layoutInfo.visibleItemsInfo
                                .find { item -> offset.y.toInt() in item.offset..(item.offset + item.size) }
                                ?.let { item ->
                                    if (item.key is String && (item.key as String).startsWith("feature_")) {
                                        draggedItemIndex = repoFeatures.indexOfFirst { "feature_${it.id}" == item.key }
                                    }
                                }
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            val currentDraggedIndex = draggedItemIndex ?: return@detectDragGesturesAfterLongPress
                            val targetItem = listState.layoutInfo.visibleItemsInfo
                                .find { item -> change.position.y.toInt() in item.offset..(item.offset + item.size) }

                            if (targetItem != null && targetItem.key is String && (targetItem.key as String).startsWith("feature_")) {
                                val targetFeatureIndex = repoFeatures.indexOfFirst { "feature_${it.id}" == targetItem.key }
                                if (targetFeatureIndex != -1 && targetFeatureIndex != currentDraggedIndex) {
                                    val newList = repoFeatures.toMutableList()
                                    val item = newList.removeAt(currentDraggedIndex)
                                    newList.add(targetFeatureIndex, item)
                                    repoFeatures = newList
                                    draggedItemIndex = targetFeatureIndex
                                }
                            }
                        },
                        onDragEnd = {
                            draggedItemIndex = null
                        },
                        onDragCancel = {
                            draggedItemIndex = null
                        },
                    )
                },
            state = listState,
        ) {
            item {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        "Features",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        onClick = {
                            if (isReorderMode) {
                                onMoveFeature(repoFeatures)
                            }
                            isReorderMode = !isReorderMode
                        },
                    ) {
                        Text(
                            if (isReorderMode) "Save order" else "Reorder",
                            color = ColorHelper.JulesAccent,
                        )
                    }
                }
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
                        if (!isReorderMode) onSelectFeature(JulesNavigation.ORPHAN_FEATURE_ID, JulesNavigation.ORPHAN_FEATURE_TITLE)
                    },
                    isReorderMode = false, // Orphan feature is not reorderable
                )
            }

            items(repoFeatures, key = { "feature_${it.id}" }) { feature ->
                val count = JulesNavigation.sessionCountForFeature(sessions, selectedSourceName, feature.id)
                FeatureRow(
                    title = feature.title,
                    subtitle = "$count conversation${if (count == 1) "" else "s"} · ${feature.status}",
                    onClick = { if (!isReorderMode) onSelectFeature(feature.id, feature.title) },
                    isReorderMode = isReorderMode,
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }
}

@Composable
private fun FeatureRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    isReorderMode: Boolean,
    modifier: Modifier = Modifier,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle, fontSize = 12.sp) },
        trailingContent = {
            if (!isReorderMode) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
            }
        },
        leadingContent = {
            if (isReorderMode) {
                Icon(
                    Icons.Default.Reorder,
                    contentDescription = "Reorder",
                    tint = Color.White.copy(alpha = 0.3f),
                )
            }
        },
        modifier = modifier.clickable(onClick = onClick),
        colors = ListItemDefaults.colors(
            containerColor = Color.Transparent,
            headlineColor = Color.White,
            supportingColor = Color.White.copy(alpha = 0.6f),
            trailingIconColor = Color.White.copy(alpha = 0.3f),
        ),
    )
    HorizontalDivider(color = Color.White.copy(alpha = 0.05f))
}
