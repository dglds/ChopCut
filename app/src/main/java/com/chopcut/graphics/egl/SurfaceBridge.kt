package com.chopcut.graphics.egl

import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES20
import android.view.Surface

/**
 * Bridge between MediaCodec and OpenGL using EGL context.
 * Manages EGL display, context, and surfaces for video processing.
 */
class SurfaceBridge {

    private var eglDisplay: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
    private var eglConfig: EGLConfig? = null

    // Surfaces for decoder and encoder
    private var decoderSurface: EGLSurface? = null
    private var encoderSurface: EGLSurface? = null

    // Output surfaces for MediaCodec
    private var decoderOutputSurface: Surface? = null
    private var encoderOutputSurface: Surface? = null

    private var initialized = false

    /**
     * Initialize EGL context
     */
    fun initialize() {
        if (initialized) {
            return
        }

        try {
            // Get EGL display
            eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            if (eglDisplay === EGL14.EGL_NO_DISPLAY) {
                throw RuntimeException("Unable to get EGL14 display")
            }

            // Initialize EGL
            val version = IntArray(2)
            if (!EGL14.eglInitialize(eglDisplay, version, 0, version, 1)) {
                throw RuntimeException("Unable to initialize EGL14")
            }


            // Choose EGL config
            val configAttribs = intArrayOf(
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_NONE
            )

            val configs = arrayOfNulls<EGLConfig>(1)
            val numConfigs = IntArray(1)
            if (!EGL14.eglChooseConfig(
                    eglDisplay,
                    configAttribs, 0,
                    configs, 0, 1,
                    numConfigs, 0
                ) || numConfigs[0] == 0
            ) {
                throw RuntimeException("Unable to find EGL config")
            }

            eglConfig = configs[0]

            // Create EGL context
            val contextAttribs = intArrayOf(
                EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
                EGL14.EGL_NONE
            )

            eglContext = EGL14.eglCreateContext(
                eglDisplay,
                eglConfig,
                EGL14.EGL_NO_CONTEXT,
                contextAttribs, 0
            )

            if (eglContext === EGL14.EGL_NO_CONTEXT) {
                throw RuntimeException("Failed to create EGL context")
            }

            initialized = true

        } catch (e: Exception) {
            release()
            throw e
        }
    }

    /**
     * Create input surface for decoder (MediaCodec -> OpenGL)
     */
    fun createDecoderSurface(): Surface {
        check(initialized) { "SurfaceBridge not initialized" }

        if (decoderOutputSurface != null) {
            return decoderOutputSurface!!
        }

        // Create SurfaceTexture for receiving frames from decoder
        val surfaceTexture = SurfaceTexture(false)

        // Create EGL surface from SurfaceTexture
        val surfaceTextureHelper = SurfaceTextureHelper(surfaceTexture)
        decoderSurface = createEGLSurfaceFromSurfaceTexture(surfaceTexture)

        // Create output Surface for MediaCodec
        decoderOutputSurface = Surface(surfaceTexture)

        return decoderOutputSurface!!
    }

    /**
     * Create output surface for encoder (OpenGL -> MediaCodec)
     */
    fun createEncoderSurface(width: Int, height: Int): Surface {
        check(initialized) { "SurfaceBridge not initialized" }

        if (encoderOutputSurface != null) {
            return encoderOutputSurface!!
        }

        // Create SurfaceTexture for encoder output
        val surfaceTexture = SurfaceTexture(false)

        // Create EGL surface for rendering
        val surfaceAttribs = intArrayOf(
            EGL14.EGL_WIDTH, width,
            EGL14.EGL_HEIGHT, height,
            EGL14.EGL_NONE
        )

        encoderSurface = EGL14.eglCreateWindowSurface(
            eglDisplay,
            eglConfig,
            surfaceTexture,
            surfaceAttribs, 0
        )

        if (encoderSurface === EGL14.EGL_NO_SURFACE) {
            throw RuntimeException("Failed to create encoder EGL surface")
        }

        // Create output Surface for MediaCodec
        encoderOutputSurface = Surface(surfaceTexture)

        return encoderOutputSurface!!
    }

    /**
     * Make the decoder surface current for rendering
     */
    fun makeDecoderSurfaceCurrent() {
        check(initialized) { "SurfaceBridge not initialized" }
        check(decoderSurface != null) { "Decoder surface not created" }

        if (!EGL14.eglMakeCurrent(
                eglDisplay,
                decoderSurface,
                decoderSurface,
                eglContext
            )
        ) {
            throw RuntimeException("Failed to make decoder surface current")
        }
    }

    /**
     * Make the encoder surface current for rendering
     */
    fun makeEncoderSurfaceCurrent() {
        check(initialized) { "SurfaceBridge not initialized" }
        check(encoderSurface != null) { "Encoder surface not created" }

        if (!EGL14.eglMakeCurrent(
                eglDisplay,
                encoderSurface,
                encoderSurface,
                eglContext
            )
        ) {
            throw RuntimeException("Failed to make encoder surface current")
        }
    }

    /**
     * Set presentation time for encoder surface
     */
    fun setPresentationTime(nsecs: Long) {
        check(encoderSurface != null) { "Encoder surface not created" }

        // EGL14.eglPresentationTimeANDROID requires API 18+
        // For now, skip this
        // EGL14.eglPresentationTimeANDROID(eglDisplay, encoderSurface!!, nsecs)
    }

    /**
     * Swap buffers for encoder surface
     */
    fun swapBuffers(): Boolean {
        check(encoderSurface != null) { "Encoder surface not created" }

        return EGL14.eglSwapBuffers(eglDisplay, encoderSurface!!)
    }

    /**
     * Release all resources
     */
    fun release() {

        // Release output surfaces
        decoderOutputSurface?.release()
        encoderOutputSurface?.release()
        decoderOutputSurface = null
        encoderOutputSurface = null

        // Destroy EGL surfaces
        if (decoderSurface !== EGL14.EGL_NO_SURFACE) {
            EGL14.eglDestroySurface(eglDisplay, decoderSurface)
            decoderSurface = null
        }

        if (encoderSurface !== EGL14.EGL_NO_SURFACE) {
            EGL14.eglDestroySurface(eglDisplay, encoderSurface)
            encoderSurface = null
        }

        // Destroy EGL context
        if (eglContext !== EGL14.EGL_NO_CONTEXT) {
            EGL14.eglDestroyContext(eglDisplay, eglContext)
            eglContext = EGL14.EGL_NO_CONTEXT
        }

        // Terminate EGL
        if (eglDisplay !== EGL14.EGL_NO_DISPLAY) {
            EGL14.eglTerminate(eglDisplay)
            eglDisplay = EGL14.EGL_NO_DISPLAY
        }

        initialized = false
    }

    private fun createEGLSurfaceFromSurfaceTexture(surfaceTexture: SurfaceTexture): EGLSurface {
        val surfaceAttribs = intArrayOf(EGL14.EGL_NONE)

        val eglSurface = EGL14.eglCreateWindowSurface(
            eglDisplay,
            eglConfig,
            surfaceTexture,
            surfaceAttribs, 0
        )

        if (eglSurface === EGL14.EGL_NO_SURFACE) {
            throw RuntimeException("Failed to create EGL surface from SurfaceTexture")
        }

        return eglSurface
    }

    /**
     * Helper class to manage SurfaceTexture lifecycle
     */
    private class SurfaceTextureHelper(private val surfaceTexture: SurfaceTexture) {
        // Holds reference to prevent garbage collection
    }
}
