package com.chopcut.data.codec

import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.util.Range
import com.chopcut.data.model.VideoCodec

class CodecCapabilities {

    private val codecList = MediaCodecList(MediaCodecList.REGULAR_CODECS)

    /**
     * Check if device has an encoder for the specified codec
     */
    fun hasEncoder(codec: VideoCodec): Boolean {
        return try {
            val codecs = codecList.codecInfos.filter { it.isEncoder }
            codecs.any { info ->
                info.supportedTypes.any { it.equals(codec.mimeType, ignoreCase = true) }
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Check if device has a decoder for the specified codec
     */
    fun hasDecoder(codec: VideoCodec): Boolean {
        return try {
            val codecs = codecList.codecInfos.filter { !it.isEncoder }
            codecs.any { info ->
                info.supportedTypes.any { it.equals(codec.mimeType, ignoreCase = true) }
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Select the best available encoder codec
     * Priority: H265 > H264 > VP9 > VP8 > AV1 > MPEG4
     */
    fun selectBestEncoder(): VideoCodec? {
        val priority = listOf(
            VideoCodec.H265,
            VideoCodec.H264,
            VideoCodec.AV1,
            VideoCodec.VP9,
            VideoCodec.VP8,
            VideoCodec.MPEG4
        )

        for (codec in priority) {
            if (hasEncoder(codec)) {
                return codec
            }
        }

        return null
    }

    /**
     * Get codec info for a specific video codec
     */
    fun getEncoderInfo(codec: VideoCodec): MediaCodecInfo? {
        return try {
            codecList.codecInfos.find { info ->
                info.isEncoder && info.supportedTypes.any {
                    it.equals(codec.mimeType, ignoreCase = true)
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Check if encoder supports a specific resolution and frame rate
     */
    fun supportsResolution(
        codec: VideoCodec,
        width: Int,
        height: Int,
        frameRate: Int
    ): Boolean {
        val info = getEncoderInfo(codec) ?: return false

        return try {
            val capabilities = info.getCapabilitiesForType(codec.mimeType)
            val videoCapabilities = capabilities.videoCapabilities ?: return false

            videoCapabilities.isSizeSupported(width, height) &&
            videoCapabilities.areSizeAndRateSupported(width, height, frameRate.toDouble())
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Get supported resolution range for codec
     */
    fun getSupportedResolutionRange(codec: VideoCodec): Range<Int>? {
        val info = getEncoderInfo(codec) ?: return null

        return try {
            val capabilities = info.getCapabilitiesForType(codec.mimeType)
            capabilities.videoCapabilities?.supportedWidths
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Get supported bitrate range for codec
     */
    fun getSupportedBitrateRange(codec: VideoCodec): Range<Int>? {
        val info = getEncoderInfo(codec) ?: return null

        return try {
            val capabilities = info.getCapabilitiesForType(codec.mimeType)
            capabilities.videoCapabilities?.bitrateRange
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Log all available codecs on the device (for debugging)
     */
    fun logAvailableCodecs() {
        codecList.codecInfos.forEach { info ->
            val type = if (info.isEncoder) "ENCODER" else "DECODER"
            info.supportedTypes.forEach { mimeType ->
            }
        }
    }
}
