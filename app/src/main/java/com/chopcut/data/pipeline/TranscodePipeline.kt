package com.chopcut.data.pipeline

import android.content.Context
import android.opengl.EGL14
import android.opengl.EGLExt
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.graphics.SurfaceTexture
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.Surface
import com.chopcut.data.model.ExportConfig
import com.chopcut.data.model.Transform
import com.chopcut.data.model.VideoInfo
import com.chopcut.data.repository.VideoRepository
import com.chopcut.graphics.egl.SurfaceBridge
import com.chopcut.graphics.gl.GLRenderer
import com.chopcut.util.DispatcherProvider
import com.chopcut.util.TimeTracker
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
        val timer = TimeTracker.start("transcode")
        Timber.d("Starting transcode: transform=$transform, config=$config")

        val outputFile = withContext(dispatcherProvider.io) {
            videoRepository.createTempFile(".mp4")
        }

        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, uri, null)

            // Get video metadata
            val videoInfo = videoRepository.getMetadata(uri)
                ?: throw IllegalArgumentException("Unable to read video metadata")

            // Validate config
            require(config.isValid()) { "Invalid export config" }

            // Find video track
            val videoTrackIndex = (0 until extractor.trackCount).firstOrNull { i ->
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME)
                mime?.startsWith("video/") == true
            } ?: throw IllegalArgumentException("No video track found")

            val videoFormat = extractor.getTrackFormat(videoTrackIndex)
            extractor.selectTrack(videoTrackIndex)

            // Choose transcoding method based on transform and resolution change
            // Use Surface+OpenGL path for: complex transforms (rotation, visual crop) OR resolution changes
            val needsComplexTransforms = transform.hasRotation() || transform.hasCrop()
            val resolutionChanged = config.width != videoInfo.width || config.height != videoInfo.height
            val needsSurfacePath = needsComplexTransforms || resolutionChanged

            if (needsSurfacePath) {
                Timber.d("Using Surface+OpenGL path for complex transformations")
                transcodeWithSurface(
                    extractor = extractor,
                    videoFormat = videoFormat,
                    config = config,
                    outputFile = outputFile,
                    videoInfo = videoInfo,
                    transform = transform,
                    onProgress = { /* progress can be emitted here */ }
                )
            } else {
                Timber.d("Using buffer-copy path (resolution change only or no transformations)")
                transcodeWithBufferCopy(
                    extractor = extractor,
                    videoFormat = videoFormat,
                    config = config,
                    outputFile = outputFile,
                    videoInfo = videoInfo,
                    onProgress = { /* progress can be emitted here */ }
                )
            }

            Timber.d("Transcode completed successfully")
            emit(Result.success(outputFile))

        } catch (e: Exception) {
            Timber.e(e, "Error during transcode")
            emit(Result.failure(e))
            withContext(dispatcherProvider.io) {
                if (outputFile.exists()) {
                    outputFile.delete()
                }
            }
        } finally {
            timer.end()
            extractor.release()
        }
    }

    /**
     * Advanced transcoding using Surface + OpenGL for transformations
     * Handles crop, rotation, scale with GPU acceleration
     *
     * For simple resize (no crop/rotation): connects decoder directly to encoder surface
     * For complex transforms: uses OpenGL to render between decoder and encoder
     */
    private suspend fun transcodeWithSurface(
        extractor: MediaExtractor,
        videoFormat: MediaFormat,
        config: ExportConfig,
        outputFile: File,
        videoInfo: VideoInfo,
        transform: Transform,
        onProgress: (Float) -> Unit
    ) = withContext(dispatcherProvider.io) {
        val timer = TimeTracker.start("transcodeWithSurface")
        val decoderMimeType = videoFormat.getString(MediaFormat.KEY_MIME)!!

        // Setup encoder first - we need its input surface
        val encoderMimeType = config.codec.mimeType
        val encoder = MediaCodec.createEncoderByType(encoderMimeType)

        val encoderFormat = MediaFormat.createVideoFormat(encoderMimeType, config.width, config.height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, config.bitrate)
            setInteger(MediaFormat.KEY_FRAME_RATE, config.frameRate)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, config.keyFrameInterval)
        }

        encoder.configure(encoderFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)

        // Create input surface for encoder - this is where we'll render frames
        val encoderInputSurface = encoder.createInputSurface()

        val muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

        // For complex transforms (crop, rotation), we need OpenGL
        // For simple resize, decoder can render directly to encoder surface
        val needsOpenGL = transform.hasCrop() || transform.hasRotation()

        val decoder = MediaCodec.createDecoderByType(decoderMimeType)

        if (needsOpenGL) {
            // Use OpenGL path for crop/rotation
            Timber.d("Using OpenGL path for complex transforms: $transform")
            transcodeWithOpenGL(
                extractor = extractor,
                videoFormat = videoFormat,
                decoder = decoder,
                encoder = encoder,
                encoderInputSurface = encoderInputSurface,
                config = config,
                muxer = muxer,
                videoInfo = videoInfo,
                transform = transform,
                onProgress = onProgress
            )
        } else {
            // Direct surface connection for simple resize
            Timber.d("Using direct surface connection for resize")
            decoder.configure(videoFormat, encoderInputSurface, null, 0)

            encoder.start()
            decoder.start()

            val bufferInfo = MediaCodec.BufferInfo()
            var inputDone = false
            var decoderDone = false
            var outputDone = false
            var frameCount = 0
            val totalFrames = videoInfo.durationMs / 1000 * config.frameRate

            var muxerStarted = false
            var muxerTrackIndex = -1

            // Limit to ~2 seconds for testing
            val maxFrames = 60

            try {
                while (!outputDone && frameCount < maxFrames) {
                    // Feed decoder
                    if (!inputDone) {
                        val inputBufferIndex = decoder.dequeueInputBuffer(10000)
                        if (inputBufferIndex >= 0) {
                            val inputBuffer = decoder.getInputBuffer(inputBufferIndex)
                            if (inputBuffer != null) {
                                val sampleSize = extractor.readSampleData(inputBuffer, 0)
                                if (sampleSize > 0) {
                                    val sampleTime = extractor.sampleTime
                                    decoder.queueInputBuffer(
                                        inputBufferIndex,
                                        0,
                                        sampleSize,
                                        sampleTime,
                                        extractor.sampleFlags
                                    )
                                    extractor.advance()
                                } else {
                                    decoder.queueInputBuffer(
                                        inputBufferIndex,
                                        0, 0, 0,
                                        MediaCodec.BUFFER_FLAG_END_OF_STREAM
                                    )
                                    inputDone = true
                                }
                            }
                        }
                    }

                    // Check if decoder produced output (rendered to surface)
                    if (!decoderDone) {
                        val decoderOutputIndex = decoder.dequeueOutputBuffer(bufferInfo, 10000)
                        when {
                            decoderOutputIndex >= 0 -> {
                                // Release rendered frame to surface (true = render to surface)
                                val doRender = (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0
                                decoder.releaseOutputBuffer(decoderOutputIndex, doRender)

                                if (doRender) {
                                    frameCount++
                                    if (totalFrames > 0) {
                                        onProgress(frameCount.toFloat() / totalFrames.toFloat())
                                    }
                                }

                                if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                                    decoderDone = true
                                }
                            }
                            decoderOutputIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                                // No output yet
                            }
                            decoderOutputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                                // Format changed
                            }
                        }
                    }

                    // Get encoded output
                    val encoderOutputIndex = encoder.dequeueOutputBuffer(bufferInfo, 10000)
                    when {
                        encoderOutputIndex >= 0 -> {
                            if (muxerStarted) {
                                val encodedData = encoder.getOutputBuffer(encoderOutputIndex)
                                if (encodedData != null) {
                                    encodedData.position(bufferInfo.offset)
                                    encodedData.limit(bufferInfo.offset + bufferInfo.size)
                                    muxer.writeSampleData(muxerTrackIndex, encodedData, bufferInfo)
                                }
                            }
                            encoder.releaseOutputBuffer(encoderOutputIndex, false)

                            if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                                outputDone = true
                            }
                        }
                        encoderOutputIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                            // No output yet
                        }
                        encoderOutputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            if (!muxerStarted) {
                                muxerTrackIndex = muxer.addTrack(encoder.outputFormat)
                                muxer.start()
                                muxerStarted = true
                            }
                        }
                    }
                }

                // Signal end of stream to encoder
                encoder.signalEndOfInputStream()

                // Drain encoder
                var drainOutput = true
                while (drainOutput) {
                    val encoderOutputIndex = encoder.dequeueOutputBuffer(bufferInfo, 10000)
                    when {
                        encoderOutputIndex >= 0 -> {
                            if (muxerStarted) {
                                val encodedData = encoder.getOutputBuffer(encoderOutputIndex)
                                if (encodedData != null) {
                                    encodedData.position(bufferInfo.offset)
                                    encodedData.limit(bufferInfo.offset + bufferInfo.size)
                                    muxer.writeSampleData(muxerTrackIndex, encodedData, bufferInfo)
                                }
                            }
                            encoder.releaseOutputBuffer(encoderOutputIndex, false)

                            if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                                drainOutput = false
                            }
                        }
                        encoderOutputIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                            // Try again
                        }
                        else -> {
                            drainOutput = false
                        }
                    }
                }

            } finally {
                decoder.stop()
                encoder.stop()
                decoder.release()
                encoder.release()
                if (muxerStarted) {
                    muxer.stop()
                }
                muxer.release()
            }
        }

        timer.end()
        encoderInputSurface.release()
    }

    /**
     * Transcode with OpenGL for complex transformations (crop, rotation)
     * Uses SurfaceBridge for EGL context and GLRenderer for transformations
     */
    private suspend fun transcodeWithOpenGL(
        extractor: MediaExtractor,
        videoFormat: MediaFormat,
        decoder: MediaCodec,
        encoder: MediaCodec,
        encoderInputSurface: Surface,
        config: ExportConfig,
        muxer: MediaMuxer,
        videoInfo: VideoInfo,
        transform: Transform,
        onProgress: (Float) -> Unit
    ) = withContext(dispatcherProvider.io) {
        val timer = TimeTracker.start("transcodeWithOpenGL")
        Timber.d("Starting OpenGL transcode with transform: $transform")

        // Initialize EGL and OpenGL
        val surfaceBridge = SurfaceBridge()
        val glRenderer = GLRenderer()

        try {
            // Initialize EGL context
            surfaceBridge.initialize()

            // Create SurfaceTexture for decoder output
            val decoderSurfaceTexture = SurfaceTexture(false)

            // Configure decoder with surface
            decoder.configure(videoFormat, Surface(decoderSurfaceTexture), null, 0)

            encoder.start()
            decoder.start()

            // Make encoder surface current for OpenGL rendering
            val eglDisplay = getEGLDisplay(surfaceBridge)
            val eglContext = getEGLContext(surfaceBridge)
            val eglConfig = getEGLConfig(surfaceBridge)

            // Create EGL surface from encoder's input surface
            val eglSurface = createWindowSurface(eglDisplay, eglConfig, encoderInputSurface)

            if (!EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) {
                throw RuntimeException("Failed to make EGL context current")
            }

            // Initialize OpenGL renderer AFTER EGL context is made current
            glRenderer.initialize()
            glRenderer.setViewport(config.width, config.height)
            glRenderer.setTransform(transform)

            val bufferInfo = MediaCodec.BufferInfo()
            var inputDone = false
            var decoderDone = false
            var outputDone = false
            var frameCount = 0
            val totalFrames = videoInfo.durationMs / 1000 * config.frameRate

            var muxerStarted = false
            var muxerTrackIndex = -1

            // Limit to ~2 seconds for testing
            val maxFrames = 60

            // Sync object for frame availability
            var frameAvailable = false
            val frameLock = java.lang.Object()

            // Create handler for SurfaceTexture callbacks
            val handlerThread = android.os.HandlerThread("SurfaceTextureHandler")
            handlerThread.start()
            val callbackHandler = Handler(handlerThread.looper)

            decoderSurfaceTexture.setOnFrameAvailableListener({ _ ->
                synchronized(frameLock) {
                    frameAvailable = true
                    frameLock.notifyAll()
                }
            }, callbackHandler)

            try {
                while (!outputDone && frameCount < maxFrames) {
                    // Feed decoder
                    if (!inputDone) {
                        val inputBufferIndex = decoder.dequeueInputBuffer(10000)
                        if (inputBufferIndex >= 0) {
                            val inputBuffer = decoder.getInputBuffer(inputBufferIndex)
                            if (inputBuffer != null) {
                                val sampleSize = extractor.readSampleData(inputBuffer, 0)
                                if (sampleSize > 0) {
                                    val sampleTime = extractor.sampleTime
                                    decoder.queueInputBuffer(
                                        inputBufferIndex,
                                        0,
                                        sampleSize,
                                        sampleTime,
                                        extractor.sampleFlags
                                    )
                                    extractor.advance()
                                } else {
                                    decoder.queueInputBuffer(
                                        inputBufferIndex,
                                        0, 0, 0,
                                        MediaCodec.BUFFER_FLAG_END_OF_STREAM
                                    )
                                    inputDone = true
                                }
                            }
                        }
                    }

                    // Wait for decoder to produce a frame
                    if (!decoderDone) {
                        val decoderOutputIndex = decoder.dequeueOutputBuffer(bufferInfo, 10000)
                        when {
                            decoderOutputIndex >= 0 -> {
                                // Render to surface (updates SurfaceTexture)
                                val doRender = (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0
                                decoder.releaseOutputBuffer(decoderOutputIndex, doRender)

                                if (doRender) {
                                    // Wait for frame to be available on SurfaceTexture
                                    val waitStart = System.currentTimeMillis()
                                    var frameWasAvailable = false
                                    synchronized(frameLock) {
                                        while (!frameAvailable && System.currentTimeMillis() - waitStart < 500) {
                                            frameLock.wait(50)
                                        }
                                        frameWasAvailable = frameAvailable
                                        if (frameAvailable) {
                                            frameAvailable = false
                                        }
                                    }

                                    if (!frameWasAvailable) {
                                        Timber.w("Frame not available after 500ms - skipping")
                                    } else {
                                        try {
                                            // Update texture from SurfaceTexture and render
                                            decoderSurfaceTexture.updateTexImage()

                                            // Render with transformations to encoder surface
                                            if (glRenderer.renderFrame(decoderSurfaceTexture)) {
                                                // Set presentation time and swap buffers
                                                EGLExt.eglPresentationTimeANDROID(
                                                    eglDisplay,
                                                    eglSurface,
                                                    bufferInfo.presentationTimeUs * 1000
                                                )
                                                EGL14.eglSwapBuffers(eglDisplay, eglSurface)

                                                frameCount++
                                                Timber.d("Rendered frame $frameCount/$maxFrames")
                                                if (totalFrames > 0) {
                                                    onProgress(frameCount.toFloat() / totalFrames.toFloat())
                                                }
                                            } else {
                                                Timber.e("GLRenderer.renderFrame failed")
                                            }
                                        } catch (e: Exception) {
                                            Timber.e(e, "Error rendering frame")
                                        }
                                    }
                                }

                                if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                                    Timber.d("Decoder EOS received")
                                    decoderDone = true
                                }
                            }
                            decoderOutputIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                                // No output yet
                            }
                            decoderOutputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                                Timber.d("Decoder output format changed")
                            }
                        }
                    }

                    // Get encoded output
                    val encoderOutputIndex = encoder.dequeueOutputBuffer(bufferInfo, 10000)
                    when {
                        encoderOutputIndex >= 0 -> {
                            if (muxerStarted) {
                                val encodedData = encoder.getOutputBuffer(encoderOutputIndex)
                                if (encodedData != null) {
                                    encodedData.position(bufferInfo.offset)
                                    encodedData.limit(bufferInfo.offset + bufferInfo.size)
                                    muxer.writeSampleData(muxerTrackIndex, encodedData, bufferInfo)
                                }
                            }
                            encoder.releaseOutputBuffer(encoderOutputIndex, false)

                            if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                                outputDone = true
                            }
                        }
                        encoderOutputIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                            // No output yet
                        }
                        encoderOutputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            if (!muxerStarted) {
                                muxerTrackIndex = muxer.addTrack(encoder.outputFormat)
                                muxer.start()
                                muxerStarted = true
                            }
                        }
                    }
                }

                // Signal end of stream to encoder
                encoder.signalEndOfInputStream()

                // Drain encoder
                var drainOutput = true
                while (drainOutput) {
                    val encoderOutputIndex = encoder.dequeueOutputBuffer(bufferInfo, 10000)
                    when {
                        encoderOutputIndex >= 0 -> {
                            if (muxerStarted) {
                                val encodedData = encoder.getOutputBuffer(encoderOutputIndex)
                                if (encodedData != null) {
                                    encodedData.position(bufferInfo.offset)
                                    encodedData.limit(bufferInfo.offset + bufferInfo.size)
                                    muxer.writeSampleData(muxerTrackIndex, encodedData, bufferInfo)
                                }
                            }
                            encoder.releaseOutputBuffer(encoderOutputIndex, false)

                            if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                                drainOutput = false
                            }
                        }
                        encoderOutputIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                            // Try again
                        }
                        else -> {
                            drainOutput = false
                        }
                    }
                }

            } finally {
                // Clean up handler thread
                handlerThread.quitSafely()
                handlerThread.join()

                // Clean up EGL surface
                if (eglSurface !== EGL14.EGL_NO_SURFACE) {
                    EGL14.eglMakeCurrent(
                        eglDisplay,
                        EGL14.EGL_NO_SURFACE,
                        EGL14.EGL_NO_SURFACE,
                        EGL14.EGL_NO_CONTEXT
                    )
                    EGL14.eglDestroySurface(eglDisplay, eglSurface)
                }

                decoderSurfaceTexture.release()
                decoder.stop()
                encoder.stop()
                decoder.release()
                encoder.release()
                if (muxerStarted) {
                    muxer.stop()
                }
                muxer.release()
                glRenderer.release()
                surfaceBridge.release()
            }

        } catch (e: Exception) {
            Timber.e(e, "Error during OpenGL transcode")
            surfaceBridge.release()
            glRenderer.release()
            throw e
        } finally {
            timer.end()
        }
    }

    private fun getEGLDisplay(bridge: SurfaceBridge): EGLDisplay {
        val field = bridge.javaClass.getDeclaredField("eglDisplay")
        field.isAccessible = true
        return field.get(bridge) as EGLDisplay
    }

    private fun getEGLContext(bridge: SurfaceBridge): EGLContext {
        val field = bridge.javaClass.getDeclaredField("eglContext")
        field.isAccessible = true
        return field.get(bridge) as EGLContext
    }

    private fun getEGLConfig(bridge: SurfaceBridge): EGLConfig {
        val field = bridge.javaClass.getDeclaredField("eglConfig")
        field.isAccessible = true
        return field.get(bridge) as EGLConfig
    }

    private fun createWindowSurface(
        display: EGLDisplay,
        config: EGLConfig,
        surface: Surface
    ): EGLSurface {
        val surfaceAttribs = intArrayOf(EGL14.EGL_NONE)
        val eglSurface = EGL14.eglCreateWindowSurface(
            display,
            config,
            surface,
            surfaceAttribs,
            0
        )

        if (eglSurface === EGL14.EGL_NO_SURFACE) {
            throw RuntimeException("Failed to create EGL window surface")
        }

        return eglSurface
    }

    /**
     * Simplified transcoding using buffer copy (no OpenGL for MVP)
     * This handles resize and codec changes
     */
    private suspend fun transcodeWithBufferCopy(
        extractor: MediaExtractor,
        videoFormat: MediaFormat,
        config: ExportConfig,
        outputFile: File,
        videoInfo: VideoInfo,
        onProgress: (Float) -> Unit
    ) = withContext(dispatcherProvider.io) {
        val timer = TimeTracker.start("transcodeWithBufferCopy")
        val decoderMimeType = videoFormat.getString(MediaFormat.KEY_MIME)!!
        val decoder = MediaCodec.createDecoderByType(decoderMimeType)
        decoder.configure(videoFormat, null, null, 0)

        val encoderMimeType = config.codec.mimeType
        val encoder = MediaCodec.createEncoderByType(encoderMimeType)

        val encoderFormat = MediaFormat.createVideoFormat(encoderMimeType, config.width, config.height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
            setInteger(MediaFormat.KEY_BIT_RATE, config.bitrate)
            setInteger(MediaFormat.KEY_FRAME_RATE, config.frameRate)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, config.keyFrameInterval)
        }

        encoder.configure(encoderFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)

        val muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

        decoder.start()
        encoder.start()

        val bufferInfo = MediaCodec.BufferInfo()
        var inputDone = false
        var outputDone = false
        var frameCount = 0
        val totalFrames = videoInfo.durationMs / 1000 * config.frameRate

        // Limit to ~2 seconds (60 frames @ 30fps) for testing
        val maxFrames = 60

        var muxerStarted = false
        var muxerTrackIndex = -1

        try {
            while (!outputDone && frameCount < maxFrames) {
                // Feed decoder
                if (!inputDone) {
                    val inputBufferIndex = decoder.dequeueInputBuffer(10000)
                    if (inputBufferIndex >= 0) {
                        val inputBuffer = decoder.getInputBuffer(inputBufferIndex)
                        if (inputBuffer != null) {
                            val sampleSize = extractor.readSampleData(inputBuffer, 0)
                            if (sampleSize > 0) {
                                val sampleTime = extractor.sampleTime
                                decoder.queueInputBuffer(
                                    inputBufferIndex,
                                    0,
                                    sampleSize,
                                    sampleTime,
                                    extractor.sampleFlags
                                )
                                extractor.advance()
                            } else {
                                decoder.queueInputBuffer(
                                    inputBufferIndex,
                                    0, 0, 0,
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM
                                )
                                inputDone = true
                            }
                        }
                    }
                }

                // Get decoded frame
                var decoderOutputIndex = decoder.dequeueOutputBuffer(bufferInfo, 10000)
                while (decoderOutputIndex >= 0) {
                    // Get decoder output buffer
                    val decoderOutputBuffer = decoder.getOutputBuffer(decoderOutputIndex)

                    // Feed to encoder
                    val encoderInputIndex = encoder.dequeueInputBuffer(10000)
                    if (encoderInputIndex >= 0) {
                        val encoderInputBuffer = encoder.getInputBuffer(encoderInputIndex)
                        if (encoderInputBuffer != null && decoderOutputBuffer != null) {
                            // Copy data from decoder to encoder
                            decoderOutputBuffer.position(bufferInfo.offset)
                            decoderOutputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                            encoderInputBuffer.put(decoderOutputBuffer)

                            encoder.queueInputBuffer(
                                encoderInputIndex,
                                0,
                                bufferInfo.size,
                                bufferInfo.presentationTimeUs,
                                bufferInfo.flags
                            )
                        }
                    }

                    decoder.releaseOutputBuffer(decoderOutputIndex, false)
                    decoderOutputIndex = decoder.dequeueOutputBuffer(bufferInfo, 10000)
                }

                when (decoderOutputIndex) {
                    MediaCodec.INFO_TRY_AGAIN_LATER -> {
                        // No output available yet
                    }
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        // Decoder format changed
                    }
                }

                // Get encoded output
                val encoderOutputIndex = encoder.dequeueOutputBuffer(bufferInfo, 10000)
                when {
                    encoderOutputIndex >= 0 -> {
                        if (muxerStarted) {
                            val encodedData = encoder.getOutputBuffer(encoderOutputIndex)
                            if (encodedData != null) {
                                encodedData.position(bufferInfo.offset)
                                encodedData.limit(bufferInfo.offset + bufferInfo.size)
                                muxer.writeSampleData(muxerTrackIndex, encodedData, bufferInfo)
                            }

                            frameCount++
                            if (totalFrames > 0) {
                                onProgress(frameCount.toFloat() / totalFrames.toFloat())
                            }
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
                        // Encoder format changed - add track to muxer
                        if (!muxerStarted) {
                            muxerTrackIndex = muxer.addTrack(encoder.outputFormat)
                            muxer.start()
                            muxerStarted = true
                        }
                    }
                }
            }

            // Signal end of stream to encoder
            val inputBufferIndex = encoder.dequeueInputBuffer(10000)
            if (inputBufferIndex >= 0) {
                encoder.queueInputBuffer(
                    inputBufferIndex,
                    0, 0, 0,
                    MediaCodec.BUFFER_FLAG_END_OF_STREAM
                )
            }

            // Drain encoder
            var drainOutput = true
            while (drainOutput) {
                val encoderOutputIndex = encoder.dequeueOutputBuffer(bufferInfo, 10000)
                when {
                    encoderOutputIndex >= 0 -> {
                        if (muxerStarted) {
                            val encodedData = encoder.getOutputBuffer(encoderOutputIndex)
                            if (encodedData != null) {
                                encodedData.position(bufferInfo.offset)
                                encodedData.limit(bufferInfo.offset + bufferInfo.size)
                                muxer.writeSampleData(muxerTrackIndex, encodedData, bufferInfo)
                            }
                        }
                        encoder.releaseOutputBuffer(encoderOutputIndex, false)

                        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            drainOutput = false
                        }
                    }
                    encoderOutputIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                        // Try again
                    }
                    else -> {
                        drainOutput = false
                    }
                }
            }

        } finally {
            decoder.stop()
            encoder.stop()
            decoder.release()
            encoder.release()
            if (muxerStarted) {
                muxer.stop()
            }
            muxer.release()
        }
        timer.end()
    }
}
