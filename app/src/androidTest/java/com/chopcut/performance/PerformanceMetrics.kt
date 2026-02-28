package com.chopcut.performance

import android.os.SystemClock
import timber.log.Timber

/**
 * Classe para coletar métricas de performance de operações
 */
data class PerformanceMetrics(
    val operationName: String,
    val durationMs: Long,
    val memoryUsedKb: Long,
    val success: Boolean,
    val metadata: Map<String, Any> = emptyMap()
) {
    val durationSeconds: Double
        get() = durationMs / 1000.0

    override fun toString(): String {
        return """
            ┌─────────────────────────────────────────
            │ Operation: $operationName
            │ Duration: ${durationMs}ms (${String.format("%.2f", durationSeconds)}s)
            │ Memory: ${memoryUsedKb}KB
            │ Success: $success
            │ Metadata: $metadata
            └─────────────────────────────────────────
        """.trimIndent()
    }
}

/**
 * Helper para medir performance de operações
 */
class PerformanceMeasurer {

    private val metrics = mutableListOf<PerformanceMetrics>()

    /**
     * Mede o tempo de execução e uso de memória de uma operação suspensa
     */
    suspend fun <T> measure(
        operationName: String,
        metadata: Map<String, Any> = emptyMap(),
        operation: suspend () -> T
    ): Pair<T?, PerformanceMetrics> {
        // Força garbage collection antes de medir
        System.gc()
        Thread.sleep(100)

        val runtime = Runtime.getRuntime()
        val memoryBefore = runtime.totalMemory() - runtime.freeMemory()

        val startTime = SystemClock.elapsedRealtime()
        var result: T? = null
        var success = false

        try {
            result = operation()
            success = true
        } catch (e: Exception) {
            Timber.e(e, "Error during performance measurement: $operationName")
        }

        val endTime = SystemClock.elapsedRealtime()
        val durationMs = endTime - startTime

        val memoryAfter = runtime.totalMemory() - runtime.freeMemory()
        val memoryUsedKb = (memoryAfter - memoryBefore) / 1024

        val metric = PerformanceMetrics(
            operationName = operationName,
            durationMs = durationMs,
            memoryUsedKb = memoryUsedKb,
            success = success,
            metadata = metadata
        )

        metrics.add(metric)
        Timber.d(metric.toString())

        return Pair(result, metric)
    }

    /**
     * Retorna todas as métricas coletadas
     */
    fun getAllMetrics(): List<PerformanceMetrics> = metrics.toList()

    /**
     * Limpa todas as métricas
     */
    fun clear() {
        metrics.clear()
    }

    /**
     * Retorna estatísticas agregadas
     */
    fun getStatistics(): PerformanceStatistics {
        val durations = metrics.map { it.durationMs }
        val memoryUsages = metrics.map { it.memoryUsedKb }

        return PerformanceStatistics(
            totalOperations = metrics.size,
            successfulOperations = metrics.count { it.success },
            failedOperations = metrics.count { !it.success },
            totalDurationMs = durations.sum(),
            averageDurationMs = if (durations.isNotEmpty()) durations.average() else 0.0,
            minDurationMs = durations.minOrNull() ?: 0,
            maxDurationMs = durations.maxOrNull() ?: 0,
            totalMemoryKb = memoryUsages.sum(),
            averageMemoryKb = if (memoryUsages.isNotEmpty()) memoryUsages.average() else 0.0
        )
    }
}

/**
 * Estatísticas agregadas de performance
 */
data class PerformanceStatistics(
    val totalOperations: Int,
    val successfulOperations: Int,
    val failedOperations: Int,
    val totalDurationMs: Long,
    val averageDurationMs: Double,
    val minDurationMs: Long,
    val maxDurationMs: Long,
    val totalMemoryKb: Long,
    val averageMemoryKb: Double
) {
    override fun toString(): String {
        return """
            ╔═════════════════════════════════════════════════════════════
            ║ PERFORMANCE STATISTICS
            ╠═════════════════════════════════════════════════════════════
            ║ Total Operations: $totalOperations
            ║ Successful: $successfulOperations
            ║ Failed: $failedOperations
            ║
            ║ Duration:
            ║   • Total: ${totalDurationMs}ms (${String.format("%.2f", totalDurationMs / 1000.0)}s)
            ║   • Average: ${String.format("%.2f", averageDurationMs)}ms
            ║   • Min: ${minDurationMs}ms
            ║   • Max: ${maxDurationMs}ms
            ║
            ║ Memory:
            ║   • Total: ${totalMemoryKb}KB (${String.format("%.2f", totalMemoryKb / 1024.0)}MB)
            ║   • Average: ${String.format("%.2f", averageMemoryKb)}KB
            ╚═════════════════════════════════════════════════════════════
        """.trimIndent()
    }
}
