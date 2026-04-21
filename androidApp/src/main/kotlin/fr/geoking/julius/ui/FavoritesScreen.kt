package fr.geoking.julius.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.maps.model.LatLng
import fr.geoking.julius.SettingsManager
import fr.geoking.julius.api.geocoding.GeocodedPlace
import fr.geoking.julius.api.geocoding.GeocodingClient
import fr.geoking.julius.community.FavoritesRepository
import fr.geoking.julius.poi.Poi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreen(
    favoritesRepo: FavoritesRepository,
    geocodingClient: GeocodingClient,
    settingsManager: SettingsManager,
    onBack: () -> Unit,
    onOpenMap: (Poi) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var suggestions by remember { mutableStateOf<List<GeocodedPlace>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var favorites by remember { mutableStateOf<List<Poi>>(emptyList()) }
    var favoriteIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var poiToRename by remember { mutableStateOf<Poi?>(null) }
    var newName by remember { mutableStateOf("") }

    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        favorites = favoritesRepo.getFavorites()
        favoriteIds = favorites.map { it.id }.toSet()
    }

    LaunchedEffect(searchQuery) {
        if (searchQuery.length < 3) {
            suggestions = emptyList()
            return@LaunchedEffect
        }
        delay(500)
        isSearching = true
        try {
            suggestions = geocodingClient.geocode(searchQuery, limit = 5)
        } catch (e: Exception) {
            android.util.Log.e("FavoritesScreen", "Geocoding failed", e)
        } finally {
            isSearching = false
        }
    }

    PlaystoreTheme {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Favorites") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                )
            },
            containerColor = MaterialTheme.colorScheme.background
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp)
            ) {
                // Search Bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Find destination…") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (isSearching) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear")
                            }
                        }
                    },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium
                )

                if (suggestions.isNotEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest)
                    ) {
                        Column {
                            suggestions.forEach { place ->
                                val placeId = "geo:${place.latitude},${place.longitude}"
                                val isFav = placeId in favoriteIds
                                ListItem(
                                    headlineContent = { Text(place.label) },
                                    leadingContent = { Icon(Icons.Default.Place, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                                    trailingContent = {
                                        IconButton(onClick = {
                                            scope.launch {
                                                val poi = Poi(
                                                    id = placeId,
                                                    name = place.label,
                                                    address = place.label,
                                                    latitude = place.latitude,
                                                    longitude = place.longitude
                                                )
                                                favoritesRepo.toggleFavorite(poi)
                                                favorites = favoritesRepo.getFavorites()
                                                favoriteIds = favorites.map { it.id }.toSet()
                                            }
                                        }) {
                                            Icon(
                                                if (isFav) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                                contentDescription = "Favorite",
                                                tint = if (isFav) Color.Red else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    },
                                    modifier = Modifier.clickable {
                                        val poi = Poi(
                                            id = placeId,
                                            name = place.label,
                                            address = place.label,
                                            latitude = place.latitude,
                                            longitude = place.longitude
                                        )
                                        onOpenMap(poi)
                                    },
                                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "My Saved Places",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                if (favorites.isEmpty()) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text("No favorites yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(favorites) { poi ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                            ) {
                                ListItem(
                                    headlineContent = { Text(poi.name, fontWeight = FontWeight.SemiBold) },
                                    supportingContent = { Text(poi.address, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                    leadingContent = {
                                        Icon(
                                            Icons.Default.Place,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    },
                                    trailingContent = {
                                        Row {
                                            IconButton(onClick = {
                                                poiToRename = poi
                                                newName = poi.name
                                            }) {
                                                Icon(Icons.Default.Edit, contentDescription = "Rename", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                            IconButton(onClick = {
                                                scope.launch {
                                                    favoritesRepo.removeFavorite(poi.id)
                                                    favorites = favoritesRepo.getFavorites()
                                                    favoriteIds = favorites.map { it.id }.toSet()
                                                }
                                            }) {
                                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red.copy(alpha = 0.7f))
                                            }
                                        }
                                    },
                                    modifier = Modifier.clickable {
                                        onOpenMap(poi)
                                    },
                                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                                )
                            }
                        }
                    }
                }
            }
        }

        if (poiToRename != null) {
            AlertDialog(
                onDismissRequest = { poiToRename = null },
                title = { Text("Rename Favorite") },
                text = {
                    Column {
                        Text("Enter a new name for this place:", style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = newName,
                            onValueChange = { newName = it },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        poiToRename?.let { poi ->
                            scope.launch {
                                favoritesRepo.updateFavorite(poi.copy(name = newName))
                                favorites = favoritesRepo.getFavorites()
                                poiToRename = null
                            }
                        }
                    }) {
                        Text("Save")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { poiToRename = null }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}
