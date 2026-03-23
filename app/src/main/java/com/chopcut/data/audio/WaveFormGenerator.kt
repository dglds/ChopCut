package com.chopcut.data.audio

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
    ): List<Float> {
        if (pcmSamples.isEmpty()) return emptyList()

        val maxBars = barCount.coerceIn(10, quality.calculateBarCount(Long.MAX_VALUE, 1000f))
        val samplesPerBar = maxOf(1, pcmSamples.size / maxBars)
        val downsampled = mutableListOf<Float>()

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

            if (samplesInChunk >= samplesPerBar) {
                if (chunkHasPico) {
                    downsampled.add(currentChunkMax)
                } else {
                    downsampled.add(silenceHeight ?: dynamicSilenceHeight)
                }
                currentChunkMax = 0f
                samplesInChunk = 0
                chunkHasPico = false
            }
        }

        if (samplesInChunk > 0) {
            if (chunkHasPico) {
                downsampled.add(currentChunkMax)
            } else {
                downsampled.add(silenceHeight ?: dynamicSilenceHeight)
            }
        }

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
    ): List<Float> {
        val barCount = quality.calculateBarCount(durationMs, screenWidthDp)
        return generateWaveform(pcmSamples, barCount, quality, threshold, silenceHeight)
    }
}
