package com.chopcut.data.thumbnail

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Extracts thumbnails from video for timeline preview
 */
class ThumbnailExtractor(
    private val context: Context
) {

    /**
     * Extract a single thumbnail from specific position
     * @param uri Video URI
     * @param positionMs Position in milliseconds
     * @param width Target thumbnail width
     * @param height Target thumbnail height
     * @return Bitmap thumbnail or null if extraction fails
     */
    suspend fun extractAt(
        uri: Uri,
        positionMs: Long,
        width: Int,
        height: Int
    ): Bitmap? = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                retriever.setDataSource(context, uri)
            } else {
                @Suppress("DEPRECATION")
                retriever.setDataSource(uri.toString())
            }

            // Get frame at position
            val frame = retriever.getScaledFrameAtTime(
                positionMs * 1000, // Convert to microseconds
                MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                width,
                height
            )

            frame?.also {
                Timber.d("Extracted thumbnail at ${positionMs}ms: ${width}x${height}")
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to extract thumbnail at ${positionMs}ms")
            null
        } finally {
            try {
                retriever.release()
            } catch (e: Exception) {
                Timber.e(e, "Error releasing MediaMetadataRetriever")
            }
        }
    }

    /**
     * Extract a thumbnail and save it to a file
     */
    suspend fun extractToFile(
        uri: Uri,
        destFile: java.io.File,
        width: Int = 320,
        height: Int = 180,
        rotation: Int = 0
    ): Boolean = withContext(Dispatchers.IO) {
        var bitmap = extractAt(uri, 0, width, height) // Extract at start (0ms)
        
        if (bitmap != null) {
            try {
                // Apply rotation if needed
                if (rotation % 360 != 0) {
                    val matrix = android.graphics.Matrix()
                    matrix.postRotate(rotation.toFloat())
                    val rotatedBitmap = Bitmap.createBitmap(
                        bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true
                    )
                    if (rotatedBitmap != bitmap) {
                        bitmap.recycle() // Release original if rotated is different
                        bitmap = rotatedBitmap
                    }
                }

                java.io.FileOutputStream(destFile).use { out ->
                    bitmap?.compress(Bitmap.CompressFormat.JPEG, 80, out)
                }
                Timber.d("Thumbnail saved to ${destFile.absolutePath} (rot: $rotation)")
                true
            } catch (e: Exception) {
                Timber.e(e, "Failed to save thumbnail to file")
                false
            } finally {
                bitmap?.recycle()
            }
        } else {
            false
        }
    }

    /**
     * Extract a strip of thumbnails evenly distributed across video duration
     * @param uri Video URI
     * @param count Number of thumbnails to extract
     * @param width Thumbnail width
     * @param height Thumbnail height
     * @return List of thumbnails in order
     */
    suspend fun extractStrip(
        uri: Uri,
        count: Int,
        width: Int = 160,
        height: Int =80
    ): List<Bitmap> = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        val thumbnails = mutableListOf<Bitmap>()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                retriever.setDataSource(context, uri)
            } else {
                @Suppress("DEPRECATION")
                retriever.setDataSource(uri.toString())
            }

            // Get video duration
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L

            if (durationMs <= 0) {
                Timber.w("Invalid video duration: $durationMs")
                return@withContext emptyList()
            }

            // Calculate interval between thumbnails
            val intervalMs = durationMs / (count + 1)

            Timber.d("Extracting $count thumbnails from ${durationMs}ms video (interval: ${intervalMs}ms)")

            // Extract thumbnails at regular intervals
            for (i in 1..count) {
                val positionMs = i * intervalMs

                val frame = retriever.getScaledFrameAtTime(
                    positionMs * 1000, // Convert to microseconds
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                    width,
                    height
                )

                frame?.let { thumbnails.add(it) }

                if (frame == null) {
                    Timber.w("Failed to extract thumbnail at index $i (${positionMs}ms)")
                }
            }

            Timber.d("Extracted ${thumbnails.size}/${count} thumbnails")

        } catch (e: Exception) {
            Timber.e(e, "Failed to extract thumbnail strip")
        } finally {
            try {
                retriever.release()
            } catch (e: Exception) {
                Timber.e(e, "Error releasing MediaMetadataRetriever")
            }
        }

        thumbnails
    }

    /**
     * Extract multiple thumbnails at specific positions
     * @param uri Video URI
     * @param positionsMs List of positions in milliseconds
     * @param width Thumbnail width
     * @param height Thumbnail height
     * @return List of thumbnails matching the input positions
     */
    suspend fun extractAtPositions(
        uri: Uri,
        positionsMs: List<Long>,
        width: Int = 160,
        height: Int = 90
    ): List<Bitmap?> = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                retriever.setDataSource(context, uri)
            } else {
                @Suppress("DEPRECATION")
                retriever.setDataSource(uri.toString())
            }

            val thumbnails = positionsMs.map { positionMs ->
                val frame = retriever.getScaledFrameAtTime(
                    positionMs * 1000,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                    width,
                    height
                )

                if (frame != null) {
                    Timber.d("Extracted thumbnail at ${positionMs}ms")
                } else {
                    Timber.w("Failed to extract thumbnail at ${positionMs}ms")
                }

                frame
            }

            thumbnails

        } catch (e: Exception) {
            Timber.e(e, "Failed to extract thumbnails")
            emptyList()
        } finally {
            try {
                retriever.release()
            } catch (e: Exception) {
                Timber.e(e, "Error releasing MediaMetadataRetriever")
            }
        }
    }

    /**
     * Extract a grid of thumbnails (for timeline view)
     * @param uri Video URI
     * @param columns Number of columns in the grid
     * @param rows Number of rows in the grid
     * @param width Thumbnail width
     * @param height Thumbnail height
     * @return 2D list of thumbnails [row][col]
     */
    suspend fun extractGrid(
        uri: Uri,
        columns: Int,
        rows: Int,
        width: Int = 160,
        height: Int = 90
    ): List<List<Bitmap?>> = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                retriever.setDataSource(context, uri)
            } else {
                @Suppress("DEPRECATION")
                retriever.setDataSource(uri.toString())
            }

            // Get video duration
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L

            if (durationMs <= 0) {
                return@withContext emptyList()
            }

            val intervalMs = durationMs / (columns * rows + 1)

            Timber.d("Extracting ${columns}x${rows} thumbnail grid")

            val grid = mutableListOf<List<Bitmap?>>()

            // Extract thumbnails row by row
            for (row in 0 until rows) {
                val rowThumbnails = mutableListOf<Bitmap?>()

                for (col in 0 until columns) {
                    val positionMs = ((row * columns + col + 1) * intervalMs).toLong()

                    val frame = retriever.getScaledFrameAtTime(
                        positionMs * 1000,
                        MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                        width,
                        height
                    )

                    rowThumbnails.add(frame)
                }

                grid.add(rowThumbnails)
            }

            grid

        } catch (e: Exception) {
            Timber.e(e, "Failed to extract thumbnail grid")
            emptyList()
        } finally {
            try {
                retriever.release()
            } catch (e: Exception) {
                Timber.e(e, "Error releasing MediaMetadataRetriever")
            }
        }
    }

    companion object {
        /**
         * Default thumbnail size for timeline
         */
        const val DEFAULT_THUMB_WIDTH = 160
        const val DEFAULT_THUMB_HEIGHT = 90

        /**
         * Recommended number of thumbnails for timeline
         */
        const val RECOMMENDED_THUMB_COUNT = 10
    }
}
