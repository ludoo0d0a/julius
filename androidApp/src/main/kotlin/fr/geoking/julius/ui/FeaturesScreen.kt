package fr.geoking.julius.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.geoking.julius.SettingsManager
import fr.geoking.julius.api.jules.JulesClient
import fr.geoking.julius.persistence.FeatureEntity
import fr.geoking.julius.persistence.JulesSessionEntity
import fr.geoking.julius.repository.FeatureRepository
import fr.geoking.julius.repository.JulesRepository
import kotlinx.coroutines.launch
import java.util.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun FeaturesScreen(
    onBack: () -> Unit,
    featureRepository: FeatureRepository,
    julesRepository: JulesRepository,
    julesClient: JulesClient,
    settingsManager: SettingsManager,
    onOpenConversation: (JulesSessionEntity) -> Unit
) {
    val features by featureRepository.getAllFeatures().collectAsState(initial = emptyList())
    val settings by settingsManager.settings.collectAsState()
    val apiKeys = settings.julesKeys

    var searchQuery by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }
    var selectedFeature by remember { mutableStateOf<FeatureEntity?>(null) }
    var sources by remember { mutableStateOf<List<JulesClient.JulesSource>>(emptyList()) }

    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        if (apiKeys.isNotEmpty()) {
            julesRepository.getSources(apiKeys).collect { sources = it }
        }
    }

    var localFeatures by remember { mutableStateOf<List<FeatureEntity>>(emptyList()) }
    LaunchedEffect(features) {
        localFeatures = features
    }

    val filteredFeatures = localFeatures.filter {
        it.title.contains(searchQuery, ignoreCase = true) ||
        it.description.contains(searchQuery, ignoreCase = true)
    }

    BackHandler {
        if (isSearching) {
            isSearching = false
            searchQuery = ""
        } else if (selectedFeature != null) {
            selectedFeature = null
        } else {
            onBack()
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = ColorHelper.JulesBg
    ) {
        Column {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isSearching && selectedFeature == null) {
                    IconButton(onClick = {
                        isSearching = false
                        searchQuery = ""
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                    TextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Search features...", color = Color.White.copy(alpha = 0.5f)) },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            cursorColor = ColorHelper.JulesAccent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        singleLine = true
                    )
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear", tint = Color.White)
                        }
                    }
                } else {
                    IconButton(onClick = {
                        if (selectedFeature != null) selectedFeature = null
                        else onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                    Text(
                        text = if (selectedFeature != null) "Feature Detail" else "Features",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    if (selectedFeature == null) {
                        IconButton(onClick = { isSearching = true }) {
                            Icon(Icons.Default.Search, contentDescription = "Search", tint = Color.White)
                        }
                        IconButton(onClick = { showAddDialog = true }) {
                            Icon(Icons.Default.Add, contentDescription = "Add Feature", tint = Color.White)
                        }
                    } else {
                        isSearching = false
                        IconButton(onClick = {
                            scope.launch {
                                featureRepository.deleteFeature(selectedFeature!!.id)
                                selectedFeature = null
                            }
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.White)
                        }
                    }
                }
            }

            if (selectedFeature == null) {
                // List View
                var newFeatureTitle by remember { mutableStateOf("") }
                Column(modifier = Modifier.weight(1f)) {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredFeatures, key = { it.id }) { feature ->
                            FeatureItem(
                            feature = feature,
                            onClick = { selectedFeature = feature },
                            onMoveUp = {
                                val idx = localFeatures.indexOf(feature)
                                if (idx > 0) {
                                    val newList = localFeatures.toMutableList()
                                    newList.removeAt(idx)
                                    newList.add(idx - 1, feature)
                                    localFeatures = newList
                                    scope.launch { featureRepository.updatePositions(newList) }
                                }
                            },
                            onMoveDown = {
                                val idx = localFeatures.indexOf(feature)
                                if (idx < localFeatures.size - 1) {
                                    val newList = localFeatures.toMutableList()
                                    newList.removeAt(idx)
                                    newList.add(idx + 1, feature)
                                    localFeatures = newList
                                    scope.launch { featureRepository.updatePositions(newList) }
                                }
                            },
                            isFirst = localFeatures.firstOrNull()?.id == feature.id,
                            isLast = localFeatures.lastOrNull()?.id == feature.id
                        )
                    }
                }

                    // Quick Add Bar (WhatsApp style)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = newFeatureTitle,
                            onValueChange = { newFeatureTitle = it },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("Add a new feature...", color = Color.White.copy(alpha = 0.5f)) },
                            shape = RoundedCornerShape(24.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                cursorColor = ColorHelper.JulesAccent,
                                focusedBorderColor = ColorHelper.JulesAccent,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.1f)
                            ),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(
                            onClick = {
                                if (newFeatureTitle.isNotBlank()) {
                                    scope.launch {
                                        featureRepository.addFeature(
                                            title = newFeatureTitle,
                                            description = "",
                                            priority = 0,
                                            sourceName = sources.firstOrNull()?.name ?: ""
                                        )
                                        newFeatureTitle = ""
                                    }
                                }
                            },
                            enabled = newFeatureTitle.isNotBlank()
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Add",
                                tint = if (newFeatureTitle.isNotBlank()) ColorHelper.JulesAccent else Color.Gray
                            )
                        }
                    }
                }
            } else {
                // Detail View
                FeatureDetailContent(
                    feature = selectedFeature!!,
                    julesRepository = julesRepository,
                    featureRepository = featureRepository,
                    apiKeys = apiKeys,
                    onOpenConversation = onOpenConversation,
                    onUpdateFeature = { selectedFeature = it }
                )
            }
        }
    }

    if (showAddDialog) {
        AddFeatureDialog(
            sources = sources,
            onDismiss = { showAddDialog = false },
            onConfirm = { title, desc, priority, source ->
                scope.launch {
                    featureRepository.addFeature(title, desc, priority, source)
                    showAddDialog = false
                }
            }
        )
    }
}

