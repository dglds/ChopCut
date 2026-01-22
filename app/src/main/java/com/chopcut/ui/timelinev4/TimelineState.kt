package com.chopcut.ui.timelinev4

data class TimelineState(
    val currentTimeMs: Long = 0L,
    val zoomLevel: Float = 1.0f,
    val isScrubbing: Boolean = false,
    val clips: List<TimelineClip> = emptyList()
)

data class TimelineClip(
    val id: String,
    val filePath: String,
    val durationMs: Long,
    val offsetMs: Long = 0L // Start time in timeline
)
