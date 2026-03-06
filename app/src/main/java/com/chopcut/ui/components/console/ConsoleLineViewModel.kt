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

class ConsoleLineViewModel : ViewModel() {
    
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
    
    private val _maxDisplayLines = MutableStateFlow(5)
    val maxDisplayLines: StateFlow<Int> = _maxDisplayLines.asStateFlow()
    
    private val _callStackMode = MutableStateFlow(false)
    val callStackMode: StateFlow<Boolean> = _callStackMode.asStateFlow()
    
    enum class ConsolePosition {
        HEADER, FOOTER
    }
    
    private val _position = MutableStateFlow(ConsolePosition.FOOTER)
    val position: StateFlow<ConsolePosition> = _position.asStateFlow()
    
    private val logQueue = ArrayDeque<Pair<String, String?>>()
    private val maxQueueSize = 100
    private var isProcessing = false
    private val logCountMap = mutableMapOf<String, Int>()
    private val callStackMap = mutableMapOf<String, MutableList<LogEntry>>()
    
    init {
        setupTimberTree()
        startLogProcessor()
    }
    
    private fun setupTimberTree() {
        try {
            val tree = object : timber.log.Timber.Tree() {
                override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
                    try {
                        // Passar a mensagem completa, a tag será extraída pelo processor
                        addLog(message, null)
                    } catch (e: Exception) {
                        // Ignorar erros de logging para evitar loop infinito
                    }
                }
            }
            timber.log.Timber.plant(tree)
        } catch (e: Exception) {
            // Ignorar erros ao plantar tree de logging
        }
    }
    
    private fun startLogProcessor() {
        viewModelScope.launch {
            while (true) {
                try {
                    ensureActive()
                    if (logQueue.isNotEmpty() && !isProcessing) {
                        isProcessing = true
                        
                        try {
                            // Processar TODOS os logs concorrentemente (não um por um)
                            val allLogs = logQueue.toList()
                            logQueue.clear()
                            
                            val logEntries = allLogs.mapNotNull { (log, timberTag) ->
                                try {
                                    // Extrair a primeira palavra (com caracteres especiais) como tag
                                    val firstSpaceIndex = log.indexOf(" ")
                                    val tagKey = if (firstSpaceIndex > 0) {
                                        log.substring(0, firstSpaceIndex)
                                    } else {
                                        log
                                    }
                                    
                                    // O resto da mensagem (sem a primeira palavra)
                                    val messageContent = if (firstSpaceIndex > 0) {
                                        log.substring(firstSpaceIndex + 1)
                                    } else {
                                        ""
                                    }
                                    
                                    val count = (logCountMap[tagKey] ?: 0) + 1
                                    logCountMap[tagKey] = count
                                    
                                    val logEntry = LogEntry(
                                        tag = tagKey,
                                        message = messageContent,
                                        count = count,
                                        fullText = "[$count]$tagKey $messageContent"
                                    )
                                    
                                    // Manter call stack por tag
                                    if (_callStackMode.value) {
                                        val stack = callStackMap.getOrPut(tagKey) { mutableListOf() }
                                        stack.add(logEntry)
                                        // Manter apenas os últimos 50 logs por tag
                                        if (stack.size > 50) {
                                            stack.removeAt(0)
                                        }
                                    }
                                    
                                    logEntry
                                } catch (e: Exception) {
                                    null
                                }
                            }
                            
                            // Atualizar histórico com todos os logs processados
                            if (logEntries.isNotEmpty()) {
                                if (_isMultiLine.value) {
                                    _logHistory.update { current ->
                                        val updated = current + logEntries
                                        if (updated.size > _maxDisplayLines.value) {
                                            updated.takeLast(_maxDisplayLines.value)
                                        } else {
                                            updated
                                        }
                                    }
                                } else {
                                    // Modo single-line: mostrar apenas o último
                                    _logs.value = logEntries.lastOrNull()
                                }
                            }
                        } catch (e: Exception) {
                            // Ignorar erros de processamento de lote
                        }
                        
                        _hasPendingLogs.value = logQueue.isNotEmpty()
                        delay(50)
                        isProcessing = false
                    } else {
                        delay(20)
                    }
                } catch (e: Exception) {
                    // Ignorar erros do loop principal
                    isProcessing = false
                }
            }
        }
    }
    
    fun addLog(message: String, tag: String? = null) {
        viewModelScope.launch {
            try {
                if (logQueue.size >= maxQueueSize) {
                    logQueue.removeFirst()
                }
                logQueue.addLast(message to tag)
                _hasPendingLogs.value = true
            } catch (e: Exception) {
                // Ignorar erros ao adicionar log
            }
        }
    }
    
    fun clear() {
        logQueue.clear()
        _logs.value = null
        _logHistory.value = emptyList()
        _hasPendingLogs.value = false
        logCountMap.clear()
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
        if (_isMultiLine.value) {
            _logs.value = null
        } else {
            _logHistory.value = emptyList()
        }
    }
    
    fun setMultiLine(enabled: Boolean) {
        _isMultiLine.value = enabled
        if (enabled) {
            _logs.value = null
        } else {
            _logHistory.value = emptyList()
        }
    }
    
    fun setMaxDisplayLines(lines: Int) {
        _maxDisplayLines.value = lines.coerceAtLeast(1).coerceAtMost(10)
        if (_isMultiLine.value) {
            _logHistory.update { current ->
                if (current.size > lines) {
                    current.takeLast(lines)
                } else {
                    current
                }
            }
        }
    }
    
    fun increaseDisplayLines() {
        setMaxDisplayLines(_maxDisplayLines.value + 1)
    }
    
    fun decreaseDisplayLines() {
        setMaxDisplayLines(_maxDisplayLines.value - 1)
    }
    
    fun toggleCallStackMode() {
        _callStackMode.value = !_callStackMode.value
        if (!_callStackMode.value) {
            callStackMap.clear()
        }
    }
    
    fun setCallStackMode(enabled: Boolean) {
        _callStackMode.value = enabled
        if (!enabled) {
            callStackMap.clear()
        }
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
    
    override fun onCleared() {
        super.onCleared()
    }
}
