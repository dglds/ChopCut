package com.chopcut.config.constants

object ThumbnailConfig {
    object Dimensions {
        const val COMPACT_HEIGHT = 40
        const val COMPACT_WIDTH = 40
        const val DETAILED_HEIGHT = 80
        const val DETAILED_WIDTH = 80
        const val DEFAULT_HEIGHT = 180
        const val DEFAULT_WIDTH = 320
        const val NORMAL_HEIGHT = 50
        const val NORMAL_WIDTH = 50
    }
    
    object Quality {
        const val DEFAULT_THUMBS_PER_STRIP = 10
        const val HIGH_QUALITY_EXTRACT_FACTOR = 1.2f
        const val JPEG_COMPRESSION_QUALITY = 80
    }
    
    object Timing {
        const val INTERVAL_CALCULATION_DIVISOR = 1000L
    }
    
    object FileFormats {
        const val EXT_JPG = ".jpg"
        const val EXT_PNG = ".png"
        const val EXT_WEBP = ".webp"
    }
    
    object Concurrency {
        const val IO_SEMAPHORE_PERMITS = 3
    }
    
    object Cache {
        const val CACHE_VERSION = 3
        const val MAX_CACHE_SIZE = 200L * 1024 * 1024
    }
    
    object Compression {
        const val STRIP_COMPRESSION_QUALITY = 70
    }
    
    object Adaptive {
        const val MIN_THUMBS_PER_STRIP = 5
        const val ADAPTIVE_POWER_CURVE_EXPONENT = 0.5f
    }
}
