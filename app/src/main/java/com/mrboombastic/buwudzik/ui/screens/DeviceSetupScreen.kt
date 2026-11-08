package com.mrboombastic.buwudzik.ui.screens


import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.IntentFilter
import android.location.LocationManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.navigation.NavController
import com.mrboombastic.buwudzik.BluetoothStateReceiver
import com.mrboombastic.buwudzik.R
import com.mrboombastic.buwudzik.data.BatteryType
import com.mrboombastic.buwudzik.data.DeviceProfile
import com.mrboombastic.buwudzik.data.DeviceProfileRepository
import com.mrboombastic.buwudzik.data.TokenStorage
import com.mrboombastic.buwudzik.data.normalizedBluetoothMac
import com.mrboombastic.buwudzik.device.BluetoothScanner
import com.mrboombastic.buwudzik.ui.components.AppSettingsButton
import com.mrboombastic.buwudzik.ui.components.BackNavigationButton
import com.mrboombastic.buwudzik.ui.components.StatusCard
import com.mrboombastic.buwudzik.ui.components.StatusType
import com.mrboombastic.buwudzik.ui.utils.AdaptiveScreen
import com.mrboombastic.buwudzik.ui.utils.BluetoothUtils
import com.mrboombastic.buwudzik.utils.AppLogger
import com.mrboombastic.buwudzik.viewmodels.MainViewModel
import kotlin.time.Duration.Companion.milliseconds

data class DiscoveredDevice(
    val name: String?,
    val address: String,
    val rssi: Int,
    val lastSeen: Long = System.currentTimeMillis()
)

/** Makes Home the only screen on the back stack: every way of adding a device continues there. */
internal fun NavController.navigateToHomeRoot() {
    navigate("home") {
        popUpTo(graph.id) { inclusive = true }
    }
}

/** On Android 10 and 11 BLE scans silently return nothing while location is turned off. */
private fun isLocationOffForBleScan(context: Context): Boolean {
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) return false
    val locationManager = context.getSystemService(LocationManager::class.java) ?: return false
    return !LocationManagerCompat.isLocationEnabled(locationManager)
}

