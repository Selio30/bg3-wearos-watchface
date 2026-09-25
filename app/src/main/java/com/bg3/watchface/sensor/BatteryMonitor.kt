package com.bg3.watchface.sensor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager

/**
 * Monitors device battery level to represent Baldur's Gate 3 "Hit Points (HP)".
 */
class BatteryMonitor(
    private val context: Context,
    private val onBatteryChanged: (level: Float, isCharging: Boolean) -> Unit
) {

    var batteryFraction: Float = 1.0f
        private set

    var isCharging: Boolean = false
        private set

    private var isRegistered = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_BATTERY_CHANGED) {
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)

                batteryFraction = if (level >= 0 && scale > 0) {
                    (level.toFloat() / scale.toFloat()).coerceIn(0f, 1f)
                } else {
                    1.0f
                }

                isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                        status == BatteryManager.BATTERY_STATUS_FULL

                onBatteryChanged(batteryFraction, isCharging)
            }
        }
    }

    fun start() {
        if (!isRegistered) {
            val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val stickyIntent = context.registerReceiver(receiver, filter)
            // Immediately process sticky intent if available
            stickyIntent?.let { receiver.onReceive(context, it) }
            isRegistered = true
        }
    }

    fun stop() {
        if (isRegistered) {
            try {
                context.unregisterReceiver(receiver)
            } catch (e: Exception) {
                // Receiver might already be unregistered
            }
            isRegistered = false
        }
    }
}
