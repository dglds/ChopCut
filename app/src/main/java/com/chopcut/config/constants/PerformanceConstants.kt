package com.chopcut.config.constants

object PerformanceConstants {
    object ThreadCounts {
        const val LOW_END = 2
        const val MID_RANGE = 4
        const val HIGH_END = 6
        const val MAX = 8
        
        fun calculateOptimalThreads(availableProcessors: Int): Int {
            // Regra: CPU - 1 para manter a UI fluída, mínimo 1
            val optimal = (availableProcessors - 1).coerceAtLeast(1)
            return optimal.coerceAtMost(MAX)
        }
    }
    
    object Thresholds {
        const val PROGRESS_POLLING_INTERVAL_MS = 250
        const val PROGRESS_POLLING_DELAY_MS = 250
        const val CACHE_READ_WARNING_THRESHOLD_MS = 50
        const val SLOW_CACHE_READ_THRESHOLD_MS = 50
    }
    
    object Limits {
        const val MAX_DEBUG_ENTRIES = 50
    }
}