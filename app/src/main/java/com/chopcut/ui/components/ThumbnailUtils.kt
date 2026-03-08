package com.chopcut.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ThumbnailUtils {
    private val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
    private val cacheSize = maxMemory / 8 // Use 1/8th of available memory
    private val cache = object : LruCache<String, Bitmap>(cacheSize) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int {
            return bitmap.byteCount / 1024
        }
    }

    suspend fun getThumbnail(context: Context, videoUri: Uri, timeMs: Long): Bitmap? {
        val key = "${videoUri}_$timeMs"
        cache.get(key)?.let { return it }

        return withContext(Dispatchers.IO) {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, videoUri)
                // Retrieve frame at timeMs. 
                // OPTION_CLOSEST_SYNC is faster but less accurate. 
                // OPTION_CLOSEST is more accurate but slower.
                // For a timeline strip, fast is better, but let's try CLOSEST first for quality.
                // Actually, for scrolling performance, we need to be careful.
                // Let's use getScaledFrameAtTime if API 27+ for better performance on large 4k videos
                
                // Using a fixed height for the thumbnail strips usually looks best.
                // Assuming timeline height is around 100dp.
                
                val bitmap = retriever.getFrameAtTime(timeMs * 1000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                
                bitmap?.let {
                    // Create a scaled version to save memory if it's huge
                    val targetHeight = 120 // slightly larger than 100dp
                    val ratio = it.width.toFloat() / it.height.toFloat()
                    val targetWidth = (targetHeight * ratio).toInt()
                    
                    val scaled = Bitmap.createScaledBitmap(it, targetWidth, targetHeight, true)
                    if (scaled != it) {
                        it.recycle()
                    }
                    cache.put(key, scaled)
                    scaled
                }
            } catch (e: Exception) {
                null
            } finally {
                retriever.release()
            }
        }
    }
}
