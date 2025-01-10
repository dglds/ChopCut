package com.chopcut.data.pipeline

import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.view.Surface
import com.chopcut.data.model.ExportConfig
import com.chopcut.data.model.Transform
import com.chopcut.data.model.VideoInfo
import com.chopcut.data.repository.VideoRepository
import com.chopcut.graphics.egl.SurfaceBridge
import com.chopcut.graphics.gl.GLRenderer
import com.chopcut.util.DispatcherProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File

class TranscodePipeline(
    private val context: Context,
    private val videoRepository: VideoRepository,
    private val dispatcherProvider: DispatcherProvider = DispatcherProvider
) {

    @OptIn(FlowPreview::class)
    suspend fun process(
        uri: Uri,
        transform: Transform = Transform.IDENTITY,
        config: ExportConfig
    ): Flow<Result<File>> = flow {
        Timber.d("Starting transcode: transform=$transform, config=$config")

        val outputFile = withContext(dispatcherProvider.io) {
            videoRepository.createTempFile(".mp4")
        }

        try {
            withContext(dispatcherProvider.io) {
                // Get video metadata
                val videoInfo = videoRepository.getMetadata(uri)
                    ?: throw IllegalArgumentException("Unable to read video metadata")

                // Validate config
                require(config.isValid()) { "Invalid export config" }

                // Initialize components
                val surfaceBridge = SurfaceBridge()
                val glRenderer = GLRenderer()

                try {
                    // Initialize EGL and OpenGL
                    surfaceBridge.initialize()
                    glRenderer.initialize()

                    // Create surfaces
                    val decoderSurface = surfaceBridge.createDecoderSurface()
                    val encoderSurface = surfaceBridge.createEncoderSurface(
                        config.width,
                        config.height
                    )

                    // Setup decoder and encoder
                    val decoder = setupDecoder(uri, videoInfo, decoderSurface)
                    val encoder = setupEncoder(config, encoderSurface)
                    val muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

                    try {
                        // Start codec processing
                        decoder.start()
                        encoder.start()

                        // Get track info
                        val videoTrackIndex = findTrackIndex(uri, "video/")
                        val audioTrackIndex = if (config.preserveAudio) {
                            findTrackIndex(uri, "audio/")
                        } else -1

                        // Add tracks to muxer
                        val muxerVideoTrack = muxer.addTrack(encoder.outputFormat)
                        val muxerAudioTrack = if (audioTrackIndex >= 0 && videoInfo.hasAudio) {
                            // TODO: Add audio track
                            -1
                        } else -1

                        muxer.start()

                        // Process frames
                        processFrames(
                            decoder = decoder,
                            encoder = encoder,
                            surfaceBridge = surfaceBridge,
                            glRenderer = glRenderer,
                            muxer = muxer,
                            muxerTrackIndex = muxerVideoTrack,
                            transform = transform,
                            config = config,
                            videoInfo = videoInfo,
                            onProgress = { progress ->
                                // Emit progress if needed
                            }
                        )

                        // Signal end of stream
                        signalEndOfStream(encoder)
                        muxer.stop()

                        Timber.d("Transcode completed successfully")
                        emit(Result.success(outputFile))

                    } finally {
                        // Cleanup
                        decoder.stop()
                        encoder.stop()
                        decoder.release()
                        encoder.release()
                        muxer.release()
                    }

                } finally {
                    glRenderer.release()
                    surfaceBridge.release()
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error during transcode")
            emit(Result.failure(e))
            withContext(dispatcherProvider.io) {
                if (outputFile.exists()) {
                    outputFile.delete()
                }
            }
        }
    }

    private suspend fun setupDecoder(
        uri: Uri,
        videoInfo: VideoInfo,
        surface: Surface
    ): MediaCodec = withContext(dispatcherProvider.io) {
        val extractor = MediaExtractor()
        extractor.setDataSource(context, uri, null)

        val videoTrackIndex = (0 until extractor.trackCount).firstOrNull { i ->
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME)
            mime?.startsWith("video/") == true
        } ?: throw IllegalArgumentException("No video track found")

        val format = extractor.getTrackFormat(videoTrackIndex)
        val mime = format.getString(MediaFormat.KEY_MIME)!!

        val codec = MediaCodec.createDecoderByType(mime)
        codec.configure(format, surface, null, 0)

        codec
    }

    private fun setupEncoder(
        config: ExportConfig,
        surface: Surface
    ): MediaCodec {
        val mime = config.codec.mimeType
        val codec = MediaCodec.createEncoderByType(mime)

        val format = MediaFormat.createVideoFormat(mime, config.width, config.height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, config.bitrate)
            setInteger(MediaFormat.KEY_FRAME_RATE, config.frameRate)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, config.keyFrameInterval)
        }

        codec.configure(format, surface, null, MediaCodec.CONFIGURE_FLAG_ENCODE)

        return codec
    }

    private fun processFrames(
        decoder: MediaCodec,
        encoder: MediaCodec,
        surfaceBridge: SurfaceBridge,
        glRenderer: GLRenderer,
        muxer: MediaMuxer,
        muxerTrackIndex: Int,
        transform: Transform,
        config: ExportConfig,
        videoInfo: VideoInfo,
        onProgress: (Float) -> Unit
    ) {
        val bufferInfo = MediaCodec.BufferInfo()
        var outputDone = false
        var inputDone = false
        var frameCount = 0
        val totalFrames = videoInfo.durationMs / 1000 * config.frameRate

        glRenderer.setViewport(config.width, config.height)
        glRenderer.setTransform(transform)

        // Main processing loop
        while (!outputDone && !inputDone) {
            // Feed decoder with input data
            if (!inputDone) {
                val inputBufferIndex = decoder.dequeueInputBuffer(10000)
                if (inputBufferIndex >= 0) {
                    // TODO: Read from extractor and feed to decoder
                    // For now, signal end of stream
                    decoder.queueInputBuffer(
                        inputBufferIndex,
                        0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM
                    )
                    inputDone = true
                }
            }

            // Get decoded frame from decoder
            val decoderOutputIndex = decoder.dequeueOutputBuffer(bufferInfo, 10000)
            when {
                decoderOutputIndex >= 0 -> {
                    // Render decoded frame to surface
                    // This triggers the OpenGL rendering pipeline
                    val doRender = bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0
                    decoder.releaseOutputBuffer(decoderOutputIndex, doRender)

                    if (doRender) {
                        // TODO: Render with transforms via OpenGL
                        // This requires proper synchronization between decoder surface
                        // and encoder surface via the shared EGL context
                        // For now, this is a simplified version

                        // Update progress
                        frameCount++
                        if (totalFrames > 0) {
                            onProgress(frameCount.toFloat() / totalFrames.toFloat())
                        }
                    }
                }
                decoderOutputIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    // No output available yet
                }
                decoderOutputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    // Decoder format changed
                }
            }

            // Get encoded output from encoder
            val encoderOutputIndex = encoder.dequeueOutputBuffer(bufferInfo, 10000)
            when {
                encoderOutputIndex >= 0 -> {
                    // Write encoded frame to muxer
                    val encodedData = encoder.getOutputBuffer(encoderOutputIndex)
                    if (encodedData != null) {
                        encodedData.position(bufferInfo.offset)
                        encodedData.limit(bufferInfo.offset + bufferInfo.size)
                        muxer.writeSampleData(muxerTrackIndex, encodedData, bufferInfo)
                    }
                    encoder.releaseOutputBuffer(encoderOutputIndex, false)

                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        outputDone = true
                    }
                }
                encoderOutputIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    // No output available yet
                }
                encoderOutputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    // Encoder format changed - should have been added to muxer earlier
                }
            }
        }
    }

    private fun signalEndOfStream(encoder: MediaCodec) {
        // Signal end of stream to encoder
        val inputBufferIndex = encoder.dequeueInputBuffer(10000)
        if (inputBufferIndex >= 0) {
            encoder.queueInputBuffer(
                inputBufferIndex,
                0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM
            )
        }
    }

    private suspend fun findTrackIndex(uri: Uri, mimeType: String): Int = withContext(dispatcherProvider.io) {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, uri, null)
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME)
                if (mime?.startsWith(mimeType) == true) {
                    return@withContext i
                }
            }
            -1
        } finally {
            extractor.release()
        }
    }
}
