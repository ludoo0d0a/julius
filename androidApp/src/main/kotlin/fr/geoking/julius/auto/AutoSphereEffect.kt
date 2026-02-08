package fr.geoking.julius.auto

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Sphere effect for Android Auto surface (ported from Compose SphereEffectCanvas).
 */
object AutoSphereEffect {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()

    fun draw(
        canvas: Canvas,
        width: Int,
        height: Int,
        centerX: Float,
        centerY: Float,
        isActive: Boolean,
        rotationDeg: Float,
        pulse: Float
    ) {
        val scaleAnim = if (isActive) 1.2f else 1f
        val maxRadius = width.coerceAtMost(height) * 0.25f * scaleAnim
        val activeRadius = if (isActive) maxRadius * (1f + pulse * 0.15f) else maxRadius

        // Background
        paint.shader = RadialGradient(
            centerX, centerY, width.coerceAtLeast(height) * 0.8f,
            intArrayOf(0xFF1E293B.toInt(), 0xFF0F172A.toInt(), 0xFF020617.toInt()),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )
        paint.style = Paint.Style.FILL
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        paint.shader = null

        val circles = if (isActive) 12 else 8
        for (i in 0 until circles) {
            val angle = (i * 360f / circles + rotationDeg) * PI.toFloat() / 180f
            val z = cos(angle)
            val ellipseRadius = activeRadius * abs(z)
            val ellipseY = centerY + sin(angle) * activeRadius * 0.3f
            val alpha = (0.3f + abs(z) * 0.7f) * (if (isActive) (0.8f + pulse * 0.2f) else 0.6f)
            paint.alpha = (alpha * 255).toInt().coerceIn(0, 255)
            paint.style = Paint.Style.FILL
            paint.shader = RadialGradient(
                centerX, ellipseY, ellipseRadius,
                if (isActive) Color.WHITE else 0xFF6366F1.toInt(),
                Color.TRANSPARENT,
                Shader.TileMode.CLAMP
            )
            canvas.drawOval(
                centerX - ellipseRadius,
                ellipseY - ellipseRadius * 0.6f,
                centerX + ellipseRadius,
                ellipseY + ellipseRadius * 0.6f,
                paint
            )
            paint.shader = null
        }

        paint.shader = RadialGradient(
            centerX - activeRadius * 0.3f, centerY - activeRadius * 0.3f, activeRadius,
            intArrayOf(
                Color.argb(if (isActive) 153 else 102, 0x63, 0x66, 0xF1),
                Color.argb(if (isActive) 76 else 51, 0xEC, 0x48, 0x99),
                Color.TRANSPARENT
            ),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )
        paint.alpha = 255
        canvas.drawCircle(centerX, centerY, activeRadius, paint)
        paint.shader = null

        // Orbiting rings
        for (i in 0 until 3) {
            val ringRadius = activeRadius * (1.3f + i * 0.4f)
            val ringRotation = (rotationDeg * (if (i % 2 == 0) 1f else -1f) + i * 45f) * PI.toFloat() / 180f
            paint.color = listOf(0xFF6366F1.toInt(), 0xFFEC4899.toInt(), 0xFF8B5CF6.toInt())[i]
            paint.alpha = (if (isActive) 0.4f else 0.2f).times(255).toInt().coerceIn(0, 255)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2f
            path.reset()
            val segments = 64
            for (j in 0..segments) {
                val a = (j * 360f / segments).toDouble() * PI / 180 + ringRotation
                val x = centerX + cos(a).toFloat() * ringRadius
                val y = centerY + sin(a).toFloat() * ringRadius * 0.6f
                if (j == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            path.close()
            canvas.drawPath(path, paint)
        }
        paint.style = Paint.Style.FILL
        paint.alpha = 255

        // Glow when active
        if (isActive) {
            val glowRadius = activeRadius * (1.5f + pulse * 0.3f)
            paint.shader = RadialGradient(
                centerX, centerY, glowRadius,
                intArrayOf(
                    Color.argb((0.3f * pulse * 255).toInt(), 0x63, 0x66, 0xF1),
                    Color.argb((0.1f * pulse * 255).toInt(), 0xEC, 0x48, 0x99),
                    Color.TRANSPARENT
                ),
                floatArrayOf(0f, 0.5f, 1f),
                Shader.TileMode.CLAMP
            )
            canvas.drawCircle(centerX, centerY, glowRadius, paint)
            paint.shader = null
        }
    }
}
