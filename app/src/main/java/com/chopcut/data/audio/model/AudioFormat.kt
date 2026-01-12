package com.chopcut.data.audio.model

/**
 * Supported audio formats for extraction
 */
enum class AudioFormat(
    val extension: String,
    val mimeType: String,
    val containerFormat: Int
) {
    AAC(
        extension = ".m4a",
        mimeType = "audio/mp4",
        containerFormat = 0  // MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
    )
    // MP3(".mp3", "audio/mpeg", 1)  // Future - requires re-encoding
}
