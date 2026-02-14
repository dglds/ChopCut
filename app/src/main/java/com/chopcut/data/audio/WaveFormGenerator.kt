package com.chopcut.data.audio

/**
 * Generates waveform visualization from raw PCM data
 * Separate from extraction - allows reusing same data with different params
 */
object WaveFormGenerator {

    /**
     * Downsample raw PCM samples to waveform bars
     * 
     * @param pcmSamples Normalized 0.0-1.0
     * @param barCount Number of waveform bars to generate
     * @param quality Quality setting for processing (optional)
     */
    fun generateWaveform(
        pcmSamples: FloatArray,
        barCount: Int,
        quality: WaveformQuality = WaveformQuality.Medium
    ): List<Float> {
        if (pcmSamples.isEmpty()) return emptyList()

        val maxBars = barCount.coerceIn(10, quality.calculateBarCount(Long.MAX_VALUE, 1000f))
        val samplesPerBar = maxOf(1, pcmSamples.size / maxBars)
        val downsampled = mutableListOf<Float>()

        var currentChunkMax = 0f
        var samplesInChunk = 0

        for (sample in pcmSamples) {
            if (sample > currentChunkMax) {
                currentChunkMax = sample
            }
            samplesInChunk++

            if (samplesInChunk >= samplesPerBar) {
                downsampled.add(currentChunkMax)
                currentChunkMax = 0f
                samplesInChunk = 0
            }
        }

        if (samplesInChunk > 0) {
            downsampled.add(currentChunkMax)
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
     */
    fun generateWaveform(
        pcmSamples: FloatArray,
        durationMs: Long,
        quality: WaveformQuality,
        screenWidthDp: Float = 400f
    ): List<Float> {
        val barCount = quality.calculateBarCount(durationMs, screenWidthDp)
        return generateWaveform(pcmSamples, barCount, quality)
    }
}
