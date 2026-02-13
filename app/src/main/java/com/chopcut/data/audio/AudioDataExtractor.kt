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
    private val context: android.content.Context,
    private val waveformCache: WaveformCache? = null
) {

    suspend fun extractRawPcmData(uri: Uri): AudioRawData = withContext(Dispatchers.IO) {
        Timber.e("DEBUG: AudioDataExtractor extractRawPcmData START for $uri")
        val timer = TimeTracker.start("audio_pcm_extract")
        Timber.d("Starting PCM extraction for $uri")

        val cacheKey = uri.toString()
        waveformCache?.get(cacheKey)?.let { cached ->
            Timber.d("Returning cached waveform for $cacheKey")
            timer.end()
            return@withContext cached
        }

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
            val channelCount = if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            } else 1
            val durationUs = format.getLong(MediaFormat.KEY_DURATION)
            val expectedDurationMs = durationUs / 1000

            Timber.d("Audio format: mime=$mime, sampleRate=$sampleRate, channels=$channelCount, durationMs=$expectedDurationMs")

            // Create decoder
            val decoder = MediaCodec.createDecoderByType(mime)
            decoder.configure(format, null, null, 0)
            decoder.start()

            // Downsampling configuration optimized for timeline visualization
            // Dynamic target rate based on video duration to balance speed vs quality
            val targetSampleRate = when {
                expectedDurationMs < 30000 -> 25
                expectedDurationMs < 120000 -> 20
                else -> 15
            }
            // Accumulate RAW samples (including all channels) per point
            val samplesPerPoint = ((sampleRate * channelCount) / targetSampleRate).coerceAtLeast(1)

            val estimatedPoints = (expectedDurationMs / 1000 * targetSampleRate).toInt()
            Timber.d("Downsampling: Target ${targetSampleRate}Hz, compress factor $samplesPerPoint, est. points $estimatedPoints")

            // Use a much smaller list
            val pcmData = java.util.ArrayList<Float>(estimatedPoints + 1000)

            val bufferInfo = MediaCodec.BufferInfo()
            val timeoutUs = 100000L // 100ms

            // Read and decode
            var outputDone = false
            var inputDone = false
            var tryAgainCount = 0
            val maxTryAgain = 200

            var lastLoggedProgress = -1

            // Downsampling state
            var currentMaxAmp = 0f
            var samplesAccumulated = 0

            // Reusable buffer to reduce allocations
            var reusableShortArray: ShortArray? = null
            
            while (!outputDone) {
                if (!inputDone) {
                    val inputBufferIndex = decoder.dequeueInputBuffer(timeoutUs)
                    if (inputBufferIndex >= 0) {
                        val inputBuffer = decoder.getInputBuffer(inputBufferIndex)
                        val sampleSize = extractor.readSampleData(inputBuffer!!, 0)

                        if (sampleSize < 0) {
                            decoder.queueInputBuffer(inputBufferIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                            Timber.v("Input EOS reached at ${extractor.sampleTime / 1000}ms")
                        } else {
                            val presentationTimeUs = extractor.sampleTime
                            decoder.queueInputBuffer(inputBufferIndex, 0, sampleSize, presentationTimeUs, 0)
                            
                            val advanced = extractor.advance()
                            if (!advanced) {
                                Timber.w("Extractor could not advance at ${presentationTimeUs / 1000}ms")
                                decoder.queueInputBuffer(inputBufferIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputDone = true
                            }
                            
                            // Log progress every 10%
                            if (expectedDurationMs > 0) {
                                val progress = (presentationTimeUs / 1000 * 10 / expectedDurationMs).toInt()
                                if (progress > lastLoggedProgress) {
                                    Timber.v("Extraction progress: ${progress * 10}%")
                                    lastLoggedProgress = progress
                                }
                            }
                        }
                    } else {
                         // Input buffer full, wait for output to drain
                         // Timber.v("Input buffer full, waiting...")
                    }
                }

                val outputBufferIndex = decoder.dequeueOutputBuffer(bufferInfo, timeoutUs)
                when {
                    outputBufferIndex >= 0 -> {
                        tryAgainCount = 0
                        val outputBuffer = decoder.getOutputBuffer(outputBufferIndex)!!
                        val size = bufferInfo.size

                        if (size > 0) {
                            val pcmDataShortArray = reusableShortArray?.let {
                                if (it.size >= size / 2) it else ShortArray(size / 2)
                            } ?: ShortArray(size / 2)
                            reusableShortArray = pcmDataShortArray

                            outputBuffer.position(bufferInfo.offset)
                            outputBuffer.asShortBuffer().get(pcmDataShortArray)

                            // Process samples for downsampling immediately
                            for (sample in pcmDataShortArray) {
                                val normalized = kotlin.math.abs(sample.toInt()).toFloat() / 32768f

                                if (normalized > currentMaxAmp) {
                                    currentMaxAmp = normalized
                                }
                                samplesAccumulated++

                                if (samplesAccumulated >= samplesPerPoint) {
                                    pcmData.add(currentMaxAmp)
                                    currentMaxAmp = 0f
                                    samplesAccumulated = 0
                                }
                            }

                            // Force add remaining samples when output buffer contains the final data
                            if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                                if (samplesAccumulated > 0) {
                                    pcmData.add(currentMaxAmp)
                                    currentMaxAmp = 0f
                                    samplesAccumulated = 0
                                }
                            }
                        }

                        decoder.releaseOutputBuffer(outputBufferIndex, false)
                        
                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            outputDone = true
                            Timber.v("Output EOS reached")
                        }
                    }
                    outputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                        if (inputDone) {
                            tryAgainCount++
                            if (tryAgainCount > maxTryAgain) {
                                Timber.w("Safety timeout reached waiting for output EOS. Force stopping.")
                                outputDone = true
                            }
                        }
                    }
                    outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val newFormat = decoder.outputFormat
                        Timber.d("Decoder output format changed: $newFormat")
                    }
                }
            }

            // Fallback: add remaining accumulated sample if EOS was missed
            if (samplesAccumulated > 0) {
                pcmData.add(currentMaxAmp)
            }

            decoder.stop()
            decoder.release()

            // Calculate actual extracted duration
            // We produced N-1 full points (each with samplesPerPoint samples) + 1 final partial point
            // Total Raw Samples = (pcmData.size - 1) * samplesPerPoint + samplesAccumulated
            // Total Frames = Total Raw Samples / channelCount
            // Duration = Total Frames / sampleRate * 1000
            
            val fullPoints = (pcmData.size - 1).coerceAtLeast(0).toLong()
            val totalRawSamplesProcessed = fullPoints * samplesPerPoint + samplesAccumulated
            val extractedFrames = totalRawSamplesProcessed / channelCount
            val extractedDurationMs = (extractedFrames.toDouble() / sampleRate * 1000).toLong()

            Timber.d("TIME: audio_pcm_extract: ${pcmData.size} points generated. Duration: ${extractedDurationMs}ms (Expected: $expectedDurationMs)")
            Timber.d("TIME: audio_pcm_extract: fullPoints=$fullPoints, samplesPerPoint=$samplesPerPoint, samplesAccumulated=$samplesAccumulated, totalRawSamples=$totalRawSamplesProcessed")
            timer.end()
            
            // Effective Sample Rate of the OUTPUT data (points per second)
            // This is roughly targetSampleRate, but calculated precisely
            val effectiveSampleRate = if (extractedDurationMs > 0)
                (pcmData.size / (extractedDurationMs / 1000.0)).toInt()
            else targetSampleRate

            val result = AudioRawData(
                pcmSamples = pcmData.toFloatArray(),
                sampleRate = effectiveSampleRate,
                durationMs = if (extractedDurationMs > 0) extractedDurationMs else expectedDurationMs
            )

            waveformCache?.put(uri.toString(), result, targetSampleRate)

            result

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
