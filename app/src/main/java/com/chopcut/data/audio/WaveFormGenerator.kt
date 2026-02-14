package com.chopcut.data.audio

/**
 * Generates waveform visualization from raw PCM data
 * Separate from extraction - allows reusing same data with different params
 */
object WaveFormGenerator {

    private fun calculateDynamicSilenceHeight(samples: FloatArray, threshold: Float): Float {
        if (samples.isEmpty()) return 0.15f

        val samplesBelowThreshold = samples.filter { it <= threshold }

        if (samplesBelowThreshold.isEmpty()) {
            return 0.15f
        }

        val sorted = samplesBelowThreshold.sorted()
        val bottomTwentyPercentIndex = (sorted.size * 0.2f).toInt()
        val bottomSamples = sorted.take(bottomTwentyPercentIndex.coerceAtLeast(1))

        val averageSilence = bottomSamples.average().toFloat()

        return (averageSilence * 1.5f).coerceIn(0.1f, 0.25f)
    }

    fun generateWaveform(
        pcmSamples: FloatArray,
        barCount: Int,
        quality: WaveformQuality = WaveformQuality.Medium,
        threshold: Float = 0.05f,
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
        threshold: Float = 0.05f,
        silenceHeight: Float? = null
    ): List<Float> {
        val barCount = quality.calculateBarCount(durationMs, screenWidthDp)
        return generateWaveform(pcmSamples, barCount, quality, threshold, silenceHeight)
    }
}
