package com.mrboombastic.buwudzik


import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PhonelinkSetup
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.mrboombastic.buwudzik.data.SettingsRepository
import com.mrboombastic.buwudzik.device.BluetoothScanner
import com.mrboombastic.buwudzik.device.SensorData
import com.mrboombastic.buwudzik.ui.components.CustomSnackbarHost
import com.mrboombastic.buwudzik.ui.components.MenuTile
import com.mrboombastic.buwudzik.ui.screens.AlarmManagementScreen
import com.mrboombastic.buwudzik.ui.screens.DeviceImportScreen
import com.mrboombastic.buwudzik.ui.screens.DeviceSettingsScreen
import com.mrboombastic.buwudzik.ui.screens.DeviceSetupScreen
import com.mrboombastic.buwudzik.ui.screens.DeviceSharingScreen
import com.mrboombastic.buwudzik.ui.screens.RingtoneUploadScreen
import com.mrboombastic.buwudzik.ui.screens.SettingsScreen
import com.mrboombastic.buwudzik.ui.theme.BuwudzikTheme
import com.mrboombastic.buwudzik.ui.utils.BluetoothUtils
import com.mrboombastic.buwudzik.ui.utils.ThemeUtils
import com.mrboombastic.buwudzik.utils.AppLogger
import com.mrboombastic.buwudzik.viewmodels.MainViewModel
import com.mrboombastic.buwudzik.widget.WidgetUpdateScheduler
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


class MainActivity : AppCompatActivity() {
    private lateinit var scanner: BluetoothScanner
    private lateinit var settingsRepository: SettingsRepository
    private var mainViewModel: MainViewModel? = null

    override fun onPause() {
        super.onPause()
        // Stop BLE scanning when app goes to background
        mainViewModel?.stopScanning()
    }

    override fun onResume() {
        super.onResume()
        // Resume scanning when app comes back to foreground - ONLY if we have permissions
        if (BluetoothUtils.hasBluetoothPermissions(this)) {
            mainViewModel?.startScanning()
        }
    }

    companion object {
        private const val TAG = "MainActivity"

        /**
         * Schedule periodic widget updates using AlarmManager.
         * This provides reliable updates even with aggressive battery optimization.
         * Call this when the app starts or when widgets are enabled.
         */
        fun scheduleUpdates(context: Context, intervalMinutes: Long) {
            AppLogger.d(TAG, "Scheduling AlarmManager for $intervalMinutes min intervals")
            WidgetUpdateScheduler.scheduleUpdates(
                context,
                intervalMinutes,
                promptUserForExactAlarms = true
            )
        }

        /**
         * Force reschedule updates with a new interval.
         * Call this when user changes the update interval in settings.
         */
        fun rescheduleUpdates(context: Context, intervalMinutes: Long) {
            AppLogger.d(TAG, "Rescheduling AlarmManager for $intervalMinutes min intervals")
            // Cancel existing alarms and schedule new one with updated interval
            WidgetUpdateScheduler.cancelUpdates(context)
            WidgetUpdateScheduler.scheduleUpdates(
                context,
                intervalMinutes,
                promptUserForExactAlarms = true
            )
        }
    }

    private fun clearCacheIfUpdated() {
        try {
            val packageInfo =
                packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
            val currentVersionCode = packageInfo.longVersionCode.toInt()

            if (settingsRepository.lastVersionCode != currentVersionCode) {
                AppLogger.i(
                    "MainActivity",
                    "App updated from ${settingsRepository.lastVersionCode} to $currentVersionCode. Clearing cache..."
                )

                applicationContext.cacheDir?.listFiles()?.forEach { it.deleteRecursively() }
                settingsRepository.lastVersionCode = currentVersionCode
            }
        } catch (e: Exception) {
            AppLogger.e("MainActivity", "Failed to check version or clear cache", e)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        scanner = BluetoothScanner(applicationContext)
        settingsRepository = SettingsRepository(applicationContext)

        clearCacheIfUpdated()

        // Apply Language
        val lang = settingsRepository.language
        val appLocale = if (lang == "system") {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(lang)
        }
        AppCompatDelegate.setApplicationLocales(appLocale)

        // Apply Theme
        AppCompatDelegate.setDefaultNightMode(ThemeUtils.themeToNightMode(settingsRepository.theme))

        // Schedule Worker or Alarm
        if (BluetoothUtils.hasBluetoothPermissions(applicationContext)) {
            scheduleUpdates(applicationContext, settingsRepository.updateInterval)
        }

        val viewModel: MainViewModel by viewModels {
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    @Suppress("UNCHECKED_CAST") return MainViewModel(
                        scanner, settingsRepository, applicationContext
                    ) as T
                }
            }
        }
        mainViewModel = viewModel

