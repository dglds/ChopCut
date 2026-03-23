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
        private const val SILENCE_THRESHOLD = AudioConfig.Quality.SILENCE_THRESHOLD
        private const val VOICE_BOOST = AudioConfig.Quality.VOICE_BOOST_FACTOR
        private const val NOISE_SAMPLE_SIZE = AudioConfig.Extraction.NOISE_SAMPLE_SIZE
    }

    suspend fun extractRawPcmData(
        uri: Uri,
        targetBarCount: Int = -1
    ): AudioRawData = withContext(Dispatchers.IO) {
        android.os.Trace.beginSection("AudioDataExtractor.extractRawPcmData")
        val timer = TimeTracker.start("audio_pcm_extract")

        val extractor = MediaExtractor()
        try {
            android.os.Trace.beginSection("AudioDataExtractor.Setup")
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
                android.os.Trace.endSection() // Setup
                android.os.Trace.endSection() // extractRawPcmData
                return@withContext AudioRawData.empty()
            }

            val format = extractor.getTrackFormat(audioTrackIndex)
            val mime = format.getString(MediaFormat.KEY_MIME)!!
            val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE, AudioConfig.Extraction.DEFAULT_SAMPLE_RATE)
            val channelCount = if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            } else 1
            val durationUs = format.getLong(MediaFormat.KEY_DURATION)
            val expectedDurationMs = durationUs / 1000

            // DENSIDADE DINÂMICA: 40 barras por segundo de vídeo (Dobrado para mais precisão)
            val finalBarCount = if (targetBarCount > 0) {
                targetBarCount 
            } else {
                ((expectedDurationMs / 1000f) * 40).toInt().coerceIn(100, 8000)
            }

            // Calcular samplesPerBar baseado no finalBarCount
            val samplesPerBar = if (expectedDurationMs > 0 && finalBarCount > 0) {
                val totalFrames = (sampleRate.toLong() * channelCount * expectedDurationMs / 1000)
                (totalFrames.toFloat() / finalBarCount).toInt().coerceAtLeast(1)
            } else {
                AudioConfig.Extraction.DEFAULT_SAMPLES_PER_BAR
            }
            val estimatedPoints = finalBarCount


            // FASE 1: Coletar amostras para análise de ruído (primeiros segundos)
            val noiseSamples = mutableListOf<Float>()
            val bufferInfo = MediaCodec.BufferInfo()
            val timeoutUs = AudioConfig.Extraction.DECODER_TIMEOUT_US

            var outputDone = false
            var inputDone = false
            var tryAgainCount = 0
            val maxTryAgain = AudioConfig.Extraction.MAX_TRY_AGAIN
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
        
        android.os.Trace.endSection() // Setup
        android.os.Trace.beginSection("AudioDataExtractor.DecodeLoop")
        
        // Processamento em streaming - não carrega tudo na memória
        var frameCount = 0
        var currentSeekTimeUs = 0L
        
        // Pulo agressivo: se o áudio for longo, vamos pular blocos inteiros
        // Isso degrada a qualidade do gráfico, mas a velocidade vai pro teto.
        // Pulo moderado: evita ler 100% dos frames mas não pula eventos inteiros
        val seekStepUs = if (expectedDurationMs > 60000) {
            100000L // 100ms em vídeos longos (>1min)
        } else if (expectedDurationMs > 20000) {
            50000L  // 50ms em vídeos médios
        } else {
            0L // leitura contínua em vídeos curtos
        }

        while (!outputDone) {
            ensureActive() // Permitir cancelamento responsivo
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

                                if (seekStepUs > 0) {
                                    // Pulo agressivo: avança no tempo em vez de ler o frame seguinte adjacente
                                    currentSeekTimeUs += seekStepUs
                                    if (currentSeekTimeUs > durationUs) {
                                        needsEosSent = true
                                        inputDone = true
                                    } else {
                                        extractor.seekTo(currentSeekTimeUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
                                        // Se o seek cair no fim, o próximo readSampleData vai dar -1 no próximo loop
                                    }
                                } else {
                                    if (!extractor.advance()) {
                                        needsEosSent = true
                                        inputDone = true
                                    }
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

                                // Process samples em streaming (pulando amostras para piorar qualidade e focar em velocidade)
                                for (i in pcmDataShortArray.indices step 4) {
                                    val sample = pcmDataShortArray[i]
                                    val normalized = abs(sample.toInt()).toFloat() / 32768f

                                    // Coletar amostras para análise de ruído (apenas no início)
                                    if (!noiseCollected && noiseSamples.size < NOISE_SAMPLE_SIZE) {
                                        noiseSamples.add(normalized)
                                    }

                                    // Acumular para barras (máximo por grupo)
                                    if (normalized > currentMaxAmp) {
                                        currentMaxAmp = normalized
                                    }
                                    
                                    if (seekStepUs == 0L) {
                                        samplesAccumulated += 4
                                        totalSamplesProcessed += 4

                                        if (samplesAccumulated >= samplesPerBar) {
                                            pcmData.add(currentMaxAmp)
                                            currentMaxAmp = 0f
                                            samplesAccumulated = 0
                                        }
                                    }
                                }

                                if (seekStepUs > 0) {
                                    // Se estamos em modo Seek, consideramos o outputBuffer como uma "amostra"
                                    // Adicionamos a maior amplitude encontrada neste buffer como uma barra
                                    // E "avançamos" o total de amostras processadas para manter o timing
                                    pcmData.add(currentMaxAmp)
                                    currentMaxAmp = 0f
                                    totalSamplesProcessed += samplesPerBar // Simular avanço para o timing bater
                                }
                                
                                // Checkpoint a cada frame para responsividade (frequência aumentada para suavidade)
                                frameCount++
                                if (frameCount % 5 == 0) {
                                    ensureActive()
                                    kotlinx.coroutines.yield()
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
                                outputDone = true
                            }
                        }
                    }
                    outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val newFormat = decoder.outputFormat
                    }
                }
            }

            // Adicionar última barra parcial se houver
            if (samplesAccumulated > 0) {
                pcmData.add(currentMaxAmp)
            }

            decoder.stop()
            decoder.release()
            android.os.Trace.endSection() // DecodeLoop

            val finalDurationMs = (totalSamplesProcessed.toFloat() / (sampleRate * channelCount) * 1000).toLong()

            // FASE 2: Calcular threshold e aplicar processamento de voz
            android.os.Trace.beginSection("AudioDataExtractor.ThresholdPhase")
            if (noiseSamples.isNotEmpty()) {
                // Calcular noise floor (mediana dos 20% menores)
                val sortedNoise = noiseSamples.sorted()
                val noiseSampleSize = (sortedNoise.size * 0.2).toInt().coerceAtLeast(1)
                val noiseFloor = sortedNoise.take(noiseSampleSize).average().toFloat()
                val dynamicThreshold = (noiseFloor * AudioConfig.Quality.DYNAMIC_THRESHOLD_MULTIPLIER).coerceAtLeast(SILENCE_THRESHOLD)


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
            }
            android.os.Trace.endSection() // ThresholdPhase

            val extractedDurationMs = expectedDurationMs
            val effectiveSampleRate = if (extractedDurationMs > 0)
                (pcmData.size / (extractedDurationMs / 1000.0)).toInt()
            else targetBarCount

            timer.end()
            android.os.Trace.endSection() // extractRawPcmData

            val result = AudioRawData(
                pcmSamples = pcmData.toFloatArray(),
                sampleRate = effectiveSampleRate,
                durationMs = extractedDurationMs
            )

            result

        } catch (e: OutOfMemoryError) {
            android.os.Trace.endSection() // extractRawPcmData (fallback)
            // Retornar waveform vazio em caso de OOM
            AudioRawData.empty()
        } catch (e: Exception) {
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
            sampleRate = AudioConfig.Extraction.DEFAULT_SAMPLE_RATE,
            durationMs = 0
        )
    }
}
