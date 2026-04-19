package fr.geoking.julius.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import fr.geoking.julius.feature.location.LocationHelper
import fr.geoking.julius.repository.FuelForecastRepository
import fr.geoking.julius.repository.FuelForecastUiState
import fr.geoking.julius.ui.components.FuelForecastChartCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FuelForecastScreen(
    repository: FuelForecastRepository,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var states by remember { mutableStateOf<Map<String, FuelForecastUiState>>(emptyMap()) }
    var isLoading by remember { mutableStateOf(false) }
    var refreshTick by remember { mutableStateOf(0) }
var brentRange by remember { mutableStateOf("1m") }

    val allFuelIds = setOf("gazole", "sp95_e10", "sp95", "sp98", "gplc", "e85")

    LaunchedEffect(refreshTick, brentRange) {
        isLoading = true
        try {
            val loc = withContext(Dispatchers.IO) { LocationHelper.getCurrentLocation(context) }
            if (loc != null) {
                states = repository.refreshAndBuildMultiUiState(loc.latitude, loc.longitude, allFuelIds, brentRange)
            }
        } catch (e: Exception) {
            android.util.Log.e("FuelForecastScreen", "Failed to refresh forecasts", e)
        } finally {
            isLoading = false
        }
    }

    PlaystoreTheme {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Price Estimation") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        IconButton(onClick = { refreshTick++ }, enabled = !isLoading) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                        }
                    }
                )
            }
        ) { padding ->
            if (isLoading && states.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        Text(
                            "Regional estimations based on market trends and local pump averages.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }

                    item {
                        val brentState = states["brent"] ?: FuelForecastUiState(fuelId = "brent", locationKey = "")
                        FuelForecastChartCard(
                            state = brentState,
                            isLoading = isLoading && states.isEmpty(), onRangeSelected = { brentRange = it }
                        )
                    }

                    val sortedFuels = listOf("gazole", "sp95_e10", "sp95", "sp98", "gplc", "e85")
                    items(sortedFuels) { fuelId ->
                        val state = states[fuelId] ?: FuelForecastUiState(fuelId = fuelId, locationKey = "")
                        FuelForecastChartCard(
                            state = state,
                            isLoading = isLoading && states.isEmpty() // Only show inner loading if first load
                        )
                    }

                    item {
                        Box(Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}
