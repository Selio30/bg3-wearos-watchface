package com.bg3.watchface.controller

import android.content.Context
import android.os.Build
import android.os.CombinedVibration
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.bg3.watchface.model.RollState
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

/**
 * Controller responsible for orchestrating the D20 dice roll:
 * - Physics, shaking, and rotation
 * - Decelerating shuffle of numbers 1-20
 * - Critical Success (Nat 20) and Critical Failure (Nat 1) triggers
 * - Tactile haptic feedback patterns
 * - Lifecycle state machine transitions
 */
class D20RollController(
    private val context: Context,
    val particleSystem: ParticleSystem
) {

    companion object {
        const val ROLL_DURATION_MS = 1300L
        const val DISPLAY_RESULT_DURATION_MS = 4000L
        const val BASE_SHAKE_AMPLITUDE = 14f
    }

    var currentState: RollState = RollState.Idle
        private set

    private var rollStartTimeMs: Long = 0L
    private var stateStartTimeMs: Long = 0L
    private var lastShuffleTimeMs: Long = 0L
    private var shuffleIntervalMs: Long = 50L
    private var currentShuffleValue: Int = 20

    private val vibrator: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    /**
     * Checks if a user tap falls within the central D20 die hit target.
     */
    fun isD20Tapped(touchX: Float, touchY: Float, centerX: Float, centerY: Float, radius: Float): Boolean {
        val distance = hypot(touchX - centerX, touchY - centerY)
        // Add a generous 15% touch padding for comfortable tapping on round smartwatch screens
        return distance <= radius * 1.15f
    }

    /**
     * Initiates a saving throw / ability check roll.
     */
    fun triggerRoll(nowMs: Long, centerX: Float, centerY: Float) {
        if (currentState is RollState.Rolling) {
            return // Prevent re-trigger while currently rolling
        }

        rollStartTimeMs = nowMs
        stateStartTimeMs = nowMs
        lastShuffleTimeMs = nowMs
        shuffleIntervalMs = 45L
        currentShuffleValue = Random.nextInt(1, 21)

        particleSystem.clear()
        currentState = RollState.Rolling(
            elapsedMs = 0L,
            displayValue = currentShuffleValue,
            shakeOffsetX = 0f,
            shakeOffsetY = 0f,
            rotationDegrees = 0f
        )

        triggerRollHaptics()
    }

    /**
     * Frame-by-frame update called by the CanvasRenderer (approx 30-60 FPS).
     */
    fun update(nowMs: Long, centerX: Float, centerY: Float, deltaSeconds: Float) {
        particleSystem.update(deltaSeconds)

        when (val state = currentState) {
            is RollState.Rolling -> {
                val elapsed = nowMs - rollStartTimeMs

                if (elapsed >= ROLL_DURATION_MS) {
                    // Finalize roll outcome (uniform 1 to 20)
                    val finalResult = Random.nextInt(1, 21)
                    stateStartTimeMs = nowMs

                    when (finalResult) {
                        20 -> {
                            currentState = RollState.CriticalSuccess(elapsedMs = 0L)
                            particleSystem.emitCriticalSuccess(centerX, centerY, count = 55)
                            triggerCriticalSuccessHaptics()
                        }
                        1 -> {
                            currentState = RollState.CriticalFailure(elapsedMs = 0L)
                            particleSystem.emitCriticalFailure(centerX, centerY, count = 40)
                            triggerCriticalFailureHaptics()
                        }
                        else -> {
                            currentState = RollState.Settled(value = finalResult, elapsedMs = 0L)
                            triggerNormalSettleHaptics()
                        }
                    }
                } else {
                    // Progressively slow down number shuffling as the die decelerates
                    val progress = elapsed.toFloat() / ROLL_DURATION_MS
                    val currentInterval = 40L + (progress * progress * 160L).toLong()

                    if (nowMs - lastShuffleTimeMs >= currentInterval) {
                        currentShuffleValue = Random.nextInt(1, 21)
                        lastShuffleTimeMs = nowMs
                    }

                    // Damped physical shaking
                    val remainingFactor = (1f - progress).coerceIn(0f, 1f)
                    val shakeMagnitude = BASE_SHAKE_AMPLITUDE * remainingFactor
                    val shakeX = (Random.nextFloat() - 0.5f) * 2f * shakeMagnitude
                    val shakeY = (Random.nextFloat() - 0.5f) * 2f * shakeMagnitude

                    // Spinning rotational angle
                    val rotation = (elapsed * 0.45f * remainingFactor) % 360f

                    currentState = RollState.Rolling(
                        elapsedMs = elapsed,
                        displayValue = currentShuffleValue,
                        shakeOffsetX = shakeX,
                        shakeOffsetY = shakeY,
                        rotationDegrees = rotation
                    )
                }
            }

            is RollState.CriticalSuccess -> {
                val elapsed = nowMs - stateStartTimeMs
                if (elapsed >= DISPLAY_RESULT_DURATION_MS) {
                    currentState = RollState.Idle
                } else {
                    currentState = RollState.CriticalSuccess(elapsedMs = elapsed)
                }
            }

            is RollState.CriticalFailure -> {
                val elapsed = nowMs - stateStartTimeMs
                if (elapsed >= DISPLAY_RESULT_DURATION_MS) {
                    currentState = RollState.Idle
                } else {
                    currentState = RollState.CriticalFailure(elapsedMs = elapsed)
                }
            }

            is RollState.Settled -> {
                val elapsed = nowMs - stateStartTimeMs
                if (elapsed >= DISPLAY_RESULT_DURATION_MS) {
                    currentState = RollState.Idle
                } else {
                    currentState = RollState.Settled(value = state.value, elapsedMs = elapsed)
                }
            }

            is RollState.Idle -> {
                // Resting state - nothing to transition
            }
        }
    }

    /**
     * Haptic feedback when the D20 is actively tumbling.
     */
    private fun triggerRollHaptics() {
        vibrator?.let { v ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // Quick rhythmic ticks simulating tumbling dice
                val timings = longArrayOf(0, 30, 40, 30, 50, 30, 70, 40)
                val amplitudes = intArrayOf(0, 80, 0, 120, 0, 150, 0, 180)
                v.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(100L)
            }
        }
    }

    /**
     * Celebratory haptic fanfare for Natural 20!
     */
    private fun triggerCriticalSuccessHaptics() {
        vibrator?.let { v ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val timings = longArrayOf(0, 80, 60, 120, 80, 220)
                val amplitudes = intArrayOf(0, 150, 0, 200, 0, 255)
                v.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(300L)
            }
        }
    }

    /**
     * Heavy, grim haptic tremor for Natural 1!
     */
    private fun triggerCriticalFailureHaptics() {
        vibrator?.let { v ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val timings = longArrayOf(0, 350, 100, 200)
                val amplitudes = intArrayOf(0, 255, 0, 180)
                v.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(400L)
            }
        }
    }

    /**
     * Standard tactile click when dice lands on 2-19.
     */
    private fun triggerNormalSettleHaptics() {
        vibrator?.let { v ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                v.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(VibrationEffect.createOneShot(45L, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(45L)
            }
        }
    }
}