/**
 * Screen for finding a new CGD1 device nearby, or getting one from another phone by QR code.
 *
 * @param isFirstRun true for the first-launch flow, which can be skipped; false when adding
 * another device from the device list, which is left with the back button instead.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceSetupScreen(
    navController: NavController,
    isFirstRun: Boolean,
    viewModel: MainViewModel
) {
    val context = LocalContext.current
    val invalidTokenMessage = stringResource(R.string.import_token_invalid)
    val scanner = remember {
        val repo = DeviceProfileRepository(context)
        BluetoothScanner(context, repo)
    }

    var isBluetoothEnabled by remember {
        mutableStateOf(BluetoothUtils.isBluetoothEnabled(context))
    }

    DisposableEffect(context) {
        val receiver = BluetoothStateReceiver { enabled ->
            isBluetoothEnabled = enabled
            viewModel.updateBluetoothState(enabled)
        }
        val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
        ContextCompat.registerReceiver(
            context,
            receiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        val currentEnabled = BluetoothUtils.isBluetoothEnabled(context)
        isBluetoothEnabled = currentEnabled
        viewModel.updateBluetoothState(currentEnabled)

        onDispose { context.unregisterReceiver(receiver) }
    }

    DisposableEffect(isFirstRun, viewModel) {
        if (!isFirstRun) {
            viewModel.stopScanning()
        }
        onDispose {
            if (!isFirstRun) {
                viewModel.startScanning()
            }
        }
    }

    var isScanning by remember {
        mutableStateOf(
            isBluetoothEnabled && BluetoothUtils.hasBluetoothPermissions(context)
        )
    }
    val discoveredDevices = remember { mutableStateListOf<DiscoveredDevice>() }
    var selectedAddress by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedName by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedRssi by rememberSaveable { mutableIntStateOf(0) }
    var optionalToken by rememberSaveable { mutableStateOf("") }
    val selectedDevice = selectedAddress?.let { address ->
        DiscoveredDevice(
            name = selectedName,
            address = address,
            rssi = selectedRssi
        )
    }

    val savedProfiles by viewModel.devices.collectAsState()
    val savedMacAddresses = remember(savedProfiles) {
        savedProfiles.map { it.mac.normalizedBluetoothMac() }.toSet()
    }

    val permissionsToRequest = remember {
        mutableListOf<String>().apply {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                add(Manifest.permission.BLUETOOTH_SCAN)
                add(Manifest.permission.BLUETOOTH_CONNECT)
            } else {
                add(Manifest.permission.ACCESS_FINE_LOCATION)
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.toTypedArray()
    }

    var hasBluetoothPermissions by remember {
        mutableStateOf(BluetoothUtils.hasBluetoothPermissions(context))
    }
    var bluetoothPermissionDenied by remember { mutableStateOf(false) }
    var isLocationOff by remember { mutableStateOf(isLocationOffForBleScan(context)) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        val hasPerms = BluetoothUtils.hasBluetoothPermissions(context)
        hasBluetoothPermissions = hasPerms
        bluetoothPermissionDenied = !hasPerms
        val currentEnabled = BluetoothUtils.isBluetoothEnabled(context)
        isBluetoothEnabled = currentEnabled
        viewModel.updateBluetoothState(currentEnabled)
    }

    LaunchedEffect(Unit) {
        hasBluetoothPermissions = BluetoothUtils.hasBluetoothPermissions(context)
        if (!hasBluetoothPermissions) {
            launcher.launch(permissionsToRequest)
        }
    }

    // All of these can change in system settings while this screen is in the background.
    LifecycleResumeEffect(Unit) {
        hasBluetoothPermissions = BluetoothUtils.hasBluetoothPermissions(context)
        isBluetoothEnabled = BluetoothUtils.isBluetoothEnabled(context)
        isLocationOff = isLocationOffForBleScan(context)
        onPauseOrDispose { }
    }

    // Start scanning when permissions and Bluetooth are available; stop/cancel when unavailable
    LaunchedEffect(isBluetoothEnabled, hasBluetoothPermissions, selectedAddress, isLocationOff) {
        isScanning =
            selectedAddress == null && hasBluetoothPermissions && isBluetoothEnabled
    }

    LaunchedEffect(isScanning) {
        if (isScanning) {
            discoveredDevices.clear()
            try {
                performDeviceScan(scanner, discoveredDevices, savedMacAddresses)
            } finally {
                isScanning = false
            }
        }
    }

    fun onDeviceSelected(device: DiscoveredDevice) {
        isScanning = false
        selectedAddress = device.address.normalizedBluetoothMac()
        selectedName = device.name
        selectedRssi = device.rssi
        optionalToken = ""
    }

    fun clearSelection() {
        selectedAddress = null
        selectedName = null
        selectedRssi = 0
        optionalToken = ""
    }

    // With a device selected, going back returns to the list of found devices.
    BackHandler(enabled = selectedAddress != null) { clearSelection() }

    fun addSelectedDevice(device: DiscoveredDevice) {
        val normalizedToken = normalizeAuthTokenInput(optionalToken)
        if (normalizedToken.isNotEmpty() && !isValidAuthTokenInput(normalizedToken)) {
            Toast.makeText(
                context,
                invalidTokenMessage,
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val profile = DeviceProfile(
            mac = device.address.normalizedBluetoothMac(),
            alias = device.name ?: device.address,
            batteryType = BatteryType.ALKALINE
        )

        if (normalizedToken.isNotEmpty()) {
            val tokenStorage = TokenStorage(context)
            tokenStorage.storeToken(
                profile.mac,
                tokenStorage.hexToBytes(normalizedToken)
            )
        }
        viewModel.addDevice(profile)
        navController.navigateToHomeRoot()
    }

    Scaffold(
        topBar = {
            if (!isFirstRun) {
                TopAppBar(
                    title = { Text(stringResource(R.string.add_device_label)) },
                    navigationIcon = {
                        BackNavigationButton(
                            navController = navController,
                            onClick = if (selectedAddress != null) ::clearSelection else null
                        )
                    }
                )
            }
        }
    ) { padding ->
        AdaptiveScreen(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            columnModifier = Modifier
                .padding(horizontal = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(16.dp))

                if (isFirstRun) {
                    Text(
                        text = stringResource(R.string.setup_title),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                }

                Text(
                    text = stringResource(R.string.setup_subtitle),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(24.dp))

                if (selectedDevice == null) {
                    when {
                        // Checked first: without the permission, the Bluetooth state cannot be
                        // read and is reported as disabled, whatever it really is.
                        !hasBluetoothPermissions -> {
                            StatusCard(
                                message = stringResource(R.string.permissions_required),
                                type = StatusType.WARNING
                            )
                            if (bluetoothPermissionDenied) {
                                Spacer(modifier = Modifier.height(8.dp))
                                AppSettingsButton(modifier = Modifier.fillMaxWidth())
                            }
                        }

                        !isBluetoothEnabled -> {
                            StatusCard(
                                message = stringResource(R.string.setup_enable_bluetooth),
                                type = StatusType.ERROR
                            )
                        }

                        isLocationOff -> {
                            StatusCard(
                                message = stringResource(R.string.setup_enable_location),
                                type = StatusType.WARNING
                            )
                        }

                        isScanning -> {
                            CircularProgressIndicator(modifier = Modifier.size(48.dp))
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = stringResource(R.string.setup_scanning),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    if (discoveredDevices.isEmpty() && !isScanning && !isLocationOff &&
                        hasBluetoothPermissions && isBluetoothEnabled
                    ) {
                        StatusCard(
                            message = stringResource(R.string.setup_no_devices),
                            type = StatusType.INFO,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    discoveredDevices.forEach { device ->
                        DeviceCard(
                            device = device,
                            onClick = { onDeviceSelected(device) }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                } else {
                    Text(
                        text = stringResource(R.string.setup_selected_device),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    DeviceCard(
                        device = selectedDevice,
                        onClick = null
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        text = stringResource(R.string.setup_token_optional_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = optionalToken,
                        onValueChange = { optionalToken = it },
                        label = {
                            Text(stringResource(R.string.setup_token_optional_label))
                        },
                        singleLine = true,
                        // Password type keeps the keyboard from learning or "correcting" the token.
                        keyboardOptions = KeyboardOptions(
                            autoCorrectEnabled = false,
                            keyboardType = KeyboardType.Password
                        ),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = FontFamily.Monospace
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { addSelectedDevice(selectedDevice) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.setup_add_device))
                    }
                    TextButton(
                        onClick = { clearSelection() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.setup_choose_another))
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            if (selectedDevice == null) {
                val canScan = hasBluetoothPermissions && isBluetoothEnabled
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Needs neither Bluetooth permissions nor the clock in range.
                    OutlinedButton(
                        onClick = { navController.navigate("device-import") },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.QrCodeScanner,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.setup_scan_qr))
                    }

                    if (canScan || isFirstRun) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (canScan) {
                                OutlinedButton(
                                    onClick = {
                                        isScanning = true
                                    },
                                    modifier = Modifier.weight(1f),
                                    enabled = !isScanning
                                ) {
                                    Text(stringResource(R.string.setup_scan))
                                }
                            }

                            if (isFirstRun) {
                                TextButton(
                                    onClick = {
                                        isScanning = false
                                        navController.navigateToHomeRoot()
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(stringResource(R.string.setup_skip))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}


@Composable
fun DeviceCard(
    device: DiscoveredDevice,
    onClick: (() -> Unit)?
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) {
                    Modifier.clickable(onClick = onClick)
                } else {
                    Modifier
                }
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = device.name ?: stringResource(R.string.setup_select_device),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = device.address,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                val signalPercentage = BluetoothUtils.rssiToPercentage(device.rssi)
                Text(
                    text = "$signalPercentage%",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = when {
                        signalPercentage > 70 -> MaterialTheme.colorScheme.primary
                        signalPercentage > 40 -> MaterialTheme.colorScheme.tertiary
                        else -> MaterialTheme.colorScheme.error
                    }
                )
                Text(
                    text = "${device.rssi} dBm",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private suspend fun performDeviceScan(
    scanner: BluetoothScanner,
    devices: MutableList<DiscoveredDevice>,
    savedMacAddresses: Set<String>
) {
    try {
        kotlinx.coroutines.withTimeout(15000L.milliseconds) {
            scanner.scan(targetAddress = null).collect { sensorData ->
                val macKey = sensorData.macAddress.normalizedBluetoothMac()
                if (macKey in savedMacAddresses) {
                    return@collect
                }
                val existingIndex =
                    devices.indexOfFirst { it.address.normalizedBluetoothMac() == macKey }
                val device = DiscoveredDevice(
                    name = sensorData.name,
                    address = sensorData.macAddress,
                    rssi = sensorData.rssi
                )

                if (existingIndex >= 0) {
                    devices[existingIndex] = device
                } else {
                    devices.add(device)
                }

                AppLogger.d(
                    "DeviceSetupScreen",
                    "Found device: ${device.name} at ${device.address}"
                )
            }
        }
    } catch (_: kotlinx.coroutines.TimeoutCancellationException) {
        AppLogger.d(
            "DeviceSetupScreen",
            "Scan timeout after 15 seconds - found ${devices.size} device(s)"
        )
    } catch (_: kotlinx.coroutines.CancellationException) {
        AppLogger.d("DeviceSetupScreen", "Scan cancelled (navigation or composition change)")
    } catch (e: Exception) {
        AppLogger.e("DeviceSetupScreen", "Scan error", e)
    }
}
