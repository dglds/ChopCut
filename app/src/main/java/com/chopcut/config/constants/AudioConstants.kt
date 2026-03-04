package com.chopcut.config.constants

object AudioConstants {
    object Extraction {
        const val DEFAULT_SAMPLE_RATE = 44100
        const val NOISE_SAMPLE_SIZE = 50000
        const val DEFAULT_SAMPLES_PER_BAR = 1000
        const val BUFFER_SIZE = 1024 * 1024
        const val DECODER_TIMEOUT_US = 100000L
        const val MAX_TRY_AGAIN = 200
        const val MIN_BAR_COUNT = 10
        const val MAX_BAR_COUNT = 1000
    }
    
    object Quality {
        const val SILENCE_THRESHOLD = 0.03f
        const val VOICE_BOOST_FACTOR = 1.5f
        const val DEFAULT_THRESHOLD = 0.05f
        const val DYNAMIC_THRESHOLD_MULTIPLIER = 4f
        const val SILENCE_HEIGHT = 0.15f
    }
    
    object Waveform {
        const val DEFAULT_SAMPLING_RATE = 100
        const val DEFAULT_MIN_THRESHOLD = 0.05f
        const val DEFAULT_MAX_THRESHOLD = 0.2f
        const val DEFAULT_TARGET_BAR_COUNT = 400
        const val MINIMAL_BARS_PER_SEC = 2f
        const val LOW_BARS_PER_SEC = 5f
        const val MEDIUM_BARS_PER_SEC = 10f
        const val HIGH_BARS_PER_SEC = 15f
        const val DP_PER_BAR = 4
        
        val TARGET_SAMPLE_RATES = listOf(3, 5, 10, 15)
        val BAR_COUNT_RANGE = 10..1000
    }
    
    object Display {
        const val BAR_WIDTH_DP = 4f
        const val HEIGHT_SCALE = 0.8f
        const val SIMPLIFIED_HEIGHT_SCALE = 0.4f
        const val DEFAULT_STROKE_WIDTH_DP = 1.5f
        const val DEFAULT_STROKE_COLOR = 0xFF00D9FF
        const val DEFAULT_HEIGHT_SCALE = 0.8f
        const val MIN_BAR_HEIGHT_DP = 2f
        const val CORNER_RADIUS_DP = 2f
    }
    
    object ConfigPanel {
        const val HEIGHT_SCALE_RANGE_MIN = 0.1f
        const val HEIGHT_SCALE_RANGE_MAX = 3.0f
        const val STROKE_WIDTH_RANGE_MIN = 0.5f
        const val STROKE_WIDTH_RANGE_MAX = 5.0f
        const val BUTTON_SPACING_DP = 4
    }
}