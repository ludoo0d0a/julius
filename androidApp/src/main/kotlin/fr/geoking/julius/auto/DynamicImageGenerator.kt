package fr.geoking.julius.auto

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import androidx.car.app.model.CarIcon
import androidx.core.graphics.drawable.IconCompat
import fr.geoking.julius.AgentType
import fr.geoking.julius.ui.anim.AnimationPalette
import fr.geoking.julius.ui.anim.AnimationPalettes
import kotlin.random.Random

/**
 * Generates procedural, "shiny" images for Android Auto as Bitmaps converted to CarIcons.
 * These replace the static background images with dynamic ones based on the active agent.
 */
object DynamicImageGenerator {

    private const val ICON_WIDTH = 400
    private const val ICON_HEIGHT = 400

    private val agentPaletteMap = mapOf(
        AgentType.OpenAI to 0,     // Aurora
        AgentType.ElevenLabs to 1, // Sunset
        AgentType.Deepgram to 2,   // Ocean
        AgentType.Native to 3,     // Forest
        AgentType.Gemini to 5,     // Cosmic
        AgentType.FirebaseAI to 4, // Ember
        AgentType.OpenCodeZen to 1, // Sunset
        AgentType.CompletionsMe to 0, // Aurora
        AgentType.ApiFreeLLM to 2, // Ocean
        AgentType.Local to 2,     // Ocean
        AgentType.Offline to 6     // Micro
    )

    fun generateIcon(agentType: AgentType, isActive: Boolean): CarIcon {
        val bitmap = Bitmap.createBitmap(ICON_WIDTH, ICON_HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val paletteIndex = agentPaletteMap[agentType] ?: 0
        val palette = AnimationPalettes.paletteFor(paletteIndex)

        drawShinyBackground(canvas, palette, isActive)

        return CarIcon.Builder(IconCompat.createWithBitmap(bitmap)).build()
    }

    private fun drawShinyBackground(canvas: Canvas, palette: AnimationPalette, isActive: Boolean) {
        val width = canvas.width.toFloat()
        val height = canvas.height.toFloat()
        val centerX = width / 2f
        val centerY = height / 2f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // 1. Deep Background Gradient
        val bgColors = if (isActive) {
            intArrayOf(palette.primary, 0xFF020617.toInt())
        } else {
            intArrayOf(0xFF1E1B4B.toInt(), 0xFF020617.toInt())
        }

        paint.shader = RadialGradient(
            centerX, centerY, width * 1.2f,
            bgColors,
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, width, height, paint)
        paint.shader = null

        // 2. Procedural Particles/Glow
        val seed = palette.name.hashCode() + (if (isActive) 1 else 0) + (System.currentTimeMillis() / 100).toInt()
        val random = Random(seed)

        // Draw some "shiny" rays
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        val rayCount = if (isActive) 12 else 6
        for (i in 0 until rayCount) {
            val angle = random.nextFloat() * 360f
            val rayWidth = random.nextFloat() * 40f + 10f
            val length = width * 0.6f

            val rayColor = palette.colors[random.nextInt(palette.colors.size)]
            paint.color = rayColor
            paint.alpha = if (isActive) 60 else 30
            paint.strokeWidth = rayWidth

            canvas.save()
            canvas.rotate(angle, centerX, centerY)
            canvas.drawLine(centerX, centerY, centerX + length, centerY, paint)
            canvas.restore()
        }

        // 3. Floating "Orbs"
        paint.style = Paint.Style.FILL
        val orbCount = if (isActive) 15 else 8
        for (i in 0 until orbCount) {
            val orbX = random.nextFloat() * width
            val orbY = random.nextFloat() * height
            val radius = random.nextFloat() * 40f + 10f
            val orbColor = palette.colors[random.nextInt(palette.colors.size)]

            // Subtle glow around orb
            val glow = RadialGradient(
                orbX, orbY, radius * 3f,
                intArrayOf(orbColor, Color.TRANSPARENT),
                floatArrayOf(0.2f, 1f), Shader.TileMode.CLAMP
            )
            paint.shader = glow
            paint.alpha = if (isActive) 120 else 60
            canvas.drawCircle(orbX, orbY, radius * 3f, paint)
            paint.shader = null

            // Core of the orb
            paint.color = if (isActive) Color.WHITE else orbColor
            paint.alpha = if (isActive) 220 else 120
            canvas.drawCircle(orbX, orbY, radius, paint)
        }

        // 4. Foreground Glass/Flare
        paint.shader = RadialGradient(
            0f, 0f, width * 1.5f,
            intArrayOf(Color.WHITE, Color.TRANSPARENT),
            floatArrayOf(0f, 0.3f),
            Shader.TileMode.CLAMP
        )
        paint.alpha = 40
        canvas.drawRect(0f, 0f, width, height, paint)
        paint.shader = null
    }
}
