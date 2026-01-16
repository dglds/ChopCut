package com.chopcut.data.model

import android.graphics.RectF

/**
 * Transform configuration for video processing
 */
data class Transform(
    val rotation: Float = 0f,        // Rotation in degrees
    val scaleX: Float = 1f,          // Horizontal scale
    val scaleY: Float = 1f,          // Vertical scale
    val cropRect: RectF? = null,     // Crop region (normalized 0-1)
    val translationX: Float = 0f,    // X translation (normalized)
    val translationY: Float = 0f,    // Y translation (normalized)
    val volume: Float = 1.0f,        // Audio volume multiplier
    val filter: FilterType = FilterType.NONE, // Video filter
    val filterIntensity: Float = 1.0f, // Filter intensity
    val fadeInMs: Long = 0L,         // Audio Fade In duration in ms
    val fadeOutMs: Long = 0L         // Audio Fade Out duration in ms
) {
    companion object {
        val IDENTITY = Transform()
    }

    fun hasCrop(): Boolean = cropRect != null

    fun hasRotation(): Boolean = rotation != 0f

    fun hasScale(): Boolean = scaleX != 1f || scaleY != 1f

    fun hasTransform(): Boolean = hasRotation() || hasScale() || hasCrop()
}