@Composable
fun FeatureItem(
    feature: FeatureEntity,
    onClick: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    isFirst: Boolean,
    isLast: Boolean
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = ColorHelper.JulesCardAgent)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(feature.title, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(feature.description, color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Spacer(modifier = Modifier.width(8.dp))
            StatusBadge(feature.status)

            Spacer(modifier = Modifier.width(8.dp))
            Column {
                IconButton(onClick = onMoveUp, enabled = !isFirst, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Move Up", tint = if (isFirst) Color.Gray else Color.White)
                }
                IconButton(onClick = onMoveDown, enabled = !isLast, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Move Down", tint = if (isLast) Color.Gray else Color.White)
                }
            }
        }
    }
}

@Composable
fun FeatureDetailContent(
    feature: FeatureEntity,
    julesRepository: JulesRepository,
    featureRepository: FeatureRepository,
    apiKeys: List<String>,
    onOpenConversation: (JulesSessionEntity) -> Unit,
    onUpdateFeature: (FeatureEntity) -> Unit
) {
    val scope = rememberCoroutineScope()
    var isEditing by remember { mutableStateOf(false) }
    var editedTitle by remember { mutableStateOf(feature.title) }
    var editedDescription by remember { mutableStateOf(feature.description) }
    var sessions by remember { mutableStateOf<List<JulesSessionEntity>>(emptyList()) }
    var messageDraft by remember { mutableStateOf("") }
    var showInProgressOnly by remember { mutableStateOf(false) }
    var showUnmergedOnly by remember { mutableStateOf(false) }

    val filteredSessions = remember(sessions, showInProgressOnly, showUnmergedOnly) {
        sessions.filter { session ->
            val matchesInProgress = !showInProgressOnly || !session.isFinished
            val matchesUnmerged = !showUnmergedOnly || (session.prUrl != null && session.prState == "open")
            matchesInProgress && matchesUnmerged
        }
    }

    LaunchedEffect(feature.id) {
        julesRepository.getSessions(apiKeys, feature.sourceName, "").collect { list ->
            sessions = list.filter { it.featureId == feature.id }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        if (isEditing) {
            OutlinedTextField(
                value = editedTitle,
                onValueChange = { editedTitle = it },
                label = { Text("Title") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = editedDescription,
                onValueChange = { editedDescription = it },
                label = { Text("Description") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { isEditing = false }) { Text("Cancel") }
                Button(onClick = {
                    val updated = feature.copy(title = editedTitle, description = editedDescription)
                    scope.launch {
                        featureRepository.updateFeature(updated)
                        onUpdateFeature(updated)
                        isEditing = false
                    }
                }) { Text("Save") }
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(feature.title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconButton(onClick = { isEditing = true }) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit", tint = Color.White)
                }
            }
            StatusBadge(feature.status)
            Spacer(modifier = Modifier.height(16.dp))
            Text(feature.description, color = Color.White.copy(alpha = 0.8f))

            Spacer(modifier = Modifier.height(24.dp))
            Text("Conversations", color = Color.White, fontWeight = FontWeight.Bold)

            LazyColumn(modifier = Modifier.weight(1f)) {
                items(filteredSessions) { session ->
                    androidx.compose.material3.ListItem(
                        headlineContent = { Text(session.title.ifBlank { session.prompt }, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        supportingContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                StatusBadge(session.sessionState ?: "Active")
                                Spacer(modifier = Modifier.size(8.dp))
                                Text(session.updateTime?.take(16) ?: "", fontSize = 12.sp)
                            }
                        },
                        trailingContent = {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                        },
                        modifier = Modifier.clickable { onOpenConversation(session) },
                        colors = androidx.compose.material3.ListItemDefaults.colors(
                            containerColor = Color.Transparent,
                            headlineColor = Color.White,
                            supportingColor = Color.White.copy(alpha = 0.6f)
                        )
                    )
                    HorizontalDivider(color = Color.White.copy(alpha = 0.05f))
                }
            }

            FeatureActionBar(
                onArchiveCompleted = {
                    val completed = sessions.filter { it.isFinished }
                    scope.launch {
                        completed.forEach { julesRepository.archiveSession(it.id) }
                        // Refresh will happen via the LaunchedEffect if getSessions is observing
                    }
                },
                showInProgressOnly = showInProgressOnly,
                onShowInProgressOnlyChange = { showInProgressOnly = it },
                showUnmergedOnly = showUnmergedOnly,
                onShowUnmergedOnlyChange = { showUnmergedOnly = it }
            )

            FeatureChatInput(
                value = messageDraft,
                onValueChange = { messageDraft = it },
                placeholder = androidx.compose.ui.res.stringResource(fr.geoking.julius.R.string.message_jules_on, feature.title),
                onSend = {
                    val prompt = messageDraft.trim()
                    if (prompt.isEmpty()) return@FeatureChatInput
                    scope.launch {
                        val sessionId = julesRepository.createSession(
                            apiKeys = apiKeys,
                            prompt = prompt,
                            source = feature.sourceName,
                            title = prompt.take(80),
                            featureId = feature.id
                        )
                        messageDraft = ""
                        val session = julesRepository.getSession(sessionId)
                        if (session != null) onOpenConversation(session)
                    }
                },
                enabled = apiKeys.isNotEmpty()
            )
        }
    }
}

@Composable
fun FeatureActionBar(
    onArchiveCompleted: () -> Unit,
    showInProgressOnly: Boolean,
    onShowInProgressOnlyChange: (Boolean) -> Unit,
    showUnmergedOnly: Boolean,
    onShowUnmergedOnlyChange: (Boolean) -> Unit
) {
    val scrollState = rememberScrollState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Archive Button
        Surface(
            modifier = Modifier.clickable(onClick = onArchiveCompleted),
            color = Color.White.copy(alpha = 0.05f),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(Icons.Default.Archive, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                Text(
                    androidx.compose.ui.res.stringResource(fr.geoking.julius.R.string.archive_completed),
                    color = Color.White,
                    fontSize = 12.sp
                )
            }
        }

        // In Progress Filter
        FilterChip(
            selected = showInProgressOnly,
            onClick = { onShowInProgressOnlyChange(!showInProgressOnly) },
            label = { Text(androidx.compose.ui.res.stringResource(fr.geoking.julius.R.string.in_progress)) },
            colors = FilterChipDefaults.filterChipColors(
                labelColor = Color.White.copy(alpha = 0.6f),
                selectedLabelColor = Color.White,
                selectedContainerColor = ColorHelper.JulesAccent.copy(alpha = 0.2f)
            ),
            border = FilterChipDefaults.filterChipBorder(
                enabled = true,
                selected = showInProgressOnly,
                borderColor = Color.White.copy(alpha = 0.1f),
                selectedBorderColor = ColorHelper.JulesAccent.copy(alpha = 0.5f)
            )
        )

        // Unmerged Filter
        FilterChip(
            selected = showUnmergedOnly,
            onClick = { onShowUnmergedOnlyChange(!showUnmergedOnly) },
            label = { Text(androidx.compose.ui.res.stringResource(fr.geoking.julius.R.string.unmerged_code)) },
            colors = FilterChipDefaults.filterChipColors(
                labelColor = Color.White.copy(alpha = 0.6f),
                selectedLabelColor = Color.White,
                selectedContainerColor = ColorHelper.JulesAccent.copy(alpha = 0.2f)
            ),
            border = FilterChipDefaults.filterChipBorder(
                enabled = true,
                selected = showUnmergedOnly,
                borderColor = Color.White.copy(alpha = 0.1f),
                selectedBorderColor = ColorHelper.JulesAccent.copy(alpha = 0.5f)
            )
        )
    }
}

@Composable
fun FeatureChatInput(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    onSend: () -> Unit,
    enabled: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text(placeholder, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            enabled = enabled,
            shape = RoundedCornerShape(24.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = ColorHelper.JulesAccent,
                unfocusedBorderColor = Color.White.copy(alpha = 0.1f),
                cursorColor = ColorHelper.JulesAccent
            )
        )
        IconButton(
            onClick = onSend,
            enabled = enabled && value.isNotBlank(),
            modifier = Modifier
                .size(48.dp)
                .background(
                    if (enabled && value.isNotBlank()) ColorHelper.JulesAccent else Color.Gray.copy(alpha = 0.2f),
                    CircleShape
                )
        ) {
            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = Color.White)
        }
    }
}

