package com.chopcut.data.pipeline

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import com.chopcut.data.model.TimeRange
import com.chopcut.data.repository.VideoRepository
import com.chopcut.util.DispatcherProvider
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File

class CopyPipeline(
    private val context: Context,
    private val videoRepository: VideoRepository,
    private val dispatcherProvider: DispatcherProvider = DispatcherProvider
) {

    /**
     * Trim video without re-encoding (fast copy)
     * @param uri Source video URI
     * @param ranges Time ranges to extract (multiple ranges will be concatenated)
     * @return Flow<Float> Progress from 0.0 to 1.0
     */
    @OptIn(FlowPreview::class)
    suspend fun trim(uri: Uri, ranges: List<TimeRange>): Flow<Result<File>> = flow {
        Timber.d("Starting trim operation for $uri with ${ranges.size} range(s)")

        val outputFile = withContext(dispatcherProvider.io) {
            videoRepository.createTempFile(".mp4")
        }

        try {
            withContext(dispatcherProvider.io) {
                var sourceExtractor: MediaExtractor? = null
                var muxer: MediaMuxer? = null

                try {
                    // Get video metadata
                    val metadata = videoRepository.getMetadata(uri)
                        ?: throw IllegalArgumentException("Unable to read video metadata")

                    sourceExtractor = MediaExtractor()
                    sourceExtractor.setDataSource(context, uri, null)

                    // Create muxer
                    muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

                    // Find tracks
                    val videoTrackIndex = findTrackIndex(sourceExtractor, "video/")
                    val audioTrackIndex = findTrackIndex(sourceExtractor, "audio/")

                    if (videoTrackIndex < 0) {
                        throw IllegalArgumentException("No video track found")
                    }

                    // Get track formats
                    sourceExtractor.selectTrack(videoTrackIndex)
                    val videoFormat = sourceExtractor.getTrackFormat(videoTrackIndex)

                    var audioFormat: MediaFormat? = null
                    var audioTrackOutputIndex = -1

                    if (audioTrackIndex >= 0 && metadata.hasAudio) {
                        sourceExtractor.selectTrack(audioTrackIndex)
                        audioFormat = sourceExtractor.getTrackFormat(audioTrackIndex)
                    }

                    // Calculate total duration to process
                    val totalDurationUs = ranges.sumOf { (it.endMs - it.startMs) * 1000 }
                    var processedDurationUs = 0L

                    // Add tracks to muxer
                    val videoTrackOutputIndex = muxer.addTrack(videoFormat)
                    if (audioFormat != null) {
                        audioTrackOutputIndex = muxer.addTrack(audioFormat)
                    }

                    muxer.start()

                    // Process each range
                    ranges.forEachIndexed { rangeIndex, range ->
                        Timber.d("Processing range ${rangeIndex + 1}/${ranges.size}: ${range.startMs}ms - ${range.endMs}ms")

                        val rangeDurationUs = (range.endMs - range.startMs) * 1000

                        // Process video track
                        processTrack(
                            extractor = sourceExtractor,
                            muxer = muxer,
                            trackIndex = videoTrackIndex,
                            outputTrackIndex = videoTrackOutputIndex,
                            startUs = range.startMs * 1000,
                            endUs = range.endMs * 1000,
                            onProgress = { progress ->
                                // Progress callback - could emit progress here
                            }
                        )

                        processedDurationUs += rangeDurationUs

                        // Process audio track if exists
                        if (audioTrackIndex >= 0 && audioTrackOutputIndex >= 0 && metadata.hasAudio) {
                            processTrack(
                                extractor = sourceExtractor,
                                muxer = muxer,
                                trackIndex = audioTrackIndex,
                                outputTrackIndex = audioTrackOutputIndex,
                                startUs = range.startMs * 1000,
                                endUs = range.endMs * 1000,
                                onProgress = { }
                            )
                        }

                        // Reset extractor for next range
                        sourceExtractor.unselectTrack(videoTrackIndex)
                        sourceExtractor.selectTrack(videoTrackIndex)
                        if (audioTrackIndex >= 0) {
                            sourceExtractor.unselectTrack(audioTrackIndex)
                            sourceExtractor.selectTrack(audioTrackIndex)
                        }
                    }

                    muxer.stop()
                    Timber.d("Trim completed successfully: ${outputFile.absolutePath}")
                    emit(Result.success(outputFile))

                } finally {
                    sourceExtractor?.release()
                    muxer?.release()
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error during trim operation")
            emit(Result.failure(e))
            withContext(dispatcherProvider.io) {
                if (outputFile.exists()) {
                    outputFile.delete()
                }
            }
        }
    }

    /**
     * Join/concatenate multiple videos without re-encoding
     * Note: All videos must have the same resolution and codec
     * @param uris List of video URIs to concatenate
     * @return Flow<Float> Progress from 0.0 to 1.0
     */
    @OptIn(FlowPreview::class)
    suspend fun concat(uris: List<Uri>): Flow<Result<File>> = flow {
        if (uris.isEmpty()) {
            emit(Result.failure(IllegalArgumentException("No videos provided")))
            return@flow
        }

        Timber.d("Starting concat operation for ${uris.size} video(s)")

        val outputFile = withContext(dispatcherProvider.io) {
            videoRepository.createTempFile(".mp4")
        }

        try {
            withContext(dispatcherProvider.io) {
                var muxer: MediaMuxer? = null

                try {
                    // Get metadata from first video (reference)
                    val firstMetadata = videoRepository.getMetadata(uris.first())
                        ?: throw IllegalArgumentException("Unable to read first video metadata")

                    // Validate all videos have compatible formats
                    uris.forEach { uri ->
                        val metadata = videoRepository.getMetadata(uri)
                        if (metadata?.width != firstMetadata.width ||
                            metadata?.height != firstMetadata.height) {
                            throw IllegalArgumentException("Videos must have the same resolution")
                        }
                    }

                    muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

                    // Create extractors for all sources
                    val sources = uris.map { uri ->
                        val extractor = MediaExtractor()
                        extractor.setDataSource(context, uri, null)

                        val videoTrackIndex = findTrackIndex(extractor, "video/")
                        val audioTrackIndex = findTrackIndex(extractor, "audio/")

                        val metadata = videoRepository.getMetadata(uri)!!

                        VideoSource(
                            extractor = extractor,
                            videoTrackIndex = videoTrackIndex,
                            audioTrackIndex = audioTrackIndex,
                            videoFormat = extractor.getTrackFormat(videoTrackIndex),
                            audioFormat = if (audioTrackIndex >= 0) extractor.getTrackFormat(audioTrackIndex) else null,
                            durationUs = metadata.durationUs,
                            hasAudio = metadata.hasAudio
                        )
                    }

                    // Use format from first video
                    val firstSource = sources.first()
                    val videoTrackOutputIndex = muxer.addTrack(firstSource.videoFormat)
                    val audioTrackOutputIndex = if (firstSource.audioFormat != null) {
                        muxer.addTrack(firstSource.audioFormat)
                    } else -1

                    muxer.start()

                    // Calculate total duration
                    val totalDurationUs = sources.sumOf { it.durationUs }
                    var processedDurationUs = 0L

                    // Process each source
                    sources.forEachIndexed { index, source ->
                        Timber.d("Processing source ${index + 1}/${sources.size}")

                        // Copy video track
                        copyTrack(
                            extractor = source.extractor,
                            muxer = muxer!!,
                            trackIndex = source.videoTrackIndex,
                            outputTrackIndex = videoTrackOutputIndex,
                            onProgress = { progress ->
                                val sourceProgress = (progress * source.durationUs).toLong()
                                val totalProgress = ((processedDurationUs + sourceProgress).toFloat() / totalDurationUs.toFloat())
                                // Could emit progress here
                            }
                        )

                        // Copy audio track if exists
                        if (source.audioTrackIndex >= 0 && audioTrackOutputIndex >= 0 && source.hasAudio) {
                            copyTrack(
                                extractor = source.extractor,
                                muxer = muxer!!,
                                trackIndex = source.audioTrackIndex,
                                outputTrackIndex = audioTrackOutputIndex,
                                onProgress = { }
                            )
                        }

                        processedDurationUs += source.durationUs
                    }

                    muxer.stop()
                    Timber.d("Concat completed successfully: ${outputFile.absolutePath}")
                    emit(Result.success(outputFile))

                } finally {
                    muxer?.release()
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error during concat operation")
            emit(Result.failure(e))
            withContext(dispatcherProvider.io) {
                if (outputFile.exists()) {
                    outputFile.delete()
                }
            }
        }
    }

    /**
     * Find track index by MIME type prefix
     */
    private fun findTrackIndex(extractor: MediaExtractor, mimeTypePrefix: String): Int {
        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME)
            if (mime?.startsWith(mimeTypePrefix) == true) {
                return i
            }
        }
        return -1
    }

    /**
     * Process a single track within a time range
     */
    private fun processTrack(
        extractor: MediaExtractor,
        muxer: MediaMuxer,
        trackIndex: Int,
        outputTrackIndex: Int,
        startUs: Long,
        endUs: Long,
        onProgress: (Float) -> Unit
    ) {
        extractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)

        val buffer = MediaCodec.BufferInfo()
        val byteBuffer = java.nio.ByteBuffer.allocate(1024 * 1024)

        var lastProgressUpdate = 0f

        while (true) {
            val sampleSize = extractor.readSampleData(byteBuffer, 0)
            if (sampleSize < 0) break

            val sampleTime = extractor.sampleTime
            if (sampleTime == -1L) break

            // Check if we've reached the end of range
            if (sampleTime >= endUs) {
                break
            }

            // Skip samples before start time
            if (sampleTime >= startUs) {
                buffer.offset = 0
                buffer.size = sampleSize
                buffer.presentationTimeUs = sampleTime - startUs
                buffer.flags = extractor.sampleFlags

                muxer.writeSampleData(outputTrackIndex, byteBuffer, buffer)

                // Update progress
                val progress = ((sampleTime - startUs).toFloat() / (endUs - startUs).toFloat())
                if (progress - lastProgressUpdate > 0.01f) { // Update every 1%
                    onProgress(progress)
                    lastProgressUpdate = progress
                }
            }

            extractor.advance()
        }

        onProgress(1f)
    }

    /**
     * Copy an entire track
     */
    private fun copyTrack(
        extractor: MediaExtractor,
        muxer: MediaMuxer,
        trackIndex: Int,
        outputTrackIndex: Int,
        onProgress: (Float) -> Unit
    ) {
        extractor.unselectTrack(trackIndex)
        extractor.selectTrack(trackIndex)
        extractor.seekTo(0, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)

        val buffer = MediaCodec.BufferInfo()
        val byteBuffer = java.nio.ByteBuffer.allocate(1024 * 1024)

        val durationUs = extractor.getTrackFormat(trackIndex).getLong(MediaFormat.KEY_DURATION)
        var lastProgressUpdate = 0f

        while (true) {
            val sampleSize = extractor.readSampleData(byteBuffer, 0)
            if (sampleSize < 0) break

            val sampleTime = extractor.sampleTime
            if (sampleTime == -1L) break

            buffer.offset = 0
            buffer.size = sampleSize
            buffer.presentationTimeUs = sampleTime
            buffer.flags = extractor.sampleFlags

            muxer.writeSampleData(outputTrackIndex, byteBuffer, buffer)

            // Update progress
            val progress = if (durationUs > 0) {
                sampleTime.toFloat() / durationUs.toFloat()
            } else {
                0f
            }
            if (progress - lastProgressUpdate > 0.01f) {
                onProgress(progress.coerceIn(0f, 1f))
                lastProgressUpdate = progress
            }

            extractor.advance()
        }

        onProgress(1f)
    }

    private data class VideoSource(
        val extractor: MediaExtractor,
        val videoTrackIndex: Int,
        val audioTrackIndex: Int,
        val videoFormat: MediaFormat,
        val audioFormat: MediaFormat?,
        val durationUs: Long,
        val hasAudio: Boolean
    )
}
