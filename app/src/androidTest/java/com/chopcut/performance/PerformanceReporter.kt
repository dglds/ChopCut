package com.chopcut.performance

import android.content.Context
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Gerador de relatórios de performance em múltiplos formatos
 */
class PerformanceReporter(private val context: Context) {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US)

    /**
     * Gera relatório completo em múltiplos formatos
     */
    fun generateReport(
        testName: String,
        metrics: List<PerformanceMetrics>,
        statistics: PerformanceStatistics,
        videoInfo: String = ""
    ): PerformanceReport {
        val timestamp = dateFormat.format(Date())
        // Usa diretório de arquivos externos da app (não precisa permissão especial)
        val reportDir = File(context.getExternalFilesDir(null), "performance_reports")
        if (!reportDir.exists()) reportDir.mkdirs()

        // Gera relatórios em diferentes formatos
        val jsonFile = generateJsonReport(reportDir, testName, timestamp, metrics, statistics, videoInfo)
        val markdownFile = generateMarkdownReport(reportDir, testName, timestamp, metrics, statistics, videoInfo)
        val csvFile = generateCsvReport(reportDir, testName, timestamp, metrics)

        return PerformanceReport(
            testName = testName,
            timestamp = timestamp,
            jsonFile = jsonFile,
            markdownFile = markdownFile,
            csvFile = csvFile,
            metrics = metrics,
            statistics = statistics,
            videoInfo = videoInfo
        )
    }

    /**
     * Gera JSON como string (para imprimir no console)
     */
    fun generateJsonString(
        metrics: List<PerformanceMetrics>,
        statistics: PerformanceStatistics,
        videoInfo: String = ""
    ): String {
        val json = JSONObject().apply {
            if (videoInfo.isNotEmpty()) {
                put("videoInfo", videoInfo)
            }
            put("device", JSONObject().apply {
                put("manufacturer", Build.MANUFACTURER)
                put("model", Build.MODEL)
                put("androidVersion", Build.VERSION.RELEASE)
                put("sdkInt", Build.VERSION.SDK_INT)
            })
            put("statistics", JSONObject().apply {
                put("totalOperations", statistics.totalOperations)
                put("successfulOperations", statistics.successfulOperations)
                put("failedOperations", statistics.failedOperations)
                put("totalDurationMs", statistics.totalDurationMs)
                put("averageDurationMs", statistics.averageDurationMs)
                put("minDurationMs", statistics.minDurationMs)
                put("maxDurationMs", statistics.maxDurationMs)
                put("totalMemoryKb", statistics.totalMemoryKb)
                put("averageMemoryKb", statistics.averageMemoryKb)
            })
            put("metrics", JSONArray(metrics.map { metric ->
                JSONObject().apply {
                    put("operationName", metric.operationName)
                    put("durationMs", metric.durationMs)
                    put("memoryUsedKb", metric.memoryUsedKb)
                    put("success", metric.success)
                    put("metadata", JSONObject(metric.metadata))
                }
            }))
        }
        return json.toString(2)
    }

    /**
     * Gera relatório em formato JSON
     */
    private fun generateJsonReport(
        reportDir: File,
        testName: String,
        timestamp: String,
        metrics: List<PerformanceMetrics>,
        statistics: PerformanceStatistics,
        videoInfo: String = ""
    ): File {
        val file = File(reportDir, "${testName}_${timestamp}.json")

        val json = JSONObject().apply {
            put("testName", testName)
            put("timestamp", timestamp)
            if (videoInfo.isNotEmpty()) {
                put("videoInfo", videoInfo)
            }
            put("device", JSONObject().apply {
                put("manufacturer", Build.MANUFACTURER)
                put("model", Build.MODEL)
                put("androidVersion", Build.VERSION.RELEASE)
                put("sdkInt", Build.VERSION.SDK_INT)
            })
            put("statistics", JSONObject().apply {
                put("totalOperations", statistics.totalOperations)
                put("successfulOperations", statistics.successfulOperations)
                put("failedOperations", statistics.failedOperations)
                put("totalDurationMs", statistics.totalDurationMs)
                put("averageDurationMs", statistics.averageDurationMs)
                put("minDurationMs", statistics.minDurationMs)
                put("maxDurationMs", statistics.maxDurationMs)
                put("totalMemoryKb", statistics.totalMemoryKb)
                put("averageMemoryKb", statistics.averageMemoryKb)
            })
            put("metrics", JSONArray(metrics.map { metric ->
                JSONObject().apply {
                    put("operationName", metric.operationName)
                    put("durationMs", metric.durationMs)
                    put("memoryUsedKb", metric.memoryUsedKb)
                    put("success", metric.success)
                    put("metadata", JSONObject(metric.metadata))
                }
            }))
        }

        file.writeText(json.toString(2))
        Timber.d("JSON report saved: ${file.absolutePath}")
        return file
    }

    /**
     * Gera relatório em formato Markdown
     */
    private fun generateMarkdownReport(
        reportDir: File,
        testName: String,
        timestamp: String,
        metrics: List<PerformanceMetrics>,
        statistics: PerformanceStatistics,
        videoInfo: String = ""
    ): File {
        val file = File(reportDir, "${testName}_${timestamp}.md")

        val markdown = buildString {
            appendLine("# Performance Report: $testName")
            appendLine()
            appendLine("**Timestamp:** $timestamp")
            appendLine()
            if (videoInfo.isNotEmpty()) {
                appendLine("## Video Information")
                appendLine()
                appendLine(videoInfo)
                appendLine()
            }
            appendLine("## Device Information")
            appendLine()
            appendLine("| Property | Value |")
            appendLine("|----------|-------|")
            appendLine("| Manufacturer | ${Build.MANUFACTURER} |")
            appendLine("| Model | ${Build.MODEL} |")
            appendLine("| Android Version | ${Build.VERSION.RELEASE} |")
            appendLine("| SDK Level | ${Build.VERSION.SDK_INT} |")
            appendLine()
            appendLine("## Summary Statistics")
            appendLine()
            appendLine("| Metric | Value |")
            appendLine("|--------|-------|")
            appendLine("| Total Operations | ${statistics.totalOperations} |")
            appendLine("| Successful | ${statistics.successfulOperations} |")
            appendLine("| Failed | ${statistics.failedOperations} |")
            appendLine("| Total Duration | ${statistics.totalDurationMs}ms (${String.format("%.2f", statistics.totalDurationMs / 1000.0)}s) |")
            appendLine("| Average Duration | ${String.format("%.2f", statistics.averageDurationMs)}ms |")
            appendLine("| Min Duration | ${statistics.minDurationMs}ms |")
            appendLine("| Max Duration | ${statistics.maxDurationMs}ms |")
            appendLine("| Total Memory | ${statistics.totalMemoryKb}KB (${String.format("%.2f", statistics.totalMemoryKb / 1024.0)}MB) |")
            appendLine("| Average Memory | ${String.format("%.2f", statistics.averageMemoryKb)}KB |")
            appendLine()
            appendLine("## Detailed Metrics")
            appendLine()
            appendLine("| Operation | Duration (ms) | Memory (KB) | Success | Metadata |")
            appendLine("|-----------|---------------|-------------|---------|----------|")
            metrics.forEach { metric ->
                val metadataStr = metric.metadata.entries.joinToString(", ") { "${it.key}=${it.value}" }
                appendLine("| ${metric.operationName} | ${metric.durationMs} | ${metric.memoryUsedKb} | ${if (metric.success) "✅" else "❌"} | $metadataStr |")
            }
            appendLine()
            appendLine("## Performance Chart")
            appendLine()
            appendLine("```")
            metrics.forEachIndexed { index, metric ->
                val barLength = (metric.durationMs / 100).toInt().coerceAtMost(50)
                val bar = "█".repeat(barLength)
                appendLine("${index + 1}. ${metric.operationName}: $bar ${metric.durationMs}ms")
            }
            appendLine("```")
        }

        file.writeText(markdown)
        Timber.d("Markdown report saved: ${file.absolutePath}")
        return file
    }

    /**
     * Gera relatório em formato CSV
     */
    private fun generateCsvReport(
        reportDir: File,
        testName: String,
        timestamp: String,
        metrics: List<PerformanceMetrics>
    ): File {
        val file = File(reportDir, "${testName}_${timestamp}.csv")

        val csv = buildString {
            appendLine("Operation,Duration (ms),Memory (KB),Success,Metadata")
            metrics.forEach { metric ->
                val metadataStr = metric.metadata.entries.joinToString("; ") { "${it.key}=${it.value}" }
                appendLine("${metric.operationName},${metric.durationMs},${metric.memoryUsedKb},${metric.success},\"$metadataStr\"")
            }
        }

        file.writeText(csv)
        Timber.d("CSV report saved: ${file.absolutePath}")
        return file
    }
}

/**
 * Resultado da geração de relatório
 */
data class PerformanceReport(
    val testName: String,
    val timestamp: String,
    val jsonFile: File,
    val markdownFile: File,
    val csvFile: File,
    val metrics: List<PerformanceMetrics>,
    val statistics: PerformanceStatistics,
    val videoInfo: String = ""
) {
    override fun toString(): String {
        return """
            ╔═════════════════════════════════════════════════════════════
            ║ PERFORMANCE REPORT GENERATED
            ╠═════════════════════════════════════════════════════════════
            ║ Test: $testName
            ║ Timestamp: $timestamp
            ║
            ║ Reports Generated:
            ║   • JSON: ${jsonFile.name}
            ║   • Markdown: ${markdownFile.name}
            ║   • CSV: ${csvFile.name}
            ║
            ║ Location: ${jsonFile.parent}
            ╚═════════════════════════════════════════════════════════════
        """.trimIndent()
    }
}
