package com.chopcut.data.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WaveFormGeneratorTest {

    @Test
    fun `generateWaveform should return empty list for empty input`() {
        val result = WaveFormGenerator.generateWaveform(floatArrayOf(), 100)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `generateWaveform should downsample correctly`() {
        // 100 samples, target 10 bars -> each bar represents 10 samples
        val samples = FloatArray(100) { i -> (i % 10).toFloat() / 10f }
        // The max in each chunk of 10 should be 0.9f
        
        val result = WaveFormGenerator.generateWaveform(samples, 10)
        
        assertEquals(10, result.size)
        result.forEach { amp ->
            assertEquals(0.9f, amp, 0.01f)
        }
    }

    @Test
    fun `generateWaveform should respect silence height threshold`() {
        // All samples below threshold (0.03 by default)
        val samples = FloatArray(100) { 0.01f }
        val silenceFloor = 0.05f
        
        val result = WaveFormGenerator.generateWaveform(samples, 10, silenceHeight = silenceFloor)
        
        assertEquals(10, result.size)
        result.forEach { amp ->
            assertEquals(silenceFloor, amp, 0.001f)
        }
    }

    @Test
    fun `generateWaveform should capture peaks regardless of position`() {
        // 100 samples, 10 bars -> chunk of 10
        val samples = FloatArray(100) { 0f }
        samples[45] = 0.8f // Middle of 5th bar (index 4)
        
        val result = WaveFormGenerator.generateWaveform(samples, 10)
        
        assertEquals(0.8f, result[4], 0.001f)
        // Others should be min (silence)
        assertTrue(result[0] < 0.1f)
    }
}
