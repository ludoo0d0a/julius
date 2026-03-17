package fr.geoking.julius.auto

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.Surface

/**
 * Placeholder map renderer for Android Auto surface.
 *
 * This version does NOT render real map tiles yet; it just draws a dark
 * background with a simple grid to visualize the surface. It is intentionally
 * minimal so it can be replaced later by a proper tile-based map renderer.
 */
class AutoSurfaceRenderer(
    private val surface: Surface,
    width: Int,
    height: Int
) {
    @Volatile
    private var running = true

    @Volatile
    var isActive: Boolean = false
        set(value) { field = value }

    private val width: Int = width.coerceAtLeast(1)
    private val height: Int = height.coerceAtLeast(1)

    private val backgroundPaint = Paint().apply {
        color = Color.rgb(10, 15, 20)
        style = Paint.Style.FILL
    }

    private val gridPaint = Paint().apply {
        color = Color.rgb(40, 60, 80)
        style = Paint.Style.STROKE
        strokeWidth = 1f
    }

    private val activePaint = Paint().apply {
        color = Color.rgb(80, 200, 255)
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }

    private val drawThread = Thread(::runDrawLoop, "AutoSurfaceRenderer")

    fun start() {
        if (!drawThread.isAlive) drawThread.start()
    }

    fun stop() {
        running = false
        drawThread.join(500)
    }

    private fun runDrawLoop() {
        while (running) {
            val canvas = surface.lockCanvas(null) ?: break
            try {
                drawPlaceholderMap(canvas)
            } finally {
                try {
                    surface.unlockCanvasAndPost(canvas)
                } catch (_: Exception) { }
            }
            try { Thread.sleep(16) } catch (_: InterruptedException) { break }
        }
    }

    companion object {
        private const val FRAME_DELAY_MS = 33L
    }

    private fun drawPlaceholderMap(canvas: Canvas) {
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)

        val step = 80
        var x = 0
        while (x <= width) {
            canvas.drawLine(x.toFloat(), 0f, x.toFloat(), height.toFloat(), gridPaint)
            x += step
        }
        var y = 0
        while (y <= height) {
            canvas.drawLine(0f, y.toFloat(), width.toFloat(), y.toFloat(), gridPaint)
            y += step
        }

        if (isActive) {
            canvas.drawRect(
                10f,
                10f,
                width.toFloat() - 10f,
                height.toFloat() - 10f,
                activePaint
            )
        }
    }
}
