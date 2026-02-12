package com.mrboombastic.buwudzik.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
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
        promptUserForExactAlarms: Boolean = false
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
        if (promptUserForExactAlarms && !canScheduleExact) {
            // Prompt user only for exact alarm permission here to avoid launching multiple
            // Settings activities back-to-back. Any additional prompts (e.g., battery
            // optimization exemption) should be handled separately in the UI layer.
            requestExactAlarmPermission(context)
        }

        // Prefer exact alarms when allowed; fall back to "inexact" to avoid crashes when not permitted
        if (canScheduleExact) {
            try {
                // Use setExactAndAllowWhileIdle for reliable updates
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

    /**
     * Request exact alarm permission when the app cannot schedule exact alarms.
     */
    private fun requestExactAlarmPermission(context: Context) {
        val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
            data = Uri.parse("package:${context.packageName}")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        try {
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                AppLogger.d(TAG, "Prompted user to allow exact alarm scheduling")
            } else {
                AppLogger.w(TAG, "Exact alarm settings screen unavailable")
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Unable to request exact alarm permission", e)
        }
    }

    /**
     * Request battery optimization exemption to improve alarm reliability.
     */
    private fun requestBatteryOptimizationExemption(context: Context) {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        if (powerManager == null) {
            AppLogger.e(TAG, "PowerManager not available")
            return
        }

        if (powerManager.isIgnoringBatteryOptimizations(context.packageName)) {
            return
        }

        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        try {
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                AppLogger.d(TAG, "Prompted user to exempt app from battery optimizations")
            } else {
                AppLogger.w(TAG, "Battery optimization settings screen unavailable")
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Unable to request battery optimization exemption", e)
        }
    }
}
