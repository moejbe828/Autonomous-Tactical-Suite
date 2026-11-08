
package com.ats.sentinel

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.SystemClock
import kotlin.math.roundToInt

data class DeviceSnapshot(
    val batteryPercent: Int,
    val chargingState: String,
    val memoryUsedPercent: Int,
    val memoryAvailableMb: Long,
    val memoryTotalMb: Long,
    val uptime: String
)

object DeviceMonitor {

    fun readSnapshot(context: Context): DeviceSnapshot {

        val batteryIntent = context.registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        )

        val level = batteryIntent?.getIntExtra(
            BatteryManager.EXTRA_LEVEL, -1
        ) ?: -1

        val scale = batteryIntent?.getIntExtra(
            BatteryManager.EXTRA_SCALE, 100
        ) ?: 100

        val batteryPercent = if (level >= 0 && scale > 0) {
            ((level * 100f) / scale).roundToInt()
                .coerceIn(0, 100)
        } else {
            -1
        }

        val batteryStatus = batteryIntent?.getIntExtra(
            BatteryManager.EXTRA_STATUS,
            BatteryManager.BATTERY_STATUS_UNKNOWN
        ) ?: BatteryManager.BATTERY_STATUS_UNKNOWN

        val chargingState = when (batteryStatus) {
            BatteryManager.BATTERY_STATUS_CHARGING -> "CHARGING"
            BatteryManager.BATTERY_STATUS_FULL -> "FULL"
            BatteryManager.BATTERY_STATUS_DISCHARGING -> "DISCHARGING"
            BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "NOT CHARGING"
            else -> "UNKNOWN"
        }

        val activityManager =
            context.getSystemService(
                Context.ACTIVITY_SERVICE
            ) as ActivityManager

        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)

        val totalMb = memoryInfo.totalMem / (1024L * 1024L)
        val availableMb = memoryInfo.availMem / (1024L * 1024L)

        val usedPercent = if (totalMb > 0) {
            (((totalMb - availableMb).toFloat() /
                totalMb.toFloat()) * 100f)
                .roundToInt()
                .coerceIn(0, 100)
        } else {
            0
        }

        val uptimeSeconds =
            SystemClock.elapsedRealtime() / 1000L

        val hours = uptimeSeconds / 3600L
        val minutes = (uptimeSeconds % 3600L) / 60L

        val uptime = "${hours}h ${minutes}m"

        return DeviceSnapshot(
            batteryPercent = batteryPercent,
            chargingState = chargingState,
            memoryUsedPercent = usedPercent,
            memoryAvailableMb = availableMb,
            memoryTotalMb = totalMb,
            uptime = uptime
        )
    }
}
