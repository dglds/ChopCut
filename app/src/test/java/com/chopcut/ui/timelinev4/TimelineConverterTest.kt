package com.chopcut.ui.timelinev4

import org.junit.Assert.assertEquals
import org.junit.Test

class TimelineConverterTest {

    @Test
    fun `conversion is reversible`() {
        val originalMillis = 5000L
        val zoom = 1.0f
        
        val pixels = TimelineConverter.millisToPixels(originalMillis, zoom)
        val resultMillis = TimelineConverter.pixelsToMillis(pixels, zoom)
        
        assertEquals(originalMillis, resultMillis)
    }

    @Test
    fun `zoom affects conversion`() {
        val millis = 1000L
        
        // Zoom 1.0 -> 100px
        assertEquals(100f, TimelineConverter.millisToPixels(millis, 1.0f), 0.01f)
        
        // Zoom 2.0 -> 200px
        assertEquals(200f, TimelineConverter.millisToPixels(millis, 2.0f), 0.01f)
    }
}
