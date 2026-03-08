package com.chopcut.ui.components.console

import com.chopcut.util.debug.LogEntry
import com.chopcut.util.debug.LogLevel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.TimeUnit

data class LogMetrics(
    val totalLogs: Int = 0,
    val logsPerSecond: Float = 0f,
    val logsPerMinute: Float = 0f,
    val errorsPerMinute: Float = 0f,
    val logsByLevel: Map<LogLevel, Int> = emptyMap(),
    val logsByTag: Map<String, Int> = emptyMap(),
    val timeRange: TimeRange = TimeRange(0, 0),
    val averageLogsPerSecond: Float = 0f,
    val peakLogsPerSecond: Float = 0f,
    val errorRate: Float = 0f,
    val warningRate: Float = 0f
) {
    fun topTags(limit: Int = 10): List<TagCount> {
        return logsByTag.entries
            .sortedByDescending { it.value }
            .take(limit)
            .map { TagCount(it.key, it.value) }
    }
}

data class TagCount(
    val tag: String,
    val count: Int
)

data class TimeBucket(
    val startTime: Long,
    val endTime: Long,
    val totalLogs: Int,
    val errorCount: Int,
    val warningCount: Int
)

data class TimeSeriesData(
    val buckets: List<TimeBucket>,
    val intervalMs: Long
) {
    fun maxLogsPerBucket(): Int = buckets.maxOfOrNull { it.totalLogs } ?: 0
    
    fun averageLogsPerBucket(): Float = if (buckets.isNotEmpty()) {
        buckets.sumOf { it.totalLogs }.toFloat() / buckets.size
    } else 0f
}

class LogMetricsTracker {
    
    private val _metrics = MutableStateFlow(LogMetrics())
    val metrics: StateFlow<LogMetrics> = _metrics.asStateFlow()
    
    private val _timeSeries = MutableStateFlow(TimeSeriesData(emptyList(), 1000))
    val timeSeries: StateFlow<TimeSeriesData> = _timeSeries.asStateFlow()
    
    private val logHistory = mutableListOf<LogEntry>()
    private val maxHistorySize = 1000
    
    private var startTime: Long = 0L
    
    fun initialize() {
        startTime = System.currentTimeMillis()
    }
    
    fun addLog(entry: LogEntry) {
        logHistory.add(entry)
        
        if (logHistory.size > maxHistorySize) {
            logHistory.removeAt(0)
        }
        
        updateMetrics()
        updateTimeSeries()
    }
    
    fun clear() {
        logHistory.clear()
        startTime = System.currentTimeMillis()
        _metrics.value = LogMetrics()
        _timeSeries.value = TimeSeriesData(emptyList(), 1000)
    }
    
    fun calculateMetrics(logs: List<LogEntry>): LogMetrics {
        if (logs.isEmpty()) return LogMetrics()
        
        val now = System.currentTimeMillis()
        val oneMinuteAgo = now - TimeUnit.MINUTES.toMillis(1)
        val oneSecondAgo = now - TimeUnit.SECONDS.toMillis(1)
        
        val recentLogs = logs.filter { it.timestamp >= oneMinuteAgo }
        val lastSecondLogs = logs.filter { it.timestamp >= oneSecondAgo }
        
        val logsPerMinute = if (recentLogs.isNotEmpty()) {
            val duration = (now - recentLogs.first().timestamp) / 1000.0
            recentLogs.size / duration
        } else 0f
        
        val logsPerSecond = lastSecondLogs.size.toFloat()
        
        val errorLogs = logs.filter { it.level == LogLevel.ERROR }
        val errorLogsRecent = errorLogs.filter { it.timestamp >= oneMinuteAgo }
        val errorsPerMinute = if (errorLogsRecent.isNotEmpty()) {
            val duration = (now - errorLogsRecent.first().timestamp) / 1000.0
            (errorLogsRecent.size / duration).toFloat()
        } else 0f
        
        val logsByLevel = logs.groupBy { it.level }.mapValues { it.value.size }
        val logsByTag = logs.groupBy { it.tag }.mapValues { it.value.size }
        
        val timeRange = TimeRange(
            startTime = logs.first().timestamp,
            endTime = logs.last().timestamp
        )
        
        val durationSeconds = (timeRange.endTime - timeRange.startTime) / 1000.0
        val averageLogsPerSecond = if (durationSeconds > 0) {
            (logs.size / durationSeconds).toFloat()
        } else 0f
        
        val peakLogsPerSecond = calculatePeakLogsPerSecond(logs)
        
        val totalLogs = logs.size
        val errorRate = if (totalLogs > 0) {
            (logsByLevel[LogLevel.ERROR] ?: 0).toFloat() / totalLogs
        } else 0f
        
        val warningRate = if (totalLogs > 0) {
            (logsByLevel[LogLevel.WARN] ?: 0).toFloat() / totalLogs
        } else 0f
        
        return LogMetrics(
            totalLogs = totalLogs,
            logsPerSecond = logsPerSecond,
            logsPerMinute = logsPerMinute,
            errorsPerMinute = errorsPerMinute,
            logsByLevel = logsByLevel,
            logsByTag = logsByTag,
            timeRange = timeRange,
            averageLogsPerSecond = averageLogsPerSecond,
            peakLogsPerSecond = peakLogsPerSecond,
            errorRate = errorRate,
            warningRate = warningRate
        )
    }
    
