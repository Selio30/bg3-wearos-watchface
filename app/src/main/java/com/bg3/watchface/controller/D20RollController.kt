package com.bg3.watchface.controller

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.bg3.watchface.model.AbilityCheck
import com.bg3.watchface.model.RollState
import kotlin.math.PI
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/**
 * Controller orchestrating the Baldur's Gate 3 D20 saving throw / skill check mechanics.
 * Features:
 * - Authentic 3D tumbling physics (multi-axis angular velocities, vertical hop, scale depth)
 * - Cubic ease-out deceleration into canonical resting face orientation
 * - Post-impact squish/rebound harmonics and procedural expanding shockwave
 * - Heavy impact haptic thump and outcome-specific vibration signatures
 * - Dynamic Difficulty Class (DC 10, 15, 18, 20) and Ability Modifiers
 */
class D20RollController(
    private val context: Context,
    val particleSystem: ParticleSystem
) {
    companion object {
        const val ROLL_DURATION_MS = 1300L
        const val DISPLAY_RESULT_DURATION_MS = 4500L
        const val SHOCKWAVE_DURATION_MS = 380L
        const val SETTLE_START_MS = 880L
        const val BASE_SHAKE_AMPLITUDE = 16f
        const val TWO_PI = (PI * 2.0).toFloat()
    }

    var currentState: RollState = RollState.Idle
        private set

    var currentAbility: AbilityCheck = AbilityCheck.DEXTERITY
        private set

    var currentDC: Int = 15
        private set

    // 3D physics state
    var curRotX: Float = 0f
        private set
    var curRotY: Float = 0f
        private set
    var curRotZ: Float = 0f
        private set
    var hopY: Float = 0f
        private set
    var scaleX: Float = 1f
        private set
    var scaleY: Float = 1f
        private set
    var shockwaveProgress: Float = 1f
        private set

    private var tumbleVelX: Float = 0f
    private var tumbleVelY: Float = 0f
    private var tumbleVelZ: Float = 0f
    private var spinAt900X: Float = 0f
    private var spinAt900Y: Float = 0f
    private var spinAt900Z: Float = 0f
    private var hasCaptured900: Boolean = false

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
        hasCaptured900 = false
        shockwaveProgress = 1f

        // High-energy 3D tumble angular velocities (~5-7 rev/s)
        val dirX = if (Random.nextBoolean()) 1f else -1f
        val dirY = if (Random.nextBoolean()) 1f else -1f
        val dirZ = if (Random.nextBoolean()) 1f else -1f
        tumbleVelX = dirX * (34f + Random.nextFloat() * 14f)
        tumbleVelY = dirY * (42f + Random.nextFloat() * 18f)
        tumbleVelZ = dirZ * (28f + Random.nextFloat() * 12f)

        particleSystem.clear()
        currentState = RollState.Rolling(
            elapsedMs = 0L,
            displayValue = currentShuffleValue,
            shakeOffsetX = 0f,
            shakeOffsetY = 0f,
            rotX = curRotX,
            rotY = curRotY,
            rotZ = curRotZ,
            hopY = 0f,
            scaleX = 1f,
            scaleY = 1f,
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
                    curRotX = 0f
                    curRotY = 0f
                    curRotZ = 0f
                    hopY = 0f
                    scaleX = 1f
                    scaleY = 1f
                    shockwaveProgress = 0f

                    triggerHeavyImpactHaptics()

                    when (finalResult) {
                        20 -> {
                            val total = 20 + currentAbility.defaultMod
                            currentState = RollState.CriticalSuccess(
                                elapsedMs = 0L,
                                ability = currentAbility,
                                totalScore = total,
                                scaleX = 1f,
                                scaleY = 1f,
                                shockwaveProgress = 0f
                            )
                            particleSystem.emitCriticalSuccess(centerX, centerY, count = 55)
                            triggerCriticalSuccessHaptics()
                        }
                        1 -> {
                            val total = 1 + currentAbility.defaultMod
                            currentState = RollState.CriticalFailure(
                                elapsedMs = 0L,
                                ability = currentAbility,
                                totalScore = total,
                                scaleX = 1f,
                                scaleY = 1f,
                                shockwaveProgress = 0f
                            )
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
                                elapsedMs = 0L,
                                scaleX = 1f,
                                scaleY = 1f,
                                shockwaveProgress = 0f
                            )
                            if (isPassed) triggerSuccessHaptics() else triggerFailHaptics()
                        }
                    }
                } else {
                    val progress = (elapsed.toFloat() / ROLL_DURATION_MS).coerceIn(0f, 1f)

                    // 3D rotation update: rapid tumble early, smooth cubic ease-out to zero in final 420ms
                    if (elapsed < SETTLE_START_MS) {
                        curRotX = (curRotX + tumbleVelX * deltaSeconds) % TWO_PI
                        curRotY = (curRotY + tumbleVelY * deltaSeconds) % TWO_PI
                        curRotZ = (curRotZ + tumbleVelZ * deltaSeconds) % TWO_PI
                    } else {
                        if (!hasCaptured900) {
                            spinAt900X = normAngle(curRotX)
                            spinAt900Y = normAngle(curRotY)
                            spinAt900Z = normAngle(curRotZ)
                            hasCaptured900 = true
                        }
                        val settleProgress = ((elapsed - SETTLE_START_MS).toFloat() / (ROLL_DURATION_MS - SETTLE_START_MS)).coerceIn(0f, 1f)
                        val easeSettle = (1f - settleProgress).pow(3f)
                        curRotX = spinAt900X * easeSettle
                        curRotY = spinAt900Y * easeSettle
                        curRotZ = spinAt900Z * easeSettle
                    }

                    // Vertical Hop & Scale Depth
                    val hopSine = sin(progress * PI.toFloat())
                    hopY = -24f * hopSine * (1f - progress).pow(0.4f)
                    val scaleBounce = 1f + 0.14f * hopSine
                    scaleX = scaleBounce
                    scaleY = scaleBounce

                    // Number shuffle on front face
                    val currentInterval = 40L + (progress * progress * 160L).toLong()
                    if (nowMs - lastShuffleTimeMs >= currentInterval) {
                        currentShuffleValue = Random.nextInt(1, 21)
                        lastShuffleTimeMs = nowMs
                    }

                    // Jitter shake impulse
                    val remainingFactor = (1f - progress).pow(2f)
                    val shakeMagnitude = BASE_SHAKE_AMPLITUDE * remainingFactor
                    val shakeX = (Random.nextFloat() - 0.5f) * 2f * shakeMagnitude
                    val shakeY = (Random.nextFloat() - 0.5f) * 2f * shakeMagnitude

                    currentState = RollState.Rolling(
                        elapsedMs = elapsed,
                        displayValue = currentShuffleValue,
                        shakeOffsetX = shakeX,
                        shakeOffsetY = shakeY,
                        rotX = curRotX,
                        rotY = curRotY,
                        rotZ = curRotZ,
                        hopY = hopY,
                        scaleX = scaleX,
                        scaleY = scaleY,
                        rotationDegrees = 0f,
                        ability = currentAbility,
                        dc = currentDC
                    )
                }
            }

            is RollState.CriticalSuccess, is RollState.CriticalFailure, is RollState.Settled -> {
                val impactElapsed = nowMs - stateStartTimeMs

                // Post-impact squish & rebound harmonics
                if (impactElapsed < 240L) {
                    val squashProgress = (impactElapsed.toFloat() / 240f).coerceIn(0f, 1f)
                    val squash = sin(squashProgress * PI.toFloat()) * (1f - squashProgress)
                    scaleY = 1f - 0.14f * squash
                    scaleX = 1f + 0.08f * squash
                    hopY = 3f * squash
                } else {
                    scaleX = 1f
                    scaleY = 1f
                    hopY = 0f
                }

                // Shockwave progress
                shockwaveProgress = (impactElapsed.toFloat() / SHOCKWAVE_DURATION_MS).coerceIn(0f, 1f)

                if (impactElapsed >= DISPLAY_RESULT_DURATION_MS) {
                    currentState = RollState.Idle
                    scaleX = 1f
                    scaleY = 1f
                    hopY = 0f
                    shockwaveProgress = 1f
                }
            }

            is RollState.Idle -> {
                scaleX = 1f
                scaleY = 1f
                hopY = 0f
                shockwaveProgress = 1f
            }
        }
    }

    private fun normAngle(angle: Float): Float {
        var a = angle % TWO_PI
        if (a > PI) a -= TWO_PI
        if (a < -PI) a += TWO_PI
        return a
    }

    private fun triggerHeavyImpactHaptics() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val timings = longArrayOf(0, 45, 20, 30)
            val amplitudes = intArrayOf(0, 255, 0, 180)
            vibrator?.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
        }
    }

    private fun triggerRollHaptics() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val timings = longArrayOf(0, 25, 30, 25, 40, 25, 55, 30)
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
