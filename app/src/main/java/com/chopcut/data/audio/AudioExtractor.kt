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

        val outputFile = videoRepository.createTempFile(outputFormat.extension)

        try {
            // Get audio metadata first
            val metadata = videoRepository.getAudioMetadata(uri)
            if (metadata == null) {
                throw IllegalArgumentException("No audio track found in video")
            }

            
            var extractor: MediaExtractor? = null
            var muxer: MediaMuxer? = null

            try {
                android.os.Trace.beginSection("AudioExtractor.Setup")
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
                android.os.Trace.endSection()

                // Copy audio samples
                android.os.Trace.beginSection("AudioExtractor.CopyTrack")
                val copyTimer = TimeTracker.start("audio_extract_copy")
                val samplesCopied = copyTrack(
                    extractor = extractor,
                    muxer = muxer,
                    trackIndex = audioTrackIndex,
                    outputTrackIndex = outputTrackIndex
                )
                copyTimer.end()
                android.os.Trace.endSection()

                muxer.stop()

                
                emit(Result.success(outputFile))

            } finally {
                extractor?.release()
                muxer?.release()
            }
        } catch (e: Exception) {
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
        val byteBuffer = java.nio.ByteBuffer.allocateDirect(1024 * 1024)

        var sampleCount = 0
        var totalBytes = 0L

        while (true) {
            android.os.Trace.beginSection("AudioExtractor.ReadSample")
            val sampleSize = extractor.readSampleData(byteBuffer, 0)
            android.os.Trace.endSection()
            if (sampleSize < 0) break

            val sampleTime = extractor.sampleTime
            if (sampleTime == -1L) break

            buffer.offset = 0
            buffer.size = sampleSize
            buffer.presentationTimeUs = sampleTime
            buffer.flags = extractor.sampleFlags

            android.os.Trace.beginSection("AudioExtractor.WriteSample")
            muxer.writeSampleData(outputTrackIndex, byteBuffer, buffer)
            android.os.Trace.endSection()

            totalBytes += sampleSize
            sampleCount++

            android.os.Trace.beginSection("AudioExtractor.Advance")
            extractor.advance()
            android.os.Trace.endSection()
        }

        return sampleCount
    }
}
