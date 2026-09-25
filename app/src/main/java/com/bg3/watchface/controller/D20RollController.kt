package com.bg3.watchface.controller

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.bg3.watchface.model.AbilityCheck
import com.bg3.watchface.model.RollState
import kotlin.math.hypot
import kotlin.random.Random

/**
 * Controller orchestrating the Baldur's Gate 3 D20 saving throw / skill check mechanics.
 * Features:
 * - Dynamic Difficulty Class (DC 10, 15, 18, 20)
 * - Ability Check modifiers (Fuerza, Destreza, Constitución, Sabiduría, Carisma, Inteligencia)
 * - D&D 5e / BG3 resolution (Roll + Modifier vs DC)
 * - Critical Hit / Critical Miss overrides
 * - High-definition haptic feedback patterns
 */
class D20RollController(
    private val context: Context,
    val particleSystem: ParticleSystem
) {
    companion object {
        const val ROLL_DURATION_MS = 1300L
        const val DISPLAY_RESULT_DURATION_MS = 4500L
        const val BASE_SHAKE_AMPLITUDE = 14f
    }

    var currentState: RollState = RollState.Idle
        private set

    var currentAbility: AbilityCheck = AbilityCheck.DEXTERITY
        private set

    var currentDC: Int = 15
        private set

    private var rollStartTimeMs: Long = 0L
    private var stateStartTimeMs: Long = 0L
    private var lastShuffleTimeMs: Long = 0L
    private var currentShuffleValue: Int = 20

    private val vibrator: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vm?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    fun isD20Tapped(touchX: Float, touchY: Float, centerX: Float, centerY: Float, radius: Float): Boolean {
        return hypot(touchX - centerX, touchY - centerY) <= radius * 1.15f
    }

    fun cycleAbility(): AbilityCheck {
        currentAbility = currentAbility.next()
        return currentAbility
    }

    fun cycleDC(): Int {
        currentDC = when (currentDC) {
            10 -> 15
            15 -> 18
            18 -> 20
            else -> 10
        }
        return currentDC
    }

    fun triggerRoll(nowMs: Long, centerX: Float, centerY: Float, forcedValue: Int? = null) {
        if (currentState is RollState.Rolling) return

        rollStartTimeMs = nowMs
        stateStartTimeMs = nowMs
        lastShuffleTimeMs = nowMs
        currentShuffleValue = forcedValue ?: Random.nextInt(1, 21)

        particleSystem.clear()
        currentState = RollState.Rolling(
            elapsedMs = 0L,
            displayValue = currentShuffleValue,
            shakeOffsetX = 0f,
            shakeOffsetY = 0f,
            rotationDegrees = 0f,
            ability = currentAbility,
            dc = currentDC
        )
        triggerRollHaptics()
    }

    fun update(nowMs: Long, centerX: Float, centerY: Float, deltaSeconds: Float) {
        particleSystem.update(deltaSeconds)

        when (val state = currentState) {
            is RollState.Rolling -> {
                val elapsed = nowMs - rollStartTimeMs
                if (elapsed >= ROLL_DURATION_MS) {
                    val finalResult = Random.nextInt(1, 21)
                    stateStartTimeMs = nowMs
                    when (finalResult) {
                        20 -> {
                            val total = 20 + currentAbility.defaultMod
                            currentState = RollState.CriticalSuccess(0L, currentAbility, total)
                            particleSystem.emitCriticalSuccess(centerX, centerY, count = 55)
                            triggerCriticalSuccessHaptics()
                        }
                        1 -> {
                            val total = 1 + currentAbility.defaultMod
                            currentState = RollState.CriticalFailure(0L, currentAbility, total)
                            particleSystem.emitCriticalFailure(centerX, centerY, count = 40)
                            triggerCriticalFailureHaptics()
                        }
                        else -> {
                            val mod = currentAbility.defaultMod
                            val total = finalResult + mod
                            val isPassed = total >= currentDC
                            currentState = RollState.Settled(
                                value = finalResult,
                                modifier = mod,
                                total = total,
                                dc = currentDC,
                                isPassed = isPassed,
                                ability = currentAbility,
                                elapsedMs = 0L
                            )
                            if (isPassed) triggerSuccessHaptics() else triggerFailHaptics()
                        }
                    }
                } else {
                    val progress = elapsed.toFloat() / ROLL_DURATION_MS
                    val currentInterval = 40L + (progress * progress * 160L).toLong()

                    if (nowMs - lastShuffleTimeMs >= currentInterval) {
                        currentShuffleValue = Random.nextInt(1, 21)
                        lastShuffleTimeMs = nowMs
                    }

                    val remainingFactor = (1f - progress).coerceIn(0f, 1f)
                    val shakeMagnitude = BASE_SHAKE_AMPLITUDE * remainingFactor
                    val shakeX = (Random.nextFloat() - 0.5f) * 2f * shakeMagnitude
                    val shakeY = (Random.nextFloat() - 0.5f) * 2f * shakeMagnitude
                    val rotation = (elapsed * 0.45f * remainingFactor) % 360f

                    currentState = RollState.Rolling(
                        elapsedMs = elapsed,
                        displayValue = currentShuffleValue,
                        shakeOffsetX = shakeX,
                        shakeOffsetY = shakeY,
                        rotationDegrees = rotation,
                        ability = currentAbility,
                        dc = currentDC
                    )
                }
            }

            is RollState.CriticalSuccess, is RollState.CriticalFailure, is RollState.Settled -> {
                if (nowMs - stateStartTimeMs >= DISPLAY_RESULT_DURATION_MS) {
                    currentState = RollState.Idle
                }
            }

            is RollState.Idle -> {}
        }
    }

    private fun triggerRollHaptics() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val timings = longArrayOf(0, 30, 40, 30, 50, 30, 70, 40)
            val amplitudes = intArrayOf(0, 80, 0, 120, 0, 150, 0, 180)
            vibrator?.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
        }
    }

    private fun triggerCriticalSuccessHaptics() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val timings = longArrayOf(0, 80, 60, 120, 80, 220)
            val amplitudes = intArrayOf(0, 150, 0, 200, 0, 255)
            vibrator?.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
        }
    }

    private fun triggerCriticalFailureHaptics() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val timings = longArrayOf(0, 350, 100, 200)
            val amplitudes = intArrayOf(0, 255, 0, 180)
            vibrator?.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
        }
    }

    private fun triggerSuccessHaptics() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val timings = longArrayOf(0, 60, 50, 100)
            val amplitudes = intArrayOf(0, 140, 0, 180)
            vibrator?.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
        }
    }

    private fun triggerFailHaptics() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val timings = longArrayOf(0, 180)
            val amplitudes = intArrayOf(0, 120)
            vibrator?.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
        }
    }
}
