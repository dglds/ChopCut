package com.chopcut.ui.components.console

import com.chopcut.util.debug.LogEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.text.SimpleDateFormat
import java.util.*

data class TimelineFilter(
    val startTime: Long = 0L,
    val endTime: Long = Long.MAX_VALUE,
    val tags: Set<String> = emptySet(),
    val levels: Set<com.chopcut.util.debug.LogLevel> = emptySet(),
    val searchQuery: String = ""
) {
    fun matches(entry: LogEntry): Boolean {
        if (entry.timestamp < startTime || entry.timestamp > endTime) return false
        if (tags.isNotEmpty() && entry.tag !in tags) return false
        if (levels.isNotEmpty() && entry.level !in levels) return false
        if (searchQuery.isNotEmpty()) {
            val query = searchQuery.lowercase()
            if (!entry.tag.lowercase().contains(query) && !entry.message.lowercase().contains(query)) {
                return false
            }
        }
        return true
    }
}

data class TimelinePinnedLog(
    val entry: LogEntry,
    val note: String = "",
    val pinnedAt: Long = System.currentTimeMillis()
)

data class InvestigationMode(
    val isEnabled: Boolean = false,
    val filter: TimelineFilter = TimelineFilter(),
    val pinnedLogs: List<TimelinePinnedLog> = emptyList(),
    val selectedRange: TimeRange? = null
)

class InvestigationManager {
    
    private val _mode = MutableStateFlow(InvestigationMode())
    val mode: StateFlow<InvestigationMode> = _mode.asStateFlow()
    
    private val dateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
    
    fun toggleInvestigationMode() {
        _mode.update { it.copy(isEnabled = !it.isEnabled) }
    }
    
    fun setFilter(filter: TimelineFilter) {
        _mode.update { it.copy(filter = filter) }
    }
    
    fun setTimeRange(startTime: Long, endTime: Long) {
        val currentFilter = _mode.value.filter
        _mode.update { 
            it.copy(
                filter = currentFilter.copy(
                    startTime = startTime,
                    endTime = endTime
                )
            )
        }
    }
    
    fun setTagsFilter(tags: Set<String>) {
        val currentFilter = _mode.value.filter
        _mode.update { 
            it.copy(filter = currentFilter.copy(tags = tags))
        }
    }
    
    fun setLevelsFilter(levels: Set<com.chopcut.util.debug.LogLevel>) {
        val currentFilter = _mode.value.filter
        _mode.update { 
            it.copy(filter = currentFilter.copy(levels = levels))
        }
    }
    
    fun setSearchQuery(query: String) {
        val currentFilter = _mode.value.filter
        _mode.update { 
            it.copy(filter = currentFilter.copy(searchQuery = query))
        }
    }
    
    fun pinLog(entry: LogEntry, note: String = "") {
        val currentPinned = _mode.value.pinnedLogs.toMutableList()
        val pinnedLog = TimelinePinnedLog(entry, note)
        
        if (currentPinned.any { it.entry.timestamp == entry.timestamp && it.entry.tag == entry.tag }) {
            currentPinned.removeAll { it.entry.timestamp == entry.timestamp && it.entry.tag == entry.tag }
        } else {
            currentPinned.add(pinnedLog)
        }
        
        _mode.update { it.copy(pinnedLogs = currentPinned) }
    }
    
    fun unpinLog(entry: LogEntry) {
        val currentPinned = _mode.value.pinnedLogs.toMutableList()
        currentPinned.removeAll { it.entry.timestamp == entry.timestamp && it.entry.tag == entry.tag }
        _mode.update { it.copy(pinnedLogs = currentPinned) }
    }
    
    fun addNoteToPinnedLog(entry: LogEntry, note: String) {
        val currentPinned = _mode.value.pinnedLogs.toMutableList()
        val index = currentPinned.indexOfFirst { 
            it.entry.timestamp == entry.timestamp && it.entry.tag == entry.tag 
        }
        
        if (index >= 0) {
            currentPinned[index] = currentPinned[index].copy(note = note)
            _mode.update { it.copy(pinnedLogs = currentPinned) }
        }
    }
    
    fun clearPinnedLogs() {
        _mode.update { it.copy(pinnedLogs = emptyList()) }
    }
    
    fun clearFilters() {
        _mode.update { 
            it.copy(
                filter = TimelineFilter(),
                selectedRange = null
            )
        }
    }
    
