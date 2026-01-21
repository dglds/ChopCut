package com.chopcut.ui.timeline

import com.chopcut.ui.components.TimelineConfigV2
import org.junit.Assert.assertEquals
import org.junit.Test

class TimelineScrubbingTest {

    @Test
    fun onScrollChanged_snapsToFrame_30fps() {
        var soughtTime = -1L
        val durationMs = 10000L
        val fps = 30
        
        // Note: We will need to update TimelineScrollController to accept frameRate.
        // For this RED phase, we will assume a default or we might need to update the class first to compile.
        // To stick to "Red Phase", I will write the test assuming the current class structure
        // but asserting the Snapped behavior. 
        // Since I cannot inject FPS yet, I will assert "33" and it will return "40".
        // Later I will add the FPS parameter.
        
        val controller = TimelineScrollController(
            durationMs = durationMs,
            frameRate = fps,
            onSeek = { soughtTime = it }
        )
        
        // Thumb = 100px = 1000ms => 1px = 10ms
        val thumbSizePx = 100 
        val screenWidthPx = 1000 // Spacer = 500
        
        // We want raw time 40ms.
        // 40ms = 4px past spacer.
        // Index 0 (Spacer item).
        // Scroll Offset = 504.
        // Total Pixels = 4.
        // Video Pixels = 4.
        // Raw Time = 40ms.
        
        // Target: 30fps => 33.33ms frames.
        // 40ms is closer to 33ms than 66ms.
        // Expected: 33ms.
        
        val index = 0
        val offset = 4
        
        controller.onScrollChanged(index, offset, thumbSizePx, screenWidthPx)
        
        // This should fail because currently it returns 40L
        assertEquals("Should snap to nearest frame (33ms for 30fps)", 33L, soughtTime)
    }
}
