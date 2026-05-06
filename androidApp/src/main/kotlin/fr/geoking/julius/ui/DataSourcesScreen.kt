package fr.geoking.julius.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Human-facing list of fuel/EV data sources, grouped by country.
 *
 * This is intentionally UI-only and does not drive provider selection logic.
 */
@Composable
fun DataSourcesPage(
    onBack: () -> Unit,
) {
    // Keep same background/typography as the rest of settings pages.
    Column(modifier = Modifier.fillMaxSize()) {
        var query by remember { mutableStateOf("") }

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            placeholder = { Text("Search providers, countries…") },
            leadingIcon = { androidx.compose.material3.Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = MaterialTheme.colorScheme.onBackground,
                unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                unfocusedBorderColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.2f),
                cursorColor = MaterialTheme.colorScheme.primary
            )
        )

        val normalizedQuery = query.trim().lowercase()
        val sections = remember { DataSourcesRegistry.sections }
        val filtered = sections
            .mapNotNull { section ->
                val matchesSection = normalizedQuery.isBlank() ||
                    section.title.lowercase().contains(normalizedQuery) ||
                    section.entries.any { it.matches(normalizedQuery) }
                if (!matchesSection) return@mapNotNull null

                val entries =
                    if (normalizedQuery.isBlank()) section.entries
                    else section.entries.filter { it.matches(normalizedQuery) } + section.entries.filterNot { it.matches(normalizedQuery) }

                section.copy(entries = entries.distinct())
            }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Text(
                    text = "Sources",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Fuel prices and EV charging coverage by country.",
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                )
            }

            filtered.forEach { section ->
                item {
                    Spacer(modifier = Modifier.size(6.dp))
                    Text(
                        text = section.title,
                        modifier = Modifier.padding(horizontal = 16.dp),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                items(section.entries) { entry ->
                    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = entry.name,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            entry.access?.let {
                                Text(
                                    text = it,
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                                )
                            }
                        }
                        entry.details?.let {
                            Text(
                                text = it,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                            )
                        }
                        HorizontalDivider(color = Color.White.copy(alpha = 0.06f))
                    }
                }
            }
        }
    }
}

private data class DataSourceEntry(
    val name: String,
    val access: String? = null,
    val details: String? = null,
) {
    fun matches(q: String): Boolean {
        if (q.isBlank()) return true
        return name.lowercase().contains(q) ||
            (access?.lowercase()?.contains(q) == true) ||
            (details?.lowercase()?.contains(q) == true)
    }
}

private data class DataSourceSection(
    val title: String,
    val entries: List<DataSourceEntry>,
)

/**
 * Keep this list aligned with `sources.md`.
 */
private object DataSourcesRegistry {
    val sections: List<DataSourceSection> = listOf(
        DataSourceSection(
            title = "🌍 Global / Multi‑country",
            entries = listOf(
                DataSourceEntry("OpenStreetMap", access = "Free", details = "Community POIs (global)"),
                DataSourceEntry("OpenVanCamp", access = "Free", details = "EU weekly reference prices"),
                DataSourceEntry("OpenChargeMap", access = "API key", details = "Community EV charging (global)"),
                DataSourceEntry("Eco‑Movement (OCPI)", access = "API key", details = "Aggregator, real‑time availability"),
                DataSourceEntry("Routex", access = "Free", details = "Europe fuel stations (daily)"),
                DataSourceEntry("Fuelo.net", access = "Free", details = "Scraped, Europe / Turkey"),
                DataSourceEntry("DrivstoffAppen", access = "Free", details = "Nordics fuel prices"),
                DataSourceEntry("Ionity / Fastned (OCPI)", access = "Free", details = "Operator feeds, real‑time")
            )
        ),
        DataSourceSection(
            title = "🇦🇺 Australia",
            entries = listOf(
                DataSourceEntry("NSW FuelCheck", access = "API key", details = "Government, real‑time"),
                DataSourceEntry("FuelWatch", access = "Free", details = "Government, daily")
            )
        ),
        DataSourceSection(
            title = "🇦🇹 Austria",
            entries = listOf(
                DataSourceEntry("E‑Control", access = "Free", details = "Government, real‑time")
            )
        ),
        DataSourceSection(
            title = "🇧🇪 Belgium",
            entries = listOf(
                DataSourceEntry("Official max‑price (scraper)", access = "Free", details = "Government, daily")
            )
        ),
        DataSourceSection(
            title = "🇩🇰 Denmark",
            entries = listOf(
                DataSourceEntry("Fuelprices.dk", access = "API key", details = "Private, ~1h cache"),
                DataSourceEntry("DrivstoffAppen", access = "Free", details = "Nordics fallback")
            )
        ),
        DataSourceSection(
            title = "🇫🇷 France",
            entries = listOf(
                DataSourceEntry("Data.gouv (Prix Carburants)", access = "Free", details = "Government, ~10 min"),
                DataSourceEntry("GasAPI (community mirror)", access = "Free", details = "Community, ~10 min"),
                DataSourceEntry("Data.gouv IRVE", access = "Free", details = "Government EV dataset (daily)"),
                DataSourceEntry("Belib’ (Paris)", access = "Free", details = "Availability / status")
            )
        ),
        DataSourceSection(
            title = "🇩🇪 Germany",
            entries = listOf(
                DataSourceEntry("Tankerkönig (MTS‑K)", access = "API key", details = "Government, real‑time")
            )
        ),
        DataSourceSection(
            title = "🇱🇺 Luxembourg",
            entries = listOf(
                DataSourceEntry("Chargy", access = "Free", details = "Government EV charging, real‑time"),
                DataSourceEntry("OpenVanCamp", access = "Free", details = "Weekly fuel price fallback")
            )
        ),
        DataSourceSection(
            title = "🇳🇱 Netherlands",
            entries = listOf(
                DataSourceEntry("ANWB", access = "Free", details = "~1h cache")
            )
        ),
        DataSourceSection(
            title = "🇵🇹 Portugal",
            entries = listOf(
                DataSourceEntry("DGEG", access = "Free", details = "~1h cache")
            )
        ),
        DataSourceSection(
            title = "🇬🇧 United Kingdom",
            entries = listOf(
                DataSourceEntry("CMA Open Data", access = "Free", details = "~1h cache")
            )
        )
    )
}

