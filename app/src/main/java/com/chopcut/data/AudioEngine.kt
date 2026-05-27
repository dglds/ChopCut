package com.chopcut

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.provider.OpenableColumns
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.security.MessageDigest
import kotlin.math.abs
import kotlin.math.sqrt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import timber.log.Timber


// --- Merged from WaveFormGenerator.kt ---

/**
 * Generates waveform visualization from raw PCM data
 * Separate from extraction - allows reusing same data with different params
 */
object WaveFormGenerator {

    private fun calculateDynamicSilenceHeight(samples: FloatArray, threshold: Float): Float {
        if (samples.isEmpty()) return 0.05f

        // Use a temporary primitive array to avoid boxing
        val silenceBuffer = FloatArray(samples.size)
        var silenceCount = 0
        
        for (sample in samples) {
            if (sample <= threshold) {
                silenceBuffer[silenceCount++] = sample
            }
        }

        if (silenceCount == 0) {
            return 0.05f
        }

        // Sort the primitive array (no boxing)
        java.util.Arrays.sort(silenceBuffer, 0, silenceCount)
        
        val bottomSampleSize = (silenceCount * 0.2f).toInt().coerceAtLeast(1)
        var sum = 0f
        for (i in 0 until bottomSampleSize) {
            sum += silenceBuffer[i]
        }

        val averageSilence = sum / bottomSampleSize

        return (averageSilence * 1.5f).coerceIn(0.05f, 0.20f)
    }

    fun generateWaveform(
        pcmSamples: FloatArray,
        barCount: Int,
        quality: WaveformQuality = WaveformQuality.Medium,
        threshold: Float = 0.03f,
        silenceHeight: Float? = null
    ): FloatArray {
        if (pcmSamples.isEmpty()) {
            timber.log.Timber.w("WaveFormGenerator: pcmSamples is empty, returning empty array")
            return floatArrayOf()
        }

        val maxBars = barCount.coerceIn(10, quality.calculateBarCount(Long.MAX_VALUE, 1000f))
        val samplesPerBar = maxOf(1, pcmSamples.size / maxBars)
        val downsampled = FloatArray(maxBars)
        var barsAdded = 0

        val dynamicSilenceHeight = calculateDynamicSilenceHeight(pcmSamples, threshold)

        var currentChunkMax = 0f
        var samplesInChunk = 0
        var chunkHasPico = false

        for (sample in pcmSamples) {
            if (sample > currentChunkMax) {
                currentChunkMax = sample
            }
            
            if (sample > threshold) {
                chunkHasPico = true
            }
            
            samplesInChunk++

            if (samplesInChunk >= samplesPerBar && barsAdded < maxBars) {
                downsampled[barsAdded++] = if (chunkHasPico) {
                    currentChunkMax
                } else {
                    silenceHeight ?: dynamicSilenceHeight
                }
                currentChunkMax = 0f
                samplesInChunk = 0
                chunkHasPico = false
            }
        }

        // Preencher o restante se sobrar algo
        if (samplesInChunk > 0 && barsAdded < maxBars) {
            downsampled[barsAdded++] = if (chunkHasPico) {
                currentChunkMax
            } else {
                silenceHeight ?: dynamicSilenceHeight
            }
        }

        timber.log.Timber.d("WaveFormGenerator: Generated $barsAdded bars from ${pcmSamples.size} samples")
        return downsampled
    }

    /**
     * Generate waveform with automatic bar count calculation
     * 
     * @param pcmSamples Normalized 0.0-1.0
     * @param durationMs Video duration in milliseconds
     * @param quality Quality setting
     * @param screenWidthDp Screen width in density-independent pixels
     * @param threshold Threshold value (bars below get min height)
     * @param silenceHeight Minimum height for bars below threshold
     */
    fun generateWaveform(
        pcmSamples: FloatArray,
        durationMs: Long,
        quality: WaveformQuality,
        screenWidthDp: Float = 400f,
        threshold: Float = 0.03f,
        silenceHeight: Float? = null
    ): FloatArray {
        val barCount = quality.calculateBarCount(durationMs, screenWidthDp)
        return generateWaveform(pcmSamples, barCount, quality, threshold, silenceHeight)
    }
}