        setContent {
            BuwudzikTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    LocalContext.current
                    val resources = LocalResources.current
                    val startDestination =
                        if (settingsRepository.isSetupCompleted) "home" else "setup"

                    // Handle disconnection events
                    val disconnectionEvent by viewModel.disconnectionEvent.collectAsState()
                    val snackbarHostState = remember { SnackbarHostState() }

                    // Register Receiver Globally
                    val context = LocalContext.current
                    DisposableEffect(context) {
                        val receiver = BluetoothStateReceiver { enabled ->
                            viewModel.updateBluetoothState(enabled)
                        }
                        val filter =
                            android.content.IntentFilter(android.bluetooth.BluetoothAdapter.ACTION_STATE_CHANGED)
                        ContextCompat.registerReceiver(
                            context,
                            receiver,
                            filter,
                            ContextCompat.RECEIVER_NOT_EXPORTED
                        )
                        onDispose {
                            context.unregisterReceiver(receiver)
                        }
                    }

                    val isBluetoothEnabled by viewModel.isBluetoothEnabled.collectAsState()
                    val enableBluetoothLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.StartActivityForResult()
                    ) { }

                    if (!isBluetoothEnabled) {
                        AlertDialog(
                            onDismissRequest = { },
                            title = { Text(stringResource(R.string.bluetooth_required_title)) },
                            text = { Text(stringResource(R.string.bluetooth_required_desc)) },
                            confirmButton = {
                                TextButton(
                                    onClick = {
                                        val intent =
                                            Intent(android.bluetooth.BluetoothAdapter.ACTION_REQUEST_ENABLE)
                                        enableBluetoothLauncher.launch(intent)
                                    }) {
                                    Text(stringResource(R.string.turn_on_bluetooth))
                                }
                            },
                            icon = { Icon(Icons.Default.Settings, contentDescription = null) })
                    }

                    LaunchedEffect(disconnectionEvent) {
                        disconnectionEvent?.let { reason ->
                            // Reset connection state FIRST
                            viewModel.handleUnexpectedDisconnect()

                            // Navigate to home screen
                            navController.navigate("home") {
                                popUpTo("home") { inclusive = true }
                            }

                            val reasonMessage = reason.getMessage(resources)
                            val reasonHint = reason.getHint(resources)

                            val fullMessage = if (reasonHint != null) {
                                "$reasonMessage $reasonHint"
                            } else {
                                reasonMessage
                            }
                            
                            // Show snackbar with reason
                            snackbarHostState.showSnackbar(
                                message = fullMessage,
                                duration = SnackbarDuration.Long
                            )
                            // Clear the event
                            viewModel.clearDisconnectionEvent()
                        }
                    }

                    // Handle connection errors (diagnostic hints)
                    val connectionError by viewModel.connectionError.collectAsState()
                    val okText = stringResource(android.R.string.ok)
                    LaunchedEffect(connectionError) {
                        connectionError?.let { error ->
                            // Avoid showing generic "Disconnected" message if we already have a specific DisconnectionEvent
                            val isGenericDisconnect =
                                error.trim() == "Disconnected" || error.startsWith("Disconnected (status")
                            if (!isGenericDisconnect) {
                                snackbarHostState.showSnackbar(
                                    message = error,
                                    duration = SnackbarDuration.Long,
                                    actionLabel = okText
                                )
                            }
                            viewModel.clearConnectionError()
                        }
                    }

