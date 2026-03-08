package com.chopcut.ui.components.console

import com.chopcut.util.debug.LogEntry
import com.chopcut.util.debug.LogLevel

data class PatternGroup(
    val id: String,
    val name: String,
    val pattern: String,
    val logs: List<LogEntry>,
    val count: Int,
    val firstSeen: Long,
    val lastSeen: Long,
    val frequency: Float
) {
    fun durationMs(): Long = lastSeen - firstSeen
    
    fun averageIntervalMs(): Long {
        if (logs.size < 2) return 0
        return durationMs() / (logs.size - 1)
    }
}

data class PatternMatch(
    val entry: LogEntry,
    val group: PatternGroup,
    val confidence: Float
)

class PatternDetector {
    
    private val minGroupSize = 3
    private val similarityThreshold = 0.7f
    
    fun detectPatterns(logs: List<LogEntry>): List<PatternGroup> {
        if (logs.size < minGroupSize) return emptyList()
        
        val groups = mutableListOf<PatternGroup>()
        val processedLogs = mutableSetOf<LogEntry>()
        
        val logsByTag = logs.groupBy { it.tag }
        
        logsByTag.forEach { (tag, tagLogs) ->
            val patterns = detectTagPatterns(tag, tagLogs)
            groups.addAll(patterns)
        }
        
        val crossTagPatterns = detectCrossTagPatterns(logs)
        groups.addAll(crossTagPatterns)
        
        return groups
            .sortedByDescending { it.count }
            .take(20)
    }
    
    private fun detectTagPatterns(tag: String, logs: List<LogEntry>): List<PatternGroup> {
        val groups = mutableListOf<PatternGroup>()
        
        val errorLogs = logs.filter { it.level == LogLevel.ERROR }
        if (errorLogs.size >= minGroupSize) {
            val errorGroups = groupBySimilarity(errorLogs)
            groups.addAll(errorGroups.map { group ->
                createPatternGroup(
                    name = "Error: $tag",
                    pattern = extractPattern(group),
                    logs = group,
                    tag = tag
                )
            })
        }
        
        val warnLogs = logs.filter { it.level == LogLevel.WARN }
        if (warnLogs.size >= minGroupSize) {
            val warnGroups = groupBySimilarity(warnLogs)
            groups.addAll(warnGroups.map { group ->
                createPatternGroup(
                    name = "Warning: $tag",
                    pattern = extractPattern(group),
                    logs = group,
                    tag = tag
                )
            })
        }
        
        val infoLogs = logs.filter { it.level == LogLevel.INFO }
        if (infoLogs.size >= minGroupSize * 2) {
            val infoGroups = groupBySimilarity(infoLogs).take(3)
            groups.addAll(infoGroups.map { group ->
                createPatternGroup(
                    name = "Info: $tag",
                    pattern = extractPattern(group),
                    logs = group,
                    tag = tag
                )
            })
        }
        
        return groups
    }
    
    private fun detectCrossTagPatterns(logs: List<LogEntry>): List<PatternGroup> {
        val groups = mutableListOf<PatternGroup>()
        
        val operationLogs = logs.filter { entry ->
            entry.message.contains("iniciada", ignoreCase = true) ||
            entry.message.contains("concluída", ignoreCase = true) ||
            entry.message.contains("started", ignoreCase = true) ||
            entry.message.contains("completed", ignoreCase = true)
        }
        
        if (operationLogs.size >= minGroupSize) {
            val operationGroups = operationLogs
                .groupBy { extractOperationType(it.message) }
                .filter { it.value.size >= minGroupSize }
            
            groups.addAll(operationGroups.map { (type, typeLogs) ->
                createPatternGroup(
                    name = "Operation: $type",
                    pattern = type,
                    logs = typeLogs,
                    tag = "operation"
                )
            })
        }
        
        return groups
    }
    
    private fun groupBySimilarity(logs: List<LogEntry>): List<List<LogEntry>> {
        if (logs.isEmpty()) return emptyList()
        
        val groups = mutableListOf<List<LogEntry>>()
        val processed = mutableSetOf<LogEntry>()
        
        logs.forEach { entry ->
            if (entry in processed) return@forEach
            
            val similarLogs = mutableListOf<LogEntry>()
            similarLogs.add(entry)
            
            logs.forEach { other ->
                if (other != entry && other !in processed) {
                    val similarity = calculateSimilarity(entry.message, other.message)
                    if (similarity >= similarityThreshold) {
                        similarLogs.add(other)
                        processed.add(other)
                    }
                }
            }
            
            if (similarLogs.size >= minGroupSize) {
                groups.add(similarLogs.sortedBy { it.timestamp })
            }
        }
        
        return groups
    }
    
    private fun calculateSimilarity(text1: String, text2: String): Float {
        val words1 = text1.lowercase().split(Regex("\\s+"))
        val words2 = text2.lowercase().split(Regex("\\s+"))
        
        if (words1.isEmpty() || words2.isEmpty()) return 0f
        
        val intersection = words1.intersect(words2.toSet()).size
        val union = words1.union(words2.toSet()).size
        
        if (union == 0) return 0f
        
        return intersection.toFloat() / union.toFloat()
    }
    
