package com.mrboombastic.buwudzik.utils

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object TimeFormatUtils {

    /**
     * Format milliseconds to time string with optional minutes
     * @param ms Time in milliseconds
     * @return Formatted string like "1:23.4" or "23.4" (without minutes if < 1 min)
     */
    fun formatTime(ms: Long): String {
        val totalSeconds = ms / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        val tenths = (ms % 1000) / 100
        return if (minutes > 0) {
            String.format(Locale.getDefault(), "%d:%02d.%d", minutes, seconds, tenths)
        } else {
            String.format(Locale.getDefault(), "%d.%d", seconds, tenths)
        }
    }

    /**
     * Format milliseconds to time input format (always includes minutes)
     * @param ms Time in milliseconds
     * @return Formatted string like "1:23.4"
     */
    fun formatTimeInput(ms: Long): String {
        val totalSeconds = ms / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        val tenths = (ms % 1000) / 100
        return String.format(Locale.getDefault(), "%d:%02d.%d", minutes, seconds, tenths)
    }

    /**
     * Parse time input string to milliseconds
     * Supports formats:
     * - "123" -> 123 seconds
     * - "1:23" -> 1 minute 23 seconds
     * - "1:23.4" -> 1 minute 23.4 seconds
     * @param text Input time string
     * @return Time in milliseconds, or null if parsing fails
     */
    fun parseTimeInput(text: String): Long? {
        return try {
            val parts = text.split(":")
            when (parts.size) {
                1 -> {
                    // Just seconds
                    val secParts = parts[0].split(".")
                    val seconds = secParts[0].toLongOrNull() ?: return null
                    val tenths = secParts.getOrNull(1)?.firstOrNull()?.toString()?.toIntOrNull() ?: 0
                    seconds * 1000 + tenths * 100
                }
                2 -> {
                    // Minutes:seconds or Minutes:seconds.tenths
                    val minutes = parts[0].toLongOrNull() ?: return null
                    val secParts = parts[1].split(".")
                    val seconds = secParts[0].toLongOrNull() ?: return null
                    val tenths = secParts.getOrNull(1)?.firstOrNull()?.toString()?.toIntOrNull() ?: 0
                    (minutes * 60 + seconds) * 1000 + tenths * 100
                }
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Format absolute timestamp to date and time string
     * @param timestampMs Timestamp in milliseconds
     * @param locale Locale for formatting
     * @return Formatted string like "12.02 14:30"
     */
    fun formatAbsoluteTime(timestampMs: Long, locale: Locale = Locale.getDefault()): String {
        val sdf = SimpleDateFormat("dd.MM HH:mm", locale)
        return sdf.format(Date(timestampMs))
    }

    /**
     * Format hour and minute to time string
     * @param hour Hour (0-23)
     * @param minute Minute (0-59)
     * @return Formatted string like "14:30"
     */
    fun formatHourMinute(hour: Int, minute: Int): String {
        return String.format(Locale.getDefault(), "%02d:%02d", hour, minute)
    }
}
