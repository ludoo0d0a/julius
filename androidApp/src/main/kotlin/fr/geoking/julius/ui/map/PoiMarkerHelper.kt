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
        selectedEnergyTypes: Set<String>,
        useVehicleFilter: Boolean,
        vehicleEnergy: String,
        vehicleGasTypes: Set<String>,
        sizePx: Int = 120,
        availability: StationAvailabilitySummary? = null
    ): Bitmap {
        val label = getPoiLabel(poi, selectedEnergyTypes, useVehicleFilter, vehicleEnergy, vehicleGasTypes)
        val brandInfo = BrandHelper.getBrandInfo(poi.brand)
        val category = poi.poiCategory ?: if (poi.isElectric) PoiCategory.Irve else PoiCategory.Gas
        val color = getPoiColor(poi, category, selectedEnergyTypes, useVehicleFilter, vehicleEnergy, vehicleGasTypes)

        val cacheKey = "${poi.id}_${label}_${brandInfo?.iconResId}_${color}_${availability?.availableCount}_${availability?.totalCount}_$sizePx"
        synchronized(cache) {
            cache.get(cacheKey)?.let { return it }
        }

        // Bitmap is square, but we'll draw a slightly wider bubble to fit content.
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        val padding = sizePx * 0.05f
        val bubbleWidth = sizePx - 2 * padding
        val bubbleHeight = (sizePx - 2 * padding) * 0.85f // Leave room for the tail
        val cornerRadius = bubbleHeight * 0.25f

        val rect = RectF(padding, padding, padding + bubbleWidth, padding + bubbleHeight)

        // 1. Draw Shadow for the bubble
        paint.setShadowLayer(4f, 0f, 2f, 0x40000000)
        paint.color = Color.WHITE
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, paint)
        paint.clearShadowLayer()

        // 2. Draw Tail
        val tailWidth = bubbleWidth * 0.2f
        val tailHeight = sizePx * 0.15f
        val path = Path()
        path.moveTo(sizePx / 2f - tailWidth / 2f, padding + bubbleHeight - 2f) // Overlap slightly to avoid gaps
        path.lineTo(sizePx / 2f, sizePx - padding)
        path.lineTo(sizePx / 2f + tailWidth / 2f, padding + bubbleHeight - 2f)
        path.close()
        paint.color = Color.WHITE
        canvas.drawPath(path, paint)

        // 3. Draw Header (Colored part)
        val headerHeight = bubbleHeight * 0.45f
        val headerRect = RectF(padding, padding, padding + bubbleWidth, padding + headerHeight)
        val headerPath = Path()
        headerPath.addRoundRect(rect, cornerRadius, cornerRadius, Path.Direction.CW)
        canvas.save()
        canvas.clipPath(headerPath)
        canvas.clipRect(headerRect)
        paint.color = color
        canvas.drawRect(headerRect, paint)

        // 3.1 Header Content (Label & Category Icon)
        if (!label.isNullOrEmpty()) {
            paint.color = Color.WHITE
            paint.textSize = headerHeight * 0.55f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            paint.textAlign = Paint.Align.LEFT

            val labelWidth = paint.measureText(label)
            val iconSize = headerHeight * 0.5f
            val spacing = headerHeight * 0.15f
            val totalWidth = labelWidth + spacing + iconSize

            val startX = padding + (bubbleWidth - totalWidth) / 2f
            val fm = paint.fontMetrics
            val textY = padding + headerHeight / 2f - (fm.ascent + fm.descent) / 2f

            canvas.drawText(label, startX, textY, paint)

            val categoryIconRes = if (poi.isElectric) R.drawable.ic_poi_electric else R.drawable.ic_poi_gas
            val categoryIcon = vectorToBitmap(context, categoryIconRes, iconSize.toInt())
            if (categoryIcon != null) {
                canvas.drawBitmap(categoryIcon, startX + labelWidth + spacing, padding + (headerHeight - iconSize) / 2f, null)
            }
        }
        canvas.restore()

        // 4. Draw Body Content (Brand Icon & Availability)
        val bodyTop = padding + headerHeight
        val bodyHeight = bubbleHeight - headerHeight
        val availText = availability?.let { "${it.availableCount} / ${it.totalCount}" }
        val brandResId = brandInfo?.iconResId // Use non-rounded icon as background is already white/shaped

        if (brandResId != null) {
            val brandIconSize = (bodyHeight * (if (availText != null) 0.55f else 0.75f)).toInt()
            val brandIcon = vectorToBitmap(context, brandResId, brandIconSize)
            if (brandIcon != null) {
                val left = padding + (bubbleWidth - brandIconSize) / 2f
                val top = if (availText != null) bodyTop + bodyHeight * 0.1f else bodyTop + (bodyHeight - brandIconSize) / 2f
                canvas.drawBitmap(brandIcon, left, top, null)
            }
        } else if (availText == null) {
            // Fallback if nothing to show in body: show category icon again but larger
            val iconSize = (bodyHeight * 0.7f).toInt()
            val categoryIconRes = if (poi.isElectric) R.drawable.ic_poi_electric_rounded else R.drawable.ic_poi_gas_rounded
            val fallbackIcon = vectorToBitmap(context, categoryIconRes, iconSize)
            if (fallbackIcon != null) {
                canvas.drawBitmap(fallbackIcon, padding + (bubbleWidth - iconSize) / 2f, bodyTop + (bodyHeight - iconSize) / 2f, null)
            }
        }

        if (availText != null) {
            paint.color = Color.BLACK
            paint.textSize = bodyHeight * 0.35f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            paint.textAlign = Paint.Align.CENTER
            val fm = paint.fontMetrics
            val textY = padding + bubbleHeight - bodyHeight * 0.1f - fm.descent
            canvas.drawText(availText, sizePx / 2f, textY, paint)
        }

        // 5. Border
        paint.style = Paint.Style.STROKE
        paint.color = Color.LTGRAY
        paint.strokeWidth = 1f
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, paint)
        canvas.drawPath(path, paint)

        synchronized(cache) {
            cache.put(cacheKey, bitmap)
        }
        return bitmap
    }

    private fun getPoiLabel(
        poi: Poi,
        selectedEnergyTypes: Set<String>,
        useVehicleFilter: Boolean,
        vehicleEnergy: String,
        vehicleGasTypes: Set<String>
    ): String? {
        return when (poi.poiCategory) {
            PoiCategory.Radar -> {
                val regex = Regex("""(\d+)""")
                regex.find(poi.name)?.value?.let { "$it" } // Just the number to save space
            }
            PoiCategory.Irve -> {
                poi.powerKw?.let { "${it.toInt()}kW" }
            }
            PoiCategory.Gas -> {
                val preferredEnergies = if (useVehicleFilter) {
                    if (vehicleEnergy == "hybrid") vehicleGasTypes + "electric"
                    else vehicleGasTypes
                } else selectedEnergyTypes
                val price = poi.fuelPrices?.filter { p ->
                    val id = MapPoiFilter.fuelNameToId(p.fuelName)
                    id != null && (preferredEnergies.isEmpty() || id in preferredEnergies)
                }?.minByOrNull { it.price }?.price
                price?.let { "€%.2f".format(it) }
            }
            else -> null
        }
    }

    private fun getPoiColor(
        poi: Poi,
        category: PoiCategory,
        selectedEnergyTypes: Set<String>,
        useVehicleFilter: Boolean,
        vehicleEnergy: String,
        vehicleGasTypes: Set<String>
    ): Int {
        return when (category) {
            PoiCategory.Irve -> {
                poi.powerKw?.let { ColorHelper.getPowerColor(it).toArgb() } ?: 0xFF28A745.toInt()
            }
            PoiCategory.Gas -> {
                val preferredEnergies = if (useVehicleFilter) {
                    if (vehicleEnergy == "hybrid") vehicleGasTypes + "electric"
                    else vehicleGasTypes
                } else selectedEnergyTypes

                val cheapestFuelId = poi.fuelPrices?.filter { p ->
                    val id = MapPoiFilter.fuelNameToId(p.fuelName)
                    id != null && (preferredEnergies.isEmpty() || id in preferredEnergies)
                }?.minByOrNull { it.price }?.let { MapPoiFilter.fuelNameToId(it.fuelName) }

                cheapestFuelId?.let { ColorHelper.getFuelColor(it)?.toArgb() } ?: 0xFF007BFF.toInt()
            }
            PoiCategory.Radar -> 0xFFDC3545.toInt() // Red
            else -> 0xFF17A2B8.toInt() // Teal
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
