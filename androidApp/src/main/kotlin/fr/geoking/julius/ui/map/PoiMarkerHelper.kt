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
import fr.geoking.julius.ui.BrandHelper
import fr.geoking.julius.ui.ColorHelper
import kotlin.math.cos
import kotlin.math.sin

object PoiMarkerHelper {

    private val cache = LruCache<String, Bitmap>(100)

    fun getMarkerBitmap(
        context: Context,
        poi: Poi,
        selectedEnergyTypes: Set<String>,
        useVehicleFilter: Boolean,
        vehicleEnergy: String,
        vehicleGasTypes: Set<String>,
        sizePx: Int = 100
    ): Bitmap {
        val label = getPoiLabel(poi, selectedEnergyTypes, useVehicleFilter, vehicleEnergy, vehicleGasTypes)
        val brandInfo = BrandHelper.getBrandInfo(poi.brand)
        val iconResId = getIconResId(poi, brandInfo)
        val category = poi.poiCategory ?: if (poi.isElectric) PoiCategory.Irve else PoiCategory.Gas
        val color = getPoiColor(poi, category, selectedEnergyTypes, useVehicleFilter, vehicleEnergy, vehicleGasTypes)

        val cacheKey = "${poi.id}_${label}_${iconResId}_${color}_$sizePx"
        synchronized(cache) {
            cache.get(cacheKey)?.let { return it }
        }

        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // 1. Draw Shape Background
        paint.color = color
        paint.style = Paint.Style.FILL
        drawShape(canvas, paint, category, sizePx)

        // 2. Draw Icon
        val iconSize = (sizePx * 0.45).toInt()
        val iconBitmap = vectorToBitmap(context, iconResId, iconSize)
        if (iconBitmap != null) {
            val left = (sizePx - iconSize) / 2f
            val top = sizePx * 0.15f
            canvas.drawBitmap(iconBitmap, left, top, null)
        }

        // 3. Draw Label
        if (!label.isNullOrEmpty()) {
            paint.color = Color.WHITE
            paint.textSize = sizePx * 0.22f
            paint.textAlign = Paint.Align.CENTER
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)

            val textX = sizePx / 2f
            val textY = sizePx * 0.85f
            canvas.drawText(label, textX, textY, paint)
        }

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
                price?.let { "%.2f".format(it) }
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

    private fun drawShape(canvas: Canvas, paint: Paint, category: PoiCategory, size: Int) {
        val s = size.toFloat()
        when (category) {
            PoiCategory.Gas -> {
                canvas.drawCircle(s / 2, s / 2, s / 2, paint)
            }
            PoiCategory.Irve -> {
                val rect = RectF(0f, 0f, s, s)
                canvas.drawRoundRect(rect, s * 0.2f, s * 0.2f, paint)
            }
            PoiCategory.Radar -> {
                val path = createPolygonPath(s / 2, s / 2, s / 2, 6) // Hexagon
                canvas.drawPath(path, paint)
            }
            else -> {
                val path = createPolygonPath(s / 2, s / 2, s / 2, 5) // Pentagon
                canvas.drawPath(path, paint)
            }
        }
    }

    private fun createPolygonPath(cx: Float, cy: Float, radius: Float, sides: Int): Path {
        val path = Path()
        val angle = 2.0 * Math.PI / sides
        path.moveTo(
            cx + (radius * cos(0.0)).toFloat(),
            cy + (radius * sin(0.0)).toFloat()
        )
        for (i in 1 until sides) {
            path.lineTo(
                cx + (radius * cos(angle * i)).toFloat(),
                cy + (radius * sin(angle * i)).toFloat()
            )
        }
        path.close()
        return path
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
