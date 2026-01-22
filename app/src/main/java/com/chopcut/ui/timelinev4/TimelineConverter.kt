package com.chopcut.ui.timelinev4

object TimelineConverter {
    private const val BASE_PIXELS_PER_SECOND = 100f

    fun pixelsToMillis(pixels: Float, zoomLevel: Float): Long {
        if (zoomLevel <= 0f) return 0L
        val pixelsPerSecond = BASE_PIXELS_PER_SECOND * zoomLevel
        return ((pixels / pixelsPerSecond) * 1000).toLong()
    }

    fun millisToPixels(millis: Long, zoomLevel: Float): Float {
        val pixelsPerSecond = BASE_PIXELS_PER_SECOND * zoomLevel
        return (millis / 1000f) * pixelsPerSecond
    }
}
