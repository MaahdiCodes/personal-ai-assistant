package dev.maahdi.mavick.ai

import android.content.Context
import android.os.BatteryManager
import android.os.PowerManager

/** Whether the phone can spare the AI's work right now. */
fun interface DeviceConditions {
    /** Why the AI should wait, or null when it may work. */
    fun pauseReason(): AiPause?
}

/**
 * The AI waits while Battery Saver is on, while the phone is warm, or while the battery is low and
 * not charging (docs/PLAN.md §5.3, Runtime). It carries on with the next new message.
 */
object AiPauseRules {
    const val LOW_BATTERY_PERCENT = 20

    fun reason(powerSaveMode: Boolean, thermalStatus: Int, batteryPercent: Int, charging: Boolean): AiPause? = when {
        powerSaveMode -> AiPause.BATTERY_SAVER
        thermalStatus >= PowerManager.THERMAL_STATUS_MODERATE -> AiPause.HOT
        // The phone may not report a level (a negative number): then only the other checks count.
        batteryPercent in 0 until LOW_BATTERY_PERCENT && !charging -> AiPause.BATTERY_LOW
        else -> null
    }
}

/** Reads the phone's battery and temperature state: no permission needed. */
class AndroidDeviceConditions(context: Context) : DeviceConditions {
    private val power = context.getSystemService(PowerManager::class.java)
    private val battery = context.getSystemService(BatteryManager::class.java)

    override fun pauseReason(): AiPause? = AiPauseRules.reason(
        powerSaveMode = power.isPowerSaveMode,
        thermalStatus = power.currentThermalStatus,
        batteryPercent = battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY),
        charging = battery.isCharging,
    )
}
