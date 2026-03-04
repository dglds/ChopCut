package com.chopcut.config.constants

object CacheConstants {
    object Sizes {
        const val MEMORY_CACHE_MAX_SIZE = 100
        const val WAVEFORM_CACHE_MAX_SIZE = 5
        const val STRIPS_MAX_SIZE = 500
        const val MEMORY_RATIO_DIVISOR = 8
    }
    
    object Performance {
        const val CANCEL_FAR_JOBS_THRESHOLD = 5
        const val INITIAL_SEGMENTS_TO_PRELOAD = 5
        const val CACHE_READ_WARNING_THRESHOLD_MS = 50
    }
    
    object Thumbnail {
        const val MAX_CACHE_SIZE_BYTES = 200L * 1024 * 1024
        const val CACHE_VERSION = 3
        const val COMPRESSION_QUALITY = 70
        const val TRIM_RATIO_DIVISOR = 4
    }
}