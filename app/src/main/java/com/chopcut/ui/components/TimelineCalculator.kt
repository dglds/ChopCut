package com.chopcut.ui.components

object TimelineCalculator {
    
    fun calculateScrollOffset(currentMs: Long, thumbWidthPx: Int, msPerThumb: Long): Int {
        if (msPerThumb == 0L) return 0
        val pixelsPerMs = thumbWidthPx.toFloat() / msPerThumb
        return (currentMs * pixelsPerMs).toInt()
    }

    fun calculateLazyListScroll(
        currentMs: Long, 
        thumbWidthPx: Int, 
        msPerThumb: Long,
        spacerWidthPx: Int
    ): Pair<Int, Int> {
        if (msPerThumb == 0L) return 0 to 0
        
        val totalPixels = (currentMs * thumbWidthPx.toFloat() / msPerThumb).toInt()
        
        if (totalPixels < spacerWidthPx) {
             return 0 to totalPixels
        } else {
             val pixelsPastSpacer = totalPixels - spacerWidthPx
             val thumbIndex = pixelsPastSpacer / thumbWidthPx
             val offsetInThumb = pixelsPastSpacer % thumbWidthPx
             return (thumbIndex + 1) to offsetInThumb
        }
    }

    fun calculateTimeFromScroll(
        index: Int, 
        offset: Int, 
        thumbWidthPx: Int, 
        msPerThumb: Long,
        spacerWidthPx: Int
    ): Long {
        if (thumbWidthPx == 0) return 0L
        
        var totalPixels = 0L
        if (index == 0) {
             totalPixels = offset.toLong()
        } else {
             // Spacer + Thumbs
             val thumbsPast = index - 1
             totalPixels = spacerWidthPx + thumbsPast.toLong() * thumbWidthPx + offset
        }
        
        // Convert to Time
        val videoPixels = totalPixels - spacerWidthPx
        return (videoPixels * msPerThumb / thumbWidthPx)
    }
}