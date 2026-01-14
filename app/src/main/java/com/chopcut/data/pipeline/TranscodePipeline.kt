package com.chopcut.data.pipeline

import android.content.Context
import android.net.Uri
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.effect.ScaleAndRotateTransformation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import com.chopcut.data.model.ExportConfig
import com.chopcut.data.model.Transform
import com.chopcut.data.repository.VideoRepository
import com.chopcut.util.DispatcherProvider
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File

class TranscodePipeline(
    private val context: Context,
    private val videoRepository: VideoRepository,
    private val dispatcherProvider: DispatcherProvider = DispatcherProvider
) {

    suspend fun process(
        uri: Uri,
        transform: Transform = Transform.IDENTITY,
        config: ExportConfig
    ): Flow<Result<File>> = callbackFlow {
        val outputFile = withContext(dispatcherProvider.io) {
            videoRepository.createTempFile(".mp4")
        }

        Timber.d("Starting Media3 Transformer export")
        Timber.d("Input URI: $uri")
        
        // Check if file exists if it's a file URI
        if (uri.scheme == "file") {
            val file = File(uri.path!!)
            Timber.d("Input file exists: ${file.exists()}, Size: ${file.length()}")
            if (!file.exists()) {
                trySend(Result.failure(java.io.FileNotFoundException("Input file not found: $uri")))
                close()
                return@callbackFlow
            }
        }

        Timber.d("Output File: ${outputFile.absolutePath}")
        Timber.d("Transform: $transform, Config: $config")

        // 1. Build Effects
        val effects = mutableListOf<Effect>()
        
        if (transform.hasRotation() || transform.hasScale()) {
            val scaleAndRotate = ScaleAndRotateTransformation.Builder()
                .setRotationDegrees(transform.rotation)
                .setScale(transform.scaleX, transform.scaleY)
                .build()
            effects.add(scaleAndRotate)
        }
        
        // TODO: Add crop effect if needed (Crop transformation)

        // 2. Build Media Item with Effects
        val mediaItem = MediaItem.fromUri(uri)
        val editedMediaItem = EditedMediaItem.Builder(mediaItem)
            .setEffects(androidx.media3.transformer.Effects(
                /* audioProcessors = */ emptyList(),
                /* videoEffects = */ effects
            ))
            .build()

        // 3. Configure and Start Transformer on Main Thread (requires Looper)
        withContext(dispatcherProvider.main) {
            val transformer = Transformer.Builder(context)
                .setVideoMimeType(MimeTypes.VIDEO_H264) // Force H.264 for compatibility
                // .setAudioMimeType(MimeTypes.AUDIO_AAC) // Default
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        Timber.d("Transformer export completed successfully")
                        trySend(Result.success(outputFile))
                        close()
                    }

                                    override fun onError(
                                        composition: Composition,
                                        exportResult: ExportResult,
                                        exportException: ExportException
                                    ) {
                                        Timber.e(exportException, "Transformer export failed. Code: ${exportException.errorCode}")
                                        trySend(Result.failure(exportException))
                                        close()
                                    }
                                })
                                .build()
                                // 4. Start Export
            transformer.start(editedMediaItem, outputFile.absolutePath)
        }

        awaitClose {
            // Cancellation logic if needed
            // transformer.cancel() // Transformer doesn't expose public cancel easily in this scope context without keeping ref
        }
    }
}