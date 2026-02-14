package com.chopcut.data.audio

import kotlin.math.abs
import kotlin.math.sqrt

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
