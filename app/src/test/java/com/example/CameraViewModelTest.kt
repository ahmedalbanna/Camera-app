package com.example

import com.example.utils.formatDuration
import org.junit.Assert.assertEquals
import org.junit.Test

// Phase 1.4: Pure unit tests for ViewModel-adjacent logic — no Robolectric needed
class CameraViewModelTest {

    @Test
    fun `formatDuration formats HH MM SS`() {
        assertEquals("00:00:00", formatDuration(0))
        assertEquals("00:00:05", formatDuration(5))
        assertEquals("00:01:05", formatDuration(65))
        assertEquals("01:00:00", formatDuration(3600))
        assertEquals("01:01:01", formatDuration(3661))
    }

    @Test
    fun `CustomFlashMode mapping`() {
        // Direct enum test — ensures phase 1 refactor didn't break mapping
        assertEquals(CustomFlashMode.AUTO, CustomFlashMode.valueOf("AUTO"))
        assertEquals(CustomFlashMode.ON, CustomFlashMode.valueOf("ON"))
        assertEquals(CustomFlashMode.OFF, CustomFlashMode.valueOf("OFF"))
    }

    @Test
    fun `CameraMode availability`() {
        assertEquals(CameraMode.PHOTO, CameraMode.valueOf("PHOTO"))
        assertEquals(CameraMode.VIDEO, CameraMode.valueOf("VIDEO"))
    }
}
