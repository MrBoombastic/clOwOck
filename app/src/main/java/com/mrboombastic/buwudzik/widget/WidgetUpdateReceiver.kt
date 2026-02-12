package com.mrboombastic.buwudzik.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.mrboombastic.buwudzik.utils.AppLogger

/**
 * BroadcastReceiver triggered by AlarmManager to initiate widget updates.
 * This ensures reliable periodic updates even with aggressive battery optimization.
 */
class WidgetUpdateReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "WidgetUpdateReceiver"
        const val ACTION_UPDATE_WIDGET = "com.mrboombastic.buwudzik.ACTION_UPDATE_WIDGET"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == ACTION_UPDATE_WIDGET) {
            AppLogger.d(TAG, "AlarmManager triggered widget update")

            // Enqueue a one-time work request to fetch sensor data
            val workRequest = OneTimeWorkRequestBuilder<SensorUpdateWorker>()
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                "SensorWidgetRefresh",
                ExistingWorkPolicy.REPLACE,
                workRequest
            )
        }
    }
}
