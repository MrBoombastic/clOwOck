package com.mrboombastic.buwudzik.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

object FileDownloadUtils {

    /**
     * Download a file from a URL
     * @param url URL to download from
     * @param connectTimeout Connection timeout in milliseconds
     * @param readTimeout Read timeout in milliseconds
     * @return Downloaded file as ByteArray
     * @throws IOException if download fails
     */
    suspend fun downloadFile(
        url: String,
        connectTimeout: Int = 15000,
        readTimeout: Int = 30000
    ): ByteArray = withContext(Dispatchers.IO) {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = connectTimeout
        connection.readTimeout = readTimeout
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw IOException("HTTP error code: ${connection.responseCode}")
            }
            connection.inputStream.use { it.readBytes() }
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Format estimated time remaining
     * @param ms Time in milliseconds
     * @return Formatted string like "1m 23s" or "45s"
     */
    fun formatEta(ms: Long): String {
        val seconds = (ms / 1000).coerceAtLeast(0)
        val minutes = seconds / 60
        val secs = seconds % 60
        return if (minutes > 0) {
            "${minutes}m ${secs}s"
        } else {
            "${secs}s"
        }
    }
}
