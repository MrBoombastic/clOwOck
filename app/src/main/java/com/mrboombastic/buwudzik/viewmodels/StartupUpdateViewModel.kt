package com.mrboombastic.buwudzik.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mrboombastic.buwudzik.BuildConfig
import com.mrboombastic.buwudzik.UpdateCheckResult
import com.mrboombastic.buwudzik.UpdateManager
import com.mrboombastic.buwudzik.create
import com.mrboombastic.buwudzik.data.SettingsRepository
import com.mrboombastic.buwudzik.utils.AppLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Automatic update check at app start. Lives in a ViewModel so that recreating the activity,
 * for example on rotation, neither repeats the check nor loses a dialog that is still open.
 */
class StartupUpdateViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "StartupUpdateViewModel"

        /**
         * Release: at most one automatic check per day.
         * Debug: no throttle, so every cold start re-runs the check (fast manual/CI-style retests).
         */
        private val AUTO_UPDATE_CHECK_INTERVAL_MS =
            if (BuildConfig.DEBUG) 0L else 24L * 60L * 60L * 1000L
    }

    private val _availableUpdate = MutableStateFlow<UpdateCheckResult?>(null)

    /** The update to offer, or null when there is none or the user has dismissed it. */
    val availableUpdate: StateFlow<UpdateCheckResult?> = _availableUpdate.asStateFlow()

    init {
        checkIfDue()
    }

    fun dismiss() {
        _availableUpdate.value = null
    }

    private fun checkIfDue() {
        if (!BuildConfig.ALLOW_CUSTOM_UPDATES) return
        val settingsRepository = SettingsRepository(getApplication())
        if (!settingsRepository.autoUpdateCheckEnabled) return
        val now = System.currentTimeMillis()
        if (now - settingsRepository.lastAutoUpdateCheckMs < AUTO_UPDATE_CHECK_INTERVAL_MS) return
        // Enforce at-most-once-per-day attempts even if the request fails.
        settingsRepository.lastAutoUpdateCheckMs = now

        viewModelScope.launch {
            try {
                val updateManager = UpdateManager.create(getApplication())
                val result = try {
                    updateManager.checkForUpdates()
                } finally {
                    updateManager.close()
                }
                if (result.updateAvailable) _availableUpdate.value = result
            } catch (e: Exception) {
                AppLogger.w(TAG, "Automatic update check failed: ${e.message}", e)
            }
        }
    }
}
