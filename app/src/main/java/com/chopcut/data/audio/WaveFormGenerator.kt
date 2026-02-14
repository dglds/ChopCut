package com.chopcut.data.audio

/**
 * Generates waveform visualization from raw PCM data
 * Separate from extraction - allows reusing same data with different params
 */
object WaveFormGenerator {

    /**
     * Downsample raw PCM samples to waveform bars
     * Applies threshold: bars below threshold get minimum height
     * 
     * @param pcmSamples Normalized 0.0-1.0
     * @param barCount Number of waveform bars to generate
     * @param quality Quality setting for processing (optional)
     * @param threshold Threshold value (bars below get min height)
     * @param silenceHeight Minimum height for bars below threshold
     */
    fun generateWaveform(
        pcmSamples: FloatArray,
        barCount: Int,
        quality: WaveformQuality = WaveformQuality.Medium,
        threshold: Float = 0.05f,
        silenceHeight: Float = 0.02f
    ): List<Float> {
        if (pcmSamples.isEmpty()) return emptyList()

        val maxBars = barCount.coerceIn(10, quality.calculateBarCount(Long.MAX_VALUE, 1000f))
        val samplesPerBar = maxOf(1, pcmSamples.size / maxBars)
        val downsampled = mutableListOf<Float>()

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
                    downsampled.add(silenceHeight)
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
                downsampled.add(silenceHeight)
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
        silenceHeight: Float = 0.02f
    ): List<Float> {
        val barCount = quality.calculateBarCount(durationMs, screenWidthDp)
        return generateWaveform(pcmSamples, barCount, quality, threshold, silenceHeight)
    }
}
