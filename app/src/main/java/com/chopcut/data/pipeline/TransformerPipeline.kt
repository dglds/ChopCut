package com.chopcut.data.pipeline

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.common.Effect
import androidx.media3.effect.Presentation
import androidx.media3.common.Format
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.VideoEncoderSettings
import com.chopcut.data.model.TimeRange
import com.chopcut.ui.state.CompressionLevel
import com.chopcut.data.repository.VideoRepository
import com.chopcut.data.audio.model.AudioFormat
import com.chopcut.util.TimeTracker
import timber.log.Timber
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.io.File

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

    /**
     * Extract audio track from video using Media3 Transformer.
     * Removes video track and keeps only audio.
     *
     * @param uri Source video URI
     * @param format Output audio format (default: AAC)
     * @return Flow<Result<File>> Result with extracted audio file
     */
    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    fun extractAudio(
        uri: Uri,
        format: AudioFormat = AudioFormat.AAC
    ): Flow<Result<File>> = callbackFlow {
        val outputFile = videoRepository.createTempFile(format.extension)

        Timber.d("TransformerPipeline: extractAudio started - format=${format.name}")
        val timer = TimeTracker.start("audio_export")

        try {
            val mediaItem = MediaItem.fromUri(uri)

            // Create transformer that removes video
            val transformerListener = object : Transformer.Listener {
                override fun onCompleted(composition: Composition, result: ExportResult) {
                    val elapsedMs = timer.end()
                    Timber.d("TransformerPipeline: extractAudio completed - ${outputFile.name} (${elapsedMs}ms, ${outputFile.length()} bytes)")
                    trySend(Result.success(outputFile))
                    channel.close()
                }

                override fun onError(
                    composition: Composition,
                    result: ExportResult,
                    exception: ExportException
                ) {
                    val elapsedMs = timer.end()
                    Timber.e("TransformerPipeline: extractAudio failed - ${exception.message} (${elapsedMs}ms)")
                    trySend(Result.failure(exception))
                    channel.close()
                }
            }

            val mainHandler = Handler(Looper.getMainLooper())

            mainHandler.post {
                try {
                    val transformer = Transformer.Builder(context)
                        .addListener(transformerListener)
                        .setRemoveVideo(true)  // Remove video, keep audio
                        .setAudioMimeType(format.mimeType)
                        .build()

                    transformer.start(mediaItem, outputFile.absolutePath)
                } catch (e: Exception) {
                    timer.end()
                    Timber.e("TransformerPipeline: extractAudio exception - ${e.message}")
                    trySend(Result.failure(e))
                    channel.close()
                }
            }

            awaitClose {
                // Transformer cleanup if needed
            }

        } catch (e: Exception) {
            timer.end()
            Timber.e("TransformerPipeline: extractAudio catch - ${e.message}")
            trySend(Result.failure(e))
            channel.close()

            if (outputFile.exists()) {
                outputFile.delete()
            }
        }
    }
}

