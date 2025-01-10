package com.chopcut.data.model

enum class VideoCodec(
    val mimeType: String,
    val displayName: String
) {
    H264("video/avc", "H.264 (AVC)"),
    H265("video/hevc", "H.265 (HEVC)"),
    VP8("video/x-vnd.on2.vp8", "VP8"),
    VP9("video/x-vnd.on2.vp9", "VP9"),
    AV1("video/av01", "AV1"),
    MPEG4("video/mp4v-es", "MPEG-4");

    companion object {
        fun fromMimeType(mimeType: String?): VideoCodec? {
            return entries.find { it.mimeType == mimeType }
        }
    }
}
