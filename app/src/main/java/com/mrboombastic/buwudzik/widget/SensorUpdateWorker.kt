package com.mrboombastic.buwudzik.widget

import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.util.Log
import androidx.glance.appwidget.updateAll
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.mrboombastic.buwudzik.data.SensorRepository
import com.mrboombastic.buwudzik.data.SettingsRepository
import com.mrboombastic.buwudzik.device.BluetoothScanner
import com.mrboombastic.buwudzik.utils.AppLogger
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

class SensorUpdateWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "SensorUpdateWorker"
        private const val SCAN_TIMEOUT_MS = 15_000L
        private const val MAX_RETRY_ATTEMPTS = 3
        private const val FRESH_DATA_THRESHOLD_MS = 5_000L
    }

    override suspend fun doWork(): Result {
        AppLogger.d(TAG, "Starting background scan (attempt ${runAttemptCount + 1})...")

        val repository = SensorRepository(applicationContext)
        val settingsRepository = SettingsRepository(applicationContext)

        // Check if this is a forced refresh (user pressed button) - skip cache check on first attempt only
        val forceRefresh = inputData.getBoolean("force_refresh", false)
        val isRetry = runAttemptCount > 0

        // Check if we have fresh data already (from foreground app)
        val lastUpdate = repository.getLastUpdateTimestamp()
        val dataAge = System.currentTimeMillis() - lastUpdate
        val shouldSkipScan = dataAge < FRESH_DATA_THRESHOLD_MS && lastUpdate > 0

        if (shouldSkipScan && (!forceRefresh || isRetry)) {
            AppLogger.d(TAG, "Data is fresh (${dataAge}ms old), skipping scan and updating widget")
            updateWidget(hasError = false)
            return Result.success()
        }

        if (forceRefresh && !isRetry) {
            AppLogger.d(TAG, "Force refresh requested, bypassing cache check")
        }

        val bluetoothManager =
            applicationContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        if (bluetoothManager?.adapter == null) {
            Log.e(TAG, "Bluetooth adapter not available.")
            updateWidget(hasError = true)
            return Result.failure()
        }

        if (!bluetoothManager.adapter.isEnabled) {
            Log.w(TAG, "Bluetooth is disabled. Showing error indicator.")
            updateWidget(hasError = true)
            return Result.success()  // Don't fail, just show error
        }

        val scanner = BluetoothScanner(applicationContext)
        val targetMac = settingsRepository.targetMacAddress

        // Use LOW_LATENCY mode for faster widget updates in background
        val result = withTimeoutOrNull(SCAN_TIMEOUT_MS) {
            try {
                scanner.scan(targetMac, ScanSettings.SCAN_MODE_BALANCED).first()
            } catch (e: Exception) {
                Log.e(TAG, "Error during scan", e)
                null
            }
        }

        return if (result != null) {
            AppLogger.d(
                TAG,
                "Got data: temp=${result.temperature}°C, humidity=${result.humidity}%, battery=${result.battery}%"
            )
            repository.saveSensorData(result)
            updateWidget(hasError = false)  // Success - clear any error indicator
            Result.success()
        } else {
            Log.w(TAG, "No sensor data received within timeout (device may be out of range).")

            // Check again if data arrived from foreground while we were scanning
            val freshLastUpdate = repository.getLastUpdateTimestamp()
            if (freshLastUpdate > lastUpdate) {
                AppLogger.d(TAG, "Fresh data arrived during scan, updating widget")
                updateWidget(hasError = false)
                return Result.success()
            }

            // Retry if we haven't exceeded max attempts
            if (runAttemptCount < MAX_RETRY_ATTEMPTS) {
                AppLogger.d(
                    TAG,
                    "Will retry (attempt ${runAttemptCount + 1} of $MAX_RETRY_ATTEMPTS)"
                )
                Result.retry()
            } else {
                Log.w(TAG, "Max retry attempts reached. Device appears unreachable.")
                updateWidget(hasError = true)  // Show error indicator after all retries failed
                Result.success()  // Return success to keep periodic work scheduled
            }
        }
    }

    private suspend fun updateWidget(hasError: Boolean) {
        try {
            // Store error and loading state in repository for Glance widget
            val repository = SensorRepository(applicationContext)
            repository.setUpdateError(hasError)
            repository.setLoading(false)  // Clear loading state

            // Update Glance widget
            AppLogger.d(TAG, "Updating Glance widget, hasError=$hasError")
            SensorGlanceWidget().updateAll(applicationContext)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update widget", e)
        }
    }
}