                    @SuppressLint("UnusedMaterial3ScaffoldPaddingParameter") Scaffold(
                        snackbarHost = { CustomSnackbarHost(snackbarHostState) }) { _ ->
                        NavHost(
                            navController = navController,
                            startDestination = startDestination,
                            enterTransition = {
                                slideInHorizontally(
                                    initialOffsetX = { fullWidth -> fullWidth },
                                    animationSpec = tween(300, easing = FastOutSlowInEasing)
                                ) + fadeIn(animationSpec = tween(300))
                            },
                            exitTransition = {
                                slideOutHorizontally(
                                    targetOffsetX = { fullWidth -> -fullWidth / 4 },
                                    animationSpec = tween(300, easing = FastOutSlowInEasing)
                                ) + fadeOut(animationSpec = tween(150))
                            },
                            popEnterTransition = {
                                slideInHorizontally(
                                    initialOffsetX = { fullWidth -> -fullWidth / 4 },
                                    animationSpec = tween(300, easing = FastOutSlowInEasing)
                                ) + fadeIn(animationSpec = tween(300))
                            },
                            popExitTransition = {
                                slideOutHorizontally(
                                    targetOffsetX = { fullWidth -> fullWidth },
                                    animationSpec = tween(300, easing = FastOutSlowInEasing)
                                ) + fadeOut(animationSpec = tween(150))
                            }) {
                            composable("setup") { DeviceSetupScreen(navController) }
                            composable("home") { HomeScreen(viewModel, navController) }
                            composable("settings") {
                                // Handle system back to properly navigate back or to home
                                BackHandler {
                                    if (!navController.popBackStack()) {
                                        navController.navigate("home") {
                                            popUpTo(0) { inclusive = true }
                                        }
                                    }
                                }
                                SettingsScreen(navController, viewModel)
                            }
                            composable("alarms") {
                                BackHandler {
                                    if (!navController.popBackStack()) {
                                        navController.navigate("home") {
                                            popUpTo(0) { inclusive = true }
                                        }
                                    }
                                }
                                AlarmManagementScreen(navController, viewModel)
                            }
                            composable("device-settings") {
                                BackHandler {
                                    if (!navController.popBackStack()) {
                                        navController.navigate("home") {
                                            popUpTo(0) { inclusive = true }
                                        }
                                    }
                                }
                                DeviceSettingsScreen(
                                    navController, viewModel
                                )
                            }
                            composable("ringtone-upload") {
                                BackHandler {
                                    if (!navController.popBackStack()) {
                                        navController.navigate("home") {
                                            popUpTo(0) { inclusive = true }
                                        }
                                    }
                                }
                                RingtoneUploadScreen(
                                    navController, viewModel
                                )
                            }
                            composable("device-sharing") {
                                BackHandler {
                                    if (!navController.popBackStack()) {
                                        navController.navigate("home") {
                                            popUpTo(0) { inclusive = true }
                                        }
                                    }
                                }
                                DeviceSharingScreen(navController)
                            }
                            composable("device-import") {
                                BackHandler {
                                    if (!navController.popBackStack()) {
                                        navController.navigate("home") {
                                            popUpTo(0) { inclusive = true }
                                        }
                                    }
                                }
                                DeviceImportScreen(navController, viewModel)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HomeScreen(viewModel: MainViewModel, navController: NavController) {
    val context = LocalContext.current
    val sensorData by viewModel.sensorData.collectAsState()
    val isBluetoothEnabled by viewModel.isBluetoothEnabled.collectAsState()

    // Permissions handling
    val permissionsToRequest = BluetoothUtils.BLUETOOTH_PERMISSIONS
    val permissionsRequiredMessage = stringResource(R.string.permissions_required)

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(), onResult = { perms ->
            val allGranted = perms.values.all { it }
            AppLogger.d(
                "MainActivity", "Permissions result: $perms, All Granted: $allGranted"
            )
            if (allGranted) {
                viewModel.startScanning()
            } else {
                val deniedPerms = perms.filter { !it.value }.keys.joinToString(", ")
                val message = "$permissionsRequiredMessage\nMissing: $deniedPerms"
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            }
        })

    LaunchedEffect(Unit) {
        val allGranted = permissionsToRequest.all {
            context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED
        }
        AppLogger.d("MainActivity", "Initial permission check. All granted: $allGranted")
        if (allGranted) {
            viewModel.startScanning()
        } else {
            permissionLauncher.launch(permissionsToRequest)
        }
    }


    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { navController.navigate("settings") }) {
                Icon(
                    Icons.Default.Settings,
                    contentDescription = stringResource(R.string.settings_desc)
                )
            }
        }) { padding ->
        Column(
            horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxSize()
        ) {
            Dashboard(
                sensorData = sensorData,
                isBluetoothEnabled = isBluetoothEnabled,
                navController = navController,
                viewModel = viewModel,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(padding)
            )
        }
    }
}

