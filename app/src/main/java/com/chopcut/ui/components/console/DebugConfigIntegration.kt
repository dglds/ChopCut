package com.chopcut.ui.components.console

import android.content.Context
import com.chopcut.util.debug.DebugConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class DebugToggle(
    val key: String,
    val name: String,
    val description: String,
    val value: Boolean,
    val type: ToggleType
)

enum class ToggleType {
    FLAG,
    ENUM,
    NUMBER
}

class DebugConfigIntegration(
    private val context: Context
) {
    
    private val _toggles = MutableStateFlow<List<DebugToggle>>(emptyList())
    val toggles: StateFlow<List<DebugToggle>> = _toggles.asStateFlow()
    
    private val _isConfigVisible = MutableStateFlow(false)
    val isConfigVisible: StateFlow<Boolean> = _isConfigVisible.asStateFlow()
    
    init {
        loadToggles()
    }
    
    fun loadToggles() {
        val toggleList = listOf(
            DebugToggle(
                key = "DEBUG_MODE",
                name = "Debug Mode",
                description = "Habilita modo debug geral",
                value = DebugConfig.DEBUG_MODE,
                type = ToggleType.FLAG
            ),
            DebugToggle(
                key = "VERBOSE_THUMBNAIL_LOGS",
                name = "Verbose Thumbnail Logs",
                description = "Logs ultra-verbose do ThumbnailLoader",
                value = DebugConfig.VERBOSE_THUMBNAIL_LOGS,
                type = ToggleType.FLAG
            ),
            DebugToggle(
                key = "AUTO_LOG_STATS",
                name = "Auto Log Stats",
                description = "Estatísticas periódicas automáticas",
                value = DebugConfig.AUTO_LOG_STATS,
                type = ToggleType.FLAG
            ),
            DebugToggle(
                key = "ENABLE_SCREENSHOTS",
                name = "Enable Screenshots",
                description = "Captura automática de screenshots",
                value = DebugConfig.ENABLE_SCREENSHOTS,
                type = ToggleType.FLAG
            ),
            DebugToggle(
                key = "SHOW_DEBUG_OVERLAYS",
                name = "Show Debug Overlays",
                description = "Overlays visuais de debug na timeline",
                value = DebugConfig.SHOW_DEBUG_OVERLAYS,
                type = ToggleType.FLAG
            ),
            DebugToggle(
                key = "ENABLE_PERF_MONITORING",
                name = "Enable Perf Monitoring",
                description = "Monitoramento de performance",
                value = DebugConfig.ENABLE_PERF_MONITORING,
                type = ToggleType.FLAG
            ),
            DebugToggle(
                key = "ENABLE_VIDEO_RECORDING",
                name = "Enable Video Recording",
                description = "Gravação de vídeo durante testes",
                value = DebugConfig.ENABLE_VIDEO_RECORDING,
                type = ToggleType.FLAG
            )
        )
        
        _toggles.value = toggleList
    }
    
    fun toggleConfig(key: String) {
        val currentToggles = _toggles.value.toMutableList()
        val index = currentToggles.indexOfFirst { it.key == key }
        
        if (index >= 0) {
            val currentToggle = currentToggles[index]
            val newValue = !currentToggle.value
            
            currentToggles[index] = currentToggle.copy(value = newValue)
            _toggles.value = currentToggles
            
            applyConfigChange(key, newValue)
        }
    }
    
    private fun applyConfigChange(key: String, value: Boolean) {
        when (key) {
            "DEBUG_MODE" -> {
            }
            "VERBOSE_THUMBNAIL_LOGS" -> {
            }
            "AUTO_LOG_STATS" -> {
            }
            "ENABLE_SCREENSHOTS" -> {
            }
            "SHOW_DEBUG_OVERLAYS" -> {
            }
            "ENABLE_PERF_MONITORING" -> {
            }
            "ENABLE_VIDEO_RECORDING" -> {
            }
        }
    }
    
    fun showConfig() {
        _isConfigVisible.value = true
    }
    
    fun hideConfig() {
        _isConfigVisible.value = false
    }
    
    fun toggleConfigVisibility() {
        _isConfigVisible.value = !_isConfigVisible.value
    }
    
    fun getConfigValue(key: String): Boolean? {
        return _toggles.value.find { it.key == key }?.value
    }
    
    fun getConfigInfo(): ConfigInfo {
        val debugMode = getConfigValue("DEBUG_MODE") ?: false
        val screenshotsEnabled = getConfigValue("ENABLE_SCREENSHOTS") ?: false
        val videoEnabled = getConfigValue("ENABLE_VIDEO_RECORDING") ?: false
        val overlaysEnabled = getConfigValue("SHOW_DEBUG_OVERLAYS") ?: false
        val perfMonitoring = getConfigValue("ENABLE_PERF_MONITORING") ?: false
        
        return ConfigInfo(
            debugMode = debugMode,
            screenshotsDirectory = if (debugMode) DebugConfig.getScreenshotsDirectory().absolutePath else "Disabled",
            videosDirectory = if (debugMode && videoEnabled) DebugConfig.getVideosDirectory().absolutePath else "Disabled",
            logsDirectory = if (debugMode) DebugConfig.getLogsDirectory().absolutePath else "Disabled",
            screenshotsEnabled = screenshotsEnabled,
            videoEnabled = videoEnabled,
            overlaysEnabled = overlaysEnabled,
            perfMonitoring = perfMonitoring
        )
    }
    
    fun resetToDefaults() {
        loadToggles()
    }
}

