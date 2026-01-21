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
        val spacerWidthPx = 540 // Example screen/2
        
        // Total Pixels for 1500ms = 1.5 * 100 = 150px.
        // Spacer is 540px.
        // Total Pixels < Spacer (150 < 540).
        // So we are still scrolling the Spacer (Index 0).
        // Offset = 150.
        
        val result = TimelineCalculator.calculateLazyListScroll(currentMs, thumbWidthPx, msPerThumb, spacerWidthPx)
        
        assertEquals(0, result.first)
        assertEquals(150, result.second)
    }
    
    @Test
    fun calculateLazyListScroll_pastSpacer() {
        // We want to test when we are past spacer.
        // We need TotalPixels > Spacer.
        // Spacer = 540.
        // Let's aim for TotalPixels = 640 (Spacer + 100).
        // Time = 640px / (100px/1000ms) = 6400ms = 6.4s.
        
        val currentMs = 6400L
        val thumbWidthPx = 100
        val msPerThumb = 1000L
        val spacerWidthPx = 540
        
        // Total = 640px.
        // Past Spacer = 100px.
        // 100px is exactly 1 Thumb width.
        // So we are at Start of Thumb 1 (Index 2).
        // Or End of Thumb 0.
        // Index 1 (Thumb 0) covers 0..100 past spacer.
        // So 100 past spacer is Index 2, Offset 0.
        
        val result = TimelineCalculator.calculateLazyListScroll(currentMs, thumbWidthPx, msPerThumb, spacerWidthPx)
        
        assertEquals(2, result.first)
        assertEquals(0, result.second)
    }

    @Test
    fun calculateTimeFromScroll_isCorrect() {
        // Reverse of first case
        val index = 0
        val offset = 150
        val thumbWidthPx = 100
        val msPerThumb = 1000L
        val spacerWidthPx = 540
        
        val timeMs = TimelineCalculator.calculateTimeFromScroll(index, offset, thumbWidthPx, msPerThumb, spacerWidthPx)
        
        assertEquals(1500L, timeMs)
    }
    
    @Test
    fun calculateTimeFromScroll_pastSpacer() {
        // Reverse of second case
        val index = 2
        val offset = 0
        val thumbWidthPx = 100
        val msPerThumb = 1000L
        val spacerWidthPx = 540
        
        val timeMs = TimelineCalculator.calculateTimeFromScroll(index, offset, thumbWidthPx, msPerThumb, spacerWidthPx)
        
        assertEquals(6400L, timeMs)
    }
}
