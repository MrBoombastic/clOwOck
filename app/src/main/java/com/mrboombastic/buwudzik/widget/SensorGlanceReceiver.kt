package com.mrboombastic.buwudzik.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import com.mrboombastic.buwudzik.data.SettingsRepository
import com.mrboombastic.buwudzik.utils.AppLogger

class SensorGlanceReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SensorGlanceWidget()

    companion object {
        private const val TAG = "SensorGlanceReceiver"
    }

    /**
     * Called when the first widget is added.
     * Schedule periodic updates using AlarmManager to ensure reliable widget updates.
     */
    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        AppLogger.d(TAG, "Widget enabled - scheduling periodic updates with AlarmManager")

        val settingsRepository = SettingsRepository(context)
        val intervalMinutes = settingsRepository.updateInterval

        // Schedule periodic updates using AlarmManager for reliable widget updates
        WidgetUpdateScheduler.scheduleUpdates(context, intervalMinutes)
    }

    /**
     * Called when the last widget is removed.
     * Cancel periodic updates to save battery since no widgets need updating.
     */
    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        AppLogger.d(TAG, "Widget disabled - canceling periodic updates")

        // Cancel AlarmManager updates
        WidgetUpdateScheduler.cancelUpdates(context)
    }
}
