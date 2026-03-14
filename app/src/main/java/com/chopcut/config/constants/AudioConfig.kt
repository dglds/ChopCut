package com.chopcut.config.constants

object AudioConfig {
    object Extraction {
        const val DEFAULT_SAMPLE_RATE = 44100
        const val DEFAULT_SAMPLES_PER_BAR = 1000
        const val NOISE_SAMPLE_SIZE = 50000
        const val DECODER_TIMEOUT_US = 100000L
        const val MAX_TRY_AGAIN = 200
    }
    
    object Quality {
        const val SILENCE_THRESHOLD = 0.03f
        const val VOICE_BOOST_FACTOR = 1.5f
        const val DYNAMIC_THRESHOLD_MULTIPLIER = 4f
    }
}
