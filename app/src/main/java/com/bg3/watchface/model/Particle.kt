package com.bg3.watchface.model

/**
 * Type of particle effect for distinct visual behavior.
 */
enum class ParticleType {
    GOLD_SPARKLE,    // High-speed glittering golden ember for Critical 20
    GOLD_RAY,        // Expanding radiant golden light ray
    CRIMSON_SMOKE,   // Billowing dark crimson necrotic smoke for Critical 1
    PURPLE_EMBER     // Illithid psionic spark
}

/**
 * High-performance pooled particle model.
 */
data class Particle(
    var active: Boolean = false,
    var x: Float = 0f,
    var y: Float = 0f,
    var vx: Float = 0f,
    var vy: Float = 0f,
    var alpha: Float = 1f,
    var size: Float = 4f,
    var color: Int = 0,
    var currentLife: Float = 0f,
    var maxLife: Float = 1000f,
    var rotation: Float = 0f,
    var rotationSpeed: Float = 0f,
    var type: ParticleType = ParticleType.GOLD_SPARKLE
) {
    /**
     * Resets the particle to new initial values.
     */
    fun init(
        startX: Float,
        startY: Float,
        velX: Float,
        velY: Float,
        particleSize: Float,
        particleColor: Int,
        lifetimeMs: Float,
        particleType: ParticleType
    ) {
        active = true
        x = startX
        y = startY
        vx = velX
        vy = velY
        size = particleSize
        color = particleColor
        currentLife = 0f
        maxLife = lifetimeMs
        alpha = 1f
        rotation = (Math.random() * 360).toFloat()
        rotationSpeed = ((Math.random() - 0.5) * 180).toFloat()
        type = particleType
    }

    /**
     * Updates physics and life cycle. Returns true if still alive.
     */
    fun update(deltaSeconds: Float): Boolean {
        if (!active) return false

        currentLife += deltaSeconds * 1000f
        if (currentLife >= maxLife) {
            active = false
            return false
        }

        val progress = currentLife / maxLife

        when (type) {
            ParticleType.GOLD_SPARKLE -> {
                // High velocity burst with slight gravity and decelerating drag
                x += vx * deltaSeconds
                y += vy * deltaSeconds
                vx *= 0.94f
                vy = (vy * 0.94f) + (30f * deltaSeconds) // gentle gravity
                rotation += rotationSpeed * deltaSeconds
                // Fade out smoothly near end of life
                alpha = 1f - progress
            }
            ParticleType.GOLD_RAY -> {
                // Expands outward from center
                x += vx * deltaSeconds
                y += vy * deltaSeconds
                size *= 1.02f
                alpha = (1f - progress) * 0.85f
            }
            ParticleType.CRIMSON_SMOKE -> {
                // Drifting upward smoke with turbulent spreading
                x += vx * deltaSeconds
                y += vy * deltaSeconds
                vx *= 0.92f
                vy = (vy * 0.92f) - (20f * deltaSeconds) // buoyant rise
                size += 15f * deltaSeconds // smoke expands
                alpha = (1f - progress) * 0.75f
            }
            ParticleType.PURPLE_EMBER -> {
                x += vx * deltaSeconds
                y += vy * deltaSeconds
                vx *= 0.95f
                vy *= 0.95f
                alpha = 1f - progress
            }
        }

        return true
    }
}
