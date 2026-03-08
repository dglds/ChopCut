package com.chopcut.ui.components.console

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

import com.chopcut.util.debug.DebugLogger
import com.chopcut.util.debug.LogEntry
import com.chopcut.util.debug.LogLevel

class ConsoleLineViewModel : ViewModel() {

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

    private val _maxDisplayLines = MutableStateFlow(100)
    val maxDisplayLines: StateFlow<Int> = _maxDisplayLines.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _assertsOnly = MutableStateFlow(false)
    val assertsOnly: StateFlow<Boolean> = _assertsOnly.asStateFlow()

    private val _topCountMode = MutableStateFlow(false)
    val topCountMode: StateFlow<Boolean> = _topCountMode.asStateFlow()

    enum class ConsolePosition {
        HEADER, FOOTER
    }

    private val _position = MutableStateFlow(ConsolePosition.FOOTER)
    val position: StateFlow<ConsolePosition> = _position.asStateFlow()

    private val _timberEnabled = MutableStateFlow(true)
    val timberEnabled: StateFlow<Boolean> = _timberEnabled.asStateFlow()
    
    private val _metrics = LogMetricsTracker()
    val metrics: StateFlow<LogMetrics> = _metrics.metrics
    
    private val _investigationMode = InvestigationManager()
    val investigationMode: StateFlow<InvestigationMode> = _investigationMode.mode
    
    private val _presentationMode = PresentationManager()
    val presentationMode: StateFlow<PresentationMode> = _presentationMode.mode
    
    private val _patternDetector = PatternDetector()
    
    val allThemes = listOf(
        ConsoleThemes.DEFAULT,
        ConsoleThemes.EIGHTIES,
        ConsoleThemes.AMBER_EIGHTIES,
        ConsoleThemes.CYBERPUNK,
        ConsoleThemes.MATRIX,
        ConsoleThemes.FIRE,
        ConsoleThemes.CUSTOM_THEME
    )

    init {
        setupTimberTree()
        observeLogs()
    }

    private fun setupTimberTree() {
        val tree = object : timber.log.Timber.Tree() {
            override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
                if (!_timberEnabled.value) return
                
                val level = when (priority) {
                    android.util.Log.VERBOSE -> LogLevel.VERBOSE
                    android.util.Log.DEBUG -> LogLevel.DEBUG
                    android.util.Log.INFO -> LogLevel.INFO
                    android.util.Log.WARN -> LogLevel.WARN
                    android.util.Log.ERROR -> LogLevel.ERROR
                    android.util.Log.ASSERT -> LogLevel.ERROR
                    else -> LogLevel.DEBUG
                }

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

                _hasPendingLogs.value = true
                when (level) {
                    LogLevel.VERBOSE -> DebugLogger.v(effectiveTag, effectiveMessage)
                    LogLevel.DEBUG -> DebugLogger.d(effectiveTag, effectiveMessage)
                    LogLevel.INFO -> DebugLogger.i(effectiveTag, effectiveMessage)
                    LogLevel.WARN -> DebugLogger.w(effectiveTag, effectiveMessage)
                    LogLevel.ERROR -> DebugLogger.e(effectiveTag, effectiveMessage)
                }
            }
        }
        timber.log.Timber.plant(tree)
    }
    
    fun toggleTimber() {
        _timberEnabled.value = !_timberEnabled.value
    }

    private fun observeLogs() {
        viewModelScope.launch {
            kotlinx.coroutines.flow.combine(
                DebugLogger.logs,
                _searchQuery,
                _assertsOnly,
                _topCountMode,
                _maxDisplayLines
            ) { logs, query, isAssertsOnly, isTopCount, maxLines ->
                val filteredLogs = logs.filter { entry ->
                    val isExtractionActivity = entry.tag == "ChopCut.Activity" && entry.message.contains("ThumbnailExtraction")
                    val isAssert = isExtractionActivity && (
                        entry.message.contains("iniciada", ignoreCase = true) ||
                        entry.message.contains("concluída", ignoreCase = true)
                    )

                    if (isAssertsOnly) {
                        return@filter isAssert
                    }

                    if (query.isNotEmpty()) {
                        entry.tag.contains(query, ignoreCase = true) ||
                        entry.message.contains(query, ignoreCase = true)
                    } else {
                        isAssert || entry.level == LogLevel.ERROR
                    }
                }

                if (isTopCount) {
                    filteredLogs.groupBy { it.tag }
                        .mapNotNull { (_, entries) -> entries.maxByOrNull { it.timestamp } }
                        .sortedByDescending { it.count }
                } else {
                    filteredLogs.takeLast(maxLines)
                }
            }.collect { mappedLogs ->
                _logHistory.value = mappedLogs
                _hasPendingLogs.value = false
            }
        }
    }

    fun addLog(message: String, tag: String? = null) {
        DebugLogger.d(tag ?: "UI", message)
    }

    fun clear() {
        DebugLogger.clear()
        _logHistory.value = emptyList()
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun toggleAssertsOnly() {
        _assertsOnly.value = !_assertsOnly.value
    }

    fun toggleTopCountMode() {
        _topCountMode.value = !_topCountMode.value
    }

    fun copyToClipboard(context: android.content.Context) {
        val text = _logHistory.value.joinToString("\n") { "[${it.level}] ${it.tag}: ${it.message}" }
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
    
    fun getMetrics(): LogMetrics {
        return _metrics.calculateMetrics(_logHistory.value)
    }
    
    fun getAnomalies(): List<Anomaly> {
        return _metrics.getAnomalies()
    }
    
    fun detectPatterns(): List<PatternGroup> {
        return _patternDetector.detectPatterns(_logHistory.value)
    }
    
    fun getPerformanceInsights(): List<PerformanceInsight> {
        return _patternDetector.getPerformanceInsights(_logHistory.value)
    }
    
    fun toggleInvestigationMode() {
        _investigationMode.toggleInvestigationMode()
    }
    
    fun pinLog(entry: LogEntry, note: String = "") {
        _investigationMode.pinLog(entry, note)
    }
    
    fun unpinLog(entry: LogEntry) {
        _investigationMode.unpinLog(entry)
    }
    
    fun clearPinnedLogs() {
        _investigationMode.clearPinnedLogs()
    }
    
    fun applyTimePreset(preset: TimePreset) {
        _investigationMode.applyPreset(preset)
    }
    
    fun togglePresentationMode() {
        _presentationMode.togglePresentationMode()
    }
    
    fun nextSlide() {
        _presentationMode.nextSlide()
    }
    
    fun previousSlide() {
        _presentationMode.previousSlide()
    }
    
    fun createPresentationSlide(title: String, description: String) {
        _presentationMode.createSlide(title, description, _logHistory.value)
    }
    
    fun clearSlides() {
        _presentationMode.clearSlides()
    }
    
    fun nextTheme() {
        val currentIndex = allThemes.indexOf(_currentTheme.value)
        val nextIndex = (currentIndex + 1) % allThemes.size
        _currentTheme.value = allThemes[nextIndex]
    }
    
    fun previousTheme() {
        val currentIndex = allThemes.indexOf(_currentTheme.value)
        val prevIndex = if (currentIndex - 1 < 0) allThemes.size - 1 else currentIndex - 1
        _currentTheme.value = allThemes[prevIndex]
    }
    
    fun setCustomTheme(theme: ConsoleTheme) {
        _currentTheme.value = theme
    }
}
