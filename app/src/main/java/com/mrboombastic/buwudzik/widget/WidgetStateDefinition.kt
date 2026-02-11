package com.mrboombastic.buwudzik.widget

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.core.DataStore
import androidx.glance.state.GlanceStateDefinition
import com.mrboombastic.buwudzik.data.SensorRepository
import com.mrboombastic.buwudzik.data.SettingsRepository
import com.mrboombastic.buwudzik.device.SensorData
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

    override val data: Flow<WidgetState>
        get() = callbackFlow {
            val sensorRepo = SensorRepository(context)
            val settingsRepo = SettingsRepository(context)
            
            // Get SharedPreferences instances
            val sensorPrefs = context.getSharedPreferences("sensor_prefs", Context.MODE_PRIVATE)
            val settingsPrefs = context.getSharedPreferences("settings_prefs", Context.MODE_PRIVATE)
            
            // Function to read and emit current state
            fun emitCurrentState() {
                trySend(
                    WidgetState(
                        sensorData = sensorRepo.getSensorData(),
                        lastUpdate = sensorRepo.getLastUpdateTimestamp(),
                        hasError = sensorRepo.hasUpdateError(),
                        isLoading = sensorRepo.isLoading(),
                        language = settingsRepo.language
                    )
                )
            }
            
            // Emit initial state
            emitCurrentState()
            
            // Create listeners (keep as local variables to maintain strong references)
            val sensorListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
                emitCurrentState()
            }
            
            val settingsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
                emitCurrentState()
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
