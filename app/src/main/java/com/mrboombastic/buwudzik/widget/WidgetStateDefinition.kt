package com.mrboombastic.buwudzik.widget

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.glance.state.GlanceStateDefinition
import com.mrboombastic.buwudzik.data.SensorRepository
import com.mrboombastic.buwudzik.data.SettingsRepository
import com.mrboombastic.buwudzik.device.SensorData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
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
 * Custom DataStore that reads widget state from repositories.
 * This allows Glance to reactively observe state changes.
 */
class WidgetStateDataStore(private val context: Context) : DataStore<WidgetState> {

    override val data: Flow<WidgetState>
        get() = flow {
            val sensorRepo = SensorRepository(context)
            val settingsRepo = SettingsRepository(context)

            emit(
                WidgetState(
                    sensorData = sensorRepo.getSensorData(),
                    lastUpdate = sensorRepo.getLastUpdateTimestamp(),
                    hasError = sensorRepo.hasUpdateError(),
                    isLoading = sensorRepo.isLoading(),
                    language = settingsRepo.language
                )
            )
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
 * This is what Glance uses to observe state changes and trigger recomposition.
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
