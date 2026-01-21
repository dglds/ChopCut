package com.chopcut.ui.components

object TimelineCalculator {
    
    fun calculateScrollOffset(currentMs: Long, thumbWidthPx: Int, msPerThumb: Long): Int {
        if (msPerThumb == 0L) return 0
        val pixelsPerMs = thumbWidthPx.toFloat() / msPerThumb
        return (currentMs * pixelsPerMs).toInt()
    }

    fun calculateLazyListScroll(currentMs: Long, thumbWidthPx: Int, msPerThumb: Long): Pair<Int, Int> {
        if (msPerThumb == 0L) return 0 to 0
        
        val index = (currentMs / msPerThumb).toInt()
        val remainderMs = currentMs % msPerThumb
        val pixelsPerMs = thumbWidthPx.toFloat() / msPerThumb
        val offset = (remainderMs * pixelsPerMs).toInt()
        
        return index to offset
    }
}
