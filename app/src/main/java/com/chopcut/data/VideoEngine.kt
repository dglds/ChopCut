package com.chopcut

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Range
import androidx.media3.common.Effect
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import timber.log.Timber


// --- Merged from CopyPipeline.kt ---


class CopyPipeline(
    private val context: Context,
    private val videoRepository: VideoRepository,
    private val dispatcherProvider: DispatcherProvider = DispatcherProvider
) {

    /**
     * Trim video without re-encoding (fast copy)
     * @param uri Source video URI
     * @param ranges Time ranges to extract (multiple ranges will be concatenated)
     * @return Flow<Float> Progress from 0.0 to 1.0
     */
    @OptIn(FlowPreview::class)
    fun trim(uri: Uri, ranges: List<TimeRange>): Flow<Result<File>> = flow {
        val timer = TimeTracker.start("trim")

        val outputFile = videoRepository.createTempFile(".mp4")

        try {
            var videoExtractor: MediaExtractor? = null
            var audioExtractor: MediaExtractor? = null
            var muxer: MediaMuxer? = null

            try {
                val metadata = videoRepository.getMetadata(uri)
                    ?: throw IllegalArgumentException("Unable to read video metadata")

                videoExtractor = MediaExtractor().apply { setDataSource(context, uri, null) }
                audioExtractor = if (metadata.hasAudio) {
                    MediaExtractor().apply { setDataSource(context, uri, null) }
                } else null

                val videoTrackIndex = findTrackIndex(videoExtractor, "video/")
                val audioTrackIndex = audioExtractor?.let { findTrackIndex(it, "audio/") } ?: -1

                if (videoTrackIndex < 0) {
                    throw IllegalArgumentException("No video track found")
                }

                val videoFormat = videoExtractor.getTrackFormat(videoTrackIndex)

                // Extract rotation directly from MediaFormat (more reliable)
                val rotationFromFormat = if (videoFormat.containsKey(MediaFormat.KEY_ROTATION)) {
                    videoFormat.getInteger(MediaFormat.KEY_ROTATION)
                } else {
                    // Fallback to metadata if not in format
                    metadata.rotation
                }


                // Create a clean output format with essential fields
                val outputVideoFormat = MediaFormat()

                // Copy essential format fields
                outputVideoFormat.setString(MediaFormat.KEY_MIME, videoFormat.getString(MediaFormat.KEY_MIME))
                outputVideoFormat.setInteger(MediaFormat.KEY_WIDTH, videoFormat.getInteger(MediaFormat.KEY_WIDTH))
                outputVideoFormat.setInteger(MediaFormat.KEY_HEIGHT, videoFormat.getInteger(MediaFormat.KEY_HEIGHT))
                outputVideoFormat.setLong(MediaFormat.KEY_DURATION, videoFormat.getLong(MediaFormat.KEY_DURATION))
                if (videoFormat.containsKey(MediaFormat.KEY_BIT_RATE)) {
                    outputVideoFormat.setInteger(MediaFormat.KEY_BIT_RATE, videoFormat.getInteger(MediaFormat.KEY_BIT_RATE))
                }
                if (videoFormat.containsKey(MediaFormat.KEY_FRAME_RATE)) {
                    outputVideoFormat.setInteger(MediaFormat.KEY_FRAME_RATE, videoFormat.getInteger(MediaFormat.KEY_FRAME_RATE))
                }
                if (videoFormat.containsKey(MediaFormat.KEY_COLOR_FORMAT)) {
                    outputVideoFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, videoFormat.getInteger(MediaFormat.KEY_COLOR_FORMAT))
                }

                // CRITICAL: Set rotation BEFORE adding to muxer
                outputVideoFormat.setInteger(MediaFormat.KEY_ROTATION, rotationFromFormat)

                var audioFormat: MediaFormat? = null
                var audioTrackOutputIndex = -1

                if (audioExtractor != null && audioTrackIndex >= 0 && metadata.hasAudio) {
                    audioFormat = audioExtractor.getTrackFormat(audioTrackIndex)
                }

                muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
                val videoTrackOutputIndex = muxer.addTrack(outputVideoFormat)
                if (audioFormat != null) {
                    audioTrackOutputIndex = muxer.addTrack(audioFormat)
                }

                muxer.start()

                var processedDurationUs = 0L

                ranges.forEachIndexed { index, range ->
                    val offsetUs = processedDurationUs
                    val startUs = range.startMs * 1000
                    val endUs = range.endMs * 1000


                    // Create fresh extractors for each range to avoid seek issues
                    val rangeVideoExtractor = MediaExtractor().apply { setDataSource(context, uri, null) }
                    val rangeVideoTrackIndex = findTrackIndex(rangeVideoExtractor, "video/")

                    // Process video track for this range
                    rangeVideoExtractor.selectTrack(rangeVideoTrackIndex)
                    rangeVideoExtractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
                    processTrack(rangeVideoExtractor, muxer, rangeVideoTrackIndex, videoTrackOutputIndex, startUs, endUs, offsetUs) { }
                    rangeVideoExtractor.release()

                    // Process audio track for this range (only if audio exists)
                    if (audioExtractor != null && audioTrackIndex >= 0 && audioTrackOutputIndex >= 0) {
                        val rangeAudioExtractor = MediaExtractor().apply { setDataSource(context, uri, null) }
                        val rangeAudioTrackIndex = findTrackIndex(rangeAudioExtractor, "audio/")

                        rangeAudioExtractor.selectTrack(rangeAudioTrackIndex)
                        rangeAudioExtractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
                        processTrack(rangeAudioExtractor, muxer, rangeAudioTrackIndex, audioTrackOutputIndex, startUs, endUs, offsetUs) { }
                        rangeAudioExtractor.release()
                    }

                    // Update offset for next range
                    processedDurationUs += (range.endMs - range.startMs) * 1000
                }

                // Stop muxer - ignore if already stopped
                try {
                    muxer.stop()
                } catch (e: Exception) {
                }
                emit(Result.success(outputFile))

            } finally {
                videoExtractor?.release()
                audioExtractor?.release()
                try {
                    muxer?.release()
                } catch (e: Exception) {
                }
            }
        } catch (e: Exception) {
            emit(Result.failure(e))
            if (outputFile.exists()) {
                outputFile.delete()
            }
        } finally {
            timer.end()
        }
    }.flowOn(dispatcherProvider.io)

    /**
     * Join/concatenate multiple videos without re-encoding
     * Note: All videos must have the same resolution and codec
     * @param uris List of video URIs to concatenate
     * @return Flow<Float> Progress from 0.0 to 1.0
     */
    @OptIn(FlowPreview::class)
    fun concat(uris: List<Uri>): Flow<Result<File>> = flow {
        val timer = TimeTracker.start("concat")
        if (uris.isEmpty()) {
            emit(Result.failure(IllegalArgumentException("No videos provided")))
            return@flow
        }


        val outputFile = videoRepository.createTempFile(".mp4")

        try {
            var muxer: MediaMuxer? = null

            try {
                // Get metadata from first video (reference)
                val firstMetadata = videoRepository.getMetadata(uris.first())
                    ?: throw IllegalArgumentException("Unable to read first video metadata")

                // Validate all videos have compatible formats
                uris.forEach { uri ->
                    val metadata = videoRepository.getMetadata(uri)
                    if (metadata?.width != firstMetadata.width ||
                        metadata?.height != firstMetadata.height) {
                        throw IllegalArgumentException("Videos must have the same resolution")
                    }
                }

                muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

                // Create extractors for all sources
                val sources = uris.map { uri ->
                    val extractor = MediaExtractor()
                    extractor.setDataSource(context, uri, null)

                    val videoTrackIndex = findTrackIndex(extractor, "video/")
                    val audioTrackIndex = findTrackIndex(extractor, "audio/")

                    val metadata = videoRepository.getMetadata(uri)!!

                    VideoSource(
                        extractor = extractor,
                        videoTrackIndex = videoTrackIndex,
                        audioTrackIndex = audioTrackIndex,
                        videoFormat = extractor.getTrackFormat(videoTrackIndex),
                        audioFormat = if (audioTrackIndex >= 0) extractor.getTrackFormat(audioTrackIndex) else null,
                        durationUs = metadata.durationUs,
                        hasAudio = metadata.hasAudio
                    )
                }

                // Use format from first video with rotation preserved
                val firstSource = sources.first()
                val outputVideoFormat = MediaFormat(firstSource.videoFormat)

                // Preserve rotation from first video
                if (firstMetadata.rotation != 0) {
                    outputVideoFormat.setInteger(MediaFormat.KEY_ROTATION, firstMetadata.rotation)
                }

                val videoTrackOutputIndex = muxer.addTrack(outputVideoFormat)
                val audioTrackOutputIndex = if (firstSource.audioFormat != null) {
                    muxer.addTrack(firstSource.audioFormat)
                } else -1

                muxer.start()

                // Calculate total duration
                val totalDurationUs = sources.sumOf { it.durationUs }
                var processedDurationUs = 0L

                // Process each source
                sources.forEachIndexed { index, source ->

                    // Copy video track
                    copyTrack(
                        extractor = source.extractor,
                        muxer = muxer!!,
                        trackIndex = source.videoTrackIndex,
                        outputTrackIndex = videoTrackOutputIndex,
                        onProgress = { progress ->
                            val sourceProgress = (progress * source.durationUs).toLong()
                            val totalProgress = ((processedDurationUs + sourceProgress).toFloat() / totalDurationUs.toFloat())
                            // Could emit progress here
                        }
                    )

                    // Copy audio track if exists
                    if (source.audioTrackIndex >= 0 && audioTrackOutputIndex >= 0 && source.hasAudio) {
                        copyTrack(
                            extractor = source.extractor,
                            muxer = muxer!!,
                            trackIndex = source.audioTrackIndex,
                            outputTrackIndex = audioTrackOutputIndex,
                            onProgress = { }
                        )
                    }

                    processedDurationUs += source.durationUs
                }

                muxer.stop()
                emit(Result.success(outputFile))

            } finally {
                muxer?.release()
            }
        } catch (e: Exception) {
            emit(Result.failure(e))
            if (outputFile.exists()) {
                outputFile.delete()
            }
        } finally {
            timer.end()
        }
    }.flowOn(dispatcherProvider.io)

    /**
     * Find track index by MIME type prefix
     */
    private fun findTrackIndex(extractor: MediaExtractor, mimeTypePrefix: String): Int {
        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME)
            if (mime?.startsWith(mimeTypePrefix) == true) {
                return i
            }
        }
        return -1
    }

    /**
     * Process a single track within a time range
     */
    private fun processTrack(
        extractor: MediaExtractor,
        muxer: MediaMuxer,
        trackIndex: Int,
        outputTrackIndex: Int,
        startUs: Long,
        endUs: Long,
        offsetUs: Long,
        onProgress: (Float) -> Unit
    ) {
        extractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
        val actualStartUs = extractor.sampleTime


        val buffer = MediaCodec.BufferInfo()
        val byteBuffer = java.nio.ByteBuffer.allocate(1024 * 1024)

        var firstSampleWritten = false
        var sampleCount = 0

        while (true) {
            val sampleSize = extractor.readSampleData(byteBuffer, 0)
            if (sampleSize < 0) {
                break
            }

            val sampleTime = extractor.sampleTime
            if (sampleTime == -1L) {
                break
            }

            if (sampleTime >= endUs) {
                break
            }

            if (sampleTime >= actualStartUs) {
                buffer.offset = 0
                buffer.size = sampleSize
                buffer.presentationTimeUs = (sampleTime - actualStartUs) + offsetUs
                buffer.flags = extractor.sampleFlags

                if (!firstSampleWritten) {
                    buffer.flags = buffer.flags or MediaCodec.BUFFER_FLAG_KEY_FRAME
                    firstSampleWritten = true
                }

                muxer.writeSampleData(outputTrackIndex, byteBuffer, buffer)
                sampleCount++
            }

            extractor.advance()
        }

        onProgress(1f)
    }

    /**
     * Copy an entire track
     */
    private fun copyTrack(
        extractor: MediaExtractor,
        muxer: MediaMuxer,
        trackIndex: Int,
        outputTrackIndex: Int,
        onProgress: (Float) -> Unit
    ) {
        extractor.unselectTrack(trackIndex)
        extractor.selectTrack(trackIndex)
        extractor.seekTo(0, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)

        val buffer = MediaCodec.BufferInfo()
        val byteBuffer = java.nio.ByteBuffer.allocate(1024 * 1024)

        val durationUs = extractor.getTrackFormat(trackIndex).getLong(MediaFormat.KEY_DURATION)
        var lastProgressUpdate = 0f

        while (true) {
            val sampleSize = extractor.readSampleData(byteBuffer, 0)
            if (sampleSize < 0) break

            val sampleTime = extractor.sampleTime
            if (sampleTime == -1L) break

            buffer.offset = 0
            buffer.size = sampleSize
            buffer.presentationTimeUs = sampleTime
            buffer.flags = extractor.sampleFlags

            muxer.writeSampleData(outputTrackIndex, byteBuffer, buffer)

            // Update progress
            val progress = if (durationUs > 0) {
                sampleTime.toFloat() / durationUs.toFloat()
            } else {
                0f
            }
            if (progress - lastProgressUpdate > 0.01f) {
                onProgress(progress.coerceIn(0f, 1f))
                lastProgressUpdate = progress
            }

            extractor.advance()
        }

        onProgress(1f)
    }

    private data class VideoSource(
        val extractor: MediaExtractor,
        val videoTrackIndex: Int,
        val audioTrackIndex: Int,
        val videoFormat: MediaFormat,
        val audioFormat: MediaFormat?,
        val durationUs: Long,
        val hasAudio: Boolean
    )
}