    private fun calculatePeakLogsPerSecond(logs: List<LogEntry>): Float {
        if (logs.isEmpty()) return 0f
        
        val logsPerSecond = mutableMapOf<Long, Int>()
        
        logs.forEach { entry ->
            val second = entry.timestamp / 1000
            logsPerSecond[second] = (logsPerSecond[second] ?: 0) + 1
        }
        
        return logsPerSecond.values.maxOrNull()?.let { it.toFloat() } ?: 0f
    }
    
    private fun updateMetrics() {
        _metrics.value = calculateMetrics(logHistory)
    }
    
    private fun updateTimeSeries() {
        if (logHistory.isEmpty()) return
        
        val now = System.currentTimeMillis()
        val intervalMs = 1000L
        val bucketCount = 60
        
        val buckets = mutableListOf<TimeBucket>()
        
        for (i in bucketCount downTo 1) {
            val bucketStart = now - (i * intervalMs)
            val bucketEnd = bucketStart + intervalMs
            
            val bucketLogs = logHistory.filter { 
                it.timestamp >= bucketStart && it.timestamp < bucketEnd 
            }
            
            buckets.add(
                TimeBucket(
                    startTime = bucketStart,
                    endTime = bucketEnd,
                    totalLogs = bucketLogs.size,
                    errorCount = bucketLogs.count { it.level == LogLevel.ERROR },
                    warningCount = bucketLogs.count { it.level == LogLevel.WARN }
                )
            )
        }
        
        _timeSeries.value = TimeSeriesData(buckets, intervalMs)
    }
    
    fun getAnomalies(): List<Anomaly> {
        val anomalies = mutableListOf<Anomaly>()
        val currentMetrics = _metrics.value
        
        if (currentMetrics.logsPerSecond > currentMetrics.averageLogsPerSecond * 2) {
            anomalies.add(
                Anomaly(
                    type = AnomalyType.HIGH_LOG_RATE,
                    severity = AnomalySeverity.WARNING,
                    message = "High log rate detected: ${currentMetrics.logsPerSecond}/s (avg: ${currentMetrics.averageLogsPerSecond}/s)"
                )
            )
        }
        
        if (currentMetrics.errorRate > 0.1f) {
            anomalies.add(
                Anomaly(
                    type = AnomalyType.HIGH_ERROR_RATE,
                    severity = AnomalySeverity.ERROR,
                    message = "High error rate: ${(currentMetrics.errorRate * 100).toInt()}%"
                )
            )
        }
        
        val timeSeriesData = _timeSeries.value
        val recentBuckets = timeSeriesData.buckets.takeLast(10)
        if (recentBuckets.isNotEmpty()) {
            val avgRecent = recentBuckets.sumOf { it.totalLogs }.toFloat() / recentBuckets.size
            if (avgRecent > timeSeriesData.averageLogsPerBucket() * 1.5) {
                anomalies.add(
                    Anomaly(
                        type = AnomalyType.RECENT_SPIKE,
                        severity = AnomalySeverity.WARNING,
                        message = "Recent log spike detected"
                    )
                )
            }
        }
        
        return anomalies
    }
}

enum class AnomalyType {
    HIGH_LOG_RATE,
    HIGH_ERROR_RATE,
    RECENT_SPIKE,
    UNUSUAL_PATTERN
}

enum class AnomalySeverity {
    INFO,
    WARNING,
    ERROR,
    CRITICAL
}

data class Anomaly(
    val type: AnomalyType,
    val severity: AnomalySeverity,
    val message: String,
    val timestamp: Long = System.currentTimeMillis()
)