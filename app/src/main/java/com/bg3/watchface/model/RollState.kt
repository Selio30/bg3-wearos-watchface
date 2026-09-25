package com.bg3.watchface.model

/**
 * Represents the current animation and calculation state of the central D20 die.
 */
sealed class RollState {

    /**
     * Idle state: Die is resting, smoothly pulsating with an arcane breathing glow.
     */
    object Idle : RollState()

    /**
     * Active rolling state: Die is vibrating/shaking, rotating, and cycling random faces.
     *
     * @param elapsedMs Elapsed time in milliseconds since the roll started.
     * @param displayValue Current number temporarily visible on the face during spin.
     * @param shakeOffsetX Pixel horizontal tremor offset.
     * @param shakeOffsetY Pixel vertical tremor offset.
     * @param rotationDegrees Current rotational angle in degrees.
     */
    data class Rolling(
        val elapsedMs: Long,
        val displayValue: Int,
        val shakeOffsetX: Float,
        val shakeOffsetY: Float,
        val rotationDegrees: Float
    ) : RollState()

    /**
     * Critical Success (Natural 20): Explosive radiant golden aura and particles!
     *
     * @param elapsedMs Elapsed time in milliseconds since landing on 20.
     */
    data class CriticalSuccess(
        val elapsedMs: Long
    ) : RollState() {
        val value: Int = 20
    }

    /**
     * Critical Failure (Natural 1): Ominous crimson/purple necrotic smoke and screen tremor!
     *
     * @param elapsedMs Elapsed time in milliseconds since landing on 1.
     */
    data class CriticalFailure(
        val elapsedMs: Long
    ) : RollState() {
        val value: Int = 1
    }

    /**
     * Standard Roll Settled (Values 2 to 19): Clean settle with gold highlight pulse.
     *
     * @param value The final rolled D20 number (2..19).
     * @param elapsedMs Elapsed time in milliseconds since landing.
     */
    data class Settled(
        val value: Int,
        val elapsedMs: Long
    ) : RollState()
}
