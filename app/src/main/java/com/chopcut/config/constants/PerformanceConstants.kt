package com.chopcut.config.constants

object PerformanceConstants {
    object ThreadCounts {
        const val LOW_END = 2
        const val MID_RANGE = 4
        const val HIGH_END = 6
        const val MAX = 8
        
        fun calculateOptimalThreads(availableProcessors: Int): Int {
            return when {
                availableProcessors <= 2 -> LOW_END
                availableProcessors <= 4 -> MID_RANGE
                availableProcessors <= 6 -> HIGH_END
                else -> MAX
            }
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