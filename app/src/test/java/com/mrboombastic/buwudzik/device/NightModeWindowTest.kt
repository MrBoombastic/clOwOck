package com.mrboombastic.buwudzik.device

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NightModeWindowTest {
    @Test
    fun `overnight window is enabled`() {
        assertTrue(isNightModeWindowEnabled(22, 0, 8, 0))
    }

    @Test
    fun `same day window is enabled`() {
        assertTrue(isNightModeWindowEnabled(1, 0, 6, 30))
    }

    @Test
    fun `this app disabled window is disabled`() {
        assertFalse(isNightModeWindowEnabled(0, 0, 0, 1))
    }

    @Test
    fun `qingping disabled window is disabled`() {
        assertFalse(isNightModeWindowEnabled(21, 0, 21, 1))
    }

    @Test
    fun `one minute window across midnight is disabled`() {
        assertFalse(isNightModeWindowEnabled(23, 59, 0, 0))
    }

    @Test
    fun `equal start and end means all day night mode`() {
        assertTrue(isNightModeWindowEnabled(7, 0, 7, 0))
    }
}
