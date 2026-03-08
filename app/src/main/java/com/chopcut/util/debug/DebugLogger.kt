package com.chopcut.util.debug

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class LogLevel {
    VERBOSE, DEBUG, INFO, WARN, ERROR
}

data class LogEntry(
    val level: LogLevel,
    val tag: String,
    val message: String,
    val timestamp: Long = System.currentTimeMillis()
)

object DebugLogger {
    private const val MAX_RETENTION = 1000
    
    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()

    fun v(tag: String, message: String) = log(LogLevel.VERBOSE, tag, message)
    fun d(tag: String, message: String) = log(LogLevel.DEBUG, tag, message)
    fun i(tag: String, message: String) = log(LogLevel.INFO, tag, message)
    fun w(tag: String, message: String) = log(LogLevel.WARN, tag, message)
    fun e(tag: String, message: String) = log(LogLevel.ERROR, tag, message)

    private fun log(level: LogLevel, tag: String, message: String) {
        val entry = LogEntry(level, tag, message)
        _logs.update { currentLogs ->
            val updated = if (currentLogs.size >= MAX_RETENTION) {
                currentLogs.drop(1) + entry
            } else {
                currentLogs + entry
            }
            updated
        }
    }

    fun clear() {
        _logs.value = emptyList()
    }

    fun getFilteredLogs(query: String? = null, level: LogLevel? = null): List<LogEntry> {
        return _logs.value.filter { entry ->
            val matchesQuery = query == null || 
                entry.tag.contains(query, ignoreCase = true) || 
                entry.message.contains(query, ignoreCase = true)
            
            val matchesLevel = level == null || entry.level == level
            
            matchesQuery && matchesLevel
        }
    }
}
