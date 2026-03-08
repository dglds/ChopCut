package com.chopcut.ui.components.console

import com.chopcut.util.debug.LogEntry
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException

enum class SearchType {
    TEXT,
    REGEX,
    TAG,
    LEVEL
}

data class SearchQuery(
    val text: String,
    val type: SearchType = SearchType.TEXT,
    val isCaseSensitive: Boolean = false,
    val isWholeWord: Boolean = false
) {
    fun matches(entry: LogEntry): Boolean {
        if (text.isEmpty()) return true
        
        val searchText = if (isCaseSensitive) text else text.lowercase()
        val targetText = when (type) {
            SearchType.TEXT -> entry.message
            SearchType.REGEX -> entry.message
            SearchType.TAG -> entry.tag
            SearchType.LEVEL -> entry.level.toString()
        }
        
        val target = if (isCaseSensitive) targetText else targetText.lowercase()
        
        return when (type) {
            SearchType.REGEX -> {
                try {
                    val pattern = if (isCaseSensitive) {
                        Pattern.compile(searchText)
                    } else {
                        Pattern.compile(searchText, Pattern.CASE_INSENSITIVE)
                    }
                    pattern.matcher(targetText).find()
                } catch (e: PatternSyntaxException) {
                    false
                }
            }
            SearchType.TEXT -> {
                if (isWholeWord) {
                    val words = target.split(Regex("\\s+"))
                    words.any { it == searchText }
                } else {
                    target.contains(searchText)
                }
            }
            SearchType.TAG -> {
                if (isWholeWord) {
                    target == searchText
                } else {
                    target.contains(searchText)
                }
            }
            SearchType.LEVEL -> {
                target.contains(searchText)
            }
        }
    }
}

data class SearchResult(
    val entry: LogEntry,
    val matches: List<TextMatch> = emptyList()
)

data class TextMatch(
    val startIndex: Int,
    val endIndex: Int,
    val text: String
)

fun highlightText(
    text: String,
    query: SearchQuery
): List<TextMatch> {
    val matches = mutableListOf<TextMatch>()
    
    if (query.text.isEmpty()) return matches
    
    val searchText = if (query.isCaseSensitive) query.text else query.text.lowercase()
    val target = if (query.isCaseSensitive) text else text.lowercase()
    
    when (query.type) {
        SearchType.REGEX -> {
            try {
                val pattern = if (query.isCaseSensitive) {
                    Pattern.compile(searchText)
                } else {
                    Pattern.compile(searchText, Pattern.CASE_INSENSITIVE)
                }
                val matcher = pattern.matcher(text)
                while (matcher.find()) {
                    matches.add(
                        TextMatch(
                            startIndex = matcher.start(),
                            endIndex = matcher.end(),
                            text = text.substring(matcher.start(), matcher.end())
                        )
                    )
                }
            } catch (e: PatternSyntaxException) {
            }
        }
        SearchType.TEXT, SearchType.TAG, SearchType.LEVEL -> {
            var index = 0
            while (true) {
                val foundIndex = if (query.isWholeWord) {
                    val words = target.split(Regex("\\s+"))
                    val wordIndex = words.indexOfFirst { it == searchText && index <= target.indexOf(it, index) }
                    if (wordIndex >= 0) target.indexOf(words[wordIndex], index) else -1
                } else {
                    target.indexOf(searchText, index)
                }
                
                if (foundIndex == -1) break
                
                if (query.isWholeWord) {
                    val start = foundIndex
                    val end = start + searchText.length
                    
                    if (start == 0 || !text[start - 1].isLetterOrDigit()) {
                        if (end >= text.length || !text[end].isLetterOrDigit()) {
                            matches.add(
                                TextMatch(
                                    startIndex = start,
                                    endIndex = end,
                                    text = text.substring(start, end)
                                )
                            )
                        }
                    }
                } else {
                    matches.add(
                        TextMatch(
                            startIndex = foundIndex,
                            endIndex = foundIndex + searchText.length,
                            text = text.substring(foundIndex, foundIndex + searchText.length)
                        )
                    )
                }
                
                index = if (query.isWholeWord) {
                    foundIndex + searchText.length
                } else {
                    foundIndex + 1
                }
            }
        }
    }
    
    return matches
}

class SearchHistory {
    private val history = mutableListOf<SearchQuery>()
    private val maxSize = 10
    
    fun add(query: SearchQuery) {
        if (query.text.isEmpty()) return
        
        history.remove(query)
        history.add(0, query)
        
        if (history.size > maxSize) {
            history.removeAt(history.size - 1)
        }
    }
    
    fun getRecent(): List<SearchQuery> = history.toList()
    
    fun clear() {
        history.clear()
    }
}