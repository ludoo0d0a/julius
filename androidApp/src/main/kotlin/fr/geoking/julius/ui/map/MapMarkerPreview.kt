package fr.geoking.julius.ui.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import fr.geoking.julius.DEFAULT_MAP_ENERGY_TYPES
import fr.geoking.julius.poi.Poi

/** Same marker width as [fr.geoking.julius.ui.MapScreen] / [PoiMarkerHelper]. */
private const val PREVIEW_MARKER_SIZE_PX = 96

/** Fuels + electric so gas (€) and IRVE (kW) labels both resolve in one preview. */
private val PREVIEW_MARKER_ENERGY_TYPES: Set<String> =
    DEFAULT_MAP_ENERGY_TYPES + setOf("electric")

private data class PreviewLatLngBounds(
    val minLat: Double,
    val maxLat: Double,
    val minLng: Double,
    val maxLng: Double,
)

private fun boundsForPois(pois: List<Poi>): PreviewLatLngBounds {
    if (pois.isEmpty()) {
        return PreviewLatLngBounds(48.855, 48.858, 2.350, 2.355)
    }
    val minLat = pois.minOf { it.latitude }
    val maxLat = pois.maxOf { it.latitude }
    val minLng = pois.minOf { it.longitude }
    val maxLng = pois.maxOf { it.longitude }
    val dLat = (maxLat - minLat).coerceAtLeast(0.0005)
    val dLng = (maxLng - minLng).coerceAtLeast(0.0005)
    val pLat = dLat * 0.12
    val pLng = dLng * 0.12
    return PreviewLatLngBounds(minLat - pLat, maxLat + pLat, minLng - pLng, maxLng + pLng)
}

@Composable
private fun MapMarkerPreviewCanvas(
    pois: List<Poi>,
    markerSelectedEnergyTypes: Set<String>,
    effectivePowerLevels: Set<Int>,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val bounds = remember(pois) { boundsForPois(pois) }
    /** Minimum inset from preview edge (frame). */
    val framePadDp = 2.dp

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFFE0F2FE),
                        Color(0xFFBAE6FD),
                        Color(0xFF7DD3FC)
                    )
                )
            )
            val gridColor = Color(0x1A0C4A6E)
            val cols = 5
            val rows = 5
            val sx = size.width / cols
            val sy = size.height / rows
            for (i in 0..cols) {
                drawLine(
                    color = gridColor,
                    start = Offset(i * sx, 0f),
                    end = Offset(i * sx, size.height),
                    strokeWidth = 1f
                )
            }
            for (j in 0..rows) {
                drawLine(
                    color = gridColor,
                    start = Offset(0f, j * sy),
                    end = Offset(size.width, j * sy),
                    strokeWidth = 1f
                )
            }
        }

        val latRange = bounds.maxLat - bounds.minLat
        val lngRange = bounds.maxLng - bounds.minLng

        pois.forEach { poi ->
            key(poi.id) {
                val androidBitmap = remember(poi.id, markerSelectedEnergyTypes, effectivePowerLevels) {
                    PoiMarkerHelper.getMarkerBitmap(
                        context = context,
                        poi = poi,
                        effectiveEnergyTypes = markerSelectedEnergyTypes,
                        effectivePowerLevels = effectivePowerLevels,
                        sizePx = PREVIEW_MARKER_SIZE_PX,
                        markerStyle = MarkerStyle.Circle
                    )
                }
                val imageBitmap = remember(poi.id, markerSelectedEnergyTypes, effectivePowerLevels) {
                    androidBitmap.asImageBitmap()
                }

                val framePadPx = with(density) { framePadDp.toPx() }
                val bw = androidBitmap.width.toFloat()
                val bh = androidBitmap.height.toFloat()
                val boxW = with(density) { maxWidth.toPx() }
                val boxH = with(density) { maxHeight.toPx() }
                // Extra insets so label (top) and pin width don’t clip; bottom anchor keeps tip inside frame.
                val insetHalfW = bw / 2f + with(density) { 2.dp.toPx() }
                val insetTop = bh + with(density) { 2.dp.toPx() }
                val insetBottom = with(density) { 3.dp.toPx() }
                val innerW = (boxW - 2 * framePadPx - 2 * insetHalfW).coerceAtLeast(1f)
                val innerH = (boxH - 2 * framePadPx - insetTop - insetBottom).coerceAtLeast(1f)
                val xBase = framePadPx + insetHalfW +
                    ((poi.longitude - bounds.minLng) / lngRange).toFloat() * innerW
                val yBase = framePadPx + insetTop +
                    (1.0 - (poi.latitude - bounds.minLat) / latRange).toFloat() * innerH
                val xPx = xBase - bw / 2f
                val yPx = yBase - bh
                val xOffsetDp = (xPx / density.density).dp
                val yOffsetDp = (yPx / density.density).dp
                val wDp = (bw / density.density).dp
                val hDp = (bh / density.density).dp

                Image(
                    bitmap = imageBitmap,
                    contentDescription = poi.name,
                    modifier = Modifier
                        .offset(xOffsetDp, yOffsetDp)
                        .width(wDp)
                        .height(hDp)
                )
            }
        }
    }
}

@Composable
private fun MapPoiMarkersPreviewContent(
    pois: List<Poi>,
    markerSelectedEnergyTypes: Set<String>,
    effectivePowerLevels: Set<Int>,
) {
    MapMarkerPreviewCanvas(
        pois = pois,
        markerSelectedEnergyTypes = markerSelectedEnergyTypes,
        effectivePowerLevels = effectivePowerLevels,
        modifier = Modifier.fillMaxSize()
    )
}

@Preview(
    showBackground = true,
    backgroundColor = 0xFF0F172A,
    name = "Map canvas — 6 IRVE (power + range) + 4 fuel",
    widthDp = 440,
    heightDp = 300
)
@Composable
private fun MapPoiMarkersPreviewDiverse() {
    MapPoiMarkersPreviewContent(
        pois = PreviewPoiSamples.diverseMapPois(),
        markerSelectedEnergyTypes = PREVIEW_MARKER_ENERGY_TYPES,
        effectivePowerLevels = PreviewPoiSamples.previewAllIrvePowerLevels,
    )
}
