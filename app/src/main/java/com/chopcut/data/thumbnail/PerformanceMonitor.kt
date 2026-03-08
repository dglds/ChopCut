package com.chopcut.data.thumbnail

import com.chopcut.data.model.ExtractionStage
import com.chopcut.data.model.PerformanceEvent
import com.chopcut.data.model.PerformanceMetrics
import android.content.Context
import timber.log.Timber
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Monitor de performance global para o pipeline de processamento.
 * Coleta eventos de telemetria e calcula métricas em tempo real.
 */
object PerformanceMonitor {
    private val events = ConcurrentLinkedQueue<PerformanceEvent>()
    private var lastCalculationTime = System.currentTimeMillis()
    private var logger: FilePerformanceLogger? = null

    /**
     * Inicializa o monitor com contexto p/ persistência
     */
    fun init(context: Context) {
        logger = FilePerformanceLogger(context)
        Timber.i("PerformanceMonitor inicializado")
    }

    /**
     * Registra um novo evento de telemetria
     */
    fun log(event: PerformanceEvent) {
        events.add(event)
        
        // Persistência em arquivo
        logger?.logEvent(event)
        
        // Logcat estruturado para ferramentas de análise (Tput = Throughput)
        Timber.tag("TPUT_LOG").v("stage=${event.stage} task=${event.taskId} duration=${event.durationMs}ms queue=${event.queueSize} worker=${event.workerId}")
    }

    private var previousAverageQueueSize = 0f

    /**
     * Calcula métricas da janela atual (eventos desde o último cálculo)
     */
    fun calculateMetrics(): PerformanceMetrics {
        val now = System.currentTimeMillis()
        val durationMs = now - lastCalculationTime
        val durationSec = durationMs / 1000f
        
        val currentEvents = mutableListOf<PerformanceEvent>()
        while (events.isNotEmpty()) {
            events.poll()?.let { currentEvents.add(it) }
        }
        
        lastCalculationTime = now

        if (currentEvents.isEmpty()) {
            return PerformanceMetrics(0f, emptyMap(), emptyMap())
        }

        // Throughput: frames processados (contamos o estágio PROCESS como conclusão básica para o Batch)
        val completedTasks = currentEvents.count { it.stage == ExtractionStage.PROCESS }
        val throughput = if (durationSec > 0.001f) completedTasks / durationSec else completedTasks.toFloat()

        // Durações por estágio
        val avgDurations = currentEvents.groupBy { it.stage }
            .mapValues { (_, stageEvents) -> 
                stageEvents.map { it.durationMs }.average().toFloat() 
            }

        val maxDurations = currentEvents.groupBy { it.stage }
            .mapValues { (_, stageEvents) -> 
                stageEvents.maxOfOrNull { it.durationMs } ?: 0L
            }

        // Detecção de gargalo (estágio com maior duração média)
        val bottleneck = avgDurations.maxByOrNull { it.value }?.key

        // Diagnóstico de fila
        val avgQueueSize = currentEvents.map { it.queueSize.toFloat() }.average().toFloat()
        val isQueueSaturated = avgQueueSize > 5 && avgQueueSize >= previousAverageQueueSize
        previousAverageQueueSize = avgQueueSize

        val metrics = PerformanceMetrics(throughput, avgDurations, maxDurations, bottleneck)
        
        if (isQueueSaturated) {
            Timber.tag("PERF_ALERT").w("Pipeline saturado! Fila média: %.1f | Gargalo: %s", avgQueueSize, bottleneck)
        }

        Timber.tag("PERF_SUMMARY").i("Throughput: %.2f fps | Bottleneck: %s | Events: %d", 
            metrics.throughput, metrics.bottleneckStage, currentEvents.size)
        
        return metrics
    }

    /**
     * Limpa o histórico de eventos
     */
    @Synchronized
    fun clear() {
        events.clear()
        lastCalculationTime = System.currentTimeMillis()
    }

    /**
     * Retorna um resumo formatado para inserção no DuckDB ou registro histórico.
     * Útil para persistência centralizada.
     */
    fun getSummaryJson(): String {
        val metrics = calculateMetrics()
        val durations = metrics.avgDurationMs.map { "\"${it.key}\": ${it.value}" }.joinToString(",")
        
        return """
            {
                "timestamp": ${System.currentTimeMillis()},
                "throughput_fps": ${"%.2f".format(metrics.throughput)},
                "bottleneck": "${metrics.bottleneckStage ?: "NONE"}",
                "avg_durations": { $durations }
            }
        """.trimIndent()
    }
}
