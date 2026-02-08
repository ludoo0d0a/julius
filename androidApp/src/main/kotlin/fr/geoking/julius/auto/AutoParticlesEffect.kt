package fr.geoking.julius.auto

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Particles + rays effect for Android Auto surface (ported from Compose ParticleEffectCanvas).
 */
class AutoParticlesEffect(
    particleCount: Int = 50,
    rayCount: Int = 8
) {
    private val particles = List(particleCount) { Particle() }
    private val rays = List(rayCount) { Ray() }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    fun draw(
        canvas: Canvas,
        width: Int,
        height: Int,
        centerX: Float,
        centerY: Float,
        isActive: Boolean,
        time: Float,
        rotationDeg: Float,
        pulse: Float
    ) {
        val pulseScale = if (isActive) 0.5f + 0.5f * pulse else 1f
        val scale = if (isActive) 1.2f else 1f

        // Background vignette
        paint.shader = RadialGradient(
            centerX, centerY, width.coerceAtLeast(height) * 0.8f,
            intArrayOf(0xFF1E1B4B.toInt(), 0xFF020617.toInt()),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP
        )
        paint.style = Paint.Style.FILL
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        paint.shader = null

        canvas.save()
        canvas.rotate(rotationDeg, centerX, centerY)
        canvas.scale(pulseScale, pulseScale, centerX, centerY)

        // Rays
        val rayAlpha = if (isActive) 0.15f else 0.05f
        rays.forEach { ray ->
            val angleRad = (ray.angleOffset + rotationDeg) * PI.toFloat() / 180f
            val endX = centerX + cos(angleRad) * width
            val endY = centerY + sin(angleRad) * width
            paint.color = Color.argb((rayAlpha * 128).toInt(), 0x63, 0x66, 0xF1)
            paint.strokeWidth = ray.width * (if (isActive) 2f else 1f)
            paint.style = Paint.Style.STROKE
            paint.strokeCap = Paint.Cap.ROUND
            canvas.drawLine(centerX, centerY, endX, endY, paint)
        }

        canvas.restore()

        // Particles
        particles.forEach { p ->
            p.update(isActive, time)
            val x = centerX + p.x * scale
            val y = centerY + p.y * scale
            if (isActive) {
                paint.color = p.colorAura
                paint.alpha = (0.1f * p.alpha * 255).toInt().coerceIn(0, 255)
                canvas.drawCircle(x, y, p.size * 4 * pulseScale, paint)
            }
            paint.color = p.colorFill
            paint.alpha = (p.alpha * 255).toInt().coerceIn(0, 255)
            val radius = p.size * (if (isActive) 1.5f else 1f)
            canvas.drawCircle(x, y, radius, paint)
        }
        paint.alpha = 255
    }

    private class Particle {
        var angle = Random.nextFloat() * 2 * PI.toFloat()
        var radius = Random.nextFloat() * 150f + 40f
        val speed = Random.nextFloat() * 2f + 0.5f
        var size = Random.nextFloat() * 4f + 2f
        var alpha = Random.nextFloat() * 0.5f + 0.3f
        val baseColor: Int = listOf(0xFF6366F1.toInt(), 0xFFEC4899.toInt(), 0xFF8B5CF6.toInt(), 0xFF06B6D4.toInt()).random()
        var x = 0f
        var y = 0f
        var colorFill: Int = baseColor
        var colorAura: Int = baseColor

        fun update(active: Boolean, time: Float) {
            val mult = if (active) 3f else 1f
            angle += speed * 0.01f * mult
            val wobble = sin(time * 50f + size) * (if (active) 20f else 5f)
            val r = radius + wobble
            x = cos(angle) * r
            y = sin(angle) * r
            colorFill = if (active) Color.WHITE else baseColor
            colorAura = baseColor
        }
    }

    private class Ray {
        val angleOffset = Random.nextFloat() * 360f
        val width = Random.nextFloat() * 30f + 10f
    }
}
