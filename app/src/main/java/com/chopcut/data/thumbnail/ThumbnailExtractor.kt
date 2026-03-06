package com.chopcut.data.thumbnail

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import com.chopcut.config.constants.ThumbnailConstants
import com.chopcut.config.constants.FileFormatConstants
import com.chopcut.data.model.ExtractionStage
import com.chopcut.data.model.PerformanceEvent
import com.chopcut.data.model.ThumbnailExtractionProgress
import com.chopcut.data.model.ThumbnailFormat
import com.chopcut.data.model.ThumbnailSettings
import com.chopcut.data.model.ThumbnailQuality
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.yield

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
     * @param quality Quality of the extraction
     * @return Bitmap thumbnail or null if extraction fails
     */
    suspend fun extractAt(
        uri: Uri,
        positionMs: Long,
        width: Int,
        height: Int,
        quality: ThumbnailQuality = ThumbnailQuality.HIGH
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
            // For HIGH quality, we extract slightly larger and scale down with filtering (Anti-Aliasing)
            val extractWidth = if (quality == ThumbnailQuality.HIGH) (width * ThumbnailConstants.Quality.HIGH_QUALITY_EXTRACT_FACTOR).toInt() else width
            val extractHeight = if (quality == ThumbnailQuality.HIGH) (height * ThumbnailConstants.Quality.HIGH_QUALITY_EXTRACT_FACTOR).toInt() else height

            val rawFrame = retriever.getScaledFrameAtTime(
                positionMs * 1000, // Convert to microseconds
                MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                extractWidth,
                extractHeight
            )

            val frame = if (quality == ThumbnailQuality.HIGH && rawFrame != null && (rawFrame.width != width || rawFrame.height != height)) {
                // Criar bitmap de destino no tamanho exato solicitado
                val result = Bitmap.createBitmap(width, height, rawFrame.config ?: Bitmap.Config.ARGB_8888)
                val canvas = android.graphics.Canvas(result)
                
                // Calcular área de crop centralizado
                val srcWidth = rawFrame.width
                val srcHeight = rawFrame.height
                val srcAspect = srcWidth.toFloat() / srcHeight
                val dstAspect = width.toFloat() / height

                val (cropWidth, cropHeight) = if (srcAspect > dstAspect) {
                    // Source é mais largo que o destino (e.g. 16:9 -> 1:1)
                    (srcHeight * dstAspect).toInt() to srcHeight
                } else {
                    // Source é mais alto que o destino (e.g. 9:16 -> 1:1)
                    srcWidth to (srcWidth / dstAspect).toInt()
                }

                val cropX = (srcWidth - cropWidth) / 2
                val cropY = (srcHeight - cropHeight) / 2

                val srcRect = android.graphics.Rect(cropX, cropY, cropX + cropWidth, cropY + cropHeight)
                val dstRect = android.graphics.Rect(0, 0, width, height)

                // Desenhar com filtro bilinear para anti-aliasing
                val paint = android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG)
                canvas.drawBitmap(rawFrame, srcRect, dstRect, paint)
                
                rawFrame.recycle()
                result
            } else {
                rawFrame
            }

            frame?.also {
                Timber.d("Extracted thumbnail ($quality) at ${positionMs}ms: ${width}x${height}")
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to extract thumbnail ($quality) at ${positionMs}ms")
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
        width: Int = ThumbnailConstants.Dimensions.DEFAULT_WIDTH,
        height: Int = ThumbnailConstants.Dimensions.DEFAULT_HEIGHT,
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

                val startSave = System.currentTimeMillis()
                java.io.FileOutputStream(destFile).use { out ->
                    bitmap?.compress(Bitmap.CompressFormat.JPEG, ThumbnailConstants.Quality.JPEG_COMPRESSION_QUALITY, out)
                }
                val saveDuration = System.currentTimeMillis() - startSave
                PerformanceMonitor.log(PerformanceEvent(
                    stage = ExtractionStage.SAVE,
                    taskId = destFile.name,
                    durationMs = saveDuration
                ))
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
     * Extract all thumbnails from video with progress updates
     * @param uri Video URI
     * @param durationMs Video duration in milliseconds
     * @param settings Thumbnail extraction settings
     * @return Flow emitting progress updates
     */
    suspend fun extractAllWithProgress(
        uri: Uri,
        durationMs: Long,
        settings: ThumbnailSettings
    ): Flow<ThumbnailExtractionProgress> = withContext(Dispatchers.IO) {
        callbackFlow {
            val retriever = MediaMetadataRetriever()
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                    retriever.setDataSource(context, uri)
                } else {
                    @Suppress("DEPRECATION")
                    retriever.setDataSource(uri.toString())
                }

                // Calculate interval between thumbnails
                val intervalMs = 1000L / settings.thumbsPerSecond
                val totalThumbnails = (durationMs / intervalMs).toInt()

                Timber.d("Extracting $totalThumbnails thumbnails from ${durationMs}ms video")

                // Emit initial progress
                trySend(
                    ThumbnailExtractionProgress(
                        currentIndex = 0,
                        total = totalThumbnails,
                        currentPositionMs = 0
                    )
                )

                var bitmap: Bitmap? = null

                // Extract thumbnails at regular intervals
                for (i in 0 until totalThumbnails) {
                    val positionMs = (i * intervalMs).toLong()

                    try {
                        val quality = settings.extractionQuality
                        val extractWidth = if (quality == ThumbnailQuality.HIGH) (settings.dimensionPreset.width * ThumbnailConstants.Quality.HIGH_QUALITY_EXTRACT_FACTOR).toInt() else settings.dimensionPreset.width
                        val extractHeight = if (quality == ThumbnailQuality.HIGH) (settings.dimensionPreset.height * ThumbnailConstants.Quality.HIGH_QUALITY_EXTRACT_FACTOR).toInt() else settings.dimensionPreset.height

                        val rawFrame = retriever.getScaledFrameAtTime(
                            positionMs * 1000,
                            MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                            extractWidth,
                            extractHeight
                        )

                        bitmap = if (quality == ThumbnailQuality.HIGH && rawFrame != null && (rawFrame.width != settings.dimensionPreset.width || rawFrame.height != settings.dimensionPreset.height)) {
                            val scaled = Bitmap.createScaledBitmap(rawFrame, settings.dimensionPreset.width, settings.dimensionPreset.height, true)
                            if (scaled != rawFrame) rawFrame.recycle()
                            scaled
                        } else {
                            rawFrame
                        }

                        // Recycle previous bitmap if exists
                        bitmap?.let {
                            // Yield to avoid blocking
                            yield()

                            trySend(
                                ThumbnailExtractionProgress(
                                    currentIndex = i + 1,
                                    total = totalThumbnails,
                                    currentPositionMs = positionMs
                                )
                            )
                        }
                    } catch (e: Exception) {
                        Timber.w(e, "Failed to extract thumbnail at index $i (${positionMs}ms)")
                    }

                    // Recycle bitmap after use to free memory
                    bitmap?.recycle()
                    bitmap = null
                }

                // Emit completion
                trySend(
                    ThumbnailExtractionProgress(
                        currentIndex = totalThumbnails,
                        total = totalThumbnails,
                        currentPositionMs = durationMs,
                        isComplete = true
                    )
                )

                Timber.d("Extracted $totalThumbnails thumbnails")

            } catch (e: Exception) {
                Timber.e(e, "Failed to extract thumbnails with progress")
            } finally {
                try {
                    retriever.release()
                } catch (e: Exception) {
                    Timber.e(e, "Error releasing MediaMetadataRetriever")
                }
            }

            awaitClose {}
        }
    }

    /**
     * Extract all thumbnails from video to directory with progress updates
     * @param uri Video URI
     * @param durationMs Video duration in milliseconds
     * @param outputDir Output directory for thumbnails
     * @param settings Thumbnail extraction settings
     * @return Flow emitting progress updates
     */
    suspend fun extractAllToDirectory(
        uri: Uri,
        durationMs: Long,
        outputDir: File,
        settings: ThumbnailSettings
    ): Flow<ThumbnailExtractionProgress> = withContext(Dispatchers.IO) {
        callbackFlow {
            val retriever = MediaMetadataRetriever()
            try {
                // Create output directory if it doesn't exist
                if (!outputDir.exists()) {
                    outputDir.mkdirs()
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                    retriever.setDataSource(context, uri)
                } else {
                    @Suppress("DEPRECATION")
                    retriever.setDataSource(uri.toString())
                }

                // Calculate interval between thumbnails
                val intervalMs = 1000L / settings.thumbsPerSecond
                val totalThumbnails = (durationMs / intervalMs).toInt()

                Timber.d("Extracting $totalThumbnails thumbnails to ${outputDir.absolutePath}")

                // Emit initial progress
                trySend(
                    ThumbnailExtractionProgress(
                        currentIndex = 0,
                        total = totalThumbnails,
                        currentPositionMs = 0
                    )
                )

                // Determine file extension and compress format
                val (extension, compressFormat) = when (settings.format) {
                    ThumbnailFormat.JPEG -> FileFormatConstants.Extensions.JPG to Bitmap.CompressFormat.JPEG
                    ThumbnailFormat.PNG -> FileFormatConstants.Extensions.PNG to Bitmap.CompressFormat.PNG
                    ThumbnailFormat.WEBP -> FileFormatConstants.Extensions.WEBP to Bitmap.CompressFormat.WEBP
                }

                var bitmap: Bitmap? = null

                // Extract thumbnails at regular intervals
                for (i in 0 until totalThumbnails) {
                    val positionMs = (i * intervalMs).toLong()
                    val outputFile = File(outputDir, "thumb_${String.format("%05d", i)}$extension")

                    try {
                        val quality = settings.extractionQuality
                        val extractWidth = if (quality == ThumbnailQuality.HIGH) (settings.dimensionPreset.width * ThumbnailConstants.Quality.HIGH_QUALITY_EXTRACT_FACTOR).toInt() else settings.dimensionPreset.width
                        val extractHeight = if (quality == ThumbnailQuality.HIGH) (settings.dimensionPreset.height * ThumbnailConstants.Quality.HIGH_QUALITY_EXTRACT_FACTOR).toInt() else settings.dimensionPreset.height

                        val rawFrame = retriever.getScaledFrameAtTime(
                            positionMs * 1000,
                            MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                            extractWidth,
                            extractHeight
                        )

                        bitmap = if (quality == ThumbnailQuality.HIGH && rawFrame != null && (rawFrame.width != settings.dimensionPreset.width || rawFrame.height != settings.dimensionPreset.height)) {
                            val scaled = Bitmap.createScaledBitmap(rawFrame, settings.dimensionPreset.width, settings.dimensionPreset.height, true)
                            if (scaled != rawFrame) rawFrame.recycle()
                            scaled
                        } else {
                            rawFrame
                        }

                        // Save bitmap to file
                        bitmap?.let {
                            val startSave = System.currentTimeMillis()
                            java.io.FileOutputStream(outputFile).use { out ->
                                it.compress(compressFormat, settings.quality, out)
                            }
                            val saveDuration = System.currentTimeMillis() - startSave
                            PerformanceMonitor.log(PerformanceEvent(
                                stage = ExtractionStage.SAVE,
                                taskId = outputFile.name,
                                durationMs = saveDuration,
                                queueSize = totalThumbnails - (i + 1)
                            ))

                            Timber.d("Saved thumbnail ${i + 1}/$totalThumbnails to ${outputFile.name}")

                            // Yield to avoid blocking
                            yield()

                            trySend(
                                ThumbnailExtractionProgress(
                                    currentIndex = i + 1,
                                    total = totalThumbnails,
                                    currentPositionMs = positionMs
                                )
                            )
                        }
                    } catch (e: Exception) {
                        Timber.w(e, "Failed to extract/save thumbnail at index $i (${positionMs}ms)")
                    }

                    // Recycle bitmap after use to free memory
                    bitmap?.recycle()
                    bitmap = null
                }

                // Emit completion
                trySend(
                    ThumbnailExtractionProgress(
                        currentIndex = totalThumbnails,
                        total = totalThumbnails,
                        currentPositionMs = durationMs,
                        isComplete = true
                    )
                )

                Timber.d("Extracted $totalThumbnails thumbnails to ${outputDir.absolutePath}")

            } catch (e: Exception) {
                Timber.e(e, "Failed to extract thumbnails to directory")
            } finally {
                try {
                    retriever.release()
                } catch (e: Exception) {
                    Timber.e(e, "Error releasing MediaMetadataRetriever")
                }
            }

            awaitClose {}
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
        width: Int = 50,
        height: Int =50
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

                // For strip, we use HIGH quality by default for best visual
                val extractWidth = (width * ThumbnailConstants.Quality.HIGH_QUALITY_EXTRACT_FACTOR).toInt()
                val extractHeight = (height * ThumbnailConstants.Quality.HIGH_QUALITY_EXTRACT_FACTOR).toInt()

                val rawFrame = retriever.getScaledFrameAtTime(
                    positionMs * 1000,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                    extractWidth,
                    extractHeight
                )

                val frame = rawFrame?.let {
                    val scaled = Bitmap.createScaledBitmap(it, width, height, true)
                    if (scaled != it) it.recycle()
                    scaled
                }

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
        width: Int = 50,
        height: Int = 50
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
                val quality = ThumbnailQuality.HIGH // Default to high for positions
                
                // Extract frame at larger size to allow proper center crop
                val extractFactor = if (quality == ThumbnailQuality.HIGH) 2 else 1
                val sourceBitmap = retriever.getScaledFrameAtTime(
                    positionMs * 1000,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                    width * extractFactor,
                    height * extractFactor
                )

                val croppedBitmap = sourceBitmap?.let { src ->
                    // Create cropped thumbnail at exact dimensions (center crop)
                    val croppedThumb = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    val canvas = android.graphics.Canvas(croppedThumb)

                    // Calculate center crop
                    val srcWidth = src.width
                    val srcHeight = src.height
                    val srcAspect = srcWidth.toFloat() / srcHeight
                    val dstAspect = width.toFloat() / height

                    val (cropWidth, cropHeight) = if (srcAspect > dstAspect) {
                        // Source is wider: crop sides
                        ((srcHeight * dstAspect).toInt()) to srcHeight
                    } else {
                        // Source is taller: crop top/bottom
                        srcWidth to ((srcWidth / dstAspect).toInt())
                    }

                    val cropX = (srcWidth - cropWidth) / 2
                    val cropY = (srcHeight - cropHeight) / 2

                    val srcRect = android.graphics.Rect(cropX, cropY, cropX + cropWidth, cropY + cropHeight)
                    val dstRect = android.graphics.Rect(0, 0, width, height)

                    canvas.drawBitmap(src, srcRect, dstRect, null)

                    // Recycle source bitmap after cropping
                    src.recycle()

                    croppedThumb
                }

                if (croppedBitmap != null) {
                    Timber.d("Extracted and cropped thumbnail at ${positionMs}ms: ${width}x${height}")
                } else {
                    Timber.w("Failed to extract thumbnail at ${positionMs}ms")
                }

                croppedBitmap
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
        width: Int = 50,
        height: Int =50
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

                    // Default to normal quality for grid
                    val extractWidth = width
                    val extractHeight = height

                    val frame = retriever.getScaledFrameAtTime(
                        positionMs * 1000,
                        MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                        extractWidth,
                        extractHeight
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

    /**
     * Extract all thumbnails and stitch them into a single horizontal strip (filmstrip)
     * All thumbnails are placed side by side without any gaps
     * @param uri Video URI
     * @param durationMs Video duration in milliseconds
     * @param settings Thumbnail extraction settings
     * @param maxStripWidth Maximum width of the final strip (in pixels), 0 for unlimited
     * @return Flow emitting progress updates, completing with the final stitched Bitmap
     */
    suspend fun extractFilmstrip(
        uri: Uri,
        durationMs: Long,
        settings: ThumbnailSettings,
        maxStripWidth: Int = 0
    ): Flow<Pair<ThumbnailExtractionProgress, Bitmap?>> = withContext(Dispatchers.IO) {
        callbackFlow {
            val retriever = MediaMetadataRetriever()
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                    retriever.setDataSource(context, uri)
                } else {
                    @Suppress("DEPRECATION")
                    retriever.setDataSource(uri.toString())
                }

                val thumbWidth = settings.dimensionPreset.width
                val thumbHeight = settings.dimensionPreset.height

                // Calculate interval between thumbnails
                val intervalMs = ThumbnailConstants.Timing.INTERVAL_CALCULATION_DIVISOR / settings.thumbsPerSecond
                val totalThumbnails = (durationMs / intervalMs).toInt()

                Timber.d("Extracting filmstrip: $totalThumbnails thumbs from ${durationMs}ms video")

                // Emit initial progress
                trySend(
                    ThumbnailExtractionProgress(0, totalThumbnails, 0) to null
                )

                val bitmaps = mutableListOf<Bitmap>()

                // Extract all thumbnails with fixed size (cropped, not aspect-ratio preserved)
                for (i in 0 until totalThumbnails) {
                    val positionMs = (i * intervalMs).toLong()

                    try {
                        val quality = settings.extractionQuality
                        val extractFactor = if (quality == ThumbnailQuality.HIGH) 2 else 1
                        
                        // Extract frame at a larger size to ensure we have enough pixels
                        val sourceBitmap = retriever.getScaledFrameAtTime(
                            positionMs * 1000,
                            MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                            thumbWidth * extractFactor,
                            thumbHeight * extractFactor
                        )

                        sourceBitmap?.let { src ->
                            // Create cropped thumbnail at exact dimensions
                            val croppedThumb = Bitmap.createBitmap(thumbWidth, thumbHeight, Bitmap.Config.ARGB_8888)
                            val canvas = android.graphics.Canvas(croppedThumb)

                            // Calculate center crop
                            val srcWidth = src.width
                            val srcHeight = src.height
                            val srcAspect = srcWidth.toFloat() / srcHeight
                            val dstAspect = thumbWidth.toFloat() / thumbHeight

                            val (cropWidth, cropHeight) = if (srcAspect > dstAspect) {
                                // Source is wider: crop sides
                                ((srcHeight * dstAspect).toInt()) to srcHeight
                            } else {
                                // Source is taller: crop top/bottom
                                srcWidth to ((srcWidth / dstAspect).toInt())
                            }

                            val cropX = (srcWidth - cropWidth) / 2
                            val cropY = (srcHeight - cropHeight) / 2

                            val srcRect = android.graphics.Rect(cropX, cropY, cropX + cropWidth, cropY + cropHeight)
                            val dstRect = android.graphics.Rect(0, 0, thumbWidth, thumbHeight)

                            canvas.drawBitmap(src, srcRect, dstRect, null)

                            bitmaps.add(croppedThumb)
                            src.recycle()

                            // Emit progress
                            trySend(
                                ThumbnailExtractionProgress(i + 1, totalThumbnails, positionMs) to null
                            )

                            // Yield to avoid blocking
                            yield()
                        }
                    } catch (e: Exception) {
                        Timber.w(e, "Failed to extract thumbnail at index $i")
                    }
                }

                // Stitch all bitmaps into single horizontal strip
                if (bitmaps.isNotEmpty()) {
                    val actualWidth = thumbWidth * bitmaps.size

                    // Check if we need to limit strip width
                    val finalWidth = if (maxStripWidth > 0 && actualWidth > maxStripWidth) {
                        maxStripWidth
                    } else {
                        actualWidth
                    }

                    // Create the filmstrip bitmap
                    val filmstrip = Bitmap.createBitmap(finalWidth, thumbHeight, Bitmap.Config.ARGB_8888)
                    val canvas = android.graphics.Canvas(filmstrip)

                    // Draw each bitmap side by side (bitmaps are already exact size)
                    var xOffset = 0
                    bitmaps.forEach { thumb ->
                        canvas.drawBitmap(thumb, xOffset.toFloat(), 0f, null)
                        xOffset += thumbWidth

                        // Recycle bitmap after drawing
                        thumb.recycle()
                    }

                    Timber.d("Created filmstrip: ${finalWidth}x$thumbHeight from ${bitmaps.size} thumbs")

                    // Emit final result
                    trySend(
                        ThumbnailExtractionProgress(
                            totalThumbnails,
                            totalThumbnails,
                            durationMs,
                            isComplete = true
                        ) to filmstrip
                    )
                }

            } catch (e: Exception) {
                Timber.e(e, "Failed to extract filmstrip")
            } finally {
                try {
                    retriever.release()
                } catch (e: Exception) {
                    Timber.e(e, "Error releasing MediaMetadataRetriever")
                }
            }

            awaitClose {}
        }
    }

    /**
     * Extract all thumbnails and stitch them into a single horizontal strip, saving to file
     * @param uri Video URI
     * @param durationMs Video duration in milliseconds
     * @param outputFile Output file for the filmstrip image
     * @param settings Thumbnail extraction settings
     * @param maxStripWidth Maximum width of the final strip, 0 for unlimited
     * @return Flow emitting progress updates, completing with success boolean
     */
    suspend fun extractFilmstripToFile(
        uri: Uri,
        durationMs: Long,
        outputFile: File,
        settings: ThumbnailSettings,
        maxStripWidth: Int = 0
    ): Flow<ThumbnailExtractionProgress> = withContext(Dispatchers.IO) {
        callbackFlow {
            extractFilmstrip(uri, durationMs, settings, maxStripWidth).collect { (progress, filmstrip) ->

                // Send progress updates
                trySend(progress)

                // When complete, save to file
                if (progress.isComplete && filmstrip != null) {
                    try {
                        val (extension, compressFormat) = when (settings.format) {
                            ThumbnailFormat.JPEG -> ".jpg" to Bitmap.CompressFormat.JPEG
                            ThumbnailFormat.PNG -> ".png" to Bitmap.CompressFormat.PNG
                            ThumbnailFormat.WEBP -> ".webp" to Bitmap.CompressFormat.WEBP
                        }

                        // Ensure file has correct extension
                        val finalFile = if (!outputFile.name.endsWith(extension)) {
                            File(outputFile.parent, outputFile.nameWithoutExtension + extension)
                        } else {
                            outputFile
                        }

                        java.io.FileOutputStream(finalFile).use { out ->
                            filmstrip.compress(compressFormat, settings.quality, out)
                        }

                        Timber.d("Saved filmstrip to ${finalFile.absolutePath}")
                    } catch (e: Exception) {
                        Timber.e(e, "Failed to save filmstrip to file")
                    } finally {
                        filmstrip.recycle()
                    }
                }
            }

            awaitClose {}
        }
    }

    companion object {
        /**
         * Default thumbnail size for timeline
         */
        const val DEFAULT_THUMB_WIDTH = ThumbnailConstants.Dimensions.NORMAL_WIDTH
        const val DEFAULT_THUMB_HEIGHT = ThumbnailConstants.Dimensions.NORMAL_HEIGHT

        /**
         * Recommended number of thumbnails for timeline
         */
        const val RECOMMENDED_THUMB_COUNT = ThumbnailConstants.Quality.DEFAULT_THUMBS_PER_STRIP
    }

    /**
     * Size presets for timeline thumbnails
     */
    enum class ThumbnailSize(val width: Int, val height: Int) {
        COMPACT(ThumbnailConstants.Dimensions.COMPACT_WIDTH, ThumbnailConstants.Dimensions.COMPACT_HEIGHT),
        NORMAL(ThumbnailConstants.Dimensions.NORMAL_WIDTH, ThumbnailConstants.Dimensions.NORMAL_HEIGHT),
        DETAILED(ThumbnailConstants.Dimensions.DETAILED_WIDTH, ThumbnailConstants.Dimensions.DETAILED_HEIGHT)
    }
}
