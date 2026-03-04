package com.chopcut.config.constants

object QualityConstants {
    object Compression {
        const val JPEG_COMPRESSION_QUALITY = 80
        const val DEFAULT_QUALITY = 85
    }
    
    object Presets {
        const val DEFAULT_THUMBS_PER_SECOND = 1
        const val DEFAULT_BARS_PER_SECOND = 10
        const val DEFAULT_THUMBS_PER_STRIP = 10
    }
    
    object AspectRatio {
        const val ASPECT_16_9 = 16f / 9f
        const val ASPECT_9_16 = 9f / 16f
        const val ASPECT_4_3 = 4f / 3f
        const val ASPECT_1_1 = 1f
        const val TOLERANCE = 0.01f
        
        val SUPPORTED_RATIOS = listOf(ASPECT_16_9, ASPECT_9_16, ASPECT_4_3, ASPECT_1_1)
    }
    
    object Opacity {
        const val PRIMARY_ALPHA = 0.12f
        const val OVERLAY_ALPHA = 0.7f
        const val TEXT_ALPHA = 0.85f
        const val BACKGROUND_ALPHA = 0.2f
        const val ERROR_ALPHA = 0.8f
        const val RANGE_ALPHA = 0.3f
        const val SELECTION_ALPHA = 0.2f
    }
    
    object Scale {
        const val OVERLAY_SCALE_OUT_TARGET = 0.95f
        const val TRIM_SCALE_IN_START = 0.98f
        const val TRIM_SCALE_IN_END = 1.0f
        const val NAV_SCALE_START = 0.95f
        const val NAV_SCALE_END = 1.0f
        const val CONFIRM_SCALE = 1.15f
        const val ANIMATION_MIN = 0.95f
        const val ANIMATION_MAX = 1.05f
    }
}