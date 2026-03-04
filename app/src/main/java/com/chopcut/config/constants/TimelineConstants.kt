package com.chopcut.config.constants

import androidx.compose.ui.unit.dp

object TimelineConstants {
    object Dimensions {
        val TIMELINE_HEIGHT = 72.dp
        val WAVEFORM_HEIGHT = 48.dp
        val TRIM_HANDLE_SIZE = 24.dp
    }
    
    object Constraints {
        const val MIN_TRIM_DURATION_MS = 100L
        const val MIN_TRIM_GAP_RATIO = 0.05f
        const val MIN_RANGE_DURATION_MS = 500L
        const val POSITION_CALCULATION_HACK = 1000000
    }
    
    object Display {
        const val BAR_WIDTH_DP = 4f
        const val MIN_BAR_HEIGHT_DP = 2f
        const val STROKE_WIDTH_DP = 1f
        const val CORNER_RADIUS_DP = 2f
        const val TRACK_STROKE_WIDTH_DP = 4f
        const val SELECTION_STROKE_WIDTH_DP = 4f
        const val BORDER_WIDTH_DP = 2f
        const val BAR_X_OFFSET_DP_1 = 1f
        const val BAR_X_OFFSET_DP_2 = 2f
    }
}