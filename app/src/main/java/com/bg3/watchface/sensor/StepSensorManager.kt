package com.bg3.watchface.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager

/**
 * Monitors daily step count to represent Baldur's Gate 3 "Experience Points (XP)".
 */
class StepSensorManager(
    context: Context,
    val stepGoal: Int = 10000,
    private val onStepsUpdated: (steps: Int, progress: Float) -> Unit
) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val stepSensor: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)

    var currentSteps: Int = 0
        private set

    var progressFraction: Float = 0f
        private set

    private var initialStepOffset = -1
    private var isListening = false

    fun start() {
        if (!isListening && stepSensor != null) {
            sensorManager?.registerListener(
                this,
                stepSensor,
                SensorManager.SENSOR_DELAY_NORMAL
            )
            isListening = true
        }
    }

    fun stop() {
        if (isListening) {
            sensorManager?.unregisterListener(this)
            isListening = false
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_STEP_COUNTER) {
            val totalStepsSinceBoot = event.values[0].toInt()

            if (initialStepOffset < 0) {
                // Initialize baseline at watchface launch or midnight
                initialStepOffset = totalStepsSinceBoot
            }

            currentSteps = (totalStepsSinceBoot - initialStepOffset).coerceAtLeast(0)
            progressFraction = (currentSteps.toFloat() / stepGoal.toFloat()).coerceIn(0f, 1f)

            onStepsUpdated(currentSteps, progressFraction)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // No-op
    }

    /**
     * Fallback step setter for demonstration or complication overrides.
     */
    fun setManualSteps(steps: Int) {
        currentSteps = steps.coerceAtLeast(0)
        progressFraction = (currentSteps.toFloat() / stepGoal.toFloat()).coerceIn(0f, 1f)
        onStepsUpdated(currentSteps, progressFraction)
    }
}