@Composable
fun StatusBadge(status: String) {
    val color = when (status) {
        "COMPLETED" -> Color.Green
        "FAILED" -> Color.Red
        "IN_PROGRESS" -> ColorHelper.JulesAccent
        "QUEUED" -> Color.Yellow
        else -> Color.Gray
    }
    Surface(
        color = color.copy(alpha = 0.1f),
        shape = RoundedCornerShape(4.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.5f))
    ) {
        Text(
            text = status,
            color = color,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddFeatureDialog(
    sources: List<JulesClient.JulesSource>,
    onDismiss: () -> Unit,
    onConfirm: (String, String, Int, String) -> Unit,
    initialSourceName: String? = null,
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var priority by remember { mutableStateOf(0) }
    var selectedSource by remember {
        mutableStateOf(
            initialSourceName?.takeIf { name -> sources.any { it.name == name } }
                ?: sources.firstOrNull()?.name
                ?: ""
        )
    }
    var expanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Feature") },
        text = {
            Column {
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Title") })
                OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("Description") })

                Spacer(modifier = Modifier.height(8.dp))

                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded }
                ) {
                    OutlinedTextField(
                        value = selectedSource,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Source Repository") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier.menuAnchor(
                            type = ExposedDropdownMenuAnchorType.PrimaryNotEditable,
                            enabled = true,
                        )
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        sources.forEach { source ->
                            DropdownMenuItem(
                                text = { Text(source.githubRepo?.let { "${it.owner}/${it.repo}" } ?: source.name) },
                                onClick = {
                                    selectedSource = source.name
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(title, description, priority, selectedSource) }, enabled = title.isNotBlank() && selectedSource.isNotBlank()) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
