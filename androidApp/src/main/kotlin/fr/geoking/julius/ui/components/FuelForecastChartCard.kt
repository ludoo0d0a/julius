package fr.geoking.julius.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.geoking.julius.repository.DailyPricePoint
import fr.geoking.julius.repository.FuelForecastUiState
import fr.geoking.julius.repository.PredictionInfo
import java.util.Locale
import kotlin.math.max

@Composable
fun FuelForecastCompactCard(
    state: FuelForecastUiState,
    isLoading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Box(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            if (isLoading && state.historyPoints.isEmpty()) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.LocalGasStation,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    val price = state.historyPoints.lastOrNull()?.priceEurPerL
                    Text(
                        text = if (price != null) "€%.3f".format(price) else "—",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
fun FuelForecastChartCard(
    state: FuelForecastUiState,
    isLoading: Boolean,
    modifier: Modifier = Modifier,
    onRangeSelected: (String) -> Unit = {}
) {
    val isBrent = state.fuelId == "brent"
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (isBrent) "Brent Crude Market" else "Fuel price outlook (rule-based)",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                if (isBrent) {
                    val currentPrice = state.historyPoints.lastOrNull()?.priceEurPerL
                    if (currentPrice != null) {
                        Text(
                            text = "$%.2f".format(currentPrice),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
            Text(
                if (isBrent) "Global market trend for Brent Oil (USD/bbl)." else "Near-you average vs Brent/heating oil/EUR-USD. Not financial advice.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            val fuelLabel = when (state.fuelId) {
                "gazole" -> "Gazole"
                "sp95" -> "SP95"
                "sp95_e10" -> "SP95-E10"
                "sp98" -> "SP98"
                "gplc" -> "GPLc"
                "e85" -> "E85"
                "brent" -> "Brent Crude Oil"
                else -> state.fuelId
            }
            Text(
                if (isBrent) "Commodity: $fuelLabel" else "Fuel: $fuelLabel",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(top = 8.dp)
            )

            when {
                isLoading -> {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                            .padding(top = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.padding(8.dp), strokeWidth = 2.dp)
                    }
                }
                state.errorMessage != null -> Text(
                    state.errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 12.dp)
                )
                else -> {
                    ForecastSparkline(
                        history = state.historyPoints,
                        forecast = state.forecastPoints,
                        national = if (isBrent) emptyList() else state.nationalHistoryPoints,
                        market = if (isBrent) emptyList() else state.marketHistoryPoints,
                        isBrent = isBrent,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(if (isBrent) 220.dp else 180.dp)
                            .padding(top = 12.dp)
                    )

                    if (isBrent) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            listOf("1m", "3m", "6m", "1y").forEach { range ->
                                FilterChip(
                                    selected = state.brentRange == range,
                                    onClick = { onRangeSelected(range) },
                                    label = { Text(range.uppercase()) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (isBrent) {
                            LegendItem("Market Price", MaterialTheme.colorScheme.primary)
                            LegendItem("Momentum Forecast", MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
                        } else {
                            LegendItem("Local", MaterialTheme.colorScheme.primary)
                            LegendItem("National", MaterialTheme.colorScheme.secondary, isDashed = true)
                            LegendItem("Brent (Trend)", Color(0xFFFFA000))
                        }
                    }

                    HorizontalDivider(Modifier.padding(vertical = 12.dp))

                    TomorrowOutlook(state.nextDayPrediction, isBrent = isBrent)

                    val dir = state.directionUp
                    val score = state.marketScore
                    if (dir != null && score != null) {
                        Text(
                            "Market signal: ${if (dir) "upward pressure" else "no strong upward signal"} " +
                                "(score ${String.format(Locale.US, "%+.4f", score)})",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                    val hit = state.accuracyHitRate7d
                    val mae = state.accuracyMae7d
                    if (hit != null && !hit.isNaN()) {
                        val maeStr = if (mae != null && !mae.isNaN()) {
                            String.format(Locale.US, "%.3f", mae)
                        } else "—"
                        Text(
                            "7d accuracy: hit rate ${String.format(Locale.US, "%.0f", hit * 100)}% · MAE $maeStr €/L",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LegendItem(label: String, color: Color, isDashed: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(width = 16.dp, height = 2.dp)
                .clip(MaterialTheme.shapes.extraSmall)
                .background(color)
        )
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun TomorrowOutlook(prediction: PredictionInfo?, isBrent: Boolean = false) {
    Column {
        Text(
            "Tomorrow's Outlook",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Bold
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 4.dp)
        ) {
            if (prediction != null) {
                val color = if (prediction.directionUp) Color(0xFFE53935) else Color(0xFF43A047)
                Icon(
                    imageVector = if (prediction.directionUp) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (isBrent) "$%.2f".format(prediction.predictedPrice) else "€%.3f".format(prediction.predictedPrice),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (isBrent) {
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "bbl",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    text = "%+.2f%%".format(prediction.changePercentage),
                    style = MaterialTheme.typography.titleMedium,
                    color = color,
                    fontWeight = FontWeight.Medium
                )
            } else {
                Text(
                    "Prediction pending...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ForecastSparkline(
    history: List<DailyPricePoint>,
    forecast: List<DailyPricePoint>,
    national: List<DailyPricePoint>,
    market: List<DailyPricePoint>,
    isBrent: Boolean = false,
    modifier: Modifier = Modifier
) {
    val histColor = MaterialTheme.colorScheme.primary
    val foreColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
    val natColor = MaterialTheme.colorScheme.secondary
    val mktColor = Color(0xFFFFA000)
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)

    val histSorted = history.sortedBy { it.day }
    val foreSorted = forecast.sortedBy { it.day }
    val natSorted = national.sortedBy { it.day }
    val mktSorted = market.sortedBy { it.day }

    // All unique dates across all series (including history, forecast, and others)
    val allDays = (histSorted.map { it.day } +
            foreSorted.map { it.day } +
            natSorted.map { it.day } +
            mktSorted.map { it.day }).distinct().sorted()

    if (allDays.isEmpty()) return

    val allPrices = (histSorted + foreSorted + natSorted).map { it.priceEurPerL }
    val yMin = allPrices.minOrNull() ?: (if (isBrent) 70.0 else 1.5)
    val yMax = allPrices.maxOrNull() ?: (if (isBrent) 90.0 else 2.0)
    val pad = max(0.02, (yMax - yMin) * 0.15)
    val ymin = yMin - pad
    val ymax = yMax + pad

    // Brent scaling: fit market series into the same Y range as fuel prices
    val mktPrices = mktSorted.map { it.priceEurPerL }
    val mktMin = mktPrices.minOrNull() ?: 0.0
    val mktMax = mktPrices.maxOrNull() ?: 1.0
    val mktRange = (mktMax - mktMin).coerceAtLeast(0.001)

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val denom = (max(2, allDays.size) - 1).coerceAtLeast(1)

        fun xForDay(day: String): Float {
            val i = allDays.indexOf(day)
            if (i < 0) return 0f
            return w * (i / denom.toFloat()).coerceIn(0f, 1f)
        }
        fun yFor(p: Double): Float {
            val t = ((p - ymin) / (ymax - ymin)).coerceIn(0.0, 1.0)
            return h - t.toFloat() * h
        }
        fun yForMarket(p: Double): Float {
            val normalized = (p - mktMin) / mktRange
            // Use 80% of the chart height for the market trend to avoid overlap with edges
            val t = 0.1 + normalized * 0.8
            return h - t.toFloat() * h
        }

        // Grid
        for (i in 0..4) {
            val y = h * (i / 4f)
            drawLine(gridColor, Offset(0f, y), Offset(w, y), strokeWidth = 1f)
        }

        // 1. Market (Brent) - Orange, subtle trend (if not already drawing Brent)
        if (!isBrent && mktSorted.size >= 2) {
            val path = Path()
            mktSorted.forEachIndexed { i, pt ->
                val x = xForDay(pt.day)
                val y = yForMarket(pt.priceEurPerL)
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, mktColor, alpha = 0.6f, style = Stroke(width = 4f, cap = StrokeCap.Round))
        }

        // 2. National Average - Dashed secondary
        if (natSorted.size >= 2) {
            val path = Path()
            natSorted.forEachIndexed { i, pt ->
                val x = xForDay(pt.day)
                val y = yFor(pt.priceEurPerL)
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(
                path, natColor,
                style = Stroke(
                    width = 3f,
                    cap = StrokeCap.Round,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                )
            )
        }

        // 3. Local History - Solid primary
        if (histSorted.size >= 2) {
            val path = Path()
            histSorted.forEachIndexed { i, pt ->
                val x = xForDay(pt.day)
                val y = yFor(pt.priceEurPerL)
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, histColor, style = Stroke(width = 6f, cap = StrokeCap.Round))
            if (allDays.size < 50) {
                histSorted.forEach { pt ->
                    drawCircle(histColor, radius = 5f, center = Offset(xForDay(pt.day), yFor(pt.priceEurPerL)))
                }
            }
        } else if (histSorted.size == 1) {
            drawCircle(histColor, radius = 6f, center = Offset(xForDay(histSorted[0].day), yFor(histSorted[0].priceEurPerL)))
        }

        // 4. Forecast - Thinner or dotted primary
        if (foreSorted.isNotEmpty()) {
            val pathF = Path()
            var started = false
            if (histSorted.isNotEmpty()) {
                pathF.moveTo(xForDay(histSorted.last().day), yFor(histSorted.last().priceEurPerL))
                started = true
            }
            foreSorted.forEachIndexed { i, pt ->
                val x = xForDay(pt.day)
                val y = yFor(pt.priceEurPerL)
                if (!started) {
                    pathF.moveTo(x, y)
                    started = true
                } else {
                    pathF.lineTo(x, y)
                }
            }
            drawPath(pathF, foreColor, style = Stroke(width = 4f, cap = StrokeCap.Round))
            if (allDays.size < 50) {
                foreSorted.forEach { pt ->
                    drawCircle(foreColor, radius = 4f, center = Offset(xForDay(pt.day), yFor(pt.priceEurPerL)))
                }
            }
        }
    }
}
