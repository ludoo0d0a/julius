package fr.geoking.julius.ui.map

import android.content.Context
import android.graphics.*
import android.util.LruCache
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.ContextCompat
import fr.geoking.julius.R
import fr.geoking.julius.poi.Poi
import fr.geoking.julius.poi.PoiCategory
import fr.geoking.julius.poi.MapPoiFilter
import fr.geoking.julius.api.belib.StationAvailabilitySummary
import fr.geoking.julius.ui.BrandHelper
import fr.geoking.julius.ui.ColorHelper

object PoiMarkerHelper {

    private val cache = LruCache<String, Bitmap>(100)

    fun getMarkerBitmap(
        context: Context,
        poi: Poi,
        effectiveEnergyTypes: Set<String>,
        effectivePowerLevels: Set<Int>,
        isSelected: Boolean = false,
        sizePx: Int = 120,
        availability: StationAvailabilitySummary? = null
    ): Bitmap {
        val totalFilters = effectiveEnergyTypes.size + effectivePowerLevels.size
        val showLabel = totalFilters == 1

        val label = if (showLabel) getPoiLabel(poi, effectiveEnergyTypes, effectivePowerLevels) else null
        val brandInfo = BrandHelper.getBrandInfo(poi.brand)
        val category = poi.poiCategory ?: if (poi.isElectric) PoiCategory.Irve else PoiCategory.Gas
        val color = if (showLabel) getPoiColor(poi, category, effectiveEnergyTypes, effectivePowerLevels) else Color.LTGRAY

        val cacheKey = "${poi.id}_${label}_${brandInfo?.iconResId}_${color}_${availability?.availableCount}_${availability?.totalCount}_${sizePx}_${isSelected}"
        synchronized(cache) {
            cache.get(cacheKey)?.let { return it }
        }

        val scale = if (isSelected) 1.2f else 1.0f
        val actualSize = (sizePx * scale).toInt()

        val bitmap = Bitmap.createBitmap(actualSize, actualSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        val padding = actualSize * 0.1f
        val contentSize = actualSize - 2 * padding

        // Shape logic
        val isFuel = poi.fuelPrices?.isNotEmpty() == true
        val isElectric = poi.isElectric

        val centerX = actualSize / 2f
        val centerY = actualSize / 2f

        // Draw Shadow
        paint.setShadowLayer(if (isSelected) 8f else 4f, 0f, 2f, 0x40000000)
        paint.color = Color.WHITE
        paint.style = Paint.Style.FILL

        val shapePath = Path()
        when {
            isFuel && isElectric -> {
                // Triangle pointing down
                shapePath.moveTo(centerX, actualSize - padding)
                shapePath.lineTo(padding, padding)
                shapePath.lineTo(actualSize - padding, padding)
                shapePath.close()
            }
            isElectric -> {
                // Square
                shapePath.addRect(padding, padding, actualSize - padding, actualSize - padding, Path.Direction.CW)
            }
            else -> {
                // Circle
                shapePath.addCircle(centerX, centerY, contentSize / 2f, Path.Direction.CW)
            }
        }
        canvas.drawPath(shapePath, paint)
        paint.clearShadowLayer()

        // Draw Shape Fill (White)
        paint.color = Color.WHITE
        canvas.drawPath(shapePath, paint)

        // Draw Color Strike (Border)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = if (isSelected) 6f else 4f
        paint.color = if (showLabel) color else Color.LTGRAY
        canvas.drawPath(shapePath, paint)

        // Draw Highlight if selected
        if (isSelected) {
            paint.strokeWidth = 2f
            paint.color = Color.WHITE
            canvas.drawPath(shapePath, paint)
        }

        // Draw Brand Icon inside
        val brandResId = brandInfo?.iconResId
        if (brandResId != null) {
            val iconSize = (contentSize * 0.6f).toInt()
            val brandIcon = vectorToBitmap(context, brandResId, iconSize)
            if (brandIcon != null) {
                canvas.drawBitmap(brandIcon, centerX - iconSize / 2f, centerY - iconSize / 2f, null)
            }
        } else {
            // Fallback to category icon
            val iconSize = (contentSize * 0.5f).toInt()
            val categoryIconRes = if (poi.isElectric) R.drawable.ic_poi_electric else R.drawable.ic_poi_gas
            val categoryIcon = vectorToBitmap(context, categoryIconRes, iconSize)
            if (categoryIcon != null) {
                canvas.drawBitmap(categoryIcon, centerX - iconSize / 2f, centerY - iconSize / 2f, null)
            }
        }

        // Draw Label Pill above if showLabel is true
        if (showLabel && !label.isNullOrEmpty()) {
            paint.style = Paint.Style.FILL
            paint.color = color
            paint.textSize = actualSize * 0.18f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            val textWidth = paint.measureText(label)
            val pillPaddingH = paint.textSize * 0.4f
            val pillPaddingV = paint.textSize * 0.2f
            val pillWidth = textWidth + 2 * pillPaddingH
            val pillHeight = paint.textSize + 2 * pillPaddingV

            val pillRect = RectF(
                centerX - pillWidth / 2f,
                padding - pillHeight / 2f,
                centerX + pillWidth / 2f,
                padding + pillHeight / 2f
            )

            // Draw pill shadow
            val shadowPaint = Paint(paint)
            shadowPaint.setShadowLayer(4f, 0f, 2f, 0x40000000)
            canvas.drawRoundRect(pillRect, pillHeight / 2f, pillHeight / 2f, shadowPaint)

            canvas.drawRoundRect(pillRect, pillHeight / 2f, pillHeight / 2f, paint)

            paint.color = Color.WHITE
            val fm = paint.fontMetrics
            val textY = pillRect.centerY() - (fm.ascent + fm.descent) / 2f
            canvas.drawText(label, centerX - textWidth / 2f, textY, paint)
        }

        synchronized(cache) {
            cache.put(cacheKey, bitmap)
        }
        return bitmap
    }

    private fun getPoiLabel(
        poi: Poi,
        effectiveEnergyTypes: Set<String>,
        effectivePowerLevels: Set<Int>
    ): String? {
        if (poi.poiCategory == PoiCategory.Radar) {
            val regex = Regex("""(\d+)""")
            return regex.find(poi.name)?.value
        }

        if (effectiveEnergyTypes.isNotEmpty()) {
            val energyId = effectiveEnergyTypes.first()
            if (energyId == "electric") {
                 return poi.powerKw?.let { "${it.toInt()}kW" }
            } else {
                val price = poi.fuelPrices?.find { MapPoiFilter.fuelNameToId(it.fuelName) == energyId }?.price
                return price?.let { "€%.2f".format(it) }
            }
        }

        if (effectivePowerLevels.isNotEmpty()) {
            return poi.powerKw?.let { "${it.toInt()}kW" }
        }

        return null
    }

    private fun getPoiColor(
        poi: Poi,
        category: PoiCategory,
        effectiveEnergyTypes: Set<String>,
        effectivePowerLevels: Set<Int>
    ): Int {
        if (effectiveEnergyTypes.isNotEmpty()) {
            val energyId = effectiveEnergyTypes.first()
            if (energyId == "electric") {
                return poi.powerKw?.let { ColorHelper.getPowerColor(it).toArgb() } ?: 0xFF28A745.toInt()
            } else {
                return ColorHelper.getFuelColor(energyId)?.toArgb() ?: 0xFF007BFF.toInt()
            }
        }

        if (effectivePowerLevels.isNotEmpty()) {
             return poi.powerKw?.let { ColorHelper.getPowerColor(it).toArgb() } ?: 0xFF28A745.toInt()
        }

        return when (category) {
            PoiCategory.Radar -> 0xFFDC3545.toInt()
            else -> Color.LTGRAY
        }
    }

    private fun vectorToBitmap(context: Context, drawableId: Int, size: Int): Bitmap? {
        val drawable = ContextCompat.getDrawable(context, drawableId) ?: return null
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, size, size)
        drawable.draw(canvas)
        return bitmap
    }
}
