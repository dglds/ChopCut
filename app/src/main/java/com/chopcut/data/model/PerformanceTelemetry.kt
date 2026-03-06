package com.chopcut.data.model

/**
 * Estágios do pipeline de extração de thumbnails
 */
enum class ExtractionStage {
    DECODE,    // Extração do frame bruto do vídeo
    PROCESS,   // Crop, resize, filtros
    SAVE       // Compressão e escrita em disco/cache
}

/**
 * Evento de telemetria para uma única tarefa em um estágio
 */
data class PerformanceEvent(
    val timestamp: Long = System.currentTimeMillis(),
    val stage: ExtractionStage,
    val taskId: String,         // Identificador (ex: positionMs ou nome do arquivo)
    val durationMs: Long,
    val queueSize: Int = 0,
    val workerId: String = Thread.currentThread().name
)

/**
 * Métricas consolidadas de performance
 */
data class PerformanceMetrics(
    val throughput: Float,      // Tarefas por segundo (frames/s)
    val avgDurationMs: Map<ExtractionStage, Float>,
    val maxDurationMs: Map<ExtractionStage, Long>,
    val bottleneckStage: ExtractionStage? = null
)