data class ConfigInfo(
    val debugMode: Boolean,
    val screenshotsDirectory: String,
    val videosDirectory: String,
    val logsDirectory: String,
    val screenshotsEnabled: Boolean,
    val videoEnabled: Boolean,
    val overlaysEnabled: Boolean,
    val perfMonitoring: Boolean
) {
    fun formatForDisplay(): String {
        return buildString {
            append("Debug Mode: $debugMode\n")
            append("Screenshots: $screenshotsEnabled\n")
            append("Video: $videoEnabled\n")
            append("Overlays: $overlaysEnabled\n")
            append("Perf Monitoring: $perfMonitoring\n")
            append("\nDirectories:\n")
            append("Screenshots: $screenshotsDirectory\n")
            append("Videos: $videosDirectory\n")
            append("Logs: $logsDirectory")
        }
    }
}

class PerformanceAnalyzer(
    private val context: Context
) {
    
    fun analyzeOperationDuration(
        logs: List<com.chopcut.util.debug.LogEntry>,
        operationTag: String
    ): List<OperationMetrics> {
        val operations = mutableListOf<OperationMetrics>()
        val pendingOperations = mutableMapOf<Int, com.chopcut.util.debug.LogEntry>()
        
        logs.forEach { entry ->
            if (entry.tag != operationTag) return@forEach
            
            val isStart = entry.message.contains("iniciada", ignoreCase = true) ||
                         entry.message.contains("started", ignoreCase = true)
            val isEnd = entry.message.contains("concluída", ignoreCase = true) ||
                       entry.message.contains("completed", ignoreCase = true)
            
            if (isStart) {
                pendingOperations[entry.count] = entry
            } else if (isEnd && pendingOperations.containsKey(entry.count)) {
                val startEntry = pendingOperations.remove(entry.count)!!
                val duration = entry.timestamp - startEntry.timestamp
                
                operations.add(
                    OperationMetrics(
                        operationId = entry.count,
                        operationTag = operationTag,
                        startTime = startEntry.timestamp,
                        endTime = entry.timestamp,
                        duration = duration,
                        startEntry = startEntry,
                        endEntry = entry
                    )
                )
            }
        }
        
        return operations.sortedByDescending { it.duration }
    }
    
    fun getPerformanceSummary(operations: List<OperationMetrics>): PerformanceSummary {
        if (operations.isEmpty()) {
            return PerformanceSummary(
                totalCount = 0,
                avgDuration = 0L,
                minDuration = 0L,
                maxDuration = 0L,
                medianDuration = 0L,
                p95Duration = 0L,
                p99Duration = 0L,
                totalDuration = 0L,
                slowOperations = emptyList()
            )
        }
        
        val durations = operations.map { it.duration }.sorted()
        val avgDuration = durations.sum() / durations.size
        val minDuration = durations.first()
        val maxDuration = durations.last()
        val medianDuration = durations[durations.size / 2]
        val p95Duration = durations[(durations.size * 0.95).toInt().coerceAtMost(durations.size - 1)]
        val p99Duration = durations[(durations.size * 0.99).toInt().coerceAtMost(durations.size - 1)]
        val totalDuration = durations.sum()
        
        val slowThreshold = DebugConfig.PERF_SLOW_THRESHOLD_MS
        val slowOperations = operations.filter { it.duration > slowThreshold }
        
        return PerformanceSummary(
            totalCount = operations.size,
            avgDuration = avgDuration,
            minDuration = minDuration,
            maxDuration = maxDuration,
            medianDuration = medianDuration,
            p95Duration = p95Duration,
            p99Duration = p99Duration,
            totalDuration = totalDuration,
            slowOperations = slowOperations
        )
    }
}

data class OperationMetrics(
    val operationId: Int,
    val operationTag: String,
    val startTime: Long,
    val endTime: Long,
    val duration: Long,
    val startEntry: com.chopcut.util.debug.LogEntry,
    val endEntry: com.chopcut.util.debug.LogEntry
)

data class PerformanceSummary(
    val totalCount: Int,
    val avgDuration: Long,
    val minDuration: Long,
    val maxDuration: Long,
    val medianDuration: Long,
    val p95Duration: Long,
    val p99Duration: Long,
    val totalDuration: Long,
    val slowOperations: List<OperationMetrics>
) {
    fun formatDuration(ms: Long): String {
        val seconds = ms / 1000.0
        return when {
            ms < 1000 -> "${ms}ms"
            ms < 60000 -> String.format("%.2fs", seconds)
            else -> String.format("%.1fm", seconds / 60)
        }
    }
    
    fun formatForDisplay(): String {
        return buildString {
            append("Total Operations: $totalCount\n")
            append("Average: ${formatDuration(avgDuration)}\n")
            append("Min: ${formatDuration(minDuration)}\n")
            append("Max: ${formatDuration(maxDuration)}\n")
            append("Median: ${formatDuration(medianDuration)}\n")
            append("P95: ${formatDuration(p95Duration)}\n")
            append("P99: ${formatDuration(p99Duration)}\n")
            append("Total Time: ${formatDuration(totalDuration)}\n")
            append("Slow Operations (>3s): ${slowOperations.size}")
        }
    }
}