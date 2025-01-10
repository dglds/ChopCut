package com.chopcut.data.model

import android.util.Size

/**
 * Video size wrapper with helper functions
 */
data class VideoSize(
    val width: Int,
    val height: Int
) {
    init {
        require(width > 0) { "Width must be positive" }
        require(height > 0) { "Height must be positive" }
    }

    val aspectRatio: Float get() = width.toFloat() / height.toFloat()

    val isPortrait: Boolean get() = height > width
    val isLandscape: Boolean get() = width > height
    val isSquare: Boolean get() = width == height

    fun toAndroidSize(): Size = Size(width, height)

    /**
     * Scale to fit within bounds while preserving aspect ratio
     */
    fun scaleToFit(maxWidth: Int, maxHeight: Int): VideoSize {
        val widthRatio = maxWidth.toFloat() / width
        val heightRatio = maxHeight.toFloat() / height
        val scale = minOf(widthRatio, heightRatio)

        return VideoSize(
            width = (width * scale).toInt(),
            height = (height * scale).toInt()
        )
    }

    /**
     * Scale to fill bounds while preserving aspect ratio (may crop)
     */
    fun scaleToFill(minWidth: Int, minHeight: Int): VideoSize {
        val widthRatio = minWidth.toFloat() / width
        val heightRatio = minHeight.toFloat() / height
        val scale = maxOf(widthRatio, heightRatio)

        return VideoSize(
            width = (width * scale).toInt(),
            height = (height * scale).toInt()
        )
    }

    /**
     * Rotate dimensions
     */
    fun rotate(): VideoSize = VideoSize(width = height, height = width)

    companion object {
        fun from(size: Size): VideoSize {
            return VideoSize(size.width, size.height)
        }
    }
}
