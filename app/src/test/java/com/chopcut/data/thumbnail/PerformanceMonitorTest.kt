package com.chopcut.data.thumbnail

import com.chopcut.data.model.ExtractionStage
import com.chopcut.data.model.PerformanceEvent
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class PerformanceMonitorTest {

    @Before
    fun setup() {
        PerformanceMonitor.clear()
    }

    @Test
    fun `test metrics calculation with sample events`() {
        // Given
        val events = listOf(
            PerformanceEvent(stage = ExtractionStage.DECODE, taskId = "1", durationMs = 100),
            PerformanceEvent(stage = ExtractionStage.PROCESS, taskId = "1", durationMs = 20),
            PerformanceEvent(stage = ExtractionStage.DECODE, taskId = "2", durationMs = 150),
            PerformanceEvent(stage = ExtractionStage.PROCESS, taskId = "2", durationMs = 30)
        )
        
        // When
        events.forEach { PerformanceMonitor.log(it) }
        val metrics = PerformanceMonitor.calculateMetrics()

        // Then
        assertTrue("Throughput deve ser positivo", metrics.throughput >= 0f) 
        assertEquals(125f, metrics.avgDurationMs[ExtractionStage.DECODE]!!, 0.1f)
        assertEquals(25f, metrics.avgDurationMs[ExtractionStage.PROCESS]!!, 0.1f)
        assertEquals(ExtractionStage.DECODE, metrics.bottleneckStage)
    }

    @Test
    fun `test bottleneck detection when process is slower`() {
        // Given
        val events = listOf(
            PerformanceEvent(stage = ExtractionStage.DECODE, taskId = "1", durationMs = 50),
            PerformanceEvent(stage = ExtractionStage.PROCESS, taskId = "1", durationMs = 200)
        )
        
        // When
        events.forEach { PerformanceMonitor.log(it) }
        val metrics = PerformanceMonitor.calculateMetrics()

        // Then
        assertEquals(ExtractionStage.PROCESS, metrics.bottleneckStage)
    }
}