    fun selectTimeRange(startTime: Long, endTime: Long) {
        _mode.update { 
            it.copy(
                selectedRange = TimeRange(startTime, endTime),
                filter = it.filter.copy(
                    startTime = startTime,
                    endTime = endTime
                )
            )
        }
    }
    
    fun clearTimeRange() {
        _mode.update { 
            it.copy(
                selectedRange = null,
                filter = it.filter.copy(
                    startTime = 0L,
                    endTime = Long.MAX_VALUE
                )
            )
        }
    }
    
    fun applyPreset(preset: TimePreset) {
        val now = System.currentTimeMillis()
        val (startTime, endTime) = when (preset) {
            TimePreset.LAST_5_MINUTES -> (now - 5 * 60 * 1000) to now
            TimePreset.LAST_15_MINUTES -> (now - 15 * 60 * 1000) to now
            TimePreset.LAST_30_MINUTES -> (now - 30 * 60 * 1000) to now
            TimePreset.LAST_HOUR -> (now - 60 * 60 * 1000) to now
            TimePreset.LAST_2_HOURS -> (now - 2 * 60 * 60 * 1000) to now
            TimePreset.LAST_24_HOURS -> (now - 24 * 60 * 60 * 1000) to now
        }
        
        setTimeRange(startTime, endTime)
    }
    
    fun filterLogs(logs: List<LogEntry>): List<LogEntry> {
        if (!_mode.value.isEnabled) return logs
        
        return logs.filter { _mode.value.filter.matches(it) }
    }
    
    fun getFilteredPinnedLogs(): List<TimelinePinnedLog> {
        if (!_mode.value.isEnabled) return _mode.value.pinnedLogs
        
        return _mode.value.pinnedLogs.filter { _mode.value.filter.matches(it.entry) }
    }
    
    fun getFilteredLogsCount(logs: List<LogEntry>): Int {
        if (!_mode.value.isEnabled) return logs.size
        
        return logs.count { _mode.value.filter.matches(it) }
    }
    
    fun formatTimestamp(timestamp: Long): String {
        return dateFormat.format(Date(timestamp))
    }
    
    fun getActiveFiltersDescription(): String {
        val filter = _mode.value.filter
        val parts = mutableListOf<String>()
        
        if (filter.startTime > 0 || filter.endTime < Long.MAX_VALUE) {
            parts.add("Time Range")
        }
        
        if (filter.tags.isNotEmpty()) {
            parts.add("Tags: ${filter.tags.size}")
        }
        
        if (filter.levels.isNotEmpty()) {
            parts.add("Levels: ${filter.levels.size}")
        }
        
        if (filter.searchQuery.isNotEmpty()) {
            parts.add("Search: \"${filter.searchQuery}\"")
        }
        
        return if (parts.isNotEmpty()) {
            parts.joinToString(" | ")
        } else {
            "No filters"
        }
    }
}

enum class TimePreset {
    LAST_5_MINUTES,
    LAST_15_MINUTES,
    LAST_30_MINUTES,
    LAST_HOUR,
    LAST_2_HOURS,
    LAST_24_HOURS
}

data class TimelineEvent(
    val timestamp: Long,
    val type: TimelineEventType,
    val data: Any? = null
)

enum class TimelineEventType {
    LOG_ADDED,
    ERROR_OCCURRED,
    WARNING_OCCURRED,
    OPERATION_STARTED,
    OPERATION_COMPLETED,
    CUSTOM_MARKER
}

class TimelineAnalyzer {
    
    fun analyzeTimeline(logs: List<LogEntry>): TimelineAnalysis {
        if (logs.isEmpty()) {
            return TimelineAnalysis(
                startTime = 0,
                endTime = 0,
                totalDuration = 0,
                events = emptyList(),
                operationChains = emptyList(),
                errorClusters = emptyList()
            )
        }
        
        val startTime = logs.minOfOrNull { it.timestamp } ?: 0L
        val endTime = logs.maxOfOrNull { it.timestamp } ?: 0L
        val totalDuration = endTime - startTime
        
        val events = detectEvents(logs)
        val operationChains = detectOperationChains(logs)
        val errorClusters = detectErrorClusters(logs)
        
        return TimelineAnalysis(
            startTime = startTime,
            endTime = endTime,
            totalDuration = totalDuration,
            events = events,
            operationChains = operationChains,
            errorClusters = errorClusters
        )
    }
    