// --- Merged from TransformerPipeline.kt ---


class TransformerPipeline(
    private val context: Context,
    private val videoRepository: VideoRepository
) {
    /**
     * Trim video com múltiplos ranges usando Media3 Transformer
     *
     * Cada range é adicionado como um clip separado na composição
     */
    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    fun trim(uri: Uri, ranges: List<TimeRange>, aspectRatio: Float? = null, compressionLevel: CompressionLevel = CompressionLevel.ORIGINAL): Flow<TrimProgress> = callbackFlow {
        val outputFile = videoRepository.createTempFile(".mp4")

        Timber.d("TransformerPipeline: trim started - ${ranges.size} ranges")

        var isFinished = false
        var transformerRef: Transformer? = null
        val progressHolder = ProgressHolder()

        try {
            val sequenceBuilder = EditedMediaItemSequence.Builder()

            ranges.forEach { range ->
                val clippingConfig = MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(range.startMs)
                    .setEndPositionMs(range.endMs)
                    .build()

                val mediaItem = MediaItem.Builder()
                    .setUri(uri)
                    .setClippingConfiguration(clippingConfig)
                    .build()

                val videoEffects = mutableListOf<Effect>()
                if (aspectRatio != null) {
                    videoEffects.add(Presentation.createForAspectRatio(aspectRatio, Presentation.LAYOUT_SCALE_TO_FIT))
                }

                val editedItem = EditedMediaItem.Builder(mediaItem)
                    .setEffects(androidx.media3.transformer.Effects(emptyList(), videoEffects))
                    .build()

                sequenceBuilder.addItem(editedItem)

            }

            val sequence = sequenceBuilder.build()

            val composition = Composition.Builder(sequence)
                .build()

            val mainHandler = Handler(Looper.getMainLooper())

            // Polling de progresso a cada 250ms
            val progressRunnable = object : Runnable {
                override fun run() {
                    if (isFinished) return
                    val transformer = transformerRef ?: return
                    val state = transformer.getProgress(progressHolder)
                    if (state == Transformer.PROGRESS_STATE_AVAILABLE) {
                        trySend(TrimProgress.InProgress(progressHolder.progress))
                    }
                    mainHandler.postDelayed(this, 250)
                }
            }

            val transformerListener = object : Transformer.Listener {
                override fun onCompleted(composition: Composition, result: ExportResult) {
                    isFinished = true
                    mainHandler.removeCallbacks(progressRunnable)
                    Timber.d("TransformerPipeline: trim completed - ${outputFile.name} (${outputFile.length()} bytes)")
                    trySend(TrimProgress.Completed(outputFile))
                    channel.close()
                }

                override fun onError(
                    composition: Composition,
                    result: ExportResult,
                    exception: ExportException
                ) {
                    isFinished = true
                    mainHandler.removeCallbacks(progressRunnable)
                    Timber.e("TransformerPipeline: trim failed - ${exception.message}")
                    trySend(TrimProgress.Failed(exception))
                    channel.close()
                }
            }

            // Emitir progresso inicial
            trySend(TrimProgress.InProgress(0))

            mainHandler.post {
                try {
                    val transformerBuilder = Transformer.Builder(context)
                        .addListener(transformerListener)

                    // Aplicar compressão se selecionado
                    if (compressionLevel != CompressionLevel.ORIGINAL) {
                        val targetBitrate = when (compressionLevel) {
                            CompressionLevel.MEDIUM -> 3_000_000 // 3 Mbps
                            CompressionLevel.LOW -> 1_000_000    // 1 Mbps
                            else -> Format.NO_VALUE
                        }
                        
                        val encoderFactory = DefaultEncoderFactory.Builder(context)
                            .setRequestedVideoEncoderSettings(
                                VideoEncoderSettings.Builder()
                                    .setBitrate(targetBitrate)
                                    .build()
                            )
                            .build()
                            
                        transformerBuilder.setEncoderFactory(encoderFactory)
                    }

                    val transformer = transformerBuilder.build()

                    transformerRef = transformer
                    transformer.start(composition, outputFile.absolutePath)

                    // Iniciar polling de progresso
                    mainHandler.postDelayed(progressRunnable, 250)
                } catch (e: Exception) {
                    trySend(TrimProgress.Failed(e))
                    channel.close()
                }
            }

            awaitClose {
                isFinished = true
                mainHandler.removeCallbacks(progressRunnable)
                // Transformer deve ser cancelado na main thread
                mainHandler.post { transformerRef?.cancel() }
            }

        } catch (e: Exception) {
            trySend(TrimProgress.Failed(e))
            channel.close()

            if (outputFile.exists()) {
                outputFile.delete()
            }
        }
    }

    /**
     * Concatenar múltiplos vídeos usando Media3 Transformer
     *
     * Cada URI é adicionado como um clip separado
     */
    fun concat(uris: List<Uri>): Flow<Result<File>> = callbackFlow {
        val outputFile = videoRepository.createTempFile(".mp4")


        try {
            // Criar uma sequência com todos os vídeos
            val sequenceBuilder = EditedMediaItemSequence.Builder()

            uris.forEach { uri ->
                val mediaItem = MediaItem.fromUri(uri)
                val editedItem = EditedMediaItem.Builder(mediaItem).build()
                sequenceBuilder.addItem(editedItem)
            }

            val sequence = sequenceBuilder.build()

            // Criar composição
            val composition = Composition.Builder(sequence)
                .build()


            // CRÍTICO: Usar Handler da thread principal
            val mainHandler = Handler(Looper.getMainLooper())

            // Criar transformer com listener
            val transformerListener = object : Transformer.Listener {
                override fun onCompleted(composition: Composition, result: ExportResult) {
                    trySend(Result.success(outputFile))
                    channel.close()
                }

                override fun onError(
                    composition: Composition,
                    result: ExportResult,
                    exception: ExportException
                ) {
                    trySend(Result.failure(exception))
                    channel.close()
                }
            }

            // Criar e iniciar o Transformer na thread principal
            mainHandler.post {
                try {
                    val transformer = Transformer.Builder(context)
                        .addListener(transformerListener)
                        .build()

                    // Iniciar exportação
                    transformer.start(composition, outputFile.absolutePath)
                } catch (e: Exception) {
                    trySend(Result.failure(e))
                    channel.close()
                }
            }

            awaitClose {
                // Cleanup se necessário
            }

        } catch (e: Exception) {
            trySend(Result.failure(e))
            channel.close()

            if (outputFile.exists()) {
                outputFile.delete()
            }
        }
    }


}


