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
                val metadata = videoRepository.getMetadata(uri)
                    ?: throw IllegalArgumentException("Unable to read video metadata")

                videoExtractor = MediaExtractor().apply { setDataSource(context, uri, null) }
                audioExtractor = if (metadata.hasAudio) {
                    MediaExtractor().apply { setDataSource(context, uri, null) }
                } else null

                val videoTrackIndex = findTrackIndex(videoExtractor, "video/")
                val audioTrackIndex = audioExtractor?.let { findTrackIndex(it, "audio/") } ?: -1

                if (videoTrackIndex < 0) {
                    throw IllegalArgumentException("No video track found")
                }

                val videoFormat = videoExtractor.getTrackFormat(videoTrackIndex)

                // Extract rotation directly from MediaFormat (more reliable)
                val rotationFromFormat = if (videoFormat.containsKey(MediaFormat.KEY_ROTATION)) {
                    videoFormat.getInteger(MediaFormat.KEY_ROTATION)
                } else {
                    // Fallback to metadata if not in format
                    metadata.rotation
                }

                Timber.d("Video rotation: format=${rotationFromFormat}°, metadata=${metadata.rotation}°")

                // Create a clean output format with essential fields
                val outputVideoFormat = MediaFormat()

                // Copy essential format fields
                outputVideoFormat.setString(MediaFormat.KEY_MIME, videoFormat.getString(MediaFormat.KEY_MIME))
                outputVideoFormat.setInteger(MediaFormat.KEY_WIDTH, videoFormat.getInteger(MediaFormat.KEY_WIDTH))
                outputVideoFormat.setInteger(MediaFormat.KEY_HEIGHT, videoFormat.getInteger(MediaFormat.KEY_HEIGHT))
                outputVideoFormat.setLong(MediaFormat.KEY_DURATION, videoFormat.getLong(MediaFormat.KEY_DURATION))
                if (videoFormat.containsKey(MediaFormat.KEY_BIT_RATE)) {
                    outputVideoFormat.setInteger(MediaFormat.KEY_BIT_RATE, videoFormat.getInteger(MediaFormat.KEY_BIT_RATE))
                }
                if (videoFormat.containsKey(MediaFormat.KEY_FRAME_RATE)) {
                    outputVideoFormat.setInteger(MediaFormat.KEY_FRAME_RATE, videoFormat.getInteger(MediaFormat.KEY_FRAME_RATE))
                }
                if (videoFormat.containsKey(MediaFormat.KEY_COLOR_FORMAT)) {
                    outputVideoFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, videoFormat.getInteger(MediaFormat.KEY_COLOR_FORMAT))
                }

                // CRITICAL: Set rotation BEFORE adding to muxer
                outputVideoFormat.setInteger(MediaFormat.KEY_ROTATION, rotationFromFormat)

                var audioFormat: MediaFormat? = null
                var audioTrackOutputIndex = -1

                if (audioExtractor != null && audioTrackIndex >= 0 && metadata.hasAudio) {
                    audioFormat = audioExtractor.getTrackFormat(audioTrackIndex)
                }

                muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
                val videoTrackOutputIndex = muxer.addTrack(outputVideoFormat)
                if (audioFormat != null) {
                    audioTrackOutputIndex = muxer.addTrack(audioFormat)
                }

                muxer.start()

                var processedDurationUs = 0L

                ranges.forEachIndexed { index, range ->
                    val offsetUs = processedDurationUs
                    val startUs = range.startMs * 1000
                    val endUs = range.endMs * 1000

                    Timber.d("Processing range ${index + 1}/${ranges.size}: [${range.startMs}ms, ${range.endMs}ms], offsetUs=${offsetUs}")

                    // Create fresh extractors for each range to avoid seek issues
                    val rangeVideoExtractor = MediaExtractor().apply { setDataSource(context, uri, null) }
                    val rangeVideoTrackIndex = findTrackIndex(rangeVideoExtractor, "video/")

                    // Process video track for this range
                    rangeVideoExtractor.selectTrack(rangeVideoTrackIndex)
                    rangeVideoExtractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
                    processTrack(rangeVideoExtractor, muxer, rangeVideoTrackIndex, videoTrackOutputIndex, startUs, endUs, offsetUs) { }
                    rangeVideoExtractor.release()

                    // Process audio track for this range (only if audio exists)
                    if (audioExtractor != null && audioTrackIndex >= 0 && audioTrackOutputIndex >= 0) {
                        val rangeAudioExtractor = MediaExtractor().apply { setDataSource(context, uri, null) }
                        val rangeAudioTrackIndex = findTrackIndex(rangeAudioExtractor, "audio/")

                        rangeAudioExtractor.selectTrack(rangeAudioTrackIndex)
                        rangeAudioExtractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
                        processTrack(rangeAudioExtractor, muxer, rangeAudioTrackIndex, audioTrackOutputIndex, startUs, endUs, offsetUs) { }
                        rangeAudioExtractor.release()
                    }

                    // Update offset for next range
                    processedDurationUs += (range.endMs - range.startMs) * 1000
                }

                // Stop muxer - ignore if already stopped
                try {
                    muxer.stop()
                } catch (e: Exception) {
                    Timber.w(e, "Muxer stop failed (may already be stopped)")
                }
                emit(Result.success(outputFile))

            } finally {
                videoExtractor?.release()
                audioExtractor?.release()
                try {
                    muxer?.release()
                } catch (e: Exception) {
                    Timber.w(e, "Muxer release failed (may already be released)")
                }
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

                // Use format from first video with rotation preserved
                val firstSource = sources.first()
                val outputVideoFormat = MediaFormat(firstSource.videoFormat)

                // Preserve rotation from first video
                if (firstMetadata.rotation != 0) {
                    outputVideoFormat.setInteger(MediaFormat.KEY_ROTATION, firstMetadata.rotation)
                    Timber.d("Preserving video rotation for concat: ${firstMetadata.rotation}°")
                }

                val videoTrackOutputIndex = muxer.addTrack(outputVideoFormat)
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
        extractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
        val actualStartUs = extractor.sampleTime

        Timber.d("processTrack: requestedStart=${startUs}, actualStart=${actualStartUs}, end=${endUs}, offset=${offsetUs}")

        val buffer = MediaCodec.BufferInfo()
        val byteBuffer = java.nio.ByteBuffer.allocate(1024 * 1024)

        var firstSampleWritten = false
        var sampleCount = 0

        while (true) {
            val sampleSize = extractor.readSampleData(byteBuffer, 0)
            if (sampleSize < 0) {
                Timber.d("processTrack: No more samples (sampleSize=$sampleSize)")
                break
            }

            val sampleTime = extractor.sampleTime
            if (sampleTime == -1L) {
                Timber.d("processTrack: Reached end (sampleTime=-1)")
                break
            }

            if (sampleTime >= endUs) {
                Timber.d("processTrack: Reached endUs, breaking. sampleTime=${sampleTime} >= endUs=${endUs}")
                break
            }

            if (sampleTime >= actualStartUs) {
                buffer.offset = 0
                buffer.size = sampleSize
                buffer.presentationTimeUs = (sampleTime - actualStartUs) + offsetUs
                buffer.flags = extractor.sampleFlags

                if (!firstSampleWritten) {
                    buffer.flags = buffer.flags or MediaCodec.BUFFER_FLAG_KEY_FRAME
                    firstSampleWritten = true
                    Timber.d("processTrack: First sample written at ${sampleTime}, flags=${buffer.flags}")
                }

                muxer.writeSampleData(outputTrackIndex, byteBuffer, buffer)
                sampleCount++
            }

            extractor.advance()
        }

        Timber.d("processTrack: Completed. Wrote $sampleCount samples")
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
