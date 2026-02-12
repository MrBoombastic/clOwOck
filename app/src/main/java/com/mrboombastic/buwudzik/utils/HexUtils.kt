package com.mrboombastic.buwudzik.utils

/**
 * Utility functions for hex conversion.
 */
object HexUtils {
    /**
     * Convert bytes to hex string without separators (for storage).
     */
    fun bytesToHex(bytes: ByteArray): String {
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * Convert bytes to hex string with space separators (for logging).
     */
    fun bytesToHexSpaced(bytes: ByteArray): String {
        return bytes.joinToString(" ") { "%02x".format(it) }
    }

    /**
     * Convert hex string to bytes.
     */
    fun hexToBytes(hex: String): ByteArray {
        return hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }
}

/**
 * Extension function for ByteArray to convert to hex string with spaces (for logging).
 */
fun ByteArray.toHexString(): String = HexUtils.bytesToHexSpaced(this)
