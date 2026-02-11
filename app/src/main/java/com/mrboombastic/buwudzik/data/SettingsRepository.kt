package com.mrboombastic.buwudzik.data

import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.glance.appwidget.updateAll
import com.mrboombastic.buwudzik.utils.AppLogger
import com.mrboombastic.buwudzik.widget.SensorGlanceWidget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "SettingsRepository"


class SettingsRepository(private val context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("settings_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_TARGET_MAC = "target_mac"
        private const val KEY_SCAN_MODE = "scan_mode"

        const val DEFAULT_MAC = "21:37:13:37:04:20"
        const val DEFAULT_SCAN_MODE = android.bluetooth.le.ScanSettings.SCAN_MODE_BALANCED

        private const val KEY_LANGUAGE = "language"
        private const val KEY_UPDATE_INTERVAL = "update_interval"
        const val DEFAULT_LANGUAGE = "system"
        const val DEFAULT_INTERVAL = 15L

        private const val KEY_SELECTED_APP = "selected_app_package"
        private const val KEY_THEME = "app_theme"
        const val DEFAULT_THEME = "system"

        private const val KEY_SETUP_COMPLETED = "setup_completed"
        private const val KEY_LAST_VERSION_CODE = "last_version_code"

        private const val KEY_BATTERY_TYPE = "battery_type"
        const val DEFAULT_BATTERY_TYPE = "alkaline"
    }

    /**
     * Updates all widgets to reflect setting changes.
     * This is a suspend function that runs on a background dispatcher.
     * Callers must launch it in an appropriate coroutine scope.
     */
    suspend fun updateAllWidgets() = withContext(Dispatchers.IO) {
        SensorGlanceWidget().updateAll(context)
    }

    var lastVersionCode: Int
        get() = prefs.getInt(KEY_LAST_VERSION_CODE, -1)
        set(value) {
            prefs.edit { putInt(KEY_LAST_VERSION_CODE, value) }
        }

    var batteryType: String
        get() = prefs.getString(KEY_BATTERY_TYPE, DEFAULT_BATTERY_TYPE) ?: DEFAULT_BATTERY_TYPE
        set(value) {
            prefs.edit { putString(KEY_BATTERY_TYPE, value) }
        }

    var targetMacAddress: String
        get() {
            val mac = prefs.getString(KEY_TARGET_MAC, DEFAULT_MAC) ?: DEFAULT_MAC
            return mac.trim().ifEmpty { DEFAULT_MAC }
        }
        set(value) {
            val macToSave = value.trim().ifEmpty { DEFAULT_MAC }
            if (BluetoothAdapter.checkBluetoothAddress(macToSave)) {
                prefs.edit { putString(KEY_TARGET_MAC, macToSave.uppercase()) }
            } else {
                // Log warning and ignore invalid input
                AppLogger.w(TAG, "Attempted to save invalid MAC: $macToSave")
            }
        }

    var scanMode: Int
        get() = prefs.getInt(KEY_SCAN_MODE, DEFAULT_SCAN_MODE)
        set(value) {
            prefs.edit { putInt(KEY_SCAN_MODE, value) }
        }

    var language: String
        get() = prefs.getString(KEY_LANGUAGE, DEFAULT_LANGUAGE) ?: DEFAULT_LANGUAGE
        set(value) {
            prefs.edit { putString(KEY_LANGUAGE, value) }
        }

    var updateInterval: Long
        get() = prefs.getLong(KEY_UPDATE_INTERVAL, DEFAULT_INTERVAL).coerceAtLeast(15)
        set(value) {
            prefs.edit { putLong(KEY_UPDATE_INTERVAL, value.coerceAtLeast(15)) }
        }

    var selectedAppPackage: String?
        get() = prefs.getString(KEY_SELECTED_APP, null)
        set(value) {
            prefs.edit { putString(KEY_SELECTED_APP, value) }
        }

    var theme: String
        get() = prefs.getString(KEY_THEME, DEFAULT_THEME) ?: DEFAULT_THEME
        set(value) {
            prefs.edit { putString(KEY_THEME, value) }
        }

    var isSetupCompleted: Boolean
        get() = prefs.getBoolean(KEY_SETUP_COMPLETED, false)
        set(value) {
            prefs.edit { putBoolean(KEY_SETUP_COMPLETED, value) }
        }
}
