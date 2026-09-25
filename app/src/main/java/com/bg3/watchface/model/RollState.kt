package com.bg3.watchface.model

/**
 * Ability check / Saving throw types inspired by D&D 5e / Baldur's Gate 3.
 */
enum class AbilityCheck(val code: String, val displayName: String, val defaultMod: Int) {
    STRENGTH("FUE", "Fuerza", 3),
    DEXTERITY("DES", "Destreza", 4),
    CONSTITUTION("CON", "Constitución", 2),
    INTELLIGENCE("INT", "Inteligencia", 1),
    WISDOM("SAB", "Sabiduría", 2),
    CHARISMA("CAR", "Carisma", 3);

    fun next(): AbilityCheck {
        val values = values()
        return values[(ordinal + 1) % values.size]
    }
}

/**
 * Visual and operational states of the D20 roll with DC (Difficulty Class) resolution.
 */
sealed class RollState {

    /**
     * Idle state: Die is resting in canonical 3D orientation with breathing arcane glow.
     */
    object Idle : RollState()

    /**
     * Active rolling state: Die tumbles in 3D across X, Y, Z axes, hops vertically,
     * and shuffles random numbers with cubic deceleration physics.
     */
    data class Rolling(
        val elapsedMs: Long,
        val displayValue: Int,
        val shakeOffsetX: Float = 0f,
        val shakeOffsetY: Float = 0f,
        val rotX: Float = 0f,
        val rotY: Float = 0f,
        val rotZ: Float = 0f,
        val hopY: Float = 0f,
        val scaleX: Float = 1f,
        val scaleY: Float = 1f,
        val rotationDegrees: Float = 0f,
        val ability: AbilityCheck,
        val dc: Int
    ) : RollState()

    /**
     * Critical Success (Natural 20): Auto-success with radiant explosion and arcane shockwave.
     */
    data class CriticalSuccess(
        val elapsedMs: Long,
        val ability: AbilityCheck,
        val totalScore: Int,
        val scaleX: Float = 1f,
        val scaleY: Float = 1f,
        val shockwaveProgress: Float = 0f
    ) : RollState() {
        val value: Int = 20
    }

    /**
     * Critical Failure (Natural 1): Auto-fail with necrotic shadow smoke and heavy shockwave.
     */
    data class CriticalFailure(
        val elapsedMs: Long,
        val ability: AbilityCheck,
        val totalScore: Int,
        val scaleX: Float = 1f,
        val scaleY: Float = 1f,
        val shockwaveProgress: Float = 0f
    ) : RollState() {
        val value: Int = 1
    }

    /**
     * Standard Roll Resolved (2 to 19): Compares (roll + modifier) vs DC with impact shockwave.
     */
    data class Settled(
        val value: Int,
        val modifier: Int,
        val total: Int,
        val dc: Int,
        val isPassed: Boolean,
        val ability: AbilityCheck,
        val elapsedMs: Long,
        val scaleX: Float = 1f,
        val scaleY: Float = 1f,
        val shockwaveProgress: Float = 0f
    ) : RollState()
}
