package com.chopcut.config.constants

object ThumbnailConstants {
    object Dimensions {
        const val DEFAULT_WIDTH = 320
        const val DEFAULT_HEIGHT = 180
        const val COMPACT_WIDTH = 40
        const val COMPACT_HEIGHT = 40
        const val NORMAL_WIDTH = 50
        const val NORMAL_HEIGHT = 50
        const val DETAILED_WIDTH = 80
        const val DETAILED_HEIGHT = 80
        const val TARGET_HEIGHT = 120
        const val STRIP_DEFAULT_WIDTH = 50
        const val STRIP_DEFAULT_HEIGHT = 50
        
        val PRESETS = listOf(
            Preset(smallWidth = 240, smallHeight = 135, mediumWidth = 320, mediumHeight = 180, largeWidth = 480, largeHeight = 270, hdWidth = 640, hdHeight = 360)
        )
        
        data class Preset(
            val smallWidth: Int,
            val smallHeight: Int,
            val mediumWidth: Int = 320,
            val mediumHeight: Int = 180,
            val largeWidth: Int = 480,
            val largeHeight: Int = 270,
            val hdWidth: Int = 640,
            val hdHeight: Int = 360
        )
    }
    
    object Quality {
        const val JPEG_COMPRESSION_QUALITY = 80
        const val STRIP_COMPRESSION_QUALITY = 70
        const val DEFAULT_QUALITY = 85
        const val HIGH_QUALITY_EXTRACT_FACTOR = 1.2f
        const val ANTI_ALIASING_EXTRACT_FACTOR = 1.2f
        const val POSITION_EXTRACT_FACTOR = 2
        const val FILMSTRIP_EXTRACT_FACTOR = 2
        const val DEFAULT_THUMBS_PER_SECOND = 1
        const val DEFAULT_THUMBS_PER_STRIP = 10
        const val MIN_THUMBS_PER_STRIP = 5
        const val ADAPTIVE_POWER_CURVE_EXPONENT = 0.5f
    }
    
    object Cache {
        const val MAX_CACHE_SIZE = 200L * 1024 * 1024
        const val CACHE_VERSION = 3
        const val MEMORY_CACHE_SIZE = 100
        const val TRIM_RATIO_DIVISOR = 4
        const val INITIAL_SEGMENTS_TO_PRELOAD = 5
        const val JOB_CANCELLATION_THRESHOLD = 5
    }
    
    object Timing {
        const val DEFAULT_INTERVAL_MS = 1000L
        const val INTERVAL_CALCULATION_DIVISOR = 1000L
        const val FILMSTRIP_INTERVAL_MS = 1000L
        const val CACHE_READ_THRESHOLD_MS = 50
    }
    
    object Concurrency {
        const val IO_SEMAPHORE_PERMITS = 3
        const val THREADS_LOW_END = 2
        const val THREADS_MID_RANGE = 4
        const val THREADS_HIGH_END = 6
        const val THREADS_MAX = 8
        
        fun calculateOptimalThreadCount(availableProcessors: Int): Int {
            return when {
                availableProcessors <= 2 -> THREADS_LOW_END
                availableProcessors <= 4 -> THREADS_MID_RANGE
                availableProcessors <= 6 -> THREADS_HIGH_END
                else -> THREADS_MAX
            }
        }
    }
    
    object FileFormats {
        const val EXTENSION_JPG = ".jpg"
        const val EXTENSION_PNG = ".png"
        const val EXTENSION_WEBP = ".webp"
        
        val SUPPORTED_EXTENSIONS = listOf(EXTENSION_JPG, EXTENSION_PNG, EXTENSION_WEBP)
    }
}