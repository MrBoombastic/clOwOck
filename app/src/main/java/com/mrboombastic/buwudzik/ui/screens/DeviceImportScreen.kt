package com.mrboombastic.buwudzik.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavController
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.mrboombastic.buwudzik.R
import com.mrboombastic.buwudzik.data.AlarmTitleRepository
import com.mrboombastic.buwudzik.data.DeviceShareData
import com.mrboombastic.buwudzik.data.TokenStorage
import com.mrboombastic.buwudzik.data.normalizedBluetoothMac
import com.mrboombastic.buwudzik.ui.components.AppSettingsButton
import com.mrboombastic.buwudzik.ui.components.ContentCard
import com.mrboombastic.buwudzik.ui.components.StandardTopBar
import com.mrboombastic.buwudzik.ui.utils.AdaptiveScreen
import com.mrboombastic.buwudzik.utils.AppLogger
import com.mrboombastic.buwudzik.viewmodels.MainViewModel
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds

private val qrReader = MultiFormatReader().apply {
    setHints(
        mapOf(
            DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
            DecodeHintType.TRY_HARDER to true
        )
    )
}


/**
 * Adds a device from the QR code shown on another phone. The code says which device it is for,
 * so the clock does not have to be in range. Scanning a code for a device already on this phone
 * refreshes its token, which is how a rejected token gets replaced without losing the device.
 */
@Composable
fun DeviceImportScreen(
    navController: NavController,
    viewModel: MainViewModel
) {
    val context = LocalContext.current
    fun isCameraGranted() = context.checkSelfPermission(
        Manifest.permission.CAMERA
    ) == android.content.pm.PackageManager.PERMISSION_GRANTED

    var hasCameraPermission by remember { mutableStateOf(isCameraGranted()) }
    var cameraPermissionDenied by remember { mutableStateOf(false) }
    val defaultAlias = stringResource(R.string.default_device_alias)
    val importSuccessMsg = stringResource(R.string.import_success)
    val importErrorMsg = stringResource(R.string.import_error)
    val importTokenInvalidMsg = stringResource(R.string.import_token_invalid)

    fun showError(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    fun importSharedDevice(qrContent: String) {
        val shareData = DeviceShareData.fromQrContent(qrContent)
        val mac = shareData?.mac?.normalizedBluetoothMac()
            ?.takeIf(BluetoothAdapter::checkBluetoothAddress)
        if (shareData == null || mac == null) {
            showError(importErrorMsg)
            return
        }

        val normalizedToken = normalizeAuthTokenInput(shareData.token)
        if (!isValidAuthTokenInput(normalizedToken)) {
            showError(importTokenInvalidMsg)
            return
        }

        try {
            val tokenStorage = TokenStorage(context)
            val newToken = tokenStorage.hexToBytes(normalizedToken)
            val replacedToken = tokenStorage.getToken(mac)
                ?.takeUnless { it.contentEquals(newToken) }
            tokenStorage.storeToken(mac, newToken)
            val alarmTitleRepository = AlarmTitleRepository(context, mac)
            shareData.alarmTitles.forEach { (id, title) ->
                alarmTitleRepository.setTitle(id, title)
            }
            viewModel.addDevice(
                shareData.toProfile(viewModel.getSavedDevice(mac), defaultAlias),
                replacedToken
            )

            Toast.makeText(context, importSuccessMsg, Toast.LENGTH_SHORT).show()
            navController.navigateToHomeRoot()
        } catch (_: Exception) {
            showError(importTokenInvalidMsg)
        }
    }

    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            hasCameraPermission = granted
            cameraPermissionDenied = !granted
        }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // The permission may have been granted in system settings in the meantime.
    LifecycleResumeEffect(Unit) {
        hasCameraPermission = isCameraGranted()
        onPauseOrDispose { }
    }

    Scaffold(
        topBar = {
            StandardTopBar(
                title = stringResource(R.string.import_device_title), navController = navController
            )
        }) { padding ->
        AdaptiveScreen(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            columnModifier = Modifier
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (hasCameraPermission) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(10.dp)
                        ) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .border(
                                        width = 2.dp,
                                        color = MaterialTheme.colorScheme.primary,
                                        shape = RoundedCornerShape(12.dp)
                                    ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                QrScannerView(onQrCodeScanned = ::importSharedDevice)
                            }
                            Surface(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 10.dp),
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.88f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(
                                        horizontal = 12.dp,
                                        vertical = 10.dp
                                    ),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.QrCodeScanner,
                                        contentDescription = null,
                                        modifier = Modifier.size(22.dp),
                                        tint = MaterialTheme.colorScheme.inverseOnSurface
                                    )
                                    Text(
                                        text = stringResource(R.string.import_qr_instruction),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.inverseOnSurface
                                    )
                                }
                            }
                        }
                    }
                } else {
                    ContentCard(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CameraAlt,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.size(32.dp)
                            )
                            Text(
                                text = stringResource(R.string.camera_permission_required),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                    if (cameraPermissionDenied) {
                        AppSettingsButton(modifier = Modifier.fillMaxWidth())
                    }
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun QrScannerView(
    onQrCodeScanned: (String) -> Unit
) {
    LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var scanned by remember { mutableStateOf(false) }

    // An accepted code leaves this screen, so this only runs for a rejected one. Resume after a
    // pause, so a rejected code still in front of the camera does not repeat the error every frame.
    LaunchedEffect(scanned) {
        if (scanned) {
            delay(3.seconds)
            scanned = false
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            cameraProvider?.unbindAll()
        }
    }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            val mainExecutor = ContextCompat.getMainExecutor(ctx)
            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
            cameraProviderFuture.addListener(
                {
                    try {
                        val provider = cameraProviderFuture.get()
                        // Defer bind until PreviewView is attached; post is queued until then if needed.
                        previewView.post {
                            if (!previewView.isAttachedToWindow) return@post
                            try {
                                cameraProvider = provider

                                val preview = Preview.Builder().build().apply {
                                    surfaceProvider = previewView.surfaceProvider
                                }

                                val imageAnalysis = qrImageAnalysisBuilder()
                                    .build()
                                    .also { analysis ->
                                        analysis.setAnalyzer(ctx.mainExecutor) { imageProxy ->
                                            if (!scanned) {
                                                processImageProxy(imageProxy) {
                                                    scanned = true
                                                    onQrCodeScanned(it)
                                                }
                                            } else {
                                                imageProxy.close()
                                            }
                                        }
                                    }

                                provider.unbindAll()
                                provider.bindToLifecycle(
                                    lifecycleOwner,
                                    CameraSelector.DEFAULT_BACK_CAMERA,
                                    preview,
                                    imageAnalysis
                                )
                            } catch (e: Exception) {
                                AppLogger.e("DeviceImportScreen", "Camera setup failed", e)
                            }
                        }
                    } catch (e: Exception) {
                        AppLogger.e("DeviceImportScreen", "Camera provider failed", e)
                    }
                },
                mainExecutor
            )
            previewView
        }
    )
}


