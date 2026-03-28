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

    enum class MarkerStyle { Circle, Bubble }

    private val cache = LruCache<String, Bitmap>(100)

    fun getMarkerBitmap(
        context: Context,
        poi: Poi,
        selectedEnergyTypes: Set<String>,
        useVehicleFilter: Boolean,
        vehicleEnergy: String,
        vehicleGasTypes: Set<String>,
        sizePx: Int = 120,
        availability: StationAvailabilitySummary? = null,
        style: MarkerStyle = MarkerStyle.Bubble
    ): Bitmap {
        val label = getPoiLabel(poi, selectedEnergyTypes, useVehicleFilter, vehicleEnergy, vehicleGasTypes)
        val brandInfo = BrandHelper.getBrandInfo(poi.brand)
        val category = poi.poiCategory ?: if (poi.isElectric) PoiCategory.Irve else PoiCategory.Gas
        val color = getPoiColor(poi, category, selectedEnergyTypes, useVehicleFilter, vehicleEnergy, vehicleGasTypes)
        val iconResId = getIconResId(poi, brandInfo)

        val cacheKey = "${poi.id}_${label}_${iconResId}_${color}_${availability?.availableCount}_${availability?.totalCount}_${style.name}_$sizePx"
        synchronized(cache) {
            cache.get(cacheKey)?.let { return it }
        }

        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        when (style) {
            MarkerStyle.Circle -> drawCircleMarker(context, canvas, paint, color, iconResId, label, sizePx)
            MarkerStyle.Bubble -> drawBubbleMarker(context, canvas, paint, color, iconResId, label, brandInfo?.iconResId, availability, sizePx, poi.isElectric)
        }

        synchronized(cache) {
            cache.put(cacheKey, bitmap)
        }
        return bitmap
    }

    private fun drawCircleMarker(
        context: Context,
        canvas: Canvas,
        paint: Paint,
        color: Int,
        iconResId: Int,
        label: String?,
        sizePx: Int
    ) {
        val s = sizePx.toFloat()
        // 1. Draw Shape Background
        paint.color = color
        paint.style = Paint.Style.FILL
        canvas.drawCircle(s / 2, s / 2, s / 2, paint)

        // 2. Draw Icon
        // Icon fits at 90% in colored circles.
        val iconSize = (sizePx * 0.90).toInt()
        val iconBitmap = vectorToBitmap(context, iconResId, iconSize)
        if (iconBitmap != null) {
            val left = (sizePx - iconSize) / 2f
            val top = (sizePx - iconSize) / 2f
            canvas.drawBitmap(iconBitmap, left, top, null)
        }

        // 3. Draw Label (price / power) directly upon the icon.
        if (!label.isNullOrEmpty()) {
            val textSizePx = sizePx * 0.26f
            paint.color = Color.WHITE
            paint.textSize = textSizePx
            paint.textAlign = Paint.Align.CENTER
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            // Add shadow to ensure readability on different icon backgrounds.
            paint.setShadowLayer(3f, 0f, 0f, Color.BLACK)

            val textX = sizePx / 2f
            val textCenterY = sizePx / 2f

            // Center the text using font metrics.
            val fm = paint.fontMetrics
            val textY = textCenterY - (fm.ascent + fm.descent) / 2f
            canvas.drawText(label, textX, textY, paint)
        }
    }

    private fun drawBubbleMarker(
        context: Context,
        canvas: Canvas,
        paint: Paint,
        color: Int,
        categoryIconResId: Int,
        label: String?,
        brandIconResId: Int?,
        availability: StationAvailabilitySummary?,
        sizePx: Int,
        isElectric: Boolean
    ) {
        val padding = sizePx * 0.05f
        val bubbleWidth = sizePx - 2 * padding
        val bubbleHeight = (sizePx - 2 * padding) * 0.85f // Leave room for the tail
        val cornerRadius = bubbleHeight * 0.25f

        val rect = RectF(padding, padding, padding + bubbleWidth, padding + bubbleHeight)

        // 1. Draw Shadow for the bubble
        paint.setShadowLayer(4f, 0f, 2f, 0x40000000)
        paint.color = Color.WHITE
        paint.style = Paint.Style.FILL
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

        // 3.1 Header Content (Label & Small Category Icon)
        paint.color = Color.WHITE
        paint.textSize = headerHeight * 0.55f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textAlign = Paint.Align.LEFT

        val iconSize = headerHeight * 0.5f
        val spacing = if (!label.isNullOrEmpty()) headerHeight * 0.15f else 0f
        val labelWidth = if (!label.isNullOrEmpty()) paint.measureText(label) else 0f
        val totalWidth = labelWidth + spacing + iconSize

        val startX = padding + (bubbleWidth - totalWidth) / 2f
        val fm = paint.fontMetrics
        val textY = padding + headerHeight / 2f - (fm.ascent + fm.descent) / 2f

        if (!label.isNullOrEmpty()) {
            canvas.drawText(label, startX, textY, paint)
        }

        val headerIconRes = if (isElectric) R.drawable.ic_poi_electric else R.drawable.ic_poi_gas
        val headerIcon = vectorToBitmap(context, headerIconRes, iconSize.toInt())
        if (headerIcon != null) {
            val iconLeft = startX + labelWidth + spacing
            canvas.drawBitmap(headerIcon, iconLeft, padding + (headerHeight - iconSize) / 2f, null)
        }
        canvas.restore()

        // 4. Draw Body Content (Brand Icon & Availability)
        val bodyTop = padding + headerHeight
        val bodyHeight = bubbleHeight - headerHeight
        val availText = availability?.let { "${it.availableCount} / ${it.totalCount}" }
        val bodyIconResId = brandIconResId ?: categoryIconResId

        val bodyIconSize = (bodyHeight * (if (availText != null) 0.55f else 0.75f)).toInt()
        val bodyIcon = vectorToBitmap(context, bodyIconResId, bodyIconSize)
        if (bodyIcon != null) {
            val left = padding + (bubbleWidth - bodyIconSize) / 2f
            val top = if (availText != null) bodyTop + bodyHeight * 0.1f else bodyTop + (bodyHeight - bodyIconSize) / 2f
            canvas.drawBitmap(bodyIcon, left, top, null)
        }

        if (availText != null) {
            paint.color = Color.BLACK
            paint.textSize = bodyHeight * 0.35f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            paint.textAlign = Paint.Align.CENTER
            val fmAvail = paint.fontMetrics
            val textYAvail = padding + bubbleHeight - bodyHeight * 0.1f - fmAvail.descent
            canvas.drawText(availText, sizePx / 2f, textYAvail, paint)
        }

        // 5. Border
        paint.style = Paint.Style.STROKE
        paint.color = Color.LTGRAY
        paint.strokeWidth = 1f
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, paint)
        canvas.drawPath(path, paint)
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

    private fun getIconResId(poi: Poi, brandInfo: BrandHelper.BrandInfo?): Int {
        brandInfo?.roundedIconResId?.let { return it }
        return when (poi.poiCategory) {
            PoiCategory.Toilet -> R.drawable.ic_poi_toilet_rounded
            PoiCategory.DrinkingWater -> R.drawable.ic_poi_water_rounded
            PoiCategory.Camping -> R.drawable.ic_poi_camping_rounded
            PoiCategory.CaravanSite -> R.drawable.ic_poi_caravan_rounded
            PoiCategory.PicnicSite -> R.drawable.ic_poi_picnic_rounded
            PoiCategory.Radar -> R.drawable.ic_poi_radar_rounded
            else -> if (poi.isElectric) R.drawable.ic_poi_electric_rounded else R.drawable.ic_poi_gas_rounded
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