// --- Merged from WaveformAnalyzer.kt ---


object WaveformAnalyzer {
    
    fun calculateThreshold(
        samples: FloatArray,
        samplingRate: Int,
        config: WaveformConfig
    ): Float {
        if (samples.isEmpty()) return config.minThreshold
        
        val effectiveSamples = if (samplingRate > 0) {
            samples.toList()
        } else {
            samples.toList()
        }
        
        val mean = effectiveSamples.average().toFloat()
        val variance = effectiveSamples.map { (it - mean).let { d -> d * d } }.average().toFloat()
        val stdDev = sqrt(variance.toDouble()).toFloat()
        
        val thresholdFromStats = mean + (config.sensitivityMultiplier * stdDev)
        val maxValue = effectiveSamples.maxOrNull() ?: 0f
        
        val thresholdFromMax = maxValue * config.maxThreshold
        
        val threshold = minOf(
            maxOf(thresholdFromStats, config.minThreshold),
            thresholdFromMax
        )
        
        val median = effectiveScoresMedian(effectiveSamples)
        val finalThreshold = if (config.useMedian && median > threshold) {
            median
        } else {
            threshold
        }
        
        return finalThreshold.coerceIn(config.minThreshold, config.maxThreshold)
    }
    
    private fun effectiveScoresMedian(scores: List<Float>): Float {
        if (scores.isEmpty()) return 0f
        
        val sorted = scores.sorted()
        val size = sorted.size
        
        return if (size % 2 == 0) {
            (sorted[size / 2 - 1] + sorted[size / 2]) / 2f
        } else {
            sorted[size / 2]
        }
    }
    
    fun applyThreshold(
        samples: FloatArray,
        threshold: Float,
        config: WaveformConfig
    ): Pair<FloatArray, Int> {
        val result = FloatArray(samples.size)
        var skippedCount = 0
        val silenceHeight = 0.15f  // Valor padrão
        
        for (i in samples.indices) {
            val absSample = abs(samples[i])
            
            if (absSample > threshold) {
                result[i] = absSample
            } else {
                result[i] = silenceHeight
                skippedCount++
            }
        }
        
        return Pair(result, skippedCount)
    }
    
    fun groupIntoBars(
        samples: FloatArray,
        targetBarCount: Int,
        threshold: Float,
        config: WaveformConfig
    ): Pair<FloatArray, Int> {
        if (samples.isEmpty()) return Pair(floatArrayOf(0f), 0)
        
        val effectiveBarCount = targetBarCount.coerceIn(10, 1000)
        val samplesPerBar = maxOf(1, samples.size / effectiveBarCount)
        
        val bars = mutableListOf<Float>()
        var barsWithPicos = 0
        val silenceHeight = 0.15f  // Valor padrão
        
        for (i in 0 until effectiveBarCount) {
            val startIndex = i * samplesPerBar
            val endIndex = minOf(startIndex + samplesPerBar, samples.size)
            
            var maxInChunk = 0f
            var hasPico = false
            
            for (j in startIndex until endIndex) {
                val sample = samples[j]
                if (sample > threshold) {
                    hasPico = true
                }
                if (sample > maxInChunk) {
                    maxInChunk = sample
                }
            }
            
            if (hasPico) {
                bars.add(maxInChunk)
                barsWithPicos++
            } else {
                bars.add(silenceHeight)
            }
        }
        
        return Pair(bars.toFloatArray(), barsWithPicos)
    }
    
