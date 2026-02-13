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
import com.chopcut.util.TimeTracker
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
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
    fun trim(uri: Uri, ranges: List<TimeRange>): Flow<Result<File>> = flow {
        val timer = TimeTracker.start("trim")
        Timber.d("Starting trim operation for $uri with ${ranges.size} range(s)")

        val outputFile = videoRepository.createTempFile(".mp4")

        try {
            var videoExtractor: MediaExtractor? = null
            var audioExtractor: MediaExtractor? = null
            var muxer: MediaMuxer? = null

            try {
                // Get video metadata
                val metadata = videoRepository.getMetadata(uri)
                    ?: throw IllegalArgumentException("Unable to read video metadata")

                Timber.d("Video metadata: ${metadata.width}x${metadata.height}, duration=${metadata.durationUs}us, hasAudio=${metadata.hasAudio}")

                // Create extractors for video and audio separately
                videoExtractor = MediaExtractor()
                videoExtractor.setDataSource(context, uri, null)

                audioExtractor = if (metadata.hasAudio) {
                    MediaExtractor().apply { setDataSource(context, uri, null) }
                } else {
                    null
                }

                // Find tracks
                val videoTrackIndex = findTrackIndex(videoExtractor, "video/")
                val audioTrackIndex = if (audioExtractor != null) {
                    findTrackIndex(audioExtractor, "audio/")
                } else {
                    -1
                }

                Timber.d("Track indices: video=$videoTrackIndex, audio=$audioTrackIndex")

                if (videoTrackIndex < 0) {
                    throw IllegalArgumentException("No video track found")
                }

                // Get track formats
                videoExtractor.selectTrack(videoTrackIndex)
                val videoFormat = videoExtractor.getTrackFormat(videoTrackIndex)

                Timber.d("Video format: ${videoFormat.toString()}")

                var audioFormat: MediaFormat? = null
                var audioTrackOutputIndex = -1

                if (audioExtractor != null && audioTrackIndex >= 0 && metadata.hasAudio) {
                    audioExtractor.selectTrack(audioTrackIndex)
                    audioFormat = audioExtractor.getTrackFormat(audioTrackIndex)
                    Timber.d("Audio format: ${audioFormat.toString()}")
                }

                // Create muxer
                muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

                // Add tracks to muxer
                val videoTrackOutputIndex = muxer.addTrack(videoFormat)
                Timber.d("Video track output index: $videoTrackOutputIndex")

                if (audioFormat != null) {
                    audioTrackOutputIndex = muxer.addTrack(audioFormat)
                    Timber.d("Audio track output index: $audioTrackOutputIndex")
                }

                muxer.start()
                Timber.d("Muxer started")

                // Calculate total duration to process
                val totalDurationUs = ranges.sumOf { (it.endMs - it.startMs) * 1000 }
                var processedDurationUs = 0L
                var totalSamplesWritten = 0L

                Timber.d("Processing ${ranges.size} ranges, total duration: ${totalDurationUs}us")

                // Process each range
                ranges.forEachIndexed { rangeIndex, range ->
                    Timber.d("Processing range ${rangeIndex + 1}/${ranges.size}: ${range.startMs}ms - ${range.endMs}ms")

                    val rangeDurationUs = (range.endMs - range.startMs) * 1000
                    val offsetUs = processedDurationUs

                    // Process video track
                    videoExtractor.unselectTrack(videoTrackIndex)
                    videoExtractor.selectTrack(videoTrackIndex)
                    var videoSamples = 0
                    processTrack(
                        extractor = videoExtractor,
                        muxer = muxer,
                        trackIndex = videoTrackIndex,
                        outputTrackIndex = videoTrackOutputIndex,
                        startUs = range.startMs * 1000,
                        endUs = range.endMs * 1000,
                        offsetUs = offsetUs,
                        onProgress = { progress ->
                            // Progress callback - could emit progress here
                        }
                    )

                    // Process audio track if exists
                    if (audioExtractor != null && audioTrackIndex >= 0 && audioTrackOutputIndex >= 0) {
                        audioExtractor.unselectTrack(audioTrackIndex)
                        audioExtractor.selectTrack(audioTrackIndex)
                        processTrack(
                            extractor = audioExtractor,
                            muxer = muxer,
                            trackIndex = audioTrackIndex,
                            outputTrackIndex = audioTrackOutputIndex,
                            startUs = range.startMs * 1000,
                            endUs = range.endMs * 1000,
                            offsetUs = offsetUs,
                            onProgress = { }
                        )
                    }

                    processedDurationUs += rangeDurationUs
                    Timber.d("Completed range ${rangeIndex + 1}, processedDuration=${processedDurationUs}us")
                }

                Timber.d("All ranges processed, stopping muxer...")
                muxer.stop()
                Timber.d("Muxer stopped, output file size: ${outputFile.length()} bytes")
                Timber.d("Trim completed successfully: ${outputFile.absolutePath}")
                emit(Result.success(outputFile))

            } finally {
                videoExtractor?.release()
                audioExtractor?.release()
                muxer?.release()
            }
        } catch (e: Exception) {
            Timber.e(e, "Error during trim operation")
            emit(Result.failure(e))
            if (outputFile.exists()) {
                outputFile.delete()
            }
        } finally {
            timer.end()
        }
    }.flowOn(dispatcherProvider.io)

    /**
     * Join/concatenate multiple videos without re-encoding
     * Note: All videos must have the same resolution and codec
     * @param uris List of video URIs to concatenate
     * @return Flow<Float> Progress from 0.0 to 1.0
     */
    @OptIn(FlowPreview::class)
    fun concat(uris: List<Uri>): Flow<Result<File>> = flow {
        val timer = TimeTracker.start("concat")
        if (uris.isEmpty()) {
            emit(Result.failure(IllegalArgumentException("No videos provided")))
            return@flow
        }

        Timber.d("Starting concat operation for ${uris.size} video(s)")

        val outputFile = videoRepository.createTempFile(".mp4")

        try {
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
        } catch (e: Exception) {
            Timber.e(e, "Error during concat operation")
            emit(Result.failure(e))
            if (outputFile.exists()) {
                outputFile.delete()
            }
        } finally {
            timer.end()
        }
    }.flowOn(dispatcherProvider.io)

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
        offsetUs: Long,
        onProgress: (Float) -> Unit
    ) {
        // Seek to the closest sync frame (keyframe) at or before start time
        extractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
        val actualStartUs = extractor.sampleTime

        Timber.d("processTrack: requested start=$startUs, actual seek position=$actualStartUs")

        val buffer = MediaCodec.BufferInfo()
        val byteBuffer = java.nio.ByteBuffer.allocate(1024 * 1024)

        var lastProgressUpdate = 0f
        var firstSampleWritten = false
        var samplesWritten = 0

        while (true) {
            val sampleSize = extractor.readSampleData(byteBuffer, 0)
            if (sampleSize < 0) break

            val sampleTime = extractor.sampleTime
            if (sampleTime == -1L) break

            // Check if we've reached the end of range
            if (sampleTime >= endUs) {
                break
            }

            // Write samples from sync frame onwards (needed for proper decoding)
            // but adjust presentation time to start from 0
            if (sampleTime >= actualStartUs) {
                buffer.offset = 0
                buffer.size = sampleSize

                // Adjust presentation time: subtract actual start time and add offset
                // This ensures that video starts at correct timestamp in sequence
                buffer.presentationTimeUs = (sampleTime - actualStartUs) + offsetUs

                // Preserve sync frame flag for first sample
                buffer.flags = extractor.sampleFlags
                if (!firstSampleWritten) {
                    // Ensure first frame is marked as sync frame
                    buffer.flags = buffer.flags or MediaCodec.BUFFER_FLAG_KEY_FRAME
                    firstSampleWritten = true
                }

                muxer.writeSampleData(outputTrackIndex, byteBuffer, buffer)
                samplesWritten++

                // Update progress
                val progress = ((sampleTime - actualStartUs).toFloat() / (endUs - actualStartUs).toFloat())
                if (progress - lastProgressUpdate > 0.01f) { // Update every1%
                    onProgress(progress.coerceIn(0f, 1f))
                    lastProgressUpdate = progress
                }
            }

            extractor.advance()
        }

        Timber.d("processTrack: Wrote $samplesWritten samples, range=[$startUs, $endUs), offset=$offsetUs")
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
