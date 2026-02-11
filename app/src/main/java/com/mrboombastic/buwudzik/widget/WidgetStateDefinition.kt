package com.mrboombastic.buwudzik.widget

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.core.DataStore
import androidx.glance.state.GlanceStateDefinition
import com.mrboombastic.buwudzik.data.SensorRepository
import com.mrboombastic.buwudzik.data.SettingsRepository
import com.mrboombastic.buwudzik.device.SensorData
import com.mrboombastic.buwudzik.utils.AppLogger
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.io.File

/**
 * Widget state data class that holds all the data needed to render the widget.
 * This is the state that Glance will observe and use for recomposition.
 */
data class WidgetState(
    val sensorData: SensorData? = null,
    val lastUpdate: Long = 0,
    val hasError: Boolean = false,
    val isLoading: Boolean = false,
    val language: String = "system"
)

/**
 * Custom DataStore that reads widget state from repositories and reactively observes changes.
 * This implementation uses callbackFlow to listen for SharedPreferences changes, allowing
 * the widget to automatically update when sensor data or settings change without requiring
 * explicit updateAll() calls.
 */
class WidgetStateDataStore(private val context: Context) : DataStore<WidgetState> {

    companion object {
        private const val TAG = "WidgetStateDataStore"
        
        // SharedPreferences file names (must match those in repositories)
        private const val SENSOR_PREFS_NAME = "sensor_prefs"
        private const val SETTINGS_PREFS_NAME = "settings_prefs"
        
        // Sensor preference keys that affect widget state
        private val SENSOR_KEYS = setOf(
            "temp", "humidity", "battery", "rssi", "name", 
            "mac_address", "timestamp", "has_error", "is_loading"
        )
        
        // Settings preference key that affects widget display
        private const val SETTINGS_KEY_LANGUAGE = "language"
    }

    override val data: Flow<WidgetState>
        get() = callbackFlow {
            val sensorRepo = SensorRepository(context)
            val settingsRepo = SettingsRepository(context)
            
            // Get SharedPreferences instances
            val sensorPrefs = context.getSharedPreferences(SENSOR_PREFS_NAME, Context.MODE_PRIVATE)
            val settingsPrefs = context.getSharedPreferences(SETTINGS_PREFS_NAME, Context.MODE_PRIVATE)
            
            // Function to read and emit current state
            fun emitCurrentState() {
                try {
                    val result = trySend(
                        WidgetState(
                            sensorData = sensorRepo.getSensorData(),
                            lastUpdate = sensorRepo.getLastUpdateTimestamp(),
                            hasError = sensorRepo.hasUpdateError(),
                            isLoading = sensorRepo.isLoading(),
                            language = settingsRepo.language
                        )
                    )
                    // Log if send failed (channel full or closed)
                    if (!result.isSuccess) {
                        AppLogger.w(TAG, "Failed to emit state update: ${result.exceptionOrNull()?.message}")
                    }
                } catch (e: Exception) {
                    // Log any errors from repository operations
                    AppLogger.e(TAG, "Error reading widget state from repositories", e)
                }
            }
            
            // Emit initial state
            emitCurrentState()
            
            // Create listeners (kept as local variables to maintain strong references)
            // Only emit updates for preference keys that affect widget state
            // Note: SharedPreferences listeners are invoked on the main thread, and trySend()
            // is thread-safe, so no additional synchronization is needed
            val sensorListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                // Only react to sensor-related preference changes
                if (key in SENSOR_KEYS) {
                    emitCurrentState()
                }
            }
            
            val settingsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                // Only react to widget-relevant settings changes (language affects widget display)
                if (key == SETTINGS_KEY_LANGUAGE) {
                    emitCurrentState()
                }
            }
            
            // Register listeners
            sensorPrefs.registerOnSharedPreferenceChangeListener(sensorListener)
            settingsPrefs.registerOnSharedPreferenceChangeListener(settingsListener)
            
            // Cleanup when flow is cancelled
            awaitClose {
                sensorPrefs.unregisterOnSharedPreferenceChangeListener(sensorListener)
                settingsPrefs.unregisterOnSharedPreferenceChangeListener(settingsListener)
            }
        }

    override suspend fun updateData(transform: suspend (t: WidgetState) -> WidgetState): WidgetState {
        // We don't update through DataStore - updates go through the repositories
        // Just return the current state
        val sensorRepo = SensorRepository(context)
        val settingsRepo = SettingsRepository(context)

        return WidgetState(
            sensorData = sensorRepo.getSensorData(),
            lastUpdate = sensorRepo.getLastUpdateTimestamp(),
            hasError = sensorRepo.hasUpdateError(),
            isLoading = sensorRepo.isLoading(),
            language = settingsRepo.language
        )
    }
}

/**
 * GlanceStateDefinition that provides the WidgetStateDataStore.
 * Glance uses this to reactively observe state changes and automatically trigger recomposition
 * when SharedPreferences change.
 */
object WidgetStateDefinition : GlanceStateDefinition<WidgetState> {

    override suspend fun getDataStore(context: Context, fileKey: String): DataStore<WidgetState> {
        return WidgetStateDataStore(context)
    }

    override fun getLocation(context: Context, fileKey: String): File {
        // We don't use file-based storage, but we need to return something
        return File(context.cacheDir, "widget_state_placeholder")
    }
}
