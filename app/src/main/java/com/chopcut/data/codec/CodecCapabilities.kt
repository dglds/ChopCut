package com.chopcut.data.codec

import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.util.Range
import com.chopcut.data.model.VideoCodec
import timber.log.Timber

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
            Timber.e(e, "Error checking encoder for ${codec.displayName}")
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
            Timber.e(e, "Error checking decoder for ${codec.displayName}")
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
                Timber.d("Selected encoder: ${codec.displayName}")
                return codec
            }
        }

        Timber.w("No suitable encoder found")
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
            Timber.e(e, "Error getting encoder info for ${codec.displayName}")
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
            Timber.e(e, "Error checking capabilities for ${codec.displayName}")
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
            Timber.e(e, "Error getting resolution range for ${codec.displayName}")
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
            Timber.e(e, "Error getting bitrate range for ${codec.displayName}")
            null
        }
    }

    /**
     * Log all available codecs on the device (for debugging)
     */
    fun logAvailableCodecs() {
        Timber.d("=== Available Codecs ===")
        codecList.codecInfos.forEach { info ->
            val type = if (info.isEncoder) "ENCODER" else "DECODER"
            Timber.d("$type: ${info.name}")
            info.supportedTypes.forEach { mimeType ->
                Timber.d("  - $mimeType")
            }
        }
        Timber.d("========================")
    }
}