    fun calculateMetrics(
        samples: FloatArray,
        threshold: Float,
        config: WaveformConfig
    ): WaveformMetrics {
        if (samples.isEmpty()) {
            return WaveformMetrics(
                mean = 0f,
                median = 0f,
                max = 0f,
                stdDev = 0f,
                threshold = threshold,
                samplesAboveThreshold = 0,
                samplesBelowThreshold = 0
            )
        }
        
        val mean = samples.average().toFloat()
        val variance = samples.map { (it - mean).let { d -> d * d } }.average().toFloat()
        val stdDev = sqrt(variance.toDouble()).toFloat()
        val max = samples.maxOrNull() ?: 0f
        
        var aboveThreshold = 0
        var belowThreshold = 0
        
        for (sample in samples) {
            if (abs(sample) > threshold) {
                aboveThreshold++
            } else {
                belowThreshold++
            }
        }
        
        val median = effectiveScoresMedian(samples.toList())
        
        return WaveformMetrics(
            mean = mean,
            median = median,
            max = max,
            stdDev = stdDev,
            threshold = threshold,
            samplesAboveThreshold = aboveThreshold,
            samplesBelowThreshold = belowThreshold
        )
    }
}

data class WaveformMetrics(
    val mean: Float,
    val median: Float,
    val max: Float,
    val stdDev: Float,
    val threshold: Float,
    val samplesAboveThreshold: Int,
    val samplesBelowThreshold: Int
)

// --- Merged from WaveformCache.kt ---


/**
 * Cache em disco para AudioRawData extraído.
 *
 * Chave: SHA-256(uri + size). Vídeos com mesmo URI mas tamanho diferente
 * (re-encode, edição) invalidam automaticamente.
 *
 * Formato binário versionado em cacheDir/waveforms/<hash>.bin.
 */
object WaveformCache {
    private const val CACHE_DIR = "waveforms"
    private const val FORMAT_VERSION = 1

    fun fileFor(context: Context, uri: Uri): File? {
        val key = computeKey(context, uri) ?: return null
        val dir = File(context.cacheDir, CACHE_DIR).apply { mkdirs() }
        return File(dir, "$key.bin")
    }

    fun read(file: File): AudioRawData? {
        if (!file.exists()) return null
        return try {
            DataInputStream(file.inputStream().buffered()).use { input ->
                val version = input.readInt()
                if (version != FORMAT_VERSION) return null
                val durationMs = input.readLong()
                val sampleRate = input.readInt()
                val count = input.readInt()
                if (count <= 0 || count > 10_000_000) return null
                val samples = FloatArray(count)
                for (i in 0 until count) samples[i] = input.readFloat()
                AudioRawData(samples, sampleRate, durationMs)
            }
        } catch (e: Exception) {
            file.delete()
            null
        }
    }

    fun write(file: File, data: AudioRawData) {
        try {
            DataOutputStream(file.outputStream().buffered()).use { output ->
                output.writeInt(FORMAT_VERSION)
                output.writeLong(data.durationMs)
                output.writeInt(data.sampleRate)
                output.writeInt(data.pcmSamples.size)
                for (sample in data.pcmSamples) output.writeFloat(sample)
            }
        } catch (e: Exception) {
            file.delete()
        }
    }

    private fun computeKey(context: Context, uri: Uri): String? {
        val size = querySize(context, uri) ?: return null
        val raw = "$uri|$size"
        val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray())
        val sb = StringBuilder(32)
        for (i in 0 until 16) {
            val b = digest[i].toInt() and 0xff
            sb.append(HEX[b ushr 4])
            sb.append(HEX[b and 0x0f])
        }
        return sb.toString()
    }

    private fun querySize(context: Context, uri: Uri): Long? {
        return try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val idx = c.getColumnIndex(OpenableColumns.SIZE)
                    if (idx >= 0 && !c.isNull(idx)) c.getLong(idx) else null
                } else null
            }
        } catch (e: Exception) {
            null
        }
    }

    private val HEX = charArrayOf('0','1','2','3','4','5','6','7','8','9','a','b','c','d','e','f')
}

