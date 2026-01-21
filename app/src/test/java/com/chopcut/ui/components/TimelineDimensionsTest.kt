package com.chopcut.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class TimelineDimensionsTest {

    @Test
    fun calculateScrollOffset_isCorrect() {
        val currentMs = 1500L // 1.5s
        val thumbWidthPx = 100
        val msPerThumb = 1000L
        
        // Expected: 1.5 * 100 = 150px
        val offset = TimelineCalculator.calculateScrollOffset(currentMs, thumbWidthPx, msPerThumb)
        assertEquals(150, offset)
    }
    
    @Test
    fun calculateLazyListScroll_isCorrect() {
        val currentMs = 1500L
        val thumbWidthPx = 100
        val msPerThumb = 1000L
        
        // LazyRow item index and offset
        // Index = 1 (second thumb, first is 0-1000ms)
        // Offset = 50px (halfway through second thumb)
        
        val result = TimelineCalculator.calculateLazyListScroll(currentMs, thumbWidthPx, msPerThumb)
        
        assertEquals(1, result.first)
        assertEquals(50, result.second)
    }
}
