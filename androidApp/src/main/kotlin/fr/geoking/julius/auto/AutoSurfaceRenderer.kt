package fr.geoking.julius.auto

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.content.Context
import android.graphics.Paint
import android.util.Log
import android.util.LruCache
import android.view.Surface
import fr.geoking.julius.poi.Poi
import fr.geoking.julius.ui.map.PoiMarkerHelper
import java.net.HttpURLConnection
import java.net.URL
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import kotlin.math.*

/**
 * Map renderer for Android Auto surface using OpenStreetMap tiles.
 *
 * It uses an LRU cache for bitmaps and a fixed thread pool to fetch tiles efficiently.
 */
class AutoSurfaceRenderer(
    private val context: Context,
    private val surface: Surface,
    private val width: Int,
    private val height: Int
) {
    @Volatile
    private var running = true
    private var lat: Double = 48.8566
    private var lon: Double = 2.3522
    private var zoom: Int = 13

    private var pois: List<Poi> = emptyList()
    private var effectiveEnergyTypes: Set<String> = emptySet()
    private var effectivePowerLevels: Set<Int> = emptySet()

    // Cache up to 50 tile bitmaps (approx 50 * 256*256*4 bytes ~ 12MB)
    private val tileCache = LruCache<String, Bitmap>(50)
    // Tracks active network requests to avoid duplicates
    private val pendingRequests = Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())
    // Fixed thread pool for network I/O
    private val executor = Executors.newFixedThreadPool(4)

    private val backgroundPaint = Paint().apply { color = Color.LTGRAY }
    private val drawThread = Thread(::runDrawLoop, "AutoSurfaceRenderer")

    fun start() {
        if (!drawThread.isAlive) drawThread.start()
    }

    fun stop() {
        running = false
        executor.shutdownNow()
        try { drawThread.join(500) } catch (_: Exception) {}
    }

    fun updateLocation(newLat: Double, newLon: Double, newZoom: Int = 13) {
        lat = newLat
        lon = newLon
        zoom = newZoom
    }

    fun updatePois(
        newPois: List<Poi>,
        effectiveEnergyTypes: Set<String>,
        effectivePowerLevels: Set<Int>
    ) {
        this.pois = newPois
        this.effectiveEnergyTypes = effectiveEnergyTypes
        this.effectivePowerLevels = effectivePowerLevels
    }

    private fun runDrawLoop() {
        while (running) {
            val canvas = try { surface.lockCanvas(null) } catch (_: Exception) { null } ?: break
            try {
                drawMap(canvas)
                drawPois(canvas)
            } finally {
                try { surface.unlockCanvasAndPost(canvas) } catch (_: Exception) {}
            }
            try { Thread.sleep(100) } catch (_: InterruptedException) { break }
        }
    }

    private fun drawMap(canvas: Canvas) {
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)

        val tileSize = 256
        val centerX = lonToTileX(lon, zoom)
        val centerY = latToTileY(lat, zoom)

        val startTileX = floor(centerX - (width / 2.0) / tileSize).toInt()
        val endTileX = ceil(centerX + (width / 2.0) / tileSize).toInt()
        val startTileY = floor(centerY - (height / 2.0) / tileSize).toInt()
        val endTileY = ceil(centerY + (height / 2.0) / tileSize).toInt()

        for (x in startTileX..endTileX) {
            for (y in startTileY..endTileY) {
                val bitmap = getTile(x, y, zoom)
                if (bitmap != null) {
                    val drawX = ((x - centerX) * tileSize + width / 2.0).toFloat()
                    val drawY = ((y - centerY) * tileSize + height / 2.0).toFloat()
                    canvas.drawBitmap(bitmap, drawX, drawY, null)
                }
            }
        }
    }

    private fun drawPois(canvas: Canvas) {
        val tileSize = 256
        val centerX = lonToTileX(lon, zoom)
        val centerY = latToTileY(lat, zoom)

        // Larger marker makes the price/power badge readable at a glance.
        val markerSize = 72

        pois.forEach { poi ->
            val tileX = lonToTileX(poi.longitude, zoom)
            val tileY = latToTileY(poi.latitude, zoom)

            val drawX = ((tileX - centerX) * tileSize + width / 2.0).toFloat()
            val drawY = ((tileY - centerY) * tileSize + height / 2.0).toFloat()

            // Skip if outside viewport
            if (drawX < -markerSize || drawX > width + markerSize || drawY < -markerSize || drawY > height + markerSize) {
                return@forEach
            }

            val bitmap = PoiMarkerHelper.getMarkerBitmap(
                context = context,
                poi = poi,
                effectiveEnergyTypes = effectiveEnergyTypes,
                effectivePowerLevels = effectivePowerLevels,
                isSelected = false,
                sizePx = markerSize,
                availability = null // Surface renderer doesn't have easy access to availability yet
            )

            canvas.drawBitmap(bitmap, drawX - markerSize / 2f, drawY - markerSize / 2f, null)
        }
    }

    private fun getTile(x: Int, y: Int, z: Int): Bitmap? {
        val key = "$z/$x/$y"
        synchronized(tileCache) {
            tileCache.get(key)?.let { return it }
        }

        if (pendingRequests.add(key)) {
            executor.submit {
                try {
                    val url = URL("https://tile.openstreetmap.org/$z/$x/$y.png")
                    val connection = url.openConnection() as HttpURLConnection
                    connection.setRequestProperty("User-Agent", "Julius-Android-Auto/1.0")
                    connection.connectTimeout = 5000
                    connection.readTimeout = 5000
                    connection.connect()
                    if (connection.responseCode == 200) {
                        val bitmap = BitmapFactory.decodeStream(connection.inputStream)
                        if (bitmap != null) {
                            synchronized(tileCache) {
                                tileCache.put(key, bitmap)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("AutoSurfaceRenderer", "Failed to fetch tile $key", e)
                } finally {
                    pendingRequests.remove(key)
                }
            }
        }

        return null
    }

    private fun lonToTileX(lon: Double, zoom: Int): Double =
        (lon + 180.0) / 360.0 * (1 shl zoom)

    private fun latToTileY(lat: Double, zoom: Int): Double {
        val latRad = Math.toRadians(lat)
        return (1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / PI) / 2.0 * (1 shl zoom)
    }
}
