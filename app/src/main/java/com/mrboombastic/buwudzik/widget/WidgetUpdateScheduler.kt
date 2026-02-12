package com.mrboombastic.buwudzik.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.mrboombastic.buwudzik.utils.AppLogger

/**
 * Manages AlarmManager-based periodic widget updates.
 * Uses setInexactRepeating() for recurring alarms with minimal battery impact.
 */
object WidgetUpdateScheduler {

    private const val TAG = "WidgetUpdateScheduler"
    private const val REQUEST_CODE = 1001

    /**
     * Maps a custom interval in minutes to the closest system-defined interval.
     * Uses standard AlarmManager intervals for battery efficiency.
     */
    private fun mapToSystemInterval(intervalMinutes: Long): Long {
        return when {
            intervalMinutes <= 15 -> AlarmManager.INTERVAL_FIFTEEN_MINUTES  // 15 min
            intervalMinutes <= 30 -> AlarmManager.INTERVAL_HALF_HOUR        // 30 min
            intervalMinutes <= 45 -> AlarmManager.INTERVAL_FIFTEEN_MINUTES * 3  // 45 min
            intervalMinutes <= 60 -> AlarmManager.INTERVAL_HOUR             // 1 hour
            intervalMinutes <= 120 -> AlarmManager.INTERVAL_HOUR * 2        // 2 hours
            intervalMinutes <= 240 -> AlarmManager.INTERVAL_HOUR * 4        // 4 hours
            intervalMinutes <= 480 -> AlarmManager.INTERVAL_HOUR * 8        // 8 hours
            intervalMinutes <= 720 -> AlarmManager.INTERVAL_HALF_DAY        // 12 hours
            else -> AlarmManager.INTERVAL_DAY                                // 24 hours (1440 min)
        }
    }

    /**
     * Schedule periodic widget updates using AlarmManager.
     * This ensures updates happen reliably at the specified interval.
     *
     * @param context Application context
     * @param intervalMinutes Update interval in minutes
     */
    fun scheduleUpdates(
        context: Context,
        intervalMinutes: Long,
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        if (alarmManager == null) {
            AppLogger.e(TAG, "AlarmManager not available")
            return
        }

        val pendingIntent = createUpdatePendingIntent(context)

        // Calculate interval and first trigger time
        val intervalMillis = intervalMinutes * 60 * 1000L
        val triggerAtMillis = System.currentTimeMillis() + intervalMillis

        val canScheduleExact = alarmManager.canScheduleExactAlarms()

        try {
            if (canScheduleExact) {
                // Use setRepeating for precise recurring alarms
                alarmManager.setRepeating(
                    AlarmManager.RTC_WAKEUP, triggerAtMillis, intervalMillis, pendingIntent
                )
                AppLogger.d(
                    TAG,
                    "Scheduled repeating widget update every $intervalMinutes minutes using exact alarms"
                )
            } else {
                // Use setInexactRepeating for battery-efficient recurring alarms
                val systemInterval = mapToSystemInterval(intervalMinutes)
                alarmManager.setInexactRepeating(
                    AlarmManager.RTC_WAKEUP, triggerAtMillis, systemInterval, pendingIntent
                )
                AppLogger.d(
                    TAG,
                    "Scheduled inexact repeating widget update (requested: $intervalMinutes min, system interval: ${systemInterval / 60000} min)"
                )
            }
        } catch (e: SecurityException) {
            // Permission not granted - fall back to inexact repeating alarm
            AppLogger.w(
                TAG, "Exact alarm permission not granted, falling back to inexact repeating", e
            )

            val systemInterval = mapToSystemInterval(intervalMinutes)
            try {
                alarmManager.setInexactRepeating(
                    AlarmManager.RTC_WAKEUP, triggerAtMillis, systemInterval, pendingIntent
                )
                AppLogger.d(TAG, "Fallback: Scheduled inexact repeating alarm")
            } catch (fallbackError: Exception) {
                AppLogger.e(TAG, "Failed to schedule any repeating alarm", fallbackError)
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Unexpected error scheduling alarms", e)
        }
    }

    /**
     * Cancel all scheduled widget updates.
     * Call this when the last widget is removed.
     *
     * @param context Application context
     */
    fun cancelUpdates(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        if (alarmManager == null) {
            AppLogger.e(TAG, "AlarmManager not available")
            return
        }

        val pendingIntent = createUpdatePendingIntent(context)

        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()

        AppLogger.d(TAG, "Cancelled widget updates")
    }

    /**
     * Creates a PendingIntent for widget update broadcasts.
     */
    private fun createUpdatePendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, WidgetUpdateReceiver::class.java).apply {
            action = WidgetUpdateReceiver.ACTION_UPDATE_WIDGET
        }

        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

}
