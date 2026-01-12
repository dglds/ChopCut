package com.chopcut.data.audio.model

/**
 * Audio track metadata extracted from video
 */
data class AudioInfo(
    val codec: String,
    val sampleRate: Int,
    val channelCount: Int,
    val bitrate: Long,
    val durationUs: Long,
    val mimeType: String,
    val language: String? = null
) {
    val durationMs: Long
        get() = durationUs / 1000

    val bitrateKbps: Long
        get() = bitrate / 1000

    val isStereo: Boolean
        get() = channelCount == 2
}
