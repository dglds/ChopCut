package com.chopcut.data.thumbnail

import android.content.Context
import com.chopcut.data.model.PerformanceEvent
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets

/**
 * Persiste eventos de performance em um arquivo CSV para análise posterior.
 */
class FilePerformanceLogger(private val context: Context) {
    private val logFile: File by lazy {
        File(context.filesDir, "performance_telemetry.csv").apply {
            if (!exists()) {
                writeText("timestamp,stage,taskId,durationMs,queueSize,workerId\n", StandardCharsets.UTF_8)
            }
        }
    }

    /**
     * Adiciona um evento ao arquivo de log
     */
    fun logEvent(event: PerformanceEvent) {
        try {
            val line = "${event.timestamp},${event.stage},${event.taskId},${event.durationMs},${event.queueSize},${event.workerId}\n"
            FileOutputStream(logFile, true).use { out ->
                out.write(line.toByteArray(StandardCharsets.UTF_8))
            }
        } catch (e: Exception) {
        }
    }

    /**
     * Limpa o arquivo de log
     */
    fun clearLogs() {
        if (logFile.exists()) {
            logFile.delete()
        }
    }
}
