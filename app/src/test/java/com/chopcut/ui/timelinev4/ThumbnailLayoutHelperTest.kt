package com.chopcut.ui.timelinev4

import org.junit.Assert.assertEquals
import org.junit.Test

class ThumbnailLayoutHelperTest {

    @Test
    fun `calculateVisibleThumbnails returns correct range`() {
        val helper = ThumbnailLayoutHelper()
        // Assume timeline width 1000px, thumbnail width 100px
        // Content offset 500px (scrolled halfway first screen?)
        
        // Scenario: 
        // Viewport Width: 1080px
        // Thumbnail Width: 120px (at zoom 1.0)
        // Scroll Offset X: 0
        
        val indices = helper.calculateVisibleIndices(
            viewportWidthPx = 1080,
            thumbnailWidthPx = 120,
            scrollOffsetX = 0f
        )
        
        // Should show indices 0 to 9 (1080/120 = 9) + buffer
        assertEquals(0, indices.first)
        assertEquals(11, indices.last) // 9 visible + 2 buffer
    }

    @Test
    fun `calculateVisibleThumbnails with scroll offset`() {
        val helper = ThumbnailLayoutHelper()
        
        // Scroll Offset X: 240px (2 thumbnails)
        val indices = helper.calculateVisibleIndices(
            viewportWidthPx = 1080,
            thumbnailWidthPx = 120,
            scrollOffsetX = 240f
        )
        
        // Start index should be 2 (minus buffer)
        // End index should be 2 + 9 + buffer
        
        assertEquals(0, indices.first) // 2 - buffer(2) = 0
        assertEquals(13, indices.last) // 2 + 9 + buffer(2) = 13
    }
}
