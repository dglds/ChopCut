package com.chopcut.config.constants

object AnimationConstants {
    object Durations {
        const val FAST_MS = 150
        const val NORMAL_MS = 250
        const val SLOW_MS = 350
        const val OVERLAY_FADE_OUT_MS = 500
        const val TRIM_FADE_IN_MS = 500
        const val NAV_FADE_IN_MS = 400
        const val NAV_FADE_OUT_MS = 400
        const val ROTATION_ANIMATION_MS = 3000
        const val PULSE_ANIMATION_MS = 1500
        const val SCALE_ANIMATION_MS = 1200
        const val FADE_ANIMATION_MS = 800
        const val PROGRESS_ANIMATION_MS = 500
        const val ACTION_BUTTON_ANIMATION_MS = 500
        const val ICON_ANIMATION_MS = 300
        const val WAVEFORM_ANIMATION_MS = 600
        const val VISUAL_TIMER_DELAY_MS = 100
        const val NAV_ANIMATION_MS = 300
    }
    
    object Loading {
        const val SHORT_VIDEO_THRESHOLD_MS = 60_000L
        const val MAX_LOADING_DURATION_MS = 5_000L
        const val TARGET_DURATION_MS = 3_500L
        const val MIN_LOADING_PERCENTAGE = 0.05f
        const val MIN_THUMBNAIL_PROGRESS = 20f
        const val MIN_STRIPS_REQUIRED = 6
        const val CROSS_FADE_DELAY_MS = 100L
        const val PROGRESS_BAR_MAX_VALUE = 0.95f
        const val CHECK_INTERVAL_MS = 100L
        const val THUMBNAIL_PROGRESS_WEIGHT = 0.6f
        const val AUDIO_PROGRESS_WEIGHT = 0.4f
        const val WHILE_SUBSCRIBED_TIMEOUT_MS = 5000
        const val PRELOAD_DELAY_MS = 100
        const val MIN_PRELOAD_SECONDS = 30L
        
        val BAR_COUNTS_BY_DURATION = listOf(100, 300, 600)
    }
    
    object Progress {
        val STAGES = listOf(0.0f, 0.25f, 0.5f, 0.75f, 1.0f)
        val THRESHOLDS = listOf(0.3f, 0.7f, 0.8f, 0.9f)
        val CURVE_CONSTANTS = listOf(0.3f, 0.4f, 0.7f, 0.3f)
    }
    
    object Physics {
        const val SPRING_DAMPING_RATIO = 0.85f
        const val SPRING_STIFFNESS = 300f
    }
    
    object UI {
        const val CANVAS_SIZE_DP = 160
        const val RADIUS_FACTOR = 0.35f
        const val MIDDLE_RADIUS_FACTOR = 0.25f
        const val OFFSET_CALCULATION = 0.3
        const val ALPHA_FACTOR = 0.4f
        const val SMALL_RADIUS_FACTOR = 0.15f
        const val INITIAL_VALUE = 0.3f
        const val TARGET_VALUE = 0.8f
    }
}