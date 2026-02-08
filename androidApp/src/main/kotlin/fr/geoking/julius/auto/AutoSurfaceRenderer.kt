package fr.geoking.julius.auto

import android.view.Surface
import fr.geoking.julius.ui.anim.auto.AutoParticlesEffect
import fr.geoking.julius.ui.anim.auto.AutoSphereEffect
import fr.geoking.julius.ui.anim.auto.AutoWavesEffect
import kotlin.math.PI
import kotlin.math.sin

/**
 * Renders the 3 Auto animations (Particles, Sphere, Waves) onto an Android Auto Surface.
 * Runs on a dedicated thread; cycles through effects every 15s.
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
    private val centerX: Float = width / 2f
    private val centerY: Float = height / 2f

    private val particlesEffect = AutoParticlesEffect(
        particleCount = PARTICLE_COUNT,
        rayCount = RAY_COUNT
    )

    private val drawThread = Thread(::runDrawLoop, "AutoSurfaceRenderer")

    fun start() {
        if (!drawThread.isAlive) drawThread.start()
    }

    fun stop() {
        running = false
        drawThread.join(500)
    }

    private fun runDrawLoop() {
        val startMs = System.currentTimeMillis()
        while (running) {
            val canvas = surface.lockCanvas(null) ?: break
            try {
                val elapsed = (System.currentTimeMillis() - startMs) / 1000f
                val effectIndex = ((System.currentTimeMillis() / EFFECT_CYCLE_MS) % 3).toInt()
                val time = (System.currentTimeMillis() % 20_000L) / 20_000f
                val timeRotation = (System.currentTimeMillis() % 15_000L) / 15_000f * 360f
                val pulse = 0.5f + 0.5f * sin((System.currentTimeMillis() % 2000L) / 2000f * 2 * PI.toFloat())

                when (effectIndex) {
                    0 -> particlesEffect.draw(
                        canvas, width, height, centerX, centerY,
                        isActive, time, timeRotation, pulse
                    )
                    1 -> AutoSphereEffect.draw(
                        canvas, width, height, centerX, centerY,
                        isActive, timeRotation, pulse
                    )
                    2 -> AutoWavesEffect.draw(
                        canvas, width, height, centerX, centerY,
                        isActive, elapsed, pulse
                    )
                }
            } finally {
                try {
                    surface.unlockCanvasAndPost(canvas)
                } catch (_: Exception) { }
            }
            try { Thread.sleep(16) } catch (_: InterruptedException) { break }
        }
    }

    companion object {
        private const val PARTICLE_COUNT = 50
        private const val RAY_COUNT = 8
        private const val EFFECT_CYCLE_MS = 15_000L
    }
}
