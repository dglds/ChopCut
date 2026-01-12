package com.chopcut.data.audio

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import com.chopcut.util.TimeTracker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.nio.ByteBuffer

/**
 * Extracts audio waveform data by sampling at specific time points
 * Uses seek + small decode chunks instead of full decode - MUCH FASTER
 */
class AudioDataExtractor(
    private val context: android.content.Context
) {

    /**
     * Extract waveform by sampling at evenly spaced time points
     * @param targetBars Number of samples to take (default 50)
     */
    suspend fun extractRawPcmData(uri: Uri, targetBars: Int = 50): AudioRawData = withContext(Dispatchers.IO) {
        val timer = TimeTracker.start("audio_waveform_extract")
        Timber.d("Starting waveform extraction for $uri (target: $targetBars bars)")

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

            Timber.d("Audio: $sampleRate Hz, $durationMs ms")

            // Calculate time points to sample (evenly spaced)
            val samplePointsUs = mutableListOf<Long>()
            val stepUs = durationUs / targetBars
            for (i in 0 until targetBars) {
                samplePointsUs.add(i * stepUs)
            }

            // Pre-allocate result
            val amplitudes = FloatArray(targetBars)

            // Create decoder
            val decoder = MediaCodec.createDecoderByType(mime)
            decoder.configure(format, null, null, 0)
            decoder.start()

            val bufferInfo = MediaCodec.BufferInfo()
            val timeoutUs = 10000L

            // Sample at each time point - decode small chunk, take max, move on
            for ((index, timeUs) in samplePointsUs.withIndex()) {
                // Seek to position
                extractor.seekTo(timeUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
                extractor.unselectTrack(audioTrackIndex)
                extractor.selectTrack(audioTrackIndex)

                // Flush decoder to reset state
                decoder.flush()

                // Decode a small window around this point (max 100ms of audio)
                var maxAmplitude = 0f
                val windowEndUs = timeUs + 100_000  // 100ms window
                var windowDone = false
                var samplesInWindow = 0
                val maxSamplesPerWindow = (sampleRate * 0.1).toInt()  // Max 100ms worth

                while (!windowDone && samplesInWindow < maxSamplesPerWindow) {
                    val inputBufferIndex = decoder.dequeueInputBuffer(timeoutUs)
                    if (inputBufferIndex >= 0) {
                        val inputBuffer = decoder.getInputBuffer(inputBufferIndex)
                        val sampleSize = extractor.readSampleData(inputBuffer!!, 0)

                        if (sampleSize < 0) {
                            decoder.queueInputBuffer(inputBufferIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        } else {
                            val sampleTime = extractor.sampleTime
                            // Skip samples outside our window
                            if (sampleTime >= windowEndUs) {
                                windowDone = true
                                decoder.queueInputBuffer(inputBufferIndex, 0, 0, 0, 0)
                            } else {
                                decoder.queueInputBuffer(inputBufferIndex, 0, sampleSize, sampleTime, 0)
                                extractor.advance()
                            }
                        }
                    }

                    val outputBufferIndex = decoder.dequeueOutputBuffer(bufferInfo, timeoutUs)
                    when {
                        outputBufferIndex >= 0 -> {
                            val outputBuffer = decoder.getOutputBuffer(outputBufferIndex)!!
                            val size = bufferInfo.size

                            if (size > 0) {
                                // Get max amplitude from this buffer
                                val bufferMax = getMaxAmplitude(outputBuffer, bufferInfo.offset, size)
                                if (bufferMax > maxAmplitude) {
                                    maxAmplitude = bufferMax
                                }
                                samplesInWindow += size / 2
                            }

                            decoder.releaseOutputBuffer(outputBufferIndex, false)
                        }
                        bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0 -> {
                            windowDone = true
                        }
                    }
                }

                amplitudes[index] = maxAmplitude
            }

            decoder.stop()
            decoder.release()

            Timber.d("TIME: waveform_extract: ${amplitudes.size} samples in ${durationMs}ms")
            timer.end()

            AudioRawData(
                pcmSamples = amplitudes,
                sampleRate = sampleRate,
                durationMs = durationMs
            )

        } catch (e: Exception) {
            Timber.e(e, "Error extracting waveform")
            AudioRawData.empty()
        } finally {
            try {
                extractor.release()
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    /**
     * Get max amplitude from a PCM buffer - optimized
     */
    private fun getMaxAmplitude(buffer: ByteBuffer, offset: Int, size: Int): Float {
        buffer.position(offset)
        val shortBuffer = buffer.asShortBuffer()
        val sampleCount = size / 2

        var max = 0
        for (i in 0 until sampleCount) {
            val sample = shortBuffer.get(i).toInt()
            val abs = if (sample < 0) -sample else sample
            if (abs > max) max = abs
        }
        return max / 32768f
    }
}

/**
 * Raw PCM audio data
 */
data class AudioRawData(
    val pcmSamples: FloatArray,
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
