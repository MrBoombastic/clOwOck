package com.mrboombastic.buwudzik.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.mrboombastic.buwudzik.utils.AppLogger

/**
 * Manages AlarmManager-based periodic widget updates.
 * Uses setExactAndAllowWhileIdle() for reliable updates even during Doze mode.
 * MinSdk is 34, so setExactAndAllowWhileIdle() is always available.
 */
object WidgetUpdateScheduler {

    private const val TAG = "WidgetUpdateScheduler"
    private const val REQUEST_CODE = 1001

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

        // Calculate next trigger time
        val intervalMillis = intervalMinutes * 60 * 1000L
        val triggerAtMillis = System.currentTimeMillis() + intervalMillis

        val canScheduleExact = alarmManager.canScheduleExactAlarms()

        // Always attempt to schedule exact alarms first
        // This ensures the app is registered in system settings even if it fails
        try {
            if (canScheduleExact) {
                // Use setExactAndAllowWhileIdle for reliable updates
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
                AppLogger.d(TAG, "Scheduled next widget update in $intervalMinutes minutes using exact AlarmManager alarm")
            } else {
                // Permission not granted - attempt exact alarm to trigger system registration
                // This will fail with SecurityException but registers app in settings on some devices
                try {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtMillis,
                        pendingIntent
                    )
                    AppLogger.d(
                        TAG,
                        "Unexpectedly succeeded in scheduling exact alarm without permission"
                    )
                } catch (_: SecurityException) {
                    AppLogger.d(
                        TAG,
                        "Expected SecurityException when attempting exact alarm without permission - app should now be visible in settings"
                    )
                }

                // Fall back to inexact alarm for actual functionality
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
                AppLogger.d(TAG, "Scheduled next widget update in $intervalMinutes minutes using inexact AlarmManager alarm")
            }
        } catch (e: Exception) {
            // Unexpected error - log and fall back to inexact alarm
            AppLogger.e(TAG, "Unexpected error scheduling alarms", e)
            try {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
                AppLogger.d(TAG, "Fallback: Scheduled inexact alarm")
            } catch (fallbackError: Exception) {
                AppLogger.e(TAG, "Failed to schedule any alarm", fallbackError)
            }
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
