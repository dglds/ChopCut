package com.chopcut.data.audio

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import com.chopcut.data.audio.model.AudioFormat
import com.chopcut.data.audio.model.AudioInfo
import com.chopcut.data.repository.VideoRepository
import com.chopcut.util.DispatcherProvider
import com.chopcut.util.TimeTracker
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import timber.log.Timber
import java.io.File

/**
 * Extracts audio track from video files.
 * Uses direct copy (no re-encoding) for maximum speed.
 *
 * Output format: AAC (.m4a) - compatible with most players
 */
class AudioExtractor(
    private val context: Context,
    private val videoRepository: VideoRepository,
    private val dispatcherProvider: DispatcherProvider = DispatcherProvider
) {

    /**
     * Extract audio track from video
     * @param uri Source video URI
     * @param outputFormat Output audio format (default: AAC)
     * @return Flow<Result<File>> Result with extracted audio file
     */
    @OptIn(FlowPreview::class)
    fun extract(
        uri: Uri,
        outputFormat: AudioFormat = AudioFormat.AAC
    ): Flow<Result<File>> = flow {
        val timer = TimeTracker.start("audio_extract")
        Timber.d("Starting audio extraction for $uri")

        val outputFile = videoRepository.createTempFile(outputFormat.extension)

        try {
            // Get audio metadata first
            val metadata = videoRepository.getAudioMetadata(uri)
            if (metadata == null) {
                throw IllegalArgumentException("No audio track found in video")
            }

            Timber.d("Audio metadata: codec=${metadata.codec}, " +
                     "sampleRate=${metadata.sampleRate}, " +
                     "channels=${metadata.channelCount}, " +
                     "bitrate=${metadata.bitrateKbps}kbps, " +
                     "duration=${metadata.durationMs}ms")

            var extractor: MediaExtractor? = null
            var muxer: MediaMuxer? = null

            try {
                val setupTimer = TimeTracker.start("audio_extract_setup")

                extractor = MediaExtractor()
                extractor.setDataSource(context, uri, null)

                val audioTrackIndex = findTrackIndex(extractor, "audio/")
                if (audioTrackIndex < 0) {
                    throw IllegalArgumentException("No audio track found")
                }

                extractor.selectTrack(audioTrackIndex)
                val audioFormat = extractor.getTrackFormat(audioTrackIndex)

                muxer = MediaMuxer(
                    outputFile.absolutePath,
                    outputFormat.containerFormat
                )

                val outputTrackIndex = muxer.addTrack(audioFormat)
                muxer.start()

                setupTimer.end()
                Timber.d("Muxer setup completed, output track: $outputTrackIndex")

                // Copy audio samples
                val copyTimer = TimeTracker.start("audio_extract_copy")
                val samplesCopied = copyTrack(
                    extractor = extractor,
                    muxer = muxer,
                    trackIndex = audioTrackIndex,
                    outputTrackIndex = outputTrackIndex
                )
                copyTimer.end()

                muxer.stop()

                Timber.d("TIME: audio_extract completed: ${outputFile.absolutePath}")
                Timber.d("TIME: samples_copied: $samplesCopied")
                Timber.d("TIME: output_size: ${outputFile.length()} bytes")

                emit(Result.success(outputFile))

            } finally {
                extractor?.release()
                muxer?.release()
            }
        } catch (e: Exception) {
            Timber.e(e, "Error during audio extraction")
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
     * Copy entire audio track from extractor to muxer
     * @return Number of samples copied
     */
    private fun copyTrack(
        extractor: MediaExtractor,
        muxer: MediaMuxer,
        trackIndex: Int,
        outputTrackIndex: Int
    ): Int {
        extractor.unselectTrack(trackIndex)
        extractor.selectTrack(trackIndex)
        extractor.seekTo(0, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)

        val buffer = MediaCodec.BufferInfo()
        val byteBuffer = java.nio.ByteBuffer.allocate(1024 * 1024)

        var sampleCount = 0
        var totalBytes = 0L

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

            totalBytes += sampleSize
            sampleCount++

            extractor.advance()
        }

        Timber.d("TIME: copied $sampleCount samples, ${totalBytes / 1024}KB")
        return sampleCount
    }
}
