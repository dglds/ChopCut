package com.chopcut.data.audio

/**
 * Generates waveform visualization from raw PCM data
 * Separate from extraction - allows reusing same data with different params
 */
object WaveFormGenerator {

    /**
     * Downsample raw PCM samples to waveform bars
     */
    fun generateWaveform(
        pcmSamples: FloatArray,  // Normalized 0.0-1.0
        barCount: Int
    ): List<Float> {
        if (pcmSamples.isEmpty()) return emptyList()

        val maxBars = barCount.coerceIn(10, 500)
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

        // Add remaining chunk
        if (samplesInChunk > 0) {
            downsampled.add(currentChunkMax)
        }

        return downsampled
    }
}