    private fun extractPattern(logs: List<LogEntry>): String {
        if (logs.isEmpty()) return ""
        
        val messages = logs.map { it.message }
        val firstMessage = messages[0]
        
        val commonPrefix = messages.fold(firstMessage) { acc, message ->
            val maxLength = minOf(acc.length, message.length)
            var commonLength = 0
            for (i in 0 until maxLength) {
                if (acc[i] == message[i]) {
                    commonLength++
                } else {
                    break
                }
            }
            acc.substring(0, commonLength)
        }
        
        if (commonPrefix.length > 10) {
            return commonPrefix.take(50) + if (commonPrefix.length > 50) "..." else ""
        }
        
        val words = messages.flatMap { it.split(Regex("\\s+")) }
        val wordFrequency = words.groupingBy { it.lowercase() }.eachCount()
        
        val commonWords = wordFrequency
            .filter { it.value >= messages.size * 0.6 }
            .keys
            .sortedByDescending { wordFrequency[it] }
            .take(5)
        
        return if (commonWords.isNotEmpty()) {
            commonWords.joinToString(" ")
        } else {
            firstMessage.take(50)
        }
    }
    
    private fun extractOperationType(message: String): String {
        return when {
            message.contains("ThumbnailExtraction", ignoreCase = true) -> "Thumbnail Extraction"
            message.contains("Audio", ignoreCase = true) -> "Audio Processing"
            message.contains("Cache", ignoreCase = true) -> "Cache Operation"
            message.contains("Pipeline", ignoreCase = true) -> "Pipeline Operation"
            message.contains("Trim", ignoreCase = true) -> "Trim Operation"
            else -> "General Operation"
        }
    }
    
    private fun createPatternGroup(
        name: String,
        pattern: String,
        logs: List<LogEntry>,
        tag: String
    ): PatternGroup {
        val sortedLogs = logs.sortedBy { it.timestamp }
        val duration = if (sortedLogs.size >= 2) {
            sortedLogs.last().timestamp - sortedLogs.first().timestamp
        } else {
            0L
        }
        
        val frequency = if (duration > 0) {
            sortedLogs.size.toFloat() / (duration / 1000f)
        } else {
            0f
        }
        
        return PatternGroup(
            id = "${tag}_${System.currentTimeMillis()}",
            name = name,
            pattern = pattern,
            logs = sortedLogs,
            count = sortedLogs.size,
            firstSeen = sortedLogs.first().timestamp,
            lastSeen = sortedLogs.last().timestamp,
            frequency = frequency
        )
    }
    
    fun getPerformanceInsights(logs: List<LogEntry>): List<PerformanceInsight> {
        val insights = mutableListOf<PerformanceInsight>()
        
        val slowOperations = detectSlowOperations(logs)
        insights.addAll(slowOperations)
        
        val frequentErrors = detectFrequentErrors(logs)
        insights.addAll(frequentErrors)
        
        val unusualPatterns = detectUnusualPatterns(logs)
        insights.addAll(unusualPatterns)
        
        return insights
    }
    
    private fun detectSlowOperations(logs: List<LogEntry>): List<PerformanceInsight> {
        val insights = mutableListOf<PerformanceInsight>()
        
        val operationPairs = mutableMapOf<String, MutableList<Pair<Long, Long>>>()
        
        logs.forEach { entry ->
            val operation = extractOperationType(entry.message)
            val isStart = entry.message.contains("iniciada", ignoreCase = true) ||
                         entry.message.contains("started", ignoreCase = true)
            val isEnd = entry.message.contains("concluída", ignoreCase = true) ||
                       entry.message.contains("completed", ignoreCase = true)
            
            if (isStart) {
                operationPairs.getOrPut(operation) { mutableListOf() }.add(Pair(entry.timestamp, 0))
            } else if (isEnd) {
                val pairs = operationPairs[operation]
                if (pairs != null && pairs.isNotEmpty()) {
                    val lastPair = pairs.lastOrNull()
                    if (lastPair != null && lastPair.second == 0L) {
                        pairs[pairs.size - 1] = Pair(lastPair.first, entry.timestamp)
                    }
                }
            }
        }
        
        operationPairs.forEach { (operation, pairs) ->
            val completedPairs = pairs.filter { it.second > 0 }
            if (completedPairs.isNotEmpty()) {
                val durations = completedPairs.map { it.second - it.first }
                val avgDuration = durations.sum().toFloat() / durations.size
                val maxDuration = durations.maxOrNull() ?: 0L
                
                if (avgDuration > 3000) {
                    insights.add(
                        PerformanceInsight(
                            type = InsightType.SLOW_OPERATION,
                            severity = InsightSeverity.WARNING,
                            message = "Slow operation detected: $operation (avg: ${avgDuration}ms, max: ${maxDuration}ms)",
                            relatedLogs = completedPairs.map { it.first }.flatMap { timestamp ->
                                logs.filter { it.timestamp == timestamp }
                            }
                        )
                    )
                }
            }
        }
        
        return insights
    }
    
    private fun detectFrequentErrors(logs: List<LogEntry>): List<PerformanceInsight> {
        val insights = mutableListOf<PerformanceInsight>()
        
        val errorLogs = logs.filter { it.level == LogLevel.ERROR }
        if (errorLogs.size > 10) {
            insights.add(
                PerformanceInsight(
                    type = InsightType.HIGH_ERROR_RATE,
                    severity = InsightSeverity.ERROR,
                    message = "High error rate: ${errorLogs.size} errors in session",
                    relatedLogs = errorLogs.takeLast(10)
                )
            )
        }
        
        return insights
    }
    
    private fun detectUnusualPatterns(logs: List<LogEntry>): List<PerformanceInsight> {
        return emptyList()
    }
}

enum class InsightType {
    SLOW_OPERATION,
    HIGH_ERROR_RATE,
    MEMORY_LEAK,
    CACHE_ISSUE,
    NETWORK_PROBLEM
}

enum class InsightSeverity {
    INFO,
    WARNING,
    ERROR,
    CRITICAL
}

data class PerformanceInsight(
    val type: InsightType,
    val severity: InsightSeverity,
    val message: String,
    val relatedLogs: List<LogEntry> = emptyList(),
    val timestamp: Long = System.currentTimeMillis()
)