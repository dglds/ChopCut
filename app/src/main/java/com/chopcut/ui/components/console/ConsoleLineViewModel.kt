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
    
    private val _maxDisplayLines = MutableStateFlow(100) // Aumentado para manter histórico para scroll
    val maxDisplayLines: StateFlow<Int> = _maxDisplayLines.asStateFlow()
    
    private val _callStackMode = MutableStateFlow(false)
    val callStackMode: StateFlow<Boolean> = _callStackMode.asStateFlow()
    
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedLevel = MutableStateFlow<LogLevel?>(null)
    val selectedLevel: StateFlow<LogLevel?> = _selectedLevel.asStateFlow()

    private val _assertsOnly = MutableStateFlow(false)
    val assertsOnly: StateFlow<Boolean> = _assertsOnly.asStateFlow()

    enum class ConsolePosition {
        HEADER, FOOTER
    }
    
    private val _position = MutableStateFlow(ConsolePosition.HEADER)
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
                        android.util.Log.ASSERT -> LogLevel.ERROR // Mapeia ASSERT/WTF para ERROR para garantir visibilidade
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
            kotlinx.coroutines.flow.combine(
                DebugLogger.logs,
                _searchQuery,
                _selectedLevel,
                _assertsOnly
            ) { logs, query, level, isAssertsOnly ->
                val assetKeywords = listOf("Thumbnail", "Strip", "Activity", "Asset", "Video", "Performance")
                
                logs.filter { entry ->
                    val isAssert = entry.message.contains("■") || entry.message.contains("concluída", ignoreCase = true)
                    
                    if (isAssertsOnly) {
                        return@filter isAssert
                    }

                    val matchesQuery = if (query.isNotEmpty()) {
                        entry.tag.contains(query, ignoreCase = true) || 
                        entry.message.contains(query, ignoreCase = true)
                    } else {
                        assetKeywords.any { entry.tag.contains(it, ignoreCase = true) || entry.message.contains(it, ignoreCase = true) } ||
                        entry.level.ordinal >= LogLevel.INFO.ordinal
                    }
                    
                    val matchesLevel = level == null || entry.level == level
                    
                    matchesQuery && matchesLevel
                }.takeLast(_maxDisplayLines.value).map { entry ->
                    LogEntry(
                        tag = entry.tag,
                        message = entry.message,
                        count = entry.count,
                        fullText = "[${entry.level}] ${entry.tag}: ${entry.message}"
                    )
                }
            }.collect { mappedLogs ->
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
    }

    fun setSelectedLevel(level: LogLevel?) {
        _selectedLevel.value = level
    }

    fun toggleAssertsOnly() {
        _assertsOnly.value = !_assertsOnly.value
    }

    fun copyToClipboard(context: android.content.Context) {
        val text = _logHistory.value.joinToString("\n") { it.fullText }
        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText("ChopCut Debug Logs", text)
        clipboard.setPrimaryClip(clip)
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
