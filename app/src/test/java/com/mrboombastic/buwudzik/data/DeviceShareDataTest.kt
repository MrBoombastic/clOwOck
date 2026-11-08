package com.mrboombastic.buwudzik.data

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DeviceShareDataTest {
    private val mac = "AA:BB:CC:DD:EE:FF"
    private val token = "00112233445566778899aabbccddeeff"

    @Test
    fun `new device uses the shared name and settings`() {
        val profile = DeviceShareData(
            mac = "aa:bb:cc:dd:ee:ff",
            token = token,
            batteryType = BatteryType.NIMH,
            alias = " Bedroom "
        ).toProfile(existing = null, defaultAlias = "My device")

        assertEquals(mac, profile.mac)
        assertEquals("Bedroom", profile.alias)
        assertEquals(BatteryType.NIMH, profile.batteryType)
    }

    @Test
    fun `new device falls back to the default name`() {
        val withoutAlias = DeviceShareData(mac = mac, token = token)
        val withBlankAlias = withoutAlias.copy(alias = "  ")

        assertEquals("My device", withoutAlias.toProfile(null, "My device").alias)
        assertEquals("My device", withBlankAlias.toProfile(null, "My device").alias)
    }

    @Test
    fun `saved device keeps its name and position`() {
        val saved = DeviceProfile(
            mac = mac,
            alias = "Kitchen",
            batteryType = BatteryType.ALKALINE,
            addedAt = 1234L
        )

        val profile = DeviceShareData(
            mac = mac,
            token = token,
            batteryType = BatteryType.NIMH,
            alias = "Bedroom"
        ).toProfile(existing = saved, defaultAlias = "My device")

        assertEquals("Kitchen", profile.alias)
        assertEquals(1234L, profile.addedAt)
        assertEquals(BatteryType.NIMH, profile.batteryType)
    }

    @Test
    fun `unknown battery type is not saved`() {
        val shared = DeviceShareData(mac = mac, token = token, batteryType = "plutonium")
        val saved = DeviceProfile(mac = mac, alias = "Kitchen", batteryType = BatteryType.NIMH)

        assertEquals(BatteryType.ALKALINE, shared.toProfile(null, "My device").batteryType)
        assertEquals(BatteryType.NIMH, shared.toProfile(saved, "My device").batteryType)
    }

    @Test
    fun `code from an older app version without a name is still readable`() {
        val shared = Json.decodeFromString<DeviceShareData>(
            """{"mac":"$mac","token":"$token"}"""
        )

        assertEquals(mac, shared.mac)
        assertNull(shared.alias)
    }
}
