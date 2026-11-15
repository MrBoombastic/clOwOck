package com.mrboombastic.buwudzik.utils

/**
 * Utility functions for hex conversion.
 */
object HexUtils {
    /**
     * Convert bytes to a hex string without separators (for storage).
     */
    fun bytesToHex(bytes: ByteArray): String {
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * Convert a hex string to bytes.
     */
    fun hexToBytes(hex: String): ByteArray {
        return hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }
}
