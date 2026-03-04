package com.chopcut.config.constants

object UIConstants {
    object Spacing {
        const val XXS_DP = 4
        const val XS_DP = 8
        const val SM_DP = 12
        const val MD_DP = 16
        const val LG_DP = 24
        const val XL_DP = 32
        const val XXL_DP = 48
    }
    
    object ComponentSizes {
        const val TOUCH_TARGET_DP = 48
        const val BUTTON_HEIGHT_DP = 48
        const val INPUT_HEIGHT_DP = 48
        const val FAB_SIZE_DP = 56
        const val SMALL_FAB_SIZE_DP = 40
        const val CARD_CORNER_RADIUS_DP = 16
        const val ASPECT_RATIO_NUMERATOR = 16
        const val ASPECT_RATIO_DENOMINATOR = 9
        const val DURATION_PADDING_DP = 8
        const val DURATION_CORNER_RADIUS_DP = 4
        const val DURATION_PADDING_TOP_DP = 6
        const val DURATION_PADDING_BOTTOM_DP = 2
        const val ICON_PADDING_DP = 16
        const val ICON_SIZE_DP = 48
        const val CARD_CONTENT_PADDING_DP = 12
    }
    
    object Layout {
        const val TOP_WEIGHT = 0.6f
        const val BOTTOM_WEIGHT = 0.4f
    }
    
    object Colors {
        const val COLOR_666666 = 0xFF666666.toInt()
        const val COLOR_808080 = 0xFF808080.toInt()
    }
}

object ConstantsIndex {
    val Thumbnail = ThumbnailConstants
    val Audio = AudioConstants
    val Timeline = TimelineConstants
    val Cache = CacheConstants
    val Performance = PerformanceConstants
    val Quality = QualityConstants
    val FileFormat = FileFormatConstants
    val Animation = AnimationConstants
    val UI = UIConstants
}