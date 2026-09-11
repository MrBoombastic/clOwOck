package com.mrboombastic.buwudzik.device

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BleProtocolParserTest {
    @Test
    fun `five byte ack extracts status at index 4`() {
        assertEquals(
            BleAck(command = 1, payloadSize = 0, status = 2, subIndex = 0, firstPayloadByte = null),
            parseBleAck(hex("04 ff 01 00 02"))
        )
    }

    @Test
    fun `auth init ack with pairing mode error reports code 6`() {
        assertEquals(
            BleAck(command = 1, payloadSize = 0, status = 6, subIndex = 0, firstPayloadByte = null),
            parseBleAck(hex("04 ff 01 00 06"))
        )
    }

    @Test
    fun `non zero status is reported as failure`() {
        assertEquals(
            BleAck(
                command = 0x10,
                payloadSize = 0,
                status = 6,
                subIndex = 0,
                firstPayloadByte = null
            ),
            parseBleAck(hex("04 ff 10 00 06"))
        )
    }

    @Test
    fun `four byte legacy ack remains supported`() {
        assertEquals(
            BleAck(
                command = 9,
                payloadSize = 0,
                status = 0,
                subIndex = 0,
                firstPayloadByte = null
            ),
            parseBleAck(hex("04 ff 09 00"))
        )
    }

    @Test
    fun `ack with extra payload preserves firstPayloadByte`() {
        assertEquals(
            BleAck(
                command = 0x10,
                payloadSize = 1,
                status = 0,
                subIndex = 0,
                firstPayloadByte = 0x42
            ),
            parseBleAck(hex("04 ff 10 00 00 42"))
        )
    }

    @Test
    fun `auth confirm rejects non-zero status`() {
        val ack = parseBleAck(hex("04 ff 02 00 01"))!!

        assertFalse(ack.isSuccessfulAuthConfirm())
    }

    @Test
    fun `auth confirm accepts zero status`() {
        val ack = parseBleAck(hex("04 ff 02 00 00"))!!

        assertTrue(ack.isSuccessfulAuthConfirm())
    }

    @Test
    fun `non ack packet is rejected`() {
        assertNull(parseBleAck(hex("11 06 00 00")))
    }

    private fun hex(value: String): ByteArray =
        value.split(' ').map { it.toInt(16).toByte() }.toByteArray()
}
