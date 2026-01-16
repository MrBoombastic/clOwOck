package com.mrboombastic.buwudzik.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.updateAll
import androidx.work.BackoffPolicy
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import com.mrboombastic.buwudzik.data.SensorRepository
import com.mrboombastic.buwudzik.utils.AppLogger
import java.util.concurrent.TimeUnit

/**
 * ActionCallback for handling refresh button clicks in the Glance widget.
 * Triggers a one-time WorkManager request to scan for sensor data.
 */
class RefreshAction : ActionCallback {
    companion object {
        private const val TAG = "RefreshAction"
    }

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        AppLogger.d(TAG, "Refresh button clicked, triggering sensor update")

        // Set loading state and update widget immediately
        SensorRepository(context).setLoading(true)
        AppLogger.d(TAG, "Set loading=true, updating widgets...")

        // Update the specific widget that was clicked (faster) + all others
        SensorGlanceWidget().update(context, glanceId)
        SensorGlanceWidget().updateAll(context)
        AppLogger.d(TAG, "Widget update calls completed, enqueueing worker...")

        // Create work request with force_refresh flag
        val workRequest = OneTimeWorkRequest.Builder(SensorUpdateWorker::class.java)
            .setInputData(
                Data.Builder()
                    .putBoolean("force_refresh", true)
                    .build()
            )
            .setBackoffCriteria(BackoffPolicy.LINEAR, 3, TimeUnit.SECONDS)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            "SensorWidgetRefresh",
            ExistingWorkPolicy.REPLACE,
            workRequest
        )
    }
}
