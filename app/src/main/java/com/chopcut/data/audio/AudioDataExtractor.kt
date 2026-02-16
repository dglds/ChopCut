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
import kotlin.math.sqrt

/**
 * Extracts raw PCM audio data optimized for voice detection
 * Returns normalized float samples (0.0 to 1.0) with voice emphasis
 */
class AudioDataExtractor(
    private val context: android.content.Context
) {

    companion object {
        // Threshold agressivo para silêncio (valores abaixo são considerados silêncio)
        private const val SILENCE_THRESHOLD = 0.03f

        // Fator de boost para voz (amplifica sinais de voz detectados)
        private const val VOICE_BOOST = 1.5f

        // Número de amostras para coletar para análise de ruído (limite seguro)
        private const val NOISE_SAMPLE_SIZE = 50000 // ~1 segundo de áudio
    }

    suspend fun extractRawPcmData(
        uri: Uri,
        targetBarCount: Int = 200
    ): AudioRawData = withContext(Dispatchers.IO) {
        Timber.d("AudioDataExtractor: START - uri=$uri, targetBarCount=$targetBarCount")
        val timer = TimeTracker.start("audio_pcm_extract")

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

            Timber.d("Audio info: sampleRate=$sampleRate, channelCount=$channelCount, duration=${expectedDurationMs}ms")

            // Calcular samplesPerBar baseado no targetBarCount
            val samplesPerBar = if (expectedDurationMs > 0 && targetBarCount > 0) {
                val totalFrames = (sampleRate * channelCount * expectedDurationMs / 1000).toLong()
                (totalFrames.toFloat() / targetBarCount).toInt().coerceAtLeast(1)
            } else {
                1000
            }
            val estimatedPoints = targetBarCount

            Timber.d("Calculated: samplesPerBar=$samplesPerBar, estimatedPoints=$estimatedPoints")

            // FASE 1: Coletar amostras para análise de ruído (primeiros segundos)
            val noiseSamples = mutableListOf<Float>()
            val bufferInfo = MediaCodec.BufferInfo()
            val timeoutUs = 100000L

            var outputDone = false
            var inputDone = false
            var tryAgainCount = 0
            val maxTryAgain = 200
            var needsEosSent = false

            var reusableShortArray: ShortArray? = null

            val decoder = MediaCodec.createDecoderByType(mime).apply {
                configure(format, null, null, 0)
                start()
            }

            var currentMaxAmp = 0f
            var samplesAccumulated = 0
            var noiseCollected = false
            var totalSamplesProcessed = 0L
            var lastProgressLog = 0

            val pcmData = java.util.ArrayList<Float>(estimatedPoints)

            // Processamento em streaming - não carrega tudo na memória
            while (!outputDone) {
                if (needsEosSent) {
                    val eosBufferIndex = decoder.dequeueInputBuffer(timeoutUs)
                    if (eosBufferIndex >= 0) {
                        decoder.queueInputBuffer(eosBufferIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        needsEosSent = false
                        continue
                    }
                }

                if (!inputDone) {
                    val inputBufferIndex = decoder.dequeueInputBuffer(timeoutUs)
                    if (inputBufferIndex >= 0) {
                        val inputBuffer = decoder.getInputBuffer(inputBufferIndex)
                        if (inputBuffer != null) {
                            val sampleSize = extractor.readSampleData(inputBuffer, 0)

                            if (sampleSize < 0) {
                                decoder.queueInputBuffer(inputBufferIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputDone = true
                            } else {
                                val presentationTimeUs = extractor.sampleTime
                                decoder.queueInputBuffer(inputBufferIndex, 0, sampleSize, presentationTimeUs, 0)

                                if (!extractor.advance()) {
                                    needsEosSent = true
                                    inputDone = true
                                }
                            }
                        }
                    }
                }

                val outputBufferIndex = decoder.dequeueOutputBuffer(bufferInfo, timeoutUs)
                when {
                    outputBufferIndex >= 0 -> {
                        tryAgainCount = 0
                        val outputBuffer = decoder.getOutputBuffer(outputBufferIndex)
                        if (outputBuffer != null) {
                            val size = bufferInfo.size

                            if (size > 0) {
                                val pcmDataShortArray = reusableShortArray?.let {
                                    if (it.size >= size / 2) it else ShortArray(size / 2)
                                } ?: ShortArray(size / 2)
                                reusableShortArray = pcmDataShortArray

                                outputBuffer.position(bufferInfo.offset)
                                outputBuffer.asShortBuffer().get(pcmDataShortArray)

                                // Process samples em streaming
                                for (sample in pcmDataShortArray) {
                                    val normalized = abs(sample.toInt()).toFloat() / 32768f

                                    // Coletar amostras para análise de ruído (apenas no início)
                                    if (!noiseCollected && noiseSamples.size < NOISE_SAMPLE_SIZE) {
                                        noiseSamples.add(normalized)
                                    }

                                    // Acumular para barras (máximo por grupo)
                                    if (normalized > currentMaxAmp) {
                                        currentMaxAmp = normalized
                                    }
                                    samplesAccumulated++
                                    totalSamplesProcessed++

                                    if (samplesAccumulated >= samplesPerBar) {
                                        pcmData.add(currentMaxAmp)
                                        currentMaxAmp = 0f
                                        samplesAccumulated = 0

                                        // Log progress a cada 50 barras
                                        if (pcmData.size % 50 == 0 && pcmData.size > lastProgressLog) {
                                            val progressMs = (totalSamplesProcessed.toFloat() / (sampleRate * channelCount) * 1000).toLong()
                                            Timber.d("Progress: ${pcmData.size} bars, ${progressMs}ms processed")
                                            lastProgressLog = pcmData.size
                                        }

                                        // Parar de coletar ruído depois de ter amostras suficientes
                                        if (!noiseCollected && noiseSamples.size >= NOISE_SAMPLE_SIZE) {
                                            noiseCollected = true
                                            Timber.d("Noise collection complete: ${noiseSamples.size} samples")
                                        }
                                    }
                                }
                            }
                        }

                        decoder.releaseOutputBuffer(outputBufferIndex, false)

                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            outputDone = true
                        }
                    }
                    outputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                        if (inputDone) {
                            tryAgainCount++
                            if (tryAgainCount > maxTryAgain) {
                                Timber.w("Safety timeout reached")
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

            // Adicionar última barra parcial se houver
            if (samplesAccumulated > 0) {
                pcmData.add(currentMaxAmp)
            }

            decoder.stop()
            decoder.release()

            val finalDurationMs = (totalSamplesProcessed.toFloat() / (sampleRate * channelCount) * 1000).toLong()
            Timber.d("Extraction complete: ${pcmData.size} bars, ${totalSamplesProcessed} samples processed, ${finalDurationMs}ms duration")

            // FASE 2: Calcular threshold e aplicar processamento de voz
            if (noiseSamples.isNotEmpty()) {
                // Calcular noise floor (mediana dos 20% menores)
                val sortedNoise = noiseSamples.sorted()
                val noiseSampleSize = (sortedNoise.size * 0.2).toInt().coerceAtLeast(1)
                val noiseFloor = sortedNoise.take(noiseSampleSize).average().toFloat()
                val dynamicThreshold = (noiseFloor * 4f).coerceAtLeast(SILENCE_THRESHOLD)

                Timber.d("Noise floor: $noiseFloor, Dynamic threshold: $dynamicThreshold")

                // Aplicar threshold e boost de voz
                for (i in pcmData.indices) {
                    val value = pcmData[i]

                    // Detecção de voz: valor está significativamente acima do threshold
                    val hasVoice = value > dynamicThreshold

                    pcmData[i] = if (hasVoice) {
                        // Aplicar boost para destacar voz
                        (value * VOICE_BOOST).coerceAtMost(1.0f)
                    } else {
                        // Silêncio - valor mínimo
                        SILENCE_THRESHOLD
                    }
                }

                val voiceSegments = pcmData.count { it > SILENCE_THRESHOLD * 2 }
                Timber.d("Voice segments: $voiceSegments/${pcmData.size}")
            }

            val extractedDurationMs = expectedDurationMs
            val effectiveSampleRate = if (extractedDurationMs > 0)
                (pcmData.size / (extractedDurationMs / 1000.0)).toInt()
            else targetBarCount

            Timber.d("Generated ${pcmData.size} bars. Duration: ${extractedDurationMs}ms")
            timer.end()

            val result = AudioRawData(
                pcmSamples = pcmData.toFloatArray(),
                sampleRate = effectiveSampleRate,
                durationMs = extractedDurationMs
            )

            result

        } catch (e: OutOfMemoryError) {
            Timber.e(e, "OutOfMemoryError during PCM extraction - video too large")
            // Retornar waveform vazio em caso de OOM
            AudioRawData.empty()
        } catch (e: Exception) {
            Timber.e(e, "Error extracting PCM data")
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
