package com.chopcut.data.thumbnail.v3

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

import android.content.Context

/**
 * A proof-of-concept (POC) extractor for Timeline v3 thumbnails.
 * This version focuses on memory efficiency by forcing Bitmap.Config.RGB_565.
 * Future iterations will explore direct MediaCodec integration for further performance gains.
 */
object ThumbnailExtractorV3 {

    /**
     * Extracts a video frame at a given timestamp and returns it as a memory-efficient Bitmap (RGB_565).
     *
     * @param context Android context to resolve URIs.
     * @param videoUri The URI of the video file.
     * @param timeUs The timestamp in microseconds.
     * @param width The desired width of the thumbnail.
     * @param height The desired height of the thumbnail.
     * @return A Bitmap in RGB_565 config, or null if extraction fails.
     */
    suspend fun extractFrame(
        context: Context,
        videoUri: Uri,
        timeUs: Long,
        width: Int,
        height: Int
    ): Bitmap? = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            // Use setDataSource with context and URI for proper content resolver handling
            retriever.setDataSource(context, videoUri)
            val rawFrame = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)

            rawFrame?.let {
                val optimizedBitmap = if (it.config != Bitmap.Config.RGB_565) {
                    // Scale and convert to RGB_565 for memory efficiency
                    val scaled = Bitmap.createScaledBitmap(it, width, height, true)
                    val result = scaled.copy(Bitmap.Config.RGB_565, false)
                    it.recycle() // Recycle the raw frame
                    if (scaled != result) scaled.recycle() // Recycle the scaled copy if copy created a new one
                    result
                } else {
                    // If already RGB_565, just scale if necessary
                    if (it.width != width || it.height != height) {
                        val scaled = Bitmap.createScaledBitmap(it, width, height, true)
                        it.recycle() // Recycle original if scaled
                        scaled
                    } else {
                        it
                    }
                }
                optimizedBitmap
            }
        } catch (e: Exception) {
            android.util.Log.e("ThumbnailExtractorV3", "Error extracting frame at $timeUs us from $videoUri", e)
            null
        } finally {
            try {
                retriever.release()
            } catch (e: Exception) {
                // Ignore release errors
            }
        }
    }
}