// --- Merged from TrimProgress.kt ---


sealed class TrimProgress {
    data class InProgress(val percent: Int) : TrimProgress()  // 0-100
    data class Completed(val file: File) : TrimProgress()
    data class Failed(val error: Throwable) : TrimProgress()
}

// --- Merged from VideoRepository.kt ---


class VideoRepository(
    private val context: Context
) {

    private val contentResolver: ContentResolver = context.contentResolver

    /**
     * Extract metadata from a video file
     */
    suspend fun getMetadata(uri: Uri): VideoInfo? = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)

            val durationUs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?.times(1000) ?: 0
            val vw = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            val vh = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            
            val (width, height) = if (rotation == 90 || rotation == 270) {
                vh to vw
            } else {
                vw to vh
            }
            val bitrate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toLongOrNull() ?: 0

            // Check for audio track (reusing same retriever)
            val hasAudio = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) != null

            // Get file info
            val fileName = getFileName(uri)
            val mimeType = getContentMimeType(uri)
            val sizeBytes = getContentSize(uri)

            VideoInfo(
                uri = uri,
                fileName = fileName,
                mimeType = mimeType,
                durationUs = durationUs,
                width = width,
                height = height,
                rotation = rotation,
                bitrate = bitrate,
                frameRate = 30, // Default fallback
                videoCodec = null,
                audioCodec = null,
                hasAudio = hasAudio,
                sizeBytes = sizeBytes
            ).also {
            }
        } catch (e: Exception) {
            null
        } finally {
            try {
                retriever.release()
            } catch (e: Exception) {
            }
        }
    }



    /**
     * Create a temporary file for video processing
     */
    suspend fun createTempFile(extension: String = ".mp4"): File = withContext(Dispatchers.IO) {
        val tempDir = File(context.cacheDir, "video_processing")
        if (!tempDir.exists()) {
            tempDir.mkdirs()
        }

        val timestamp = System.currentTimeMillis()
        File(tempDir, "chopcut_$timestamp$extension").also {
        }
    }

    /**
     * Save processed video to gallery/storage
     * Tries to save to root "ChopCut" folder first, falls back to Movies/ChopCut
     */
    suspend fun saveToGallery(file: File, filename: String? = null): Uri = withContext(Dispatchers.IO) {
        val videoName = filename ?: "ChopCut_${System.currentTimeMillis()}.mp4"

        if (!file.exists() || file.length() == 0L) {
            throw IllegalStateException("Arquivo de origem não existe ou está vazio: ${file.absolutePath}")
        }

        // 1. Try to save to root /ChopCut folder (Legacy/Permissive)
        try {
            val root = Environment.getExternalStorageDirectory()
            val chopCutDir = File(root, "ChopCut")
            if (!chopCutDir.exists()) {
                if (!chopCutDir.mkdirs()) {
                    throw java.io.IOException("Cannot create directory")
                }
            }

            val destFile = File(chopCutDir, videoName)
            file.copyTo(destFile, overwrite = true)

            // Scan file to make it visible in gallery
            android.media.MediaScannerConnection.scanFile(
                context,
                arrayOf(destFile.absolutePath),
                arrayOf("video/mp4"),
                null
            )

            return@withContext Uri.fromFile(destFile)

        } catch (e: Exception) {
        }

        // 2. Fallback to MediaStore (Scoped Storage / Android 10+)
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }

        val contentValues = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, videoName)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.SIZE, file.length())

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.IS_PENDING, 1)
                put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/ChopCut")
            }
        }

        val uri = contentResolver.insert(collection, contentValues)
            ?: throw IllegalStateException("MediaStore insert retornou null para $videoName")

        val outputStream = contentResolver.openOutputStream(uri)
            ?: throw IllegalStateException("Não foi possível abrir OutputStream para $uri")

        outputStream.use { output ->
            file.inputStream().use { input ->
                input.copyTo(output)
            }
        }

        // Clear pending flag
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            contentValues.clear()
            contentValues.put(MediaStore.Video.Media.IS_PENDING, 0)
            contentResolver.update(uri, contentValues, null, null)
        }

        uri
    }

    /**
     * Delete a temporary file
     */
    suspend fun deleteTempFile(file: File): Boolean = withContext(Dispatchers.IO) {
        try {
            if (file.exists() && file.delete()) {
                true
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Get file name from URI
     */
    private fun getFileName(uri: Uri): String {
        var result: String? = null

        if (uri.scheme == "content") {
            result = contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst() && nameIndex >= 0) {
                    cursor.getString(nameIndex)
                } else {
                    null
                }
            }
        }

        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/')
            if (cut != -1 && cut != null) {
                result = result?.substring(cut + 1)
            }
        }

        return result ?: "unknown"
    }

    /**
     * Get content MIME type
     */
    private fun getContentMimeType(uri: Uri): String {
        return contentResolver.getType(uri) ?: "video/mp4"
    }

    /**
     * Get content size in bytes
     */
    private fun getContentSize(uri: Uri): Long {
        return try {
            contentResolver.openFileDescriptor(uri, "r")?.use { descriptor ->
                descriptor.statSize
            } ?: 0
        } catch (e: Exception) {
            0
        }
    }

    /**
     * Check if video has audio track
     */
    private fun hasAudioTrack(uri: Uri): Boolean {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            // Try to extract a metadata key that only exists if there's audio
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) != null ||
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER) != null
        } catch (e: Exception) {
            false
        } finally {
            try {
                retriever.release()
            } catch (e: Exception) {
            }
        }
    }

    /**
     * Extract codec info
     */
    private fun extractCodecInfo(uri: Uri): Pair<String?, String?> {
        // This is a simplified version. Real implementation would use MediaExtractor
        // to inspect the actual track codec information
        return Pair(null, null)
    }

    /**
     * Extract frame rate from metadata
     */
    private fun extractFrameRate(retriever: MediaMetadataRetriever): Int {
        return try {
            // Try to extract frame rate from metadata
            // Note: This is a simplified approach. Real implementation would
            // use MediaExtractor to get accurate frame rate
            30 // Default fallback
        } catch (e: Exception) {
            30 // Default fallback
        }
    }

    /**
     * Copy URI to internal storage for project persistence
     */
    suspend fun copyToInternalStorage(uri: Uri, projectId: String): File? = withContext(Dispatchers.IO) {
        val projectsDir = File(context.filesDir, "projects")
        if (!projectsDir.exists()) projectsDir.mkdirs()
        
        val projectDir = File(projectsDir, projectId)
        if (!projectDir.exists()) projectDir.mkdirs()
        
        val destFile = File(projectDir, "source.mp4")
        
        try {
            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }
            destFile
        } catch (e: Exception) {
            // Cleanup on failure
            if (destFile.exists()) destFile.delete()
            null
        }
    }

    /**
     * Copy URI to a temp file
     */
    suspend fun copyToTempFile(uri: Uri): File? = withContext(Dispatchers.IO) {
        val tempFile = createTempFile()

        try {
            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            }
            tempFile
        } catch (e: Exception) {
            tempFile.delete()
            null
        }
    }

    /**
     * Delete all videos saved by ChopCut (Movies/ChopCut, DCIM/ChopCut, and MediaStore entries)
     * Uses MediaStore to ensure all videos are deleted, including those in scoped storage
     */
    suspend fun deleteSavedVideos(): Int = withContext(Dispatchers.IO) {
        var deletedCount = 0

        try {
            // Strategy: Delete all video files (.mp4) in ChopCut directories via MediaStore
            // This works for all Android versions and handles scoped storage properly
            deletedCount = deleteAllVideosInChopCutDirectories()

        } catch (e: Exception) {
            throw e
        }

        deletedCount
    }

    /**
     * Delete all videos in ChopCut directories by:
     * 1. Finding all .mp4 files in ChopCut folders
     * 2. For each file, finding and deleting its MediaStore entry
     * 3. Trying to delete the physical file as fallback
     */
    private fun deleteAllVideosInChopCutDirectories(): Int {
        var deletedCount = 0
        val deletedFiles = mutableSetOf<String>()

        // Known directories where ChopCut saves videos
        val directories = listOf(
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "ChopCut"),
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "ChopCut"),
            File(Environment.getExternalStorageDirectory(), "ChopCut")
        )

        directories.forEach { dir ->

            if (dir.exists() && dir.isDirectory) {
                try {
                    val files = dir.listFiles()

                    files?.forEach { file ->
                        // Only process video files
                        if (file.isFile && file.extension.lowercase() == "mp4") {
                            var deleted = false

                            // CRÍTICO: Scan file first to ensure it's registered in MediaStore
                            // This is necessary for files that might not be in MediaStore yet
                            scanFileToMediaStore(file)

                            // Method 1: Try to delete via MediaStore (most reliable)
                            deleted = deleteViaMediaStore(file)

                            // Method 2: Fallback to direct deletion
                            if (!deleted) {
                                deleted = deletePhysicalFile(file)
                            }

                            if (deleted) {
                                deletedCount++
                                deletedFiles.add(file.name)
                            } else {
                            }
                        }
                    }

                    // Try to delete the directory if empty
                    if (dir.listFiles()?.isEmpty() == true) {
                        dir.delete()
                    }
                } catch (e: Exception) {
                }
            }
        }

        return deletedCount
    }

    /**
     * Scan a file to MediaStore to ensure it's registered before deletion
     * This is necessary for files that might not be indexed yet
     */
    private fun scanFileToMediaStore(file: File) {
        try {
            android.media.MediaScannerConnection.scanFile(
                context,
                arrayOf(file.absolutePath),
                arrayOf("video/mp4")
            ) { path, uri ->
                // Callback é assíncrono, não precisamos de delay
                if (uri != null) {
                } else {
                }
            }
        } catch (e: Exception) {
        }
    }

    /**
     * Delete a video file via MediaStore API (proper way for Android 10+)
     */
    private fun deleteViaMediaStore(file: File): Boolean {
        try {
            // Query MediaStore for this specific file
            val projection = arrayOf(MediaStore.Video.Media._ID)
            val selection = "${MediaStore.Video.Media.DATA} = ?"
            val selectionArgs = arrayOf(file.absolutePath)

            val cursor = contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                null
            )

            cursor?.use {
                if (it.moveToFirst()) {
                    val id = it.getLong(it.getColumnIndexOrThrow(MediaStore.Video.Media._ID))
                    val videoUri = android.net.Uri.withAppendedPath(
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                        id.toString()
                    )

                    // Delete via ContentResolver
                    val rowsDeleted = contentResolver.delete(videoUri, null, null)
                    return rowsDeleted > 0
                }
            }

            return false
        } catch (e: Exception) {
            return false
        }
    }

    /**
     * Delete a video file directly from filesystem (fallback)
     */
    private fun deletePhysicalFile(file: File): Boolean {
        return try {
            val deleted = file.delete()
            if (deleted) {
                // Notify MediaStore about the deletion
                android.media.MediaScannerConnection.scanFile(
                    context,
                    arrayOf(file.absolutePath),
                    null,
                    null
                )
            } else {
            }
            deleted
        } catch (e: Exception) {
            false
        }
    }
}
// --- Merged from CodecCapabilities.kt ---


