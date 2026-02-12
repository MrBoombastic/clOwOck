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
     * Convert hex string to bytes.
     */
    fun hexToBytes(hex: String): ByteArray {
        return hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }
}

/**
 * Extension function to convert ByteArray to hex string.
 */
fun ByteArray.toHexString(): String = HexUtils.bytesToHex(this)

/**
 * Extension function to convert Byte to hex string.
 */
fun Byte.toHexString(): String = "%02x".format(this)

/**
 * Extension function to convert Int to hex string.
 */
fun Int.toHexString(): String = "0x%02x".format(this)
