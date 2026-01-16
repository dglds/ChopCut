package com.chopcut.data.pipeline

import android.content.Context
import android.net.Uri
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.effect.RgbAdjustment
import androidx.media3.effect.RgbFilter
import androidx.media3.effect.ScaleAndRotateTransformation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import com.chopcut.data.audio.processor.FadeAudioProcessor
import com.chopcut.data.audio.processor.VolumeAudioProcessor
import com.chopcut.data.model.ExportConfig
import com.chopcut.data.model.FilterType
import com.chopcut.data.model.TimeRange
import com.chopcut.data.model.Transform
import com.chopcut.data.repository.VideoRepository
import com.chopcut.util.DispatcherProvider
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import com.chopcut.data.player.EffectFactory

class TranscodePipeline(
    private val context: Context,
    private val videoRepository: VideoRepository,
    private val dispatcherProvider: DispatcherProvider = DispatcherProvider
) {

    suspend fun process(
        uri: Uri,
        transform: Transform = Transform.IDENTITY,
        config: ExportConfig,
        trimRange: TimeRange? = null
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
        Timber.d("Transform: $transform, Config: $config, Trim: $trimRange")

        // 1. Build Effects
        val effects = mutableListOf<Effect>()
        
        if (transform.hasRotation() || transform.hasScale()) {
            val scaleAndRotate = ScaleAndRotateTransformation.Builder()
                .setRotationDegrees(transform.rotation)
                .setScale(transform.scaleX, transform.scaleY)
                .build()
            effects.add(scaleAndRotate)
        }
        
        // 1.05 Apply Color Filters
        EffectFactory.createFilterEffect(transform.filter, transform.filterIntensity)?.let {
            Timber.d("Applying Filter: ${transform.filter} (intensity=${transform.filterIntensity})")
            effects.add(it)
        }
        
        // TODO: Add crop effect if needed (Crop transformation)

        // 1.1 Build Audio Processors
        val audioProcessors = mutableListOf<AudioProcessor>()
        if (transform.volume != 1.0f) {
            Timber.d("Applying volume: ${transform.volume}")
            audioProcessors.add(VolumeAudioProcessor(transform.volume))
        }

        if (transform.fadeInMs > 0 || transform.fadeOutMs > 0) {
            val durationMs = if (trimRange != null) {
                trimRange.endMs - trimRange.startMs
            } else {
                Long.MAX_VALUE 
            }
            
            Timber.d("Applying Fade: In=${transform.fadeInMs}, Out=${transform.fadeOutMs}, TotalDur=$durationMs")
            audioProcessors.add(FadeAudioProcessor(transform.fadeInMs, transform.fadeOutMs, durationMs))
        }

        // 2. Build Media Item with Effects
        var mediaItemBuilder = MediaItem.Builder().setUri(uri)
        
        if (trimRange != null) {
            mediaItemBuilder = mediaItemBuilder.setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(trimRange.startMs)
                    .setEndPositionMs(trimRange.endMs)
                    .build()
            )
        }
        
        val mediaItem = mediaItemBuilder.build()
        val editedMediaItem = EditedMediaItem.Builder(mediaItem)
            .setEffects(androidx.media3.transformer.Effects(
                /* audioProcessors = */ audioProcessors,
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