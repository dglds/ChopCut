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
import com.chopcut.data.model.TimeRange
import com.chopcut.data.repository.VideoRepository
import com.chopcut.util.logging.ActivityLogger
import com.chopcut.util.logging.AppActivity
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
    fun trim(uri: Uri, ranges: List<TimeRange>): Flow<TrimProgress> = callbackFlow {
        val outputFile = videoRepository.createTempFile(".mp4")

        ActivityLogger.started(AppActivity.Trim, "ranges" to ranges.size)

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

                val editedItem = EditedMediaItem.Builder(mediaItem)
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
                    ActivityLogger.finished(AppActivity.Trim, "arquivo" to outputFile.name, "tamanho" to outputFile.length())
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
                    ActivityLogger.failed(AppActivity.Trim, "motivo" to exception.message)
                    trySend(TrimProgress.Failed(exception))
                    channel.close()
                }
            }

            // Emitir progresso inicial
            trySend(TrimProgress.InProgress(0))

            mainHandler.post {
                try {
                    val transformer = Transformer.Builder(context)
                        .addListener(transformerListener)
                        .build()

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

