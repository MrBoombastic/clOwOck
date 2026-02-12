package com.mrboombastic.buwudzik.ui.screens

import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.ScanSettings
import android.content.Intent
import android.content.pm.ResolveInfo
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavController
import com.mrboombastic.buwudzik.MainActivity
import com.mrboombastic.buwudzik.R
import com.mrboombastic.buwudzik.UpdateCheckResult
import com.mrboombastic.buwudzik.UpdateChecker
import com.mrboombastic.buwudzik.data.SettingsRepository
import com.mrboombastic.buwudzik.ui.components.BackNavigationButton
import com.mrboombastic.buwudzik.ui.components.CustomSnackbarHost
import com.mrboombastic.buwudzik.ui.components.SettingsDropdown
import com.mrboombastic.buwudzik.ui.utils.ThemeUtils
import com.mrboombastic.buwudzik.utils.AppLogger
import com.mrboombastic.buwudzik.viewmodels.MainViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "SettingsScreen"


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(navController: NavController, viewModel: MainViewModel) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val repository = remember { SettingsRepository(context) }

    // Save initial values to detect changes
    val initialMacAddress = remember { repository.targetMacAddress }
    val initialScanMode = remember { repository.scanMode }

    var macAddress by remember { mutableStateOf(repository.targetMacAddress) }
    var isMacAddressValid by remember { mutableStateOf(true) }
    var scanMode by remember { mutableIntStateOf(repository.scanMode) }
    var language by remember { mutableStateOf(repository.language) }
    var updateInterval by remember { mutableLongStateOf(repository.updateInterval) }
    var selectedAppPackage by remember { mutableStateOf(repository.selectedAppPackage) }
    var theme by remember { mutableStateOf(repository.theme) }
    var ringtoneBaseUrl by remember { mutableStateOf(repository.ringtoneBaseUrl) }

    var expandedWidgetAction by remember { mutableStateOf(false) }

    var installedApps by remember { mutableStateOf<List<ResolveInfo>>(emptyList()) }
    var selectedAppLabel by remember { mutableStateOf<String?>(null) }

    var isCheckingUpdates by remember { mutableStateOf(false) }
    var updateResult by remember { mutableStateOf<UpdateCheckResult?>(null) }
    var versionName by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var showUpdateDialog by remember { mutableStateOf(false) }

    // Helper function to update widgets with proper error handling
    fun launchWidgetUpdate() {
        coroutineScope.launch {
            try {
                repository.updateAllWidgets()
            } catch (e: CancellationException) {
                // Rethrow cancellation to maintain structured concurrency
                throw e
            } catch (e: Exception) {
                AppLogger.d("SettingsScreen", "Widget update failed", e)
            }
        }
    }

    // Load version name asynchronously to avoid blocking main thread
    LaunchedEffect(Unit) {
        val fetchedVersionName = withContext(Dispatchers.IO) {
            try {
                val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                packageInfo.versionName
            } catch (_: Exception) {
                null
            }
        }
        versionName = fetchedVersionName
    }

    // Watch for MAC changes when returning from device setup screen
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                // Check if MAC was changed while we were away (e.g., from setup screen)
                val currentMac = repository.targetMacAddress
                if (currentMac != macAddress) {
                    macAddress = currentMac
                    // If MAC changed from initial value, restart scanning
                    if (currentMac != initialMacAddress) {
                        AppLogger.d(
                            "SettingsScreen",
                            "MAC changed from $initialMacAddress to $currentMac, restarting scan"
                        )
                        viewModel.restartScanning()
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(expandedWidgetAction) {
        if (expandedWidgetAction && installedApps.isEmpty()) {
            withContext(Dispatchers.IO) {
                val pm = context.packageManager
                val intent = Intent(Intent.ACTION_MAIN, null).addCategory(Intent.CATEGORY_LAUNCHER)
                val apps = pm.queryIntentActivities(intent, 0)
                AppLogger.d("SettingsScreen", "Found ${apps.size} launcher apps")
                installedApps = apps.sortedBy { it.loadLabel(pm).toString().lowercase() }
            }
        }
    }

    LaunchedEffect(selectedAppPackage) {
        if (selectedAppPackage != null) {

            withContext(Dispatchers.IO) {
                try {
                    val pm = context.packageManager
                    val appInfo = pm.getApplicationInfo(selectedAppPackage!!, 0)
                    selectedAppLabel = pm.getApplicationLabel(appInfo).toString()
                } catch (_: Exception) {
                    selectedAppLabel = selectedAppPackage
                }
            }
        } else {
            selectedAppLabel = null
        }
    }

    val scanModes = mapOf(
        ScanSettings.SCAN_MODE_LOW_POWER to stringResource(R.string.mode_low_power),
        ScanSettings.SCAN_MODE_BALANCED to stringResource(R.string.mode_balanced),
        ScanSettings.SCAN_MODE_LOW_LATENCY to stringResource(R.string.mode_low_latency)
    )

    val languages = mapOf(
        "system" to stringResource(R.string.language_system),
        "en" to stringResource(R.string.language_english),
        "pl" to stringResource(R.string.language_polish)
    )

    val intervals = mapOf(
        15L to stringResource(R.string.minutes_15),
        30L to stringResource(R.string.minutes_30),
        45L to stringResource(R.string.minutes_45),
        60L to stringResource(R.string.minutes_60),
        120L to stringResource(R.string.hours_2),
        240L to stringResource(R.string.hours_4),
        480L to stringResource(R.string.hours_8),
        720L to stringResource(R.string.hours_12),
        1440L to stringResource(R.string.hours_24)
    )

    val themes = mapOf(
        "system" to stringResource(R.string.theme_system),
        "light" to stringResource(R.string.theme_light),
        "dark" to stringResource(R.string.theme_dark)
    )



    Scaffold(snackbarHost = { CustomSnackbarHost(snackbarHostState) }, topBar = {
        TopAppBar(title = { Text(stringResource(R.string.settings_title)) }, navigationIcon = {
            BackNavigationButton(navController) {
                val finalMac = macAddress.trim().ifEmpty { SettingsRepository.DEFAULT_MAC }
                val isValid = BluetoothAdapter.checkBluetoothAddress(finalMac)

                if (isValid && finalMac != repository.targetMacAddress) {
                    repository.targetMacAddress = finalMac
                }

                // Restart scanning if MAC or scan mode changed
                if (isValid && (finalMac != initialMacAddress || scanMode != initialScanMode)) {
                    viewModel.restartScanning()
                }
                navController.popBackStack()
            }
        })
    }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // MAC Address with Scan Button
            OutlinedTextField(
                value = macAddress,
                onValueChange = {
                    val uppercased = it.uppercase()
                    macAddress = uppercased
                    val trimmed = uppercased.trim()

                    // Validate MAC address format (case-insensitive)
                    isMacAddressValid =
                        trimmed.isEmpty() || BluetoothAdapter.checkBluetoothAddress(trimmed)

                    // Save to repository only if it's a valid and complete MAC address (17 chars)
                    // or if it's cleared (will revert to default in BackNavigationButton)
                    if (trimmed.length == 17 && isMacAddressValid) {
                        repository.targetMacAddress = trimmed
                        AppLogger.d("SettingsScreen", "MAC Address updated to: $trimmed")
                    }
                },
                label = { Text(stringResource(R.string.target_mac_label)) },
                modifier = Modifier.fillMaxWidth(),
                isError = !isMacAddressValid,
                supportingText = if (!isMacAddressValid) {
                    {
                        Text(
                            text = stringResource(R.string.invalid_mac_format),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                } else null,
                trailingIcon = {
                    FilledTonalIconButton(
                        onClick = {
                            // Mark setup as incomplete to show device selection
                            repository.isSetupCompleted = false
                            navController.navigate("setup")
                        }, modifier = Modifier.padding(end = 4.dp)
                    ) {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = stringResource(R.string.scan_devices_button),
                        )
                    }
                })


            // Scan Mode
            Spacer(modifier = Modifier.height(12.dp))

            SettingsDropdown(
                value = scanMode,
                options = scanModes,
                label = stringResource(R.string.scan_mode_label),
                onValueChange = { mode ->
                    scanMode = mode
                    repository.scanMode = mode
                })
            Text(
                text = stringResource(R.string.scan_mode_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp)
            )

            // Language
            Spacer(modifier = Modifier.height(12.dp))

            SettingsDropdown(
                value = language,
                options = languages,
                label = stringResource(R.string.language_label),
                onValueChange = { code ->
                    language = code
                    repository.language = code
                    // Apply Language Immediately
                    val appLocale = if (code == "system") {
                        LocaleListCompat.getEmptyLocaleList()
                    } else {
                        LocaleListCompat.forLanguageTags(code)
                    }
                    AppCompatDelegate.setApplicationLocales(appLocale)
                    // Update widgets in a structured coroutine scope
                    launchWidgetUpdate()
                })

            // Theme
            Spacer(modifier = Modifier.height(12.dp))

            SettingsDropdown(
                value = theme,
                options = themes,
                label = stringResource(R.string.theme_label),
                onValueChange = { code ->
                    theme = code
                    repository.theme = code
                    ThemeUtils.applyTheme(code)
                })


            // Update Interval
            Spacer(modifier = Modifier.height(12.dp))

            SettingsDropdown(
                value = updateInterval,
                options = intervals,
                label = stringResource(R.string.widget_update_interval_label),
                onValueChange = { minutes ->
                    updateInterval = minutes
                    repository.updateInterval = minutes
                    // Reschedule updates immediately with new interval
                    MainActivity.rescheduleUpdates(context, minutes)
                })

            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = ringtoneBaseUrl,
                onValueChange = { newValue ->
                    ringtoneBaseUrl = newValue
                    repository.ringtoneBaseUrl = newValue
                },
                label = { Text(stringResource(R.string.ringtone_base_url_label)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Text(
                text = stringResource(R.string.ringtone_base_url_hint) + " " + stringResource(R.string.ringtone_base_url_json_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp)
            )

            // Widget Action
            Spacer(modifier = Modifier.height(12.dp))

            ExposedDropdownMenuBox(
                expanded = expandedWidgetAction,
                onExpandedChange = { expandedWidgetAction = !expandedWidgetAction }) {
                OutlinedTextField(
                    value = selectedAppLabel ?: stringResource(R.string.default_app_label),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.widget_select_app_label)) },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedWidgetAction)
                    },
                    modifier = Modifier
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                        .fillMaxWidth()
                )

                ExposedDropdownMenu(
                    expanded = expandedWidgetAction,
                    onDismissRequest = { expandedWidgetAction = false },
                    modifier = Modifier.height(300.dp)
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.default_app_label)) },
                        onClick = {
                            selectedAppPackage = null

                            repository.selectedAppPackage = null

                            expandedWidgetAction = false

                            // Update widgets in a structured coroutine scope
                            launchWidgetUpdate()
                        })

                    installedApps.forEach { resolveInfo ->
                        val pm = context.packageManager
                        val label = resolveInfo.loadLabel(pm).toString()
                        val pkg = resolveInfo.activityInfo.packageName
                        DropdownMenuItem(text = { Text(label) }, onClick = {
                            selectedAppPackage = pkg

                            repository.selectedAppPackage = pkg

                            expandedWidgetAction = false

                            // Update widgets in a structured coroutine scope
                            launchWidgetUpdate()
                        })
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Import Device Button
            Button(
                onClick = { navController.navigate("device-import") },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.import_device_button))
            }

            // Update Available Dialog
            if (showUpdateDialog && updateResult != null) {
                androidx.compose.material3.AlertDialog(onDismissRequest = {
                    showUpdateDialog = false
                }, title = { Text(stringResource(R.string.update_available_title)) }, text = {
                    Text(
                        stringResource(
                            R.string.update_available_message,
                            updateResult!!.currentVersion,
                            updateResult!!.latestVersion
                        )
                    )
                }, confirmButton = {
                    androidx.compose.material3.TextButton(
                        onClick = {
                            showUpdateDialog = false
                            val downloadUrl = updateResult?.downloadUrl
                            if (downloadUrl != null) {
                                coroutineScope.launch {
                                    val updateChecker = UpdateChecker(appContext)
                                    updateChecker.downloadAndInstall(downloadUrl)
                                    updateChecker.close()
                                }
                            }
                        }) {
                        Text(stringResource(R.string.download_update))
                    }
                }, dismissButton = {
                    androidx.compose.material3.TextButton(
                        onClick = { showUpdateDialog = false }) {
                        Text(stringResource(R.string.later))
                    }
                })
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))

            // App Info
            Text(
                stringResource(R.string.about_app_label),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                stringResource(
                    R.string.version_label, versionName ?: stringResource(R.string.na_placeholder)
                )
            )
            Text(stringResource(R.string.author_label, stringResource(R.string.app_author_name)))


            Spacer(modifier = Modifier.height(12.dp))

            // Github Button
            val githubUrl = stringResource(R.string.github_repo_url)
            val githubErrorMsg = stringResource(R.string.error_cannot_open_github)
            Button(
                onClick = {
                    try {
                        val intent = Intent(
                            Intent.ACTION_VIEW, githubUrl.toUri()
                        )
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        AppLogger.e(TAG, "Error opening GitHub URL", e)
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar(githubErrorMsg)
                        }
                    }
                }, colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF24292e), contentColor = Color.White
                ), modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.github_label))
            }

            Spacer(modifier = Modifier.height(8.dp))


            // Check for Updates
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = {
                    isCheckingUpdates = true
                    coroutineScope.launch {
                        try {
                            val updateChecker = UpdateChecker(appContext)
                            val result = try {
                                updateChecker.checkForUpdates()
                            } finally {
                                updateChecker.close()
                            }

                            withContext(Dispatchers.Main) {
                                isCheckingUpdates = false
                                if (result.updateAvailable) {
                                    updateResult = result
                                    showUpdateDialog = true
                                } else {
                                    snackbarHostState.showSnackbar(
                                        appContext.getString(R.string.no_updates_available),
                                        duration = SnackbarDuration.Long
                                    )
                                }
                            }
                        } catch (e: Exception) {
                            AppLogger.e("SettingsScreen", "Error checking for updates", e)
                            withContext(Dispatchers.Main) {
                                isCheckingUpdates = false
                                snackbarHostState.showSnackbar(appContext.getString(R.string.update_error))
                            }
                        }
                    }
                }, enabled = !isCheckingUpdates, modifier = Modifier.fillMaxWidth()
            ) {
                if (isCheckingUpdates) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
                Text(
                    text = if (isCheckingUpdates) stringResource(R.string.checking_updates)
                    else stringResource(R.string.check_updates_label)
                )
            }


            // Easter Egg Button
            Spacer(modifier = Modifier.height(8.dp))
            val deviceConnected by viewModel.deviceConnected.collectAsState()
            if (deviceConnected) {
                val successMsg = stringResource(R.string.time_set_success)
                val failedMsg = stringResource(R.string.time_set_failed)
                val errorTemplate = stringResource(R.string.error_prefix)
                val unknownErrorMsg = stringResource(R.string.unknown_error)

                Button(
                    onClick = {
                        coroutineScope.launch {
                            try {
                                val cal = java.util.Calendar.getInstance()
                                cal.set(java.util.Calendar.HOUR_OF_DAY, 21)
                                cal.set(java.util.Calendar.MINUTE, 37)
                                cal.set(java.util.Calendar.SECOND, 0)
                                val timestamp = cal.timeInMillis / 1000
                                val success = viewModel.qpController.synchronizeTime(timestamp)
                                if (success) {
                                    snackbarHostState.showSnackbar(successMsg)
                                } else {
                                    snackbarHostState.showSnackbar(failedMsg)
                                }
                            } catch (e: Exception) {
                                AppLogger.e("SettingsScreen", "Error setting time", e)
                                snackbarHostState.showSnackbar(
                                    errorTemplate.format(e.message ?: unknownErrorMsg)
                                )
                            }
                        }
                    }, modifier = Modifier.fillMaxWidth()
                ) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(stringResource(R.string.set_time_2137))
                }
            }
        }
    }
}