// --- Merged from WaveformConfig.kt ---

data class WaveformConfig(
    val samplingRate: Int = 100,
    val minThreshold: Float = 0.05f,
    val maxThreshold: Float = 0.2f,
    val sensitivityMultiplier: Float = 1.0f,
    val useMedian: Boolean = true,
    val targetBarCount: Int = 400,
    val preset: WaveformPreset = WaveformPreset.Medium
) {
    companion object {
        val DEFAULT = WaveformConfig()
        
        fun fromPreset(preset: WaveformPreset) = when (preset) {
            WaveformPreset.Minimal -> WaveformConfig(
                samplingRate = 200,
                minThreshold = 0.03f,
                sensitivityMultiplier = 1.5f,
                targetBarCount = 100,
                preset = preset
            )
            WaveformPreset.Low -> WaveformConfig(
                samplingRate = 150,
                minThreshold = 0.04f,
                sensitivityMultiplier = 1.2f,
                targetBarCount = 200,
                preset = preset
            )
            WaveformPreset.Medium -> WaveformConfig(
                samplingRate = 100,
                minThreshold = 0.05f,
                sensitivityMultiplier = 1.0f,
                targetBarCount = 400,
                preset = preset
            )
            WaveformPreset.High -> WaveformConfig(
                samplingRate = 50,
                minThreshold = 0.05f,
                sensitivityMultiplier = 0.8f,
                targetBarCount = 600,
                preset = preset
            )
            WaveformPreset.Custom -> WaveformConfig(preset = preset)
        }
    }
    
    fun isCustom(): Boolean = preset == WaveformPreset.Custom
}

enum class WaveformPreset(val displayName: String) {
    Minimal("Mínima"),
    Low("Baixa"),
    Medium("Média"),
    High("Alta"),
    Custom("Personalizada")
}

// --- Merged from WaveformExtractor.kt ---


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

// --- Merged from WaveformQuality.kt ---

/**
 * Configuração de qualidade para geração de waveform
 * Determina o nível de detalhe e performance da extração
 */
sealed class WaveformQuality(val displayName: String, val barsPerSecond: Float) {

    /**
     * Qualidade mínima - para vídeos muito longos ou dispositivos lentos
     * ~2 bars/segundo, target sample rate ~3 Hz
     */
    data object Minimal : WaveformQuality("Mínima", 2f)

    /**
     * Qualidade baixa - para vídeos longos (5+ minutos)
     * ~5 bars/segundo, target sample rate ~5 Hz
     */
    data object Low : WaveformQuality("Baixa", 5f)

    /**
     * Qualidade média - para vídeos normais (1-5 minutos)
     * ~10 bars/segundo, target sample rate ~10 Hz
     */
    data object Medium : WaveformQuality("Média", 10f)

    /**
     * Qualidade alta - para vídeos curtos (<1 minuto)
     * ~15 bars/segundo, target sample rate ~15 Hz
     */
    data object High : WaveformQuality("Alta", 15f)

    /**
     * Calcula o target sample rate ideal para esta qualidade
     */
    fun calculateTargetSampleRate(): Int {
        return when (this) {
            Minimal -> 3
            Low -> 5
            Medium -> 10
            High -> 15
        }
    }

    /**
     * Calcula o número ideal de barras baseado na duração
     * Sem limites absolutos - apenas pela duração e largura da tela
     */
    fun calculateBarCount(durationMs: Long, screenWidthDp: Float = 400f): Int {
        val durationSeconds = durationMs / 1000f
        val totalBars = (durationSeconds * barsPerSecond).toInt()
        
        // Apenas limita baseado na largura da tela (~4dp por barra)
        val maxBarsByWidth = (screenWidthDp / 4f).toInt()
        
        return minOf(totalBars, maxBarsByWidth)
    }

    companion object {
        val Default = Medium
        val AllValues = listOf(Minimal, Low, Medium, High)
    }
}
