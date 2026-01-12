package com.chopcut.data.audio

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import com.chopcut.util.TimeTracker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import kotlin.math.abs

/**
 * Extracts raw PCM audio data for later waveform generation
 * Returns normalized float samples (0.0 to 1.0)
 */
class AudioDataExtractor(
    private val context: android.content.Context
) {

    suspend fun extractRawPcmData(uri: Uri): AudioRawData = withContext(Dispatchers.IO) {
        val timer = TimeTracker.start("audio_pcm_extract")
        Timber.d("Starting PCM extraction for $uri")

        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, uri, null)

            // Find audio track
            var audioTrackIndex = -1
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME)
                if (mime?.startsWith("audio/") == true) {
                    audioTrackIndex = i
                    extractor.selectTrack(i)
                    break
                }
            }

            if (audioTrackIndex < 0) {
                Timber.w("No audio track found")
                return@withContext AudioRawData.empty()
            }

            val format = extractor.getTrackFormat(audioTrackIndex)
            val mime = format.getString(MediaFormat.KEY_MIME)!!
            val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE, 44100)
            val durationUs = format.getLong(MediaFormat.KEY_DURATION)
            val durationMs = durationUs / 1000

            Timber.d("Audio format: mime=$mime, sampleRate=$sampleRate, durationMs=$durationMs")

            // Create decoder
            val decoder = MediaCodec.createDecoderByType(mime)
            decoder.configure(format, null, null, 0)
            decoder.start()

            val pcmData = mutableListOf<Float>()
            val bufferInfo = MediaCodec.BufferInfo()
            val timeoutUs = 10000L

            // Read and decode
            var outputDone = false
            while (!outputDone) {
                val inputBufferIndex = decoder.dequeueInputBuffer(timeoutUs)
                if (inputBufferIndex >= 0) {
                    val inputBuffer = decoder.getInputBuffer(inputBufferIndex)
                    val sampleSize = extractor.readSampleData(inputBuffer!!, 0)

                    if (sampleSize < 0) {
                        decoder.queueInputBuffer(inputBufferIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                    } else {
                        decoder.queueInputBuffer(inputBufferIndex, 0, sampleSize, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }

                val outputBufferIndex = decoder.dequeueOutputBuffer(bufferInfo, timeoutUs)
                when {
                    outputBufferIndex >= 0 -> {
                        val outputBuffer = decoder.getOutputBuffer(outputBufferIndex)!!
                        val size = bufferInfo.size

                        if (size > 0) {
                            val pcmDataShortArray = ShortArray(size / 2)
                            outputBuffer.position(bufferInfo.offset)
                            outputBuffer.asShortBuffer().get(pcmDataShortArray)

                            // Convert to normalized float 0.0-1.0
                            for (sample in pcmDataShortArray) {
                                val normalized = kotlin.math.abs(sample.toInt()).toFloat() / 32768f
                                pcmData.add(normalized)
                            }
                        }

                        decoder.releaseOutputBuffer(outputBufferIndex, false)
                    }
                    bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0 -> {
                        outputDone = true
                    }
                }
            }

            decoder.stop()
            decoder.release()

            Timber.d("TIME: audio_pcm_extract: ${pcmData.size} samples extracted in ${durationMs}ms")
            timer.end()

            AudioRawData(
                pcmSamples = pcmData.toFloatArray(),
                sampleRate = sampleRate,
                durationMs = durationMs
            )

        } catch (e: Exception) {
            Timber.e(e, "Error extracting PCM data")
            e.printStackTrace()
            AudioRawData.empty()
        } finally {
            try {
                extractor.release()
            } catch (e: Exception) {
                // Ignore
            }
        }
    }
}

/**
 * Raw PCM audio data
 */
data class AudioRawData(
    val pcmSamples: FloatArray,  // Normalized 0.0-1.0
    val sampleRate: Int,
    val durationMs: Long
) {
    companion object {
        fun empty() = AudioRawData(
            pcmSamples = floatArrayOf(),
            sampleRate = 44100,
            durationMs = 0
        )
    }
}
