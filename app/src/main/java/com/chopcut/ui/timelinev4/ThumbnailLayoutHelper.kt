package com.chopcut.ui.timelinev4

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max

class ThumbnailLayoutHelper {
    
    private val bufferCount = 2

    fun calculateVisibleIndices(
        viewportWidthPx: Int,
        thumbnailWidthPx: Int,
        scrollOffsetX: Float
    ): IntRange {
        if (thumbnailWidthPx <= 0) return IntRange.EMPTY

        val firstVisibleIndex = floor(scrollOffsetX / thumbnailWidthPx).toInt()
        val visibleCount = ceil(viewportWidthPx.toFloat() / thumbnailWidthPx).toInt()
        
        val startIndex = max(0, firstVisibleIndex - bufferCount)
        val endIndex = firstVisibleIndex + visibleCount + bufferCount

        return startIndex..endIndex
    }
}
