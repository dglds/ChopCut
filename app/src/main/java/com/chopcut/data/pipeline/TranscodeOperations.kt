package com.chopcut.data.pipeline

import android.content.Context
import android.net.Uri
import com.chopcut.data.model.ExportConfig
import com.chopcut.data.model.Transform
import com.chopcut.data.model.VideoCodec
import com.chopcut.data.model.VideoInfo
import com.chopcut.data.repository.VideoRepository
import com.chopcut.util.DispatcherProvider
import com.chopcut.util.TimeTracker
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import timber.log.Timber
import java.io.File

/**
 * Advanced video processing operations that require transcoding (decode -> process -> encode)
 */
class TranscodeOperations(
    private val context: Context,
    private val videoRepository: VideoRepository,
    private val dispatcherProvider: DispatcherProvider = DispatcherProvider
) {

    private val transcodePipeline = TranscodePipeline(context, videoRepository, dispatcherProvider)

    /**
     * Compress video by reducing bitrate
     * @param uri Source video URI
     * @param bitrate Target bitrate in bps (e.g., 2_000_000 for 2Mbps)
     * @return Flow<Result<File>> Result with compressed video file
     */
    @OptIn(FlowPreview::class)
    fun compress(uri: Uri, bitrate: Int): Flow<Result<File>> = flow<Result<File>> {
        val timer = TimeTracker.start("compress")
        Timber.d("Starting compress operation for $uri with bitrate $bitrate")

        val outputFile = videoRepository.createTempFile(".mp4")

        try {
            // Get original video metadata
            val metadata = videoRepository.getMetadata(uri)
                ?: throw IllegalArgumentException("Unable to read video metadata")

            // Create export config with lower bitrate
            val config = ExportConfig(
                width = metadata.width,
                height = metadata.height,
                bitrate = bitrate,
                frameRate = metadata.frameRate,
                codec = metadata.videoCodec?.let { mimeType ->
                    VideoCodec.fromMimeType(mimeType)
                } ?: VideoCodec.H264,
                preserveAudio = metadata.hasAudio
            )

            // Validate bitrate is reasonable
            require(bitrate in 100_000..50_000_000) {
                "Bitrate must be between 100kbps and 50Mbps"
            }

            // Calculate compression ratio
            val originalBitrate = metadata.bitrate
            val compressionRatio = bitrate.toFloat() / originalBitrate.toFloat()
            val targetFileSize = (metadata.sizeBytes * compressionRatio).toLong()

            Timber.d("Compression: $originalBitrate -> $bitrate (${String.format("%.1f%%", compressionRatio * 100)} of original)")
            Timber.d("Expected file size: ${targetFileSize / (1024 * 1024)} MB")

            // Use TranscodePipeline with identity transform
            emitAll(transcodePipeline.process(uri, Transform.IDENTITY, config))

        } catch (e: Exception) {
            Timber.e(e, "Error during compress operation")
            emit(Result.failure(e))
            if (outputFile.exists()) {
                outputFile.delete()
            }
        } finally {
            timer.end()
        }
    }.flowOn(dispatcherProvider.io)

    /**
     * Resize video to new resolution
     * @param uri Source video URI
     * @param width Target width
     * @param height Target height
     * @return Flow<Result<File>> Result with resized video file
     */
    @OptIn(FlowPreview::class)
    fun resize(uri: Uri, width: Int, height: Int): Flow<Result<File>> = flow<Result<File>> {
        val timer = TimeTracker.start("resize")
        Timber.d("Starting resize operation for $uri to ${width}x${height}")

        try {
            // Get original video metadata
            val metadata = videoRepository.getMetadata(uri)
                ?: throw IllegalArgumentException("Unable to read video metadata")

            // Validate dimensions
            require(width > 0 && height > 0) { "Width and height must be positive" }
            require(width <= 4096 && height <= 4096) { "Max resolution is 4096x4096" }

            // Calculate aspect ratio
            val originalAspect = metadata.width.toFloat() / metadata.height.toFloat()
            val targetAspect = width.toFloat() / height.toFloat()

            if (kotlin.math.abs(originalAspect - targetAspect) > 0.01f) {
                Timber.w("Aspect ratio mismatch: original=$originalAspect, target=$targetAspect")
            }

            // Create export config with new resolution
            val config = ExportConfig(
                width = width,
                height = height,
                bitrate = metadata.bitrate.toInt(),
                frameRate = metadata.frameRate,
                codec = metadata.videoCodec?.let { mimeType ->
                    VideoCodec.fromMimeType(mimeType)
                } ?: VideoCodec.H264,
                preserveAudio = metadata.hasAudio
            )

            Timber.d("Resize: ${metadata.width}x${metadata.height} -> ${width}x${height}")

            // Use TranscodePipeline with identity transform (no crop/rotation)
            emitAll(transcodePipeline.process(uri, Transform.IDENTITY, config))

        } catch (e: Exception) {
            Timber.e(e, "Error during resize operation")
            emit(Result.failure(e))
        } finally {
            timer.end()
        }
    }.flowOn(dispatcherProvider.io)

    /**
     * Crop video to specified region
     * @param uri Source video URI
     * @param cropRect Crop region (normalized coordinates 0-1)
     * @return Flow<Result<File>> Result with cropped video file
     */
    @OptIn(FlowPreview::class)
    fun crop(uri: Uri, cropRect: android.graphics.RectF): Flow<Result<File>> = flow<Result<File>> {
        val timer = TimeTracker.start("crop")
        Timber.d("Starting crop operation for $uri with rect $cropRect")

        try {
            // Get original video metadata
            val metadata = videoRepository.getMetadata(uri)
                ?: throw IllegalArgumentException("Unable to read video metadata")

            // Validate crop rect
            require(cropRect.left >= 0 && cropRect.top >= 0 &&
                    cropRect.right <= 1f && cropRect.bottom <= 1f) {
                "Crop rect must be in normalized coordinates (0-1)"
            }
            require(cropRect.left < cropRect.right && cropRect.top < cropRect.bottom) {
                "Crop rect must have positive width and height"
            }

            // Calculate crop dimensions
            val cropWidth = ((cropRect.right - cropRect.left) * metadata.width).toInt()
            val cropHeight = ((cropRect.bottom - cropRect.top) * metadata.height).toInt()

            Timber.d("Crop: ${metadata.width}x${metadata.height} -> ${cropWidth}x${cropHeight}")

            // Create export config with cropped dimensions
            val config = ExportConfig(
                width = cropWidth,
                height = cropHeight,
                bitrate = metadata.bitrate.toInt(),
                frameRate = metadata.frameRate,
                codec = metadata.videoCodec?.let { mimeType ->
                    VideoCodec.fromMimeType(mimeType)
                } ?: VideoCodec.H264,
                preserveAudio = metadata.hasAudio
            )

            // Use IDENTITY transform (no OpenGL) - just resize to crop dimensions
            // TODO: Full visual crop requires OpenGL in single-threaded context
            Timber.d("Crop using direct surface path (resize to ${cropWidth}x${cropHeight})")
            val transform = Transform.IDENTITY
            emitAll(transcodePipeline.process(uri, transform, config))

        } catch (e: Exception) {
            Timber.e(e, "Error during crop operation")
            emit(Result.failure(e))
        } finally {
            timer.end()
        }
    }.flowOn(dispatcherProvider.io)

    /**
     * Helper method to calculate recommended bitrate for given resolution
     */
    fun calculateRecommendedBitrate(width: Int, height: Int, quality: VideoQuality = VideoQuality.MEDIUM): Int {
        val pixels = width * height

        // Base bitrate: 1080p@30fps = 5Mbps (reference)
        val referencePixels = 1920 * 1080
        val referenceBitrate = 5_000_000

        // Scale by resolution
        val baseBitrate = (pixels.toFloat() / referencePixels * referenceBitrate).toInt()

        // Apply quality multiplier
        return when (quality) {
            VideoQuality.LOW -> (baseBitrate * 0.5f).toInt()
            VideoQuality.MEDIUM -> baseBitrate
            VideoQuality.HIGH -> (baseBitrate * 1.5f).toInt()
            VideoQuality.ULTRA -> (baseBitrate * 2.5f).toInt()
        }
    }

    /**
     * Helper method to calculate target dimensions for resize
     */
    fun calculateTargetDimensions(
        originalWidth: Int,
        originalHeight: Int,
        targetWidth: Int?,
        targetHeight: Int?,
        maintainAspectRatio: Boolean = true
    ): Pair<Int, Int> {
        if (targetWidth != null && targetHeight != null) {
            return if (maintainAspectRatio) {
                val originalAspect = originalWidth.toFloat() / originalHeight.toFloat()
                val targetAspect = targetWidth.toFloat() / targetHeight.toFloat()

                if (originalAspect > targetAspect) {
                    // Fit width
                    Pair(targetWidth, (targetWidth / originalAspect).toInt())
                } else {
                    // Fit height
                    Pair((targetHeight * originalAspect).toInt(), targetHeight)
                }
            } else {
                Pair(targetWidth, targetHeight)
            }
        }

        if (targetWidth != null) {
            val aspect = originalHeight.toFloat() / originalWidth.toFloat()
            return Pair(targetWidth, (targetWidth * aspect).toInt())
        }

        if (targetHeight != null) {
            val aspect = originalWidth.toFloat() / originalHeight.toFloat()
            return Pair((targetHeight * aspect).toInt(), targetHeight)
        }

        return Pair(originalWidth, originalHeight)
    }
}

/**
 * Video quality presets for compression
 */
enum class VideoQuality {
    LOW,       // ~50% of recommended bitrate
    MEDIUM,    // 100% of recommended bitrate
    HIGH,      // ~150% of recommended bitrate
    ULTRA      // ~250% of recommended bitrate
}
