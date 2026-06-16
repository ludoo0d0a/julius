package fr.geoking.julius.ui.jules

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Reorder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
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
import fr.geoking.julius.persistence.FeatureEntity
import fr.geoking.julius.persistence.JulesSessionEntity
import fr.geoking.julius.shared.voice.VoiceManager
import fr.geoking.julius.ui.ColorHelper
import fr.geoking.julius.ui.components.VoiceInputIcon

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun JulesFeaturesContent(
    selectedSourceName: String,
    features: List<FeatureEntity>,
    sessions: List<JulesSessionEntity>,
    voiceManager: VoiceManager,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onSelectFeature: (featureId: String, title: String) -> Unit,
    onMoveFeature: (List<FeatureEntity>) -> Unit,
    onCreateFeature: (title: String) -> Unit,
) {
    var repoFeatures by remember { mutableStateOf(emptyList<FeatureEntity>()) }
    LaunchedEffect(features, selectedSourceName) {
        repoFeatures = features.filter { it.sourceName == selectedSourceName }.sortedBy { it.position }
    }

    val orphanCount = JulesNavigation.sessionCountForFeature(
        sessions,
        selectedSourceName,
        JulesNavigation.ORPHAN_FEATURE_ID,
    )

    val listState = rememberLazyListState()
    var newFeatureTitle by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize()) {
        JulesPullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier.weight(1f),
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
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
                                if (draggedItemIndex != null) {
                                    onMoveFeature(repoFeatures)
                                }
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
                Text(
                    "Features",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(16.dp, 8.dp),
                )
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

            items(repoFeatures, key = { "feature_${it.id}" }) { feature ->
                val count = JulesNavigation.sessionCountForFeature(sessions, selectedSourceName, feature.id)
                FeatureRow(
                    title = feature.title,
                    subtitle = "$count conversation${if (count == 1) "" else "s"} · ${feature.status}",
                    onClick = { onSelectFeature(feature.id, feature.title) },
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = newFeatureTitle,
                onValueChange = { newFeatureTitle = it },
                modifier = Modifier.weight(1f),
                placeholder = {
                    Text(
                        "Add a new feature...",
                        color = Color.White.copy(alpha = 0.5f),
                    )
                },
                shape = RoundedCornerShape(24.dp),
                trailingIcon = {
                    VoiceInputIcon(
                        voiceManager = voiceManager,
                        onTranscriptionReceived = { newFeatureTitle = (newFeatureTitle + " " + it).trim() }
                    )
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    cursorColor = ColorHelper.JulesAccent,
                    focusedBorderColor = ColorHelper.JulesAccent,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.1f),
                ),
                maxLines = 5,
            )
            IconButton(
                onClick = {
                    if (newFeatureTitle.isNotBlank()) {
                        onCreateFeature(newFeatureTitle.trim())
                        newFeatureTitle = ""
                    }
                },
                enabled = newFeatureTitle.isNotBlank(),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Add",
                    tint = if (newFeatureTitle.isNotBlank()) ColorHelper.JulesAccent else Color.Gray,
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
    modifier: Modifier = Modifier,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle, fontSize = 12.sp) },
        trailingContent = {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
        },
        leadingContent = {
            Icon(
                Icons.Default.Reorder,
                contentDescription = "Reorder",
                tint = Color.White.copy(alpha = 0.3f),
            )
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