    private fun detectEvents(logs: List<LogEntry>): List<TimelineEvent> {
        return logs.map { entry ->
            val type = when {
                entry.level == com.chopcut.util.debug.LogLevel.ERROR -> TimelineEventType.ERROR_OCCURRED
                entry.level == com.chopcut.util.debug.LogLevel.WARN -> TimelineEventType.WARNING_OCCURRED
                entry.message.contains("iniciada", ignoreCase = true) || 
                entry.message.contains("started", ignoreCase = true) -> TimelineEventType.OPERATION_STARTED
                entry.message.contains("concluída", ignoreCase = true) ||
                entry.message.contains("completed", ignoreCase = true) -> TimelineEventType.OPERATION_COMPLETED
                else -> TimelineEventType.LOG_ADDED
            }
            
            TimelineEvent(
                timestamp = entry.timestamp,
                type = type,
                data = entry
            )
        }
    }
    
    private fun detectOperationChains(logs: List<LogEntry>): List<OperationChain> {
        val chains = mutableListOf<OperationChain>()
        val pendingOperations = mutableMapOf<String, LogEntry>()
        
        logs.forEach { entry ->
            val operation = extractOperationName(entry.message)
            val isStart = entry.message.contains("iniciada", ignoreCase = true) ||
                         entry.message.contains("started", ignoreCase = true)
            val isEnd = entry.message.contains("concluída", ignoreCase = true) ||
                       entry.message.contains("completed", ignoreCase = true)
            
            if (isStart) {
                pendingOperations[operation] = entry
            } else if (isEnd && pendingOperations.containsKey(operation)) {
                val startEntry = pendingOperations.remove(operation)!!
                chains.add(
                    OperationChain(
                        operation = operation,
                        startEntry = startEntry,
                        endEntry = entry,
                        duration = entry.timestamp - startEntry.timestamp
                    )
                )
            }
        }
        
        return chains.sortedByDescending { it.duration }
    }
    
    private fun detectErrorClusters(logs: List<LogEntry>): List<ErrorCluster> {
        val errorLogs = logs.filter { it.level == com.chopcut.util.debug.LogLevel.ERROR }
        if (errorLogs.size < 2) return emptyList()
        
        val clusters = mutableListOf<ErrorCluster>()
        var currentCluster = mutableListOf<LogEntry>()
        val clusterThreshold = 5000L
        
        errorLogs.sortedBy { it.timestamp }.forEach { error ->
            if (currentCluster.isEmpty()) {
                currentCluster.add(error)
            } else {
                val lastError = currentCluster.last()
                if (error.timestamp - lastError.timestamp <= clusterThreshold) {
                    currentCluster.add(error)
                } else {
                    if (currentCluster.size >= 2) {
                        clusters.add(
                            ErrorCluster(
                                errors = currentCluster.toList(),
                                startTime = currentCluster.first().timestamp,
                                endTime = currentCluster.last().timestamp,
                                count = currentCluster.size
                            )
                        )
                    }
                    currentCluster = mutableListOf(error)
                }
            }
        }
        
        if (currentCluster.size >= 2) {
            clusters.add(
                ErrorCluster(
                    errors = currentCluster.toList(),
                    startTime = currentCluster.first().timestamp,
                    endTime = currentCluster.last().timestamp,
                    count = currentCluster.size
                )
            )
        }
        
        return clusters
    }
    
    private fun extractOperationName(message: String): String {
        return when {
            message.contains("ThumbnailExtraction", ignoreCase = true) -> "ThumbnailExtraction"
            message.contains("Audio", ignoreCase = true) -> "Audio"
            message.contains("Cache", ignoreCase = true) -> "Cache"
            message.contains("Pipeline", ignoreCase = true) -> "Pipeline"
            message.contains("Trim", ignoreCase = true) -> "Trim"
            else -> "Unknown"
        }
    }
}

data class TimelineAnalysis(
    val startTime: Long,
    val endTime: Long,
    val totalDuration: Long,
    val events: List<TimelineEvent>,
    val operationChains: List<OperationChain>,
    val errorClusters: List<ErrorCluster>
)

data class OperationChain(
    val operation: String,
    val startEntry: LogEntry,
    val endEntry: LogEntry,
    val duration: Long
)

data class ErrorCluster(
    val errors: List<LogEntry>,
    val startTime: Long,
    val endTime: Long,
    val count: Int
)