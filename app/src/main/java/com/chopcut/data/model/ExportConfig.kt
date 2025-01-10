package com.chopcut.data.model

/**
 * Export configuration for video encoding
 */
data class ExportConfig(
    val width: Int,
    val height: Int,
    val bitrate: Int = 5_000_000,        // 5 Mbps default
    val frameRate: Int = 30,
    val codec: VideoCodec = VideoCodec.H264,
    val keyFrameInterval: Int = 1,       // Key frame every 1 second
    val preserveAudio: Boolean = true
) {
    companion object {
        /**
         * Create config that matches source video
         */
        fun fromVideoInfo(videoInfo: VideoInfo): ExportConfig {
            return ExportConfig(
                width = videoInfo.width,
                height = videoInfo.height,
                bitrate = videoInfo.bitrate.toInt(),
                frameRate = videoInfo.frameRate,
                preserveAudio = videoInfo.hasAudio
            )
        }
    }

    val aspectRatio: Float get() = width.toFloat() / height.toFloat()

    fun isValid(): Boolean {
        return width > 0 && height > 0 &&
                bitrate > 0 &&
                frameRate > 0 &&
                width <= 4096 && height <= 4096 // Max resolution
    }
}