class CodecCapabilities {

    private val codecList = MediaCodecList(MediaCodecList.REGULAR_CODECS)

    /**
     * Check if device has an encoder for the specified codec
     */
    fun hasEncoder(codec: VideoCodec): Boolean {
        return try {
            val codecs = codecList.codecInfos.filter { it.isEncoder }
            codecs.any { info ->
                info.supportedTypes.any { it.equals(codec.mimeType, ignoreCase = true) }
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Check if device has a decoder for the specified codec
     */
    fun hasDecoder(codec: VideoCodec): Boolean {
        return try {
            val codecs = codecList.codecInfos.filter { !it.isEncoder }
            codecs.any { info ->
                info.supportedTypes.any { it.equals(codec.mimeType, ignoreCase = true) }
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Select the best available encoder codec
     * Priority: H265 > H264 > VP9 > VP8 > AV1 > MPEG4
     */
    fun selectBestEncoder(): VideoCodec? {
        val priority = listOf(
            VideoCodec.H265,
            VideoCodec.H264,
            VideoCodec.AV1,
            VideoCodec.VP9,
            VideoCodec.VP8,
            VideoCodec.MPEG4
        )

        for (codec in priority) {
            if (hasEncoder(codec)) {
                return codec
            }
        }

        return null
    }

    /**
     * Get codec info for a specific video codec
     */
    fun getEncoderInfo(codec: VideoCodec): MediaCodecInfo? {
        return try {
            codecList.codecInfos.find { info ->
                info.isEncoder && info.supportedTypes.any {
                    it.equals(codec.mimeType, ignoreCase = true)
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Check if encoder supports a specific resolution and frame rate
     */
    fun supportsResolution(
        codec: VideoCodec,
        width: Int,
        height: Int,
        frameRate: Int
    ): Boolean {
        val info = getEncoderInfo(codec) ?: return false

        return try {
            val capabilities = info.getCapabilitiesForType(codec.mimeType)
            val videoCapabilities = capabilities.videoCapabilities ?: return false

            videoCapabilities.isSizeSupported(width, height) &&
            videoCapabilities.areSizeAndRateSupported(width, height, frameRate.toDouble())
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Get supported resolution range for codec
     */
    fun getSupportedResolutionRange(codec: VideoCodec): Range<Int>? {
        val info = getEncoderInfo(codec) ?: return null

        return try {
            val capabilities = info.getCapabilitiesForType(codec.mimeType)
            capabilities.videoCapabilities?.supportedWidths
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Get supported bitrate range for codec
     */
    fun getSupportedBitrateRange(codec: VideoCodec): Range<Int>? {
        val info = getEncoderInfo(codec) ?: return null

        return try {
            val capabilities = info.getCapabilitiesForType(codec.mimeType)
            capabilities.videoCapabilities?.bitrateRange
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Log all available codecs on the device (for debugging)
     */
    fun logAvailableCodecs() {
        codecList.codecInfos.forEach { info ->
            val type = if (info.isEncoder) "ENCODER" else "DECODER"
            info.supportedTypes.forEach { mimeType ->
            }
        }
    }
}
