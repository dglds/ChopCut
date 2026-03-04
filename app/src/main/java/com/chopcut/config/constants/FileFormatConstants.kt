package com.chopcut.config.constants

object FileFormatConstants {
    object Extensions {
        const val JPG = ".jpg"
        const val JPEG = ".jpeg"
        const val PNG = ".png"
        const val WEBP = ".webp"
        const val MP4 = ".mp4"
        const val WEBM = ".webm"
        const val MOV = ".mov"
        
        val THUMBNAIL_EXTENSIONS = listOf(JPG, JPEG, PNG, WEBP)
        val VIDEO_EXTENSIONS = listOf(MP4, WEBM, MOV)
    }
    
    object Video {
        const val OUTPUT_EXTENSION = ".mp4"
        const val MUXER_OUTPUT_MPEG_4 = "video/mp4"
        const val MAX_DURATION_MS = 15 * 60 * 1000L
        const val MAX_DURATION_SECONDS = 900
    }
    
    object TimeFormatting {
        const val CENTISECONDS_DIVISOR = 10
        const val TIME_FORMAT = "%02d:%02d.%02d"
    }
}