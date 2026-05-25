package com.chopcut.data.audio

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import com.chopcut.config.constants.AudioConfig
import com.chopcut.util.TimeTracker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import timber.log.Timber
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Extracts raw PCM audio data optimized for waveform visualization.
 * Returns normalized float samples (0.0 to 1.0) with voice emphasis.
 * 
 * Designed for UI rendering - NOT for audio file export.
 * For audio export, use TransformerPipeline.extractAudio().
 */
class WaveformExtractor(
    private val context: android.content.Context
) {

    companion object {
        private const val SILENCE_THRESHOLD = AudioConfig.Quality.SILENCE_THRESHOLD
        private const val VOICE_BOOST = AudioConfig.Quality.VOICE_BOOST_FACTOR
        private const val NOISE_SAMPLE_SIZE = AudioConfig.Extraction.NOISE_SAMPLE_SIZE
    }

    suspend fun extractRawPcmData(
        uri: Uri,
        targetBarCount: Int = -1
    ): AudioRawData = withContext(Dispatchers.IO) {
        android.os.Trace.beginSection("WaveformExtractor.extractRawPcmData")
        val timer = TimeTracker.start("audio_pcm_extract")
        Timber.d("WaveformExtractor: extractRawPcmData START - uri=$uri, targetBarCount=$targetBarCount, Thread=${Thread.currentThread().name}")

        val extractor = MediaExtractor()
        try {
            android.os.Trace.beginSection("WaveformExtractor.Setup")
            extractor.setDataSource(context, uri, null)
            Timber.d("WaveformExtractor: DataSource set, trackCount=${extractor.trackCount}")

            // Find audio track
            var audioTrackIndex = -1
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME)
                Timber.d("WaveformExtractor: Track $i - mime=$mime")
                if (mime?.startsWith("audio/") == true) {
                    audioTrackIndex = i
                    extractor.selectTrack(i)
                    Timber.d("WaveformExtractor: Audio track found at index $i, mime=$mime")
                    break
                }
            }

            if (audioTrackIndex < 0) {
                Timber.w("WaveformExtractor: No audio track found in video - returning empty")
                android.os.Trace.endSection() // Setup
                android.os.Trace.endSection() // extractRawPcmData
                return@withContext AudioRawData.empty()
            }

            extractor.unselectTrack(audioTrackIndex)
            extractor.selectTrack(audioTrackIndex)
            extractor.seekTo(0, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            Timber.d("WaveformExtractor: Audio track selected and seeker to beginning")

            val format = extractor.getTrackFormat(audioTrackIndex)
            val mime = format.getString(MediaFormat.KEY_MIME)!!
            val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE, AudioConfig.Extraction.DEFAULT_SAMPLE_RATE)
            val channelCount = if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            } else 1
            val durationUs = format.getLong(MediaFormat.KEY_DURATION)
            val expectedDurationMs = durationUs / 1000

            Timber.d("WaveformExtractor: Audio format - mime=$mime, sampleRate=$sampleRate, channels=$channelCount, durationMs=$expectedDurationMs")

            // DENSIDADE: 30 barras/seg — suficiente pra ~3 amostras por pixel-bar a 60dp/seg
            // (renderer desenha 1 barra a cada 4.5dp ≈ 13 barras visíveis por segundo).
            // Acima disso é overhead puro: heap maior e samples descartados na renderização.
            // Cap 30000 = ~16min de cobertura plena; vídeos maiores degradam graciosamente.
            val finalBarCount = if (targetBarCount > 0) {
                targetBarCount
            } else {
                ((expectedDurationMs / 1000f) * 30).toInt().coerceIn(60, 30000)
            }

            // Bucket temporal: cada sample é atribuído à barra que cobre seu tempo absoluto.
            // Isso garante exatamente finalBarCount barras, com timing determinístico,
            // independente do tamanho de buffer que o decoder do device entrega.
            val usPerBar = if (finalBarCount > 0 && durationUs > 0) {
                durationUs.toDouble() / finalBarCount
            } else {
                33_333.0 // fallback ~30 barras/seg
            }
            val usPerFrame = if (sampleRate > 0) 1_000_000L / sampleRate else 23L
            val estimatedPoints = finalBarCount


            // FASE 1: Coletar amostras para análise de ruído (primeiros segundos)
            // FloatArray primitivo evita ~200KB de heap em wrappers Float boxed.
            val noiseSamples = FloatArray(NOISE_SAMPLE_SIZE)
            var noiseSamplesCount = 0
            val bufferInfo = MediaCodec.BufferInfo()
            val timeoutUs = AudioConfig.Extraction.DECODER_TIMEOUT_US

            var outputDone = false
            var inputDone = false
            var tryAgainCount = 0
            val maxTryAgain = AudioConfig.Extraction.MAX_TRY_AGAIN
            var needsEosSent = false
            var iterationsWithoutOutput = 0
            val maxIterationsWithoutOutput = 1000

            Timber.d("WaveformExtractor: Starting decode - durationUs=$durationUs, sampleRate=$sampleRate, channels=$channelCount")

            var reusableShortArray: ShortArray? = null

            val decoder = MediaCodec.createDecoderByType(mime).apply {
                configure(format, null, null, 0)
                start()
            }
            Timber.d("WaveformExtractor: Decoder created and started - mime=$mime")

            var noiseCollected = false

            // Proteção contra loop infinito
            val loopStartTime = System.currentTimeMillis()
            val maxLoopTimeMs = 120_000L // 2 minutos máximo para extração
            var iterationCount = 0
            val maxIterations = 100_000 // Limite de iterações

            android.os.Trace.endSection() // Setup
            android.os.Trace.beginSection("WaveformExtractor.DecodeLoop")

            // Estratégia: decode contínuo SEM seek + downsample pós-processo.
            // Mais rápido que seek-between-buckets para vídeos normais.
            var frameCount = 0

            // Coletar TODAS as amostras (decode linear, sem seek)
            // Tamanho inicial: 44100 amostras/seg × segundos + folga
            val totalSamplesEstimate = ((expectedDurationMs / 1000L) * sampleRate + sampleRate).toInt()
            var rawSamples = FloatArray(totalSamplesEstimate)
            var rawSamplesCount = 0

            Timber.d("WaveformExtractor: Starting decode loop - totalSamplesEstimate=$totalSamplesEstimate, finalBarCount=$finalBarCount, decoder=$decoder")

            try {
                while (!outputDone) {
                ensureActive() // Permitir cancelamento responsivo

                // Proteção contra loop infinito
                iterationCount++
                if (iterationCount > maxIterations) {
                    Timber.e("WaveformExtractor: Max iterations reached ($maxIterations), breaking loop")
                    break
                }

                val elapsedLoopTime = System.currentTimeMillis() - loopStartTime
                if (elapsedLoopTime > maxLoopTimeMs) {
                    Timber.e("WaveformExtractor: Max loop time reached (${elapsedLoopTime}ms), breaking loop")
                    break
                }

                // Log de progresso a cada 10.000 iterações para detectar loops
                if (iterationCount % 10000 == 0) {
                    Timber.w("WaveformExtractor: Loop iteration $iterationCount, inputDone=$inputDone, outputDone=$outputDone, tryAgainCount=$tryAgainCount")
                }
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
                                Timber.d("WaveformExtractor: No more samples from extractor, sending EOS")
                                decoder.queueInputBuffer(inputBufferIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputDone = true
                            } else {
                                val presentationTimeUs = extractor.sampleTime
                                decoder.queueInputBuffer(inputBufferIndex, 0, sampleSize, presentationTimeUs, 0)
                                // SEM seek - decode contínuo!

                                // Log do primeiro sample para verificar se está lendo
                                if (frameCount == 0 && tryAgainCount == 0) {
                                    Timber.d("WaveformExtractor: First sample queued - size=$sampleSize, time=$presentationTimeUs")
                                }
                            }
                        }
                    }
                }

                val outputBufferIndex = decoder.dequeueOutputBuffer(bufferInfo, timeoutUs)
                when {
                    outputBufferIndex >= 0 -> {
                        tryAgainCount = 0
                        iterationsWithoutOutput = 0
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
                                Timber.d("WaveformExtractor: Decoded buffer - size=$size, frameCount=$frameCount")

                                // Coletar TODAS as amostras para downsample posterior
                                // Converter Short (-32768 a 32767) para Float (0.0 a 1.0)
                                for (i in pcmDataShortArray.indices step channelCount) {
                                    if (rawSamplesCount >= rawSamples.size) {
                                        rawSamples = rawSamples.copyOf(rawSamples.size * 2)
                                    }
                                    val sample = pcmDataShortArray[i]
                                    val normalized = abs(sample.toInt()).toFloat() / 32768f
                                    rawSamples[rawSamplesCount++] = normalized
                                }

                                // Checkpoint a cada buffer para responsividade
                                frameCount++
                                if (frameCount % 100 == 0) {
                                    Timber.d("WaveformExtractor: Progress - frameCount=$frameCount, rawSamplesCount=$rawSamplesCount, size=$size")
                                    ensureActive()
                                    kotlinx.coroutines.yield()
                                }
                            } else {
                                Timber.d("WaveformExtractor: Empty output buffer received, flags=${bufferInfo.flags}")
                            }
                        }

                        decoder.releaseOutputBuffer(outputBufferIndex, false)

                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            Timber.d("WaveformExtractor: EOS received in output buffer")
                            outputDone = true
                        }
                    }
                    outputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                        tryAgainCount++
                        iterationsWithoutOutput++

                        if (tryAgainCount > maxTryAgain) {
                            Timber.w("WaveformExtractor: Max try again reached ($tryAgainCount), exiting decode loop. inputDone=$inputDone")
                            outputDone = true
                        } else if (iterationsWithoutOutput > maxIterationsWithoutOutput) {
                            Timber.e("WaveformExtractor: Too many iterations without output ($iterationsWithoutOutput), breaking loop. inputDone=$inputDone, frameCount=$frameCount")
                            break
                        } else if (tryAgainCount % 100 == 0) {
                            Timber.d("WaveformExtractor: TRY_AGAIN_LATER count=$tryAgainCount, inputDone=$inputDone, iterationsWithoutOutput=$iterationsWithoutOutput")
                        }
                    }
                    outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val newFormat = decoder.outputFormat
                        Timber.d("WaveformExtractor: Output format changed to $newFormat")
                    }
                }
            }
            } finally {
                // Garantir que o decoder seja sempre liberado
                try {
                    decoder.stop()
                    decoder.release()
                    Timber.d("WaveformExtractor: Decoder released successfully")
                } catch (e: Exception) {
                    Timber.e(e, "WaveformExtractor: Error releasing decoder")
                }
            }

            // Log do motivo de saída do loop
            val loopEndTime = System.currentTimeMillis()
            val totalLoopTime = loopEndTime - loopStartTime
            Timber.d("WaveformExtractor: Decode loop ended - iterationCount=$iterationCount, frameCount=$frameCount, rawSamplesCount=$rawSamplesCount, outputDone=$outputDone, inputDone=$inputDone, tryAgainCount=$tryAgainCount, totalTime=${totalLoopTime}ms")

            // FASE 2: DOWNSAMPLE - converter raw samples para finalBarCount barras
            // Pegar o pico máximo de cada bucket temporal
            android.os.Trace.beginSection("WaveformExtractor.Downsample")
            Timber.d("WaveformExtractor: Downsample started - rawSamplesCount=$rawSamplesCount, finalBarCount=$finalBarCount, rawSamples size=${rawSamples.size}")

            val pcmData = FloatArray(finalBarCount)
            var pcmDataCount = 0

            if (rawSamplesCount > 0 && finalBarCount > 0) {
                val samplesPerBar = rawSamplesCount.toFloat() / finalBarCount

                for (barIdx in 0 until finalBarCount) {
                    val startSample = (barIdx * samplesPerBar).toInt()
                    val endSample = ((barIdx + 1) * samplesPerBar).toInt().coerceAtMost(rawSamplesCount)

                    var maxAmp = 0f
                    for (s in startSample until endSample) {
                        val amp = rawSamples[s]
                        if (amp > maxAmp) maxAmp = amp
                    }

                    // Coletar amostras para análise de ruído (apenas nas primeiras barras)
                    if (!noiseCollected && noiseSamplesCount < NOISE_SAMPLE_SIZE && barIdx < 100) {
                        if (maxAmp > SILENCE_THRESHOLD / 2f) {
                            noiseSamples[noiseSamplesCount++] = maxAmp
                        }
                    }

                    pcmData[pcmDataCount++] = maxAmp
                }
            }

            // Preencher restantes se necessário
            while (pcmDataCount < finalBarCount) {
                pcmData[pcmDataCount++] = 0f
            }

            android.os.Trace.endSection() // Downsample
            Timber.d("WaveformExtractor: Downsample completed - pcmDataCount=$pcmDataCount, finalBarCount=$finalBarCount, rawSamplesCount=$rawSamplesCount")

            // Aviso se não coletou nenhum sample
            if (rawSamplesCount == 0) {
                Timber.w("WaveformExtractor: WARNING - No samples collected! frameCount=$frameCount, inputDone=$inputDone, outputDone=$outputDone")
            }

            android.os.Trace.endSection() // DecodeLoop

            // FASE 2: Calcular threshold e aplicar processamento de voz
            android.os.Trace.beginSection("WaveformExtractor.ThresholdPhase")
            if (noiseSamplesCount > 0) {
                // Sort in-place no array primitivo (Arrays.sort é dezenas de vezes mais
                // rápido que List<Float>.sorted() para amostras grandes — sem boxing).
                java.util.Arrays.sort(noiseSamples, 0, noiseSamplesCount)

                // Noise floor: média dos 20% menores
                val noiseSampleSize = (noiseSamplesCount * 0.2f).toInt().coerceAtLeast(1)
                var noiseSum = 0f
                for (i in 0 until noiseSampleSize) noiseSum += noiseSamples[i]
                val noiseFloor = noiseSum / noiseSampleSize
                val dynamicThreshold = (noiseFloor * AudioConfig.Quality.DYNAMIC_THRESHOLD_MULTIPLIER).coerceAtLeast(SILENCE_THRESHOLD)

                // Aplicar threshold e boost de voz
                for (i in 0 until pcmDataCount) {
                    val value = pcmData[i]
                    pcmData[i] = if (value > dynamicThreshold) {
                        (value * VOICE_BOOST).coerceAtMost(1.0f)
                    } else {
                        SILENCE_THRESHOLD
                    }
                }
            }
            Timber.d("WaveformExtractor: Threshold completed - noiseSamplesCount=$noiseSamplesCount")
            android.os.Trace.endSection() // ThresholdPhase

            val extractedDurationMs = expectedDurationMs
            val effectiveSampleRate = if (extractedDurationMs > 0)
                (pcmDataCount / (extractedDurationMs / 1000.0)).toInt()
            else targetBarCount

            val elapsedMs = timer.end()
            android.os.Trace.endSection() // extractRawPcmData

            val result = AudioRawData(
                pcmSamples = pcmData.copyOf(pcmDataCount),
                sampleRate = effectiveSampleRate,
                durationMs = extractedDurationMs
            )
            Timber.d("WaveformExtractor: Extraction COMPLETE - pcmSamples=${result.pcmSamples.size}, sampleRate=${result.sampleRate}, durationMs=${result.durationMs}, elapsedMs=${elapsedMs}ms")

            result

        } catch (e: OutOfMemoryError) {
            Timber.e(e, "WaveformExtractor: OutOfMemoryError during extraction")
            android.os.Trace.endSection() // extractRawPcmData (fallback)
            // Retornar waveform vazio em caso de OOM
            AudioRawData.empty()
        } catch (e: Exception) {
            Timber.e(e, "WaveformExtractor: Exception during extraction: ${e.message}")
            android.os.Trace.endSection() // extractRawPcmData (fallback)
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
 * Raw PCM audio data for waveform visualization
 */
data class AudioRawData(
    val pcmSamples: FloatArray,  // Normalized 0.0-1.0
    val sampleRate: Int,
    val durationMs: Long
) {
    companion object {
        fun empty() = AudioRawData(
            pcmSamples = floatArrayOf(),
            sampleRate = AudioConfig.Extraction.DEFAULT_SAMPLE_RATE,
            durationMs = 0
        )
    }
}
