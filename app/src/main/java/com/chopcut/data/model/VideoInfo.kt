package com.chopcut.data.model

import android.net.Uri

data class VideoInfo(
    val uri: Uri,
    val fileName: String,
    val mimeType: String,
    val durationUs: Long, // Duration in microseconds
    val width: Int,
    val height: Int,
    val rotation: Int,
    val bitrate: Long,
    val frameRate: Int,
    val videoCodec: String?,
    val audioCodec: String?,
    val hasAudio: Boolean,
    val sizeBytes: Long
) {
    val durationMs: Long get() = durationUs / 1000
    val widthF: Float get() = width.toFloat()
    val heightF: Float get() = height.toFloat()

    val aspectRatio: Float
        get() {
            if (width == 0 || height == 0) return 16f / 9f // Fallback
            val isPortrait = rotation == 90 || rotation == 270
            val displayWidth = if (isPortrait) height else width
            val displayHeight = if (isPortrait) width else height
            return displayWidth.toFloat() / displayHeight.toFloat()
        }
}
