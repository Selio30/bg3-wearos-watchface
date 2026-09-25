package com.bg3.watchface.controller

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.bg3.watchface.model.Particle
import com.bg3.watchface.model.ParticleType
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * High-performance, zero-allocation particle manager for Wear OS.
 * Pre-allocates a pool of particles to prevent Garbage Collection pauses during animations.
 */
class ParticleSystem(maxParticles: Int = 120) {

    private val pool = Array(maxParticles) { Particle() }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    /**
     * Emits a radiant golden celebration burst when a Natural 20 is rolled.
     */
    fun emitCriticalSuccess(centerX: Float, centerY: Float, count: Int = 50) {
        val colors = intArrayOf(
            Color.parseColor("#FFF1A8"), // Bright radiant gold
            Color.parseColor("#D4AF37"), // Classic arcane gold
            Color.parseColor("#F5D77F"), // Light amber
            Color.parseColor("#FFFFFF"), // Pure white core flash
            Color.parseColor("#E0A928")  // Deep rich gold
        )

        var emitted = 0
        for (p in pool) {
            if (!p.active) {
                val angle = Random.nextFloat() * 2f * Math.PI.toFloat()
                val speed = Random.nextFloat() * 180f + 70f
                val vx = cos(angle) * speed
                val vy = sin(angle) * speed
                val size = Random.nextFloat() * 4.5f + 2.5f
                val color = colors[Random.nextInt(colors.size)]
                val lifetime = Random.nextFloat() * 800f + 600f

                p.init(
                    startX = centerX,
                    startY = centerY,
                    velX = vx,
                    velY = vy,
                    particleSize = size,
                    particleColor = color,
                    lifetimeMs = lifetime,
                    particleType = if (Random.nextFloat() > 0.8f) ParticleType.GOLD_RAY else ParticleType.GOLD_SPARKLE
                )

                emitted++
                if (emitted >= count) break
            }
        }
    }

    /**
     * Emits ominous dark crimson and necrotic purple smoke when a Natural 1 is rolled.
     */
    fun emitCriticalFailure(centerX: Float, centerY: Float, count: Int = 40) {
        val colors = intArrayOf(
            Color.parseColor("#8B0000"), // Crimson blood red
            Color.parseColor("#500707"), // Deep coagulated shadow
            Color.parseColor("#4A0E4E"), // Necrotic dark violet
            Color.parseColor("#2B002B"), // Illithid void purple
            Color.parseColor("#1A1110")  // Ashen black
        )

        var emitted = 0
        for (p in pool) {
            if (!p.active) {
                val angle = Random.nextFloat() * 2f * Math.PI.toFloat()
                val speed = Random.nextFloat() * 70f + 20f
                val vx = cos(angle) * speed
                val vy = sin(angle) * speed - 15f // slight initial updraft
                val size = Random.nextFloat() * 8f + 5f
                val color = colors[Random.nextInt(colors.size)]
                val lifetime = Random.nextFloat() * 1000f + 800f

                p.init(
                    startX = centerX + (Random.nextFloat() - 0.5f) * 20f,
                    startY = centerY + (Random.nextFloat() - 0.5f) * 20f,
                    velX = vx,
                    velY = vy,
                    particleSize = size,
                    particleColor = color,
                    lifetimeMs = lifetime,
                    particleType = ParticleType.CRIMSON_SMOKE
                )

                emitted++
                if (emitted >= count) break
            }
        }
    }

    /**
     * Updates physics for all active particles.
     */
    fun update(deltaSeconds: Float) {
        for (p in pool) {
            if (p.active) {
                p.update(deltaSeconds)
            }
        }
    }

    /**
     * Renders all active particles onto the canvas.
     */
    fun render(canvas: Canvas) {
        for (p in pool) {
            if (p.active && p.alpha > 0.01f) {
                val originalAlpha = Color.alpha(p.color)
                val finalAlpha = (originalAlpha * p.alpha).toInt().coerceIn(0, 255)
                paint.color = (p.color and 0x00FFFFFF) or (finalAlpha shl 24)

                when (p.type) {
                    ParticleType.GOLD_SPARKLE, ParticleType.PURPLE_EMBER -> {
                        canvas.drawCircle(p.x, p.y, p.size, paint)
                    }
                    ParticleType.GOLD_RAY -> {
                        // Draw a diamond/star sparkle
                        canvas.save()
                        canvas.translate(p.x, p.y)
                        canvas.rotate(p.rotation)
                        canvas.drawRect(-p.size * 0.4f, -p.size * 1.5f, p.size * 0.4f, p.size * 1.5f, paint)
                        canvas.drawRect(-p.size * 1.5f, -p.size * 0.4f, p.size * 1.5f, p.size * 0.4f, paint)
                        canvas.restore()
                    }
                    ParticleType.CRIMSON_SMOKE -> {
                        // Soft expanding smoky circle
                        canvas.drawCircle(p.x, p.y, p.size, paint)
                    }
                }
            }
        }
    }

    /**
     * Clears and deactivates all active particles immediately.
     */
    fun clear() {
        for (p in pool) {
            p.active = false
        }
    }
}
