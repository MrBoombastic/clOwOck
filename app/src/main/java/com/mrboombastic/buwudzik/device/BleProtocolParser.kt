package com.mrboombastic.buwudzik.device

// A fast audio write carries a two-byte header and 128 PCM bytes, plus three ATT bytes.
internal fun supportsFastAudioUpload(mtu: Int): Boolean = mtu >= 133

// The firmware activates fast upload before reporting 09 for its link-length gate.
internal fun acceptsAudioInitStatus(status: Int?): Boolean =
    status == BleConstants.Status.SUCCESS || status == BleConstants.Status.ERR_BUSY

/** Parsed acknowledgement sent by the CGD1 notify characteristics. */
internal data class BleAck(
    val command: Int,
    val payloadSize: Int,
    val status: Int,
    val subIndex: Int = 0,
    val firstPayloadByte: Int? = null
)

/**
 * Parses the ACK sent by the device: `04 ff [command] 00 [status]`.
 *
 * Standard ACK notification layout (always 5 bytes):
 * - Byte 0: `0x04` (length of the 4 bytes that follow)
 * - Byte 1: `0xff` (ACK response opcode)
 * - Byte 2: `[command]` (echoed command identifier)
 * - Byte 3: `0x00` (fixed sub-index/separator)
 * - Byte 4: `[status]` (return/error code: 0x00 = success, non-zero = error)
 *
 * If extra payload follows (size > 5), it is captured in `firstPayloadByte`.
 * If a truncated 4-byte frame is received (`04 ff [command] [status]`), index 3 is taken as the status.
 */
internal fun parseBleAck(value: ByteArray): BleAck? {
    if (value.size < 4 || value[0] != 0x04.toByte() || value[1] != 0xff.toByte()) return null

    val command = value[2].toInt() and 0xff
    return if (value.size >= 5) {
        val subIndex = value[3].toInt() and 0xff
        val status = value[4].toInt() and 0xff
        BleAck(
            command = command,
            payloadSize = maxOf(0, value.size - 5),
            status = status,
            subIndex = subIndex,
            firstPayloadByte = value.getOrNull(5)?.toInt()?.and(0xff)
        )
    } else {
        // Fallback for a truncated 4-byte frame
        val status = value[3].toInt() and 0xff
        BleAck(
            command = command,
            payloadSize = 0,
            status = status,
            subIndex = 0,
            firstPayloadByte = null
        )
    }
}

internal fun BleAck.isSuccessfulAuthConfirm(): Boolean =
    status == BleConstants.Status.SUCCESS

internal fun BleAck.isAuthenticationFailure(isAuthNotification: Boolean): Boolean =
    isAuthNotification && status != BleConstants.Status.SUCCESS &&
            (command == BleConstants.Command.AUTH_INIT || command == BleConstants.Command.AUTH_CONFIRM)

/** Keep the first upload error even if a later ACK reports success. */
internal fun mergeUploadAckStatus(current: Int?, incoming: Int): Int =
    if (current != null && current != BleConstants.Status.SUCCESS) current else incoming
