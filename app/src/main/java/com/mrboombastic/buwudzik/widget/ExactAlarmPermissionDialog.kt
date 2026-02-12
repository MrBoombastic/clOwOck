package com.mrboombastic.buwudzik.widget

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.content.edit
import androidx.core.net.toUri
import com.mrboombastic.buwudzik.utils.AppLogger

/**
 * Helper object for managing exact alarm permission prompt and navigation to settings.
 */
object ExactAlarmPermissionDialog {

    private const val TAG = "ExactAlarmPermissionDialog"
    private const val PREFS_NAME = "settings_prefs"
    private const val KEY_EXACT_ALARM_PROMPT_SHOWN = "exact_alarm_prompt_shown"

    /**
     * Checks if the exact alarm permission prompt should be shown to the user.
     *
     * @param context Application context (preferably from an Activity)
     * @return True if the prompt should be shown, false otherwise
     */
    fun shouldShowPrompt(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return !prefs.getBoolean(KEY_EXACT_ALARM_PROMPT_SHOWN, false)
    }

    /**
     * Marks the exact alarm permission prompt as shown, preventing it from being displayed again.
     *
     * @param context Application context (preferably from an Activity)
     */
    fun markPromptShown(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit { putBoolean(KEY_EXACT_ALARM_PROMPT_SHOWN, true) }
        AppLogger.d(TAG, "Exact alarm prompt flag set")
    }

    /**
     * Opens the system settings page for this app's exact alarm permission.
     *
     * @param context Application context (preferably from an Activity)
     */
    fun openSystemAlarmSettings(context: Context) {
        val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
            data = "package:${context.packageName}".toUri()
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        try {
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                AppLogger.d(TAG, "Opened system exact alarm settings")
            } else {
                AppLogger.w(TAG, "Exact alarm settings screen unavailable on this device")
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Unable to open exact alarm settings", e)
        }
    }

    /**
     * Clears the one-time prompt flag for exact alarm permission.
     *
     * @param context Application context (preferably from an Activity)
     */
    fun clearPromptFlag(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit { remove(KEY_EXACT_ALARM_PROMPT_SHOWN) }
        AppLogger.d(TAG, "Exact alarm prompt flag cleared")
    }
}