@Composable
fun Dashboard(
    sensorData: SensorData?,
    isBluetoothEnabled: Boolean,
    navController: NavController,
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val deviceConnected by viewModel.deviceConnected.collectAsState()
    val deviceConnecting by viewModel.deviceConnecting.collectAsState()
    val isPaired by viewModel.isPaired.collectAsState()
    var showUnpairDialog by remember { mutableStateOf(false) }

    // Unpair confirmation dialog
    @Suppress("AssignedValueIsNeverRead") if (showUnpairDialog) {
        AlertDialog(
            onDismissRequest = { showUnpairDialog = false },
            title = { Text(stringResource(R.string.unpair_confirm_title)) },
            text = { Text(stringResource(R.string.unpair_confirm_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showUnpairDialog = false
                        viewModel.disconnectFromDevice()
                        viewModel.unpairDevice()
                    }, colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(stringResource(R.string.unpair_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showUnpairDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            })
    }

    Column(
        modifier = modifier
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (!isBluetoothEnabled) {
            Text(
                text = stringResource(R.string.bluetooth_disabled),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }

        if (sensorData == null) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(16.dp))
            Text(stringResource(R.string.scanning_status))
        } else {
            if (!sensorData.name.isNullOrEmpty()) {
                Text(
                    text = sensorData.name,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            Text(
                text = "${String.format(Locale.getDefault(), "%.1f", sensorData.temperature)}°C",
                fontSize = 64.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "${String.format(Locale.getDefault(), "%.1f", sensorData.humidity)}%",
                fontSize = 48.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.secondary
            )
            Spacer(modifier = Modifier.height(32.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.battery_label, sensorData.battery),
                    fontSize = 24.sp
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            val signalPercentage = BluetoothUtils.rssiToPercentage(sensorData.rssi)
            Text(
                text = stringResource(R.string.rssi_label, sensorData.rssi, signalPercentage),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
            val timeString = dateFormat.format(Date(sensorData.timestamp))
            Text(
                text = stringResource(R.string.last_update_label, timeString),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(24.dp))

            if (deviceConnecting) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.connecting_to_device),
                    style = MaterialTheme.typography.bodyMedium
                )
            } else if (!deviceConnected) {
                if (!isPaired) {
                    ElevatedCard(
                        colors = CardDefaults.elevatedCardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        ),
                        modifier = Modifier
                            .fillMaxWidth(0.92f)
                            .padding(bottom = 24.dp),
                        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // Header with icon
                            Icon(
                                Icons.Default.PhonelinkSetup,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                stringResource(R.string.setup_new_device),
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                stringResource(R.string.pairing_subtitle),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(24.dp))

                            // Step 1
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.Top
                            ) {
                                Surface(
                                    shape = MaterialTheme.shapes.small,
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            "1",
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        stringResource(R.string.pairing_step1_title),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        stringResource(R.string.pairing_step1_desc),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Step 2
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.Top
                            ) {
                                Surface(
                                    shape = MaterialTheme.shapes.small,
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            "2",
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        stringResource(R.string.pairing_step2_title),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        stringResource(R.string.pairing_step2_desc),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Step 3
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.Top
                            ) {
                                Surface(
                                    shape = MaterialTheme.shapes.small,
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            "3",
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        stringResource(R.string.pairing_step3_title),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        stringResource(R.string.pairing_step3_desc),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                Button(
                    onClick = { viewModel.connectToDevice() },
                    modifier = Modifier.fillMaxWidth(0.8f)
                ) {
                    Text(
                        if (isPaired) stringResource(R.string.connect_to_device) else stringResource(
                            R.string.pair_and_connect
                        )
                    )
                }

                if (isPaired) {
                    TextButton(
                        onClick = { navController.navigate("device-sharing") },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(
                            Icons.Default.Share,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.share_device_button))
                    }
                }

                if (isPaired) {
                    @Suppress("AssignedValueIsNeverRead") TextButton(
                        onClick = { showUnpairDialog = true },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.unpair_device))
                    }
                }
            } else {
                Spacer(modifier = Modifier.height(16.dp))
                Column(
                    modifier = Modifier.fillMaxWidth(0.9f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    MenuTile(
                        title = stringResource(R.string.manage_alarms_label),
                        icon = Icons.Default.Alarm,
                        onClick = { navController.navigate("alarms") })
                    MenuTile(
                        title = stringResource(R.string.device_settings_button),
                        icon = Icons.Default.Settings,
                        onClick = { navController.navigate("device-settings") })
                    MenuTile(
                        title = stringResource(R.string.disconnect),
                        icon = Icons.AutoMirrored.Filled.ExitToApp,
                        onClick = { viewModel.disconnectFromDevice() },
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    )

                    if (isPaired) {
                        @Suppress("AssignedValueIsNeverRead") MenuTile(
                            title = stringResource(R.string.unpair_device),
                            icon = Icons.Default.Warning,
                            onClick = { showUnpairDialog = true },
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        )
                    }
                }
            }
        }
    }
}

