package com.mrboombastic.buwudzik

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.FileProvider
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.contentLength
import io.ktor.serialization.kotlinx.json.*
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class GitHubRelease(
    @SerialName("tag_name") val tagName: String, val assets: List<GitHubAsset>
)

@Serializable
data class GitHubAsset(
    val name: String, @SerialName("browser_download_url") val browserDownloadURL: String
)

data class UpdateCheckResult(
    val updateAvailable: Boolean, val latestVersion: String, val downloadUrl: String? = null
)

class UpdateChecker(private val context: Context) {

    companion object {
        private const val NOTIFICATION_CHANNEL_ID = "update_download_channel"
        private const val NOTIFICATION_ID = 1001
    }

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                coerceInputValues = true
            })
        }
    }

    suspend fun checkForUpdatesWithResult(): UpdateCheckResult {
        // bruh moment for the hardcoded url
        val release: GitHubRelease =
            client.get("https://api.github.com/repos/MrBoombastic/bUwUdzik/releases/latest").body()
        Log.d("UpdateChecker", "Latest release: $release")
        val latestVersion = release.tagName.removePrefix("v")
        val currentVersion =
            context.packageManager.getPackageInfo(context.packageName, 0).versionName

        val updateAvailable = isNewerVersion(latestVersion, currentVersion)
        if (updateAvailable) {
            val asset = release.assets.firstOrNull { it.name.endsWith(".apk") }
            asset?.let {
                downloadAndInstall(it.browserDownloadURL)
            }
            return UpdateCheckResult(
                updateAvailable = true,
                latestVersion = latestVersion,
                downloadUrl = asset?.browserDownloadURL
            )
        }

        return UpdateCheckResult(
            updateAvailable = false, latestVersion = latestVersion
        )
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            context.getString(R.string.update_download_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = context.getString(R.string.update_download_channel_desc)
        }
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    private fun isNewerVersion(latestVersion: String, currentVersion: String?): Boolean {
        if (currentVersion == null) return true
        try {
            val latest = latestVersion.split(".").map { it.toInt() }
            val current = currentVersion.split(".").map { it.toInt() }

            for (i in 0 until maxOf(latest.size, current.size)) {
                val latestPart = latest.getOrNull(i) ?: 0
                val currentPart = current.getOrNull(i) ?: 0
                if (latestPart > currentPart) return true
                if (latestPart < currentPart) return false
            }
        } catch (e: NumberFormatException) {
            Log.e("UpdateChecker", "Failed to parse version names", e)
            return false // Or handle as a special case
        }
        return false
    }

    private suspend fun downloadAndInstall(url: String) {
        withContext(Dispatchers.IO) {
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            try {
                createNotificationChannel()

                val file = File(context.cacheDir, "buwudzik-update.apk")

                client.prepareGet(url).execute { httpResponse ->
                    val contentLength = httpResponse.contentLength() ?: -1L
                    val channel = httpResponse.body<io.ktor.utils.io.ByteReadChannel>()

                    var downloadedBytes = 0L
                    val buffer = ByteArray(8192)

                    file.outputStream().use { outputStream ->
                        while (!channel.isClosedForRead) {
                            val bytesRead = channel.readAvailable(buffer, 0, buffer.size)
                            if (bytesRead > 0) {
                                outputStream.write(buffer, 0, bytesRead)
                                downloadedBytes += bytesRead

                                if (contentLength > 0) {
                                    val progress = downloadedBytes.toInt()
                                    val totalBytes = contentLength.toInt()
                                    val progressPercent = (downloadedBytes * 100 / contentLength).toInt()
                                    val downloadedMB = downloadedBytes / 1024 / 1024
                                    val totalMB = contentLength / 1024 / 1024

                                    val progressStyle = Notification.ProgressStyle()
                                        .setStyledByProgress(true)
                                        .setProgress(progress)
                                        .setProgressSegments(
                                            listOf(
                                                Notification.ProgressStyle.Segment(totalBytes)
                                            )
                                        )

                                    val notification = Notification.Builder(context, NOTIFICATION_CHANNEL_ID)
                                        .setContentTitle(context.getString(R.string.update_downloading_title))
                                        .setContentText(
                                            context.getString(
                                                R.string.update_downloading_progress,
                                                progressPercent,
                                                downloadedMB.toInt(),
                                                totalMB.toInt()
                                            )
                                        )
                                        .setSmallIcon(android.R.drawable.stat_sys_download)
                                        .setStyle(progressStyle)
                                        .setOngoing(true)
                                        .build()
                                    notificationManager.notify(NOTIFICATION_ID, notification)
                                }
                            }
                        }
                    }
                }

                val completionNotification = Notification.Builder(context, NOTIFICATION_CHANNEL_ID)
                    .setContentTitle(context.getString(R.string.update_download_complete))
                    .setContentText(context.getString(R.string.update_download_complete_desc))
                    .setSmallIcon(android.R.drawable.stat_sys_download_done)
                    .setAutoCancel(true)
                    .build()
                notificationManager.notify(NOTIFICATION_ID, completionNotification)

                val intent = Intent(Intent.ACTION_VIEW)
                val uri =
                    FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
                intent.setDataAndType(uri, "application/vnd.android.package-archive")
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            } catch (e: Exception) {
                Log.e("UpdateChecker", "Error downloading or installing update", e)

                val errorNotification = Notification.Builder(context, NOTIFICATION_CHANNEL_ID)
                    .setContentTitle(context.getString(R.string.update_download_error))
                    .setContentText(context.getString(R.string.update_download_error_desc))
                    .setSmallIcon(android.R.drawable.stat_notify_error)
                    .setAutoCancel(true)
                    .build()
                notificationManager.notify(NOTIFICATION_ID, errorNotification)
            }
        }
    }
}

