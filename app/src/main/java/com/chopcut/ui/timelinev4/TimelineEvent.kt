package com.chopcut.ui.timelinev4

sealed class TimelineEvent {
    data class Seek(val positionMs: Long) : TimelineEvent()
    data class Zoom(val newZoomLevel: Float) : TimelineEvent()
    data class Scroll(val deltaX: Float) : TimelineEvent()
    object ScrubStart : TimelineEvent()
    object ScrubEnd : TimelineEvent()
}
