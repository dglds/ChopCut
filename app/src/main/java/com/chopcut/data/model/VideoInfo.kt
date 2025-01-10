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
}
