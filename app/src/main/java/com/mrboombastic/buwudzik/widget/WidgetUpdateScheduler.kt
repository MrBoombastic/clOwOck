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
    fun scheduleUpdates(context: Context, intervalMinutes: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        if (alarmManager == null) {
            AppLogger.e(TAG, "AlarmManager not available")
            return
        }

        val intent = Intent(context, WidgetUpdateReceiver::class.java).apply {
            action = WidgetUpdateReceiver.ACTION_UPDATE_WIDGET
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Calculate next trigger time
        val intervalMillis = intervalMinutes * 60 * 1000L
        val triggerAtMillis = System.currentTimeMillis() + intervalMillis

        // Prefer exact alarms when allowed; fall back to inexact to avoid crashes when not permitted
        if (alarmManager.canScheduleExactAlarms()) {
            try {
                // Use setExactAndAllowWhileIdle for reliable updates
                // MinSdk 34 guarantees this API is available (added in API 23)
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
                AppLogger.d(TAG, "Scheduled next widget update in $intervalMinutes minutes using exact AlarmManager alarm")
            } catch (se: SecurityException) {
                // Exact alarms not allowed at runtime; fall back to inexact alarm to avoid crashing
                AppLogger.e(TAG, "Exact alarm not permitted, falling back to inexact alarm for widget updates", se)
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
                AppLogger.d(TAG, "Scheduled next widget update in $intervalMinutes minutes using inexact AlarmManager alarm")
            }
        } else {
            // Exact alarms are not allowed; use inexact alarm instead
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent
            )
            AppLogger.d(TAG, "Scheduled next widget update in $intervalMinutes minutes using inexact AlarmManager alarm")
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

        val intent = Intent(context, WidgetUpdateReceiver::class.java).apply {
            action = WidgetUpdateReceiver.ACTION_UPDATE_WIDGET
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()

        AppLogger.d(TAG, "Cancelled widget updates")
    }
}