/**
 * On some models, such as MotoG3, CameraX corrects frames by shifting them one pixel in place.
 * Custom ROMs for those phones can deliver frames that cannot be written to, and the write then
 * crashes the app. Finding a QR code does not depend on one pixel, so the correction stays off.
 *
 * Affected models (YuvImageOnePixelShiftQuirk):
 * https://github.com/androidx/androidx/blob/a24a86d80ca427ad77e7cda7d36d37fe13d399c6/camera/camera-camera2/src/main/java/androidx/camera/camera2/compat/quirk/YuvImageOnePixelShiftQuirk.kt#L27-L62
 *
 * Where ImageAnalysis applies it; an explicit value overrides the device list:
 * https://github.com/androidx/androidx/blob/55e54e18102fbcdc3a4a6f68984c157dcbaaa852/camera/camera-core/src/main/java/androidx/camera/core/ImageAnalysis.java#L563-L576
 */
@SuppressLint("RestrictedApi")
private fun qrImageAnalysisBuilder() = ImageAnalysis.Builder()
    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
    .setOnePixelShiftEnabled(false)

@androidx.annotation.OptIn(ExperimentalGetImage::class)
private fun processImageProxy(
    imageProxy: ImageProxy, onBarcodeDetected: (String) -> Unit
) {
    val image = imageProxy.image ?: run {
        imageProxy.close()
        return
    }

    try {
        val plane = image.planes[0]
        val buffer = plane.buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)

        // Rows can be padded beyond the image width, so the data width is the row stride.
        val source = PlanarYUVLuminanceSource(
            bytes, plane.rowStride, image.height, 0, 0, image.width, image.height, false
        )

        val bitmap = BinaryBitmap(HybridBinarizer(source))

        val result = qrReader.decodeWithState(bitmap)
        onBarcodeDetected(result.text)
    } catch (_: NotFoundException) {
        // no QR in this frame
    } catch (e: Exception) {
        AppLogger.v("DeviceImportScreen", "Error processing image", e)
    } finally {
        imageProxy.close()
        qrReader.reset()
    }
}
