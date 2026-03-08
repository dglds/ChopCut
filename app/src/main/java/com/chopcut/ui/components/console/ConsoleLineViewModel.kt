package com.chopcut.ui.components.console

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

import com.chopcut.util.debug.DebugLogger
import com.chopcut.util.debug.LogEntry as NewLogEntry
import com.chopcut.util.debug.LogLevel
import timber.log.Timber

class ConsoleLineViewModel : ViewModel() {
    
    // Mantendo a estrutura de dados antiga por compatibilidade temporária com a UI
    data class LogEntry(
        val tag: String,
        val message: String,
        val count: Int,
        val fullText: String
    )
    
    private val _logs = MutableStateFlow<LogEntry?>(null)
    val logs: StateFlow<LogEntry?> = _logs.asStateFlow()
    
    private val _logHistory = MutableStateFlow<List<LogEntry>>(emptyList())
    val logHistory: StateFlow<List<LogEntry>> = _logHistory.asStateFlow()
    
    private val _currentTheme = MutableStateFlow(ConsoleThemes.DEFAULT)
    val currentTheme: StateFlow<ConsoleTheme> = _currentTheme.asStateFlow()
    
    private val _isVisible = MutableStateFlow(true)
    val isVisible: StateFlow<Boolean> = _isVisible.asStateFlow()
    
    private val _hasPendingLogs = MutableStateFlow(false)
    val hasPendingLogs: StateFlow<Boolean> = _hasPendingLogs.asStateFlow()
    
    private val _isMultiLine = MutableStateFlow(true)
    val isMultiLine: StateFlow<Boolean> = _isMultiLine.asStateFlow()
    
    private val _maxDisplayLines = MutableStateFlow(5) // Default to 5 lines as requested
    val maxDisplayLines: StateFlow<Int> = _maxDisplayLines.asStateFlow()
    
    private val _callStackMode = MutableStateFlow(false)
    val callStackMode: StateFlow<Boolean> = _callStackMode.asStateFlow()
    
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    enum class ConsolePosition {
        HEADER, FOOTER
    }
    
    private val _position = MutableStateFlow(ConsolePosition.FOOTER)
    val position: StateFlow<ConsolePosition> = _position.asStateFlow()

    init {
        setupTimberTree()
        observeLogs()
    }
    
    private fun setupTimberTree() {
        try {
            val tree = object : Timber.Tree() {
                override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
                    val level = when (priority) {
                        android.util.Log.VERBOSE -> LogLevel.VERBOSE
                        android.util.Log.DEBUG -> LogLevel.DEBUG
                        android.util.Log.INFO -> LogLevel.INFO
                        android.util.Log.WARN -> LogLevel.WARN
                        android.util.Log.ERROR -> LogLevel.ERROR
                        else -> LogLevel.DEBUG
                    }
                    
                    // Extrair tag se não fornecida (comportamento antigo mantido)
                    val firstSpaceIndex = message.indexOf(" ")
                    val effectiveTag = tag ?: if (firstSpaceIndex > 0) {
                        message.substring(0, firstSpaceIndex)
                    } else {
                        "LOG"
                    }
                    
                    val effectiveMessage = if (tag == null && firstSpaceIndex > 0) {
                        message.substring(firstSpaceIndex + 1)
                    } else {
                        message
                    }

                    when (level) {
                        LogLevel.VERBOSE -> DebugLogger.v(effectiveTag, effectiveMessage)
                        LogLevel.DEBUG -> DebugLogger.d(effectiveTag, effectiveMessage)
                        LogLevel.INFO -> DebugLogger.i(effectiveTag, effectiveMessage)
                        LogLevel.WARN -> DebugLogger.w(effectiveTag, effectiveMessage)
                        LogLevel.ERROR -> DebugLogger.e(effectiveTag, effectiveMessage)
                    }
                }
            }
            Timber.plant(tree)
        } catch (e: Exception) {
            // Ignorar
        }
    }
    
    private fun observeLogs() {
        viewModelScope.launch {
            DebugLogger.logs.collect { newLogs ->
                // Apenas mapeia as últimas linhas necessárias para a UI, evitando processar 1000 logs todas as vezes
                val recentLogsToMap = newLogs.takeLast(_maxDisplayLines.value)
                val mappedLogs = recentLogsToMap.map { entry ->
                    LogEntry(
                        tag = entry.tag,
                        message = entry.message,
                        count = entry.count,
                        fullText = "[${entry.level}] ${entry.tag}: ${entry.message}"
                    )
                }
                
                _logHistory.value = mappedLogs
                _logs.value = mappedLogs.lastOrNull()
                _hasPendingLogs.value = false
            }
        }
    }
    
    fun addLog(message: String, tag: String? = null) {
        DebugLogger.d(tag ?: "UI", message)
    }
    
    fun clear() {
        DebugLogger.clear()
        _logs.value = null
        _logHistory.value = emptyList()
    }
    
    fun setSearchQuery(query: String) {
        _searchQuery.value = query
        // Filtro será aplicado na UI na Fase 2/3 ou via Logger
    }

    fun setTheme(theme: ConsoleTheme) {
        _currentTheme.value = theme
    }
    
    fun toggleTheme() {
        _currentTheme.value = when (_currentTheme.value) {
            ConsoleThemes.DEFAULT -> ConsoleThemes.EIGHTIES
            ConsoleThemes.EIGHTIES -> ConsoleThemes.AMBER_EIGHTIES
            else -> ConsoleThemes.DEFAULT
        }
    }
    
    fun toggleVisibility() {
        _isVisible.value = !_isVisible.value
    }
    
    fun dismiss() {
        _isVisible.value = false
    }
    
    fun show() {
        _isVisible.value = true
    }
    
    fun toggleMultiLine() {
        _isMultiLine.value = !_isMultiLine.value
    }
    
    fun setPosition(position: ConsolePosition) {
        _position.value = position
    }
    
    fun togglePosition() {
        _position.value = when (_position.value) {
            ConsolePosition.HEADER -> ConsolePosition.FOOTER
            ConsolePosition.FOOTER -> ConsolePosition.HEADER
        }
    }
}
