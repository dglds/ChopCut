package com.chopcut.graphics.gl

import android.graphics.SurfaceTexture
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.Matrix
import com.chopcut.data.model.Transform
import timber.log.Timber
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * OpenGL ES 2.0 renderer for video frame transformations
 * Uses SurfaceTexture's external texture for rendering
 */
class GLRenderer {

    companion object {
        private const val VERTEX_SHADER = """
            uniform mat4 uMVPMatrix;
            uniform mat4 uTextureMatrix;
            attribute vec4 aPosition;
            attribute vec4 aTextureCoord;
            varying vec2 vTextureCoord;
            void main() {
                gl_Position = uMVPMatrix * aPosition;
                vTextureCoord = (uTextureMatrix * aTextureCoord).xy;
            }
        """

        // Use samplerExternalOES for SurfaceTexture textures
        private const val FRAGMENT_SHADER = """
            #extension GL_OES_EGL_image_external : require
            precision mediump float;
            varying vec2 vTextureCoord;
            uniform samplerExternalOES uTexture;
            void main() {
                gl_FragColor = texture2D(uTexture, vTextureCoord);
            }
        """

        // Vertex coordinates (full quad)
        private val VERTEX_COORDS = floatArrayOf(
            -1.0f, -1.0f,  // Bottom left
            1.0f, -1.0f,   // Bottom right
            -1.0f, 1.0f,   // Top left
            1.0f, 1.0f     // Top right
        )

        // Texture coordinates
        private val TEXTURE_COORDS = floatArrayOf(
            0.0f, 1.0f,    // Bottom left
            1.0f, 1.0f,    // Bottom right
            0.0f, 0.0f,    // Top left
            1.0f, 0.0f     // Top right
        )
    }

    // Shader program
    private var program = 0

    // Attribute and uniform locations
    private var positionHandle = 0
    private var textureCoordHandle = 0
    private var mvpMatrixHandle = 0
    private var textureMatrixHandle = 0
    private var textureSamplerHandle = 0

    // Vertex buffers
    private var vertexBuffer: FloatBuffer
    private var textureBuffer: FloatBuffer

    // Matrices
    private val mvpMatrix = FloatArray(16)
    private val textureMatrix = FloatArray(16)
    private val transformMatrix = FloatArray(16)

    // Viewport size
    private var viewportWidth = 0
    private var viewportHeight = 0

    // External OES texture handle (for SurfaceTexture)
    private var externalTextureId = 0

    private var initialized = false

    init {
        // Initialize vertex buffers
        vertexBuffer = ByteBuffer.allocateDirect(VERTEX_COORDS.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
        vertexBuffer.put(VERTEX_COORDS)
        vertexBuffer.position(0)

        textureBuffer = ByteBuffer.allocateDirect(TEXTURE_COORDS.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
        textureBuffer.put(TEXTURE_COORDS)
        textureBuffer.position(0)

        // Initialize texture matrix to identity
        Matrix.setIdentityM(textureMatrix, 0)
    }

    /**
     * Initialize OpenGL ES resources
     */
    fun initialize() {
        if (initialized) {
            Timber.w("GLRenderer already initialized")
            return
        }

        try {
            // Compile shaders
            val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, VERTEX_SHADER)
            val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER)

            // Create program
            program = GLES20.glCreateProgram()
            if (program == 0) {
                throw RuntimeException("Failed to create shader program")
            }

            GLES20.glAttachShader(program, vertexShader)
            GLES20.glAttachShader(program, fragmentShader)
            GLES20.glLinkProgram(program)

            // Check link status
            val linkStatus = IntArray(1)
            GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0)
            if (linkStatus[0] != GLES20.GL_TRUE) {
                val error = GLES20.glGetProgramInfoLog(program)
                GLES20.glDeleteProgram(program)
                throw RuntimeException("Failed to link shader program: $error")
            }

            // Get attribute and uniform locations
            positionHandle = GLES20.glGetAttribLocation(program, "aPosition")
            textureCoordHandle = GLES20.glGetAttribLocation(program, "aTextureCoord")
            mvpMatrixHandle = GLES20.glGetUniformLocation(program, "uMVPMatrix")
            textureMatrixHandle = GLES20.glGetUniformLocation(program, "uTextureMatrix")
            textureSamplerHandle = GLES20.glGetUniformLocation(program, "uTexture")

            // Generate and configure external OES texture
            val textures = IntArray(1)
            GLES20.glGenTextures(1, textures, 0)
            externalTextureId = textures[0]

            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, externalTextureId)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

            initialized = true
            Timber.d("GLRenderer initialized successfully")
            Timber.d("Handles: pos=$positionHandle, texCoord=$textureCoordHandle, mvp=$mvpMatrixHandle, texMat=$textureMatrixHandle, sampler=$textureSamplerHandle")
            Timber.d("External texture ID: $externalTextureId")

        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize GLRenderer")
            release()
            throw e
        }
    }

    /**
     * Set viewport size
     */
    fun setViewport(width: Int, height: Int) {
        viewportWidth = width
        viewportHeight = height
        GLES20.glViewport(0, 0, width, height)
    }

    /**
     * Set current transform
     */
    fun setTransform(transform: Transform) {
        Matrix.setIdentityM(transformMatrix, 0)

        // Apply scale
        if (transform.hasScale()) {
            Matrix.scaleM(transformMatrix, 0, transform.scaleX, transform.scaleY, 1f)
        }

        // Apply rotation
        if (transform.hasRotation()) {
            val rotationMatrix = FloatArray(16)
            Matrix.setRotateM(rotationMatrix, 0, transform.rotation, 0f, 0f, 1f)
            Matrix.multiplyMM(transformMatrix, 0, transformMatrix, 0, rotationMatrix, 0)
        }

        // Apply translation
        if (transform.translationX != 0f || transform.translationY != 0f) {
            Matrix.translateM(
                transformMatrix, 0,
                transform.translationX * 2f,  // Normalize to -1 to 1
                transform.translationY * 2f,
                0f
            )
        }

        // Apply crop (using scissor test)
        if (transform.hasCrop()) {
            val crop = transform.cropRect!!
            val cropLeft = (crop.left * viewportWidth).toInt()
            val cropBottom = ((1f - crop.bottom) * viewportHeight).toInt()
            val cropWidth = ((crop.right - crop.left) * viewportWidth).toInt()
            val cropHeight = ((crop.bottom - crop.top) * viewportHeight).toInt()

            GLES20.glEnable(GLES20.GL_SCISSOR_TEST)
            GLES20.glScissor(cropLeft, cropBottom, cropWidth, cropHeight)
        } else {
            GLES20.glDisable(GLES20.GL_SCISSOR_TEST)
        }
    }

    /**
     * Attach a SurfaceTexture to this renderer's external texture
     * This must be called before renderFrame()
     */
    fun attachSurfaceTexture(surfaceTexture: SurfaceTexture) {
        if (!initialized) {
            Timber.w("GLRenderer not initialized, cannot attach SurfaceTexture")
            return
        }
        // Detach from any old texture and attach to our external texture
        surfaceTexture.detachFromGLContext()
        // Note: SurfaceTexture will be attached during first use in renderFrame
    }

    /**
     * Render frame from surface texture
     * Note: SurfaceTexture manages its own OpenGL texture (GL_TEXTURE_EXTERNAL_OES)
     */
    fun renderFrame(surfaceTexture: SurfaceTexture): Boolean {
        if (!initialized) {
            Timber.e("GLRenderer not initialized")
            return false
        }

        try {
            // Update texture from SurfaceTexture (this updates the external OES texture)
            surfaceTexture.updateTexImage()

            // Get texture transformation matrix from SurfaceTexture
            surfaceTexture.getTransformMatrix(textureMatrix)

            // Clear screen
            GLES20.glClearColor(0f, 0f, 0f, 1f)
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

            // Use shader program
            GLES20.glUseProgram(program)

            // Calculate MVP matrix
            Matrix.setIdentityM(mvpMatrix, 0)
            Matrix.multiplyMM(mvpMatrix, 0, mvpMatrix, 0, transformMatrix, 0)

            // Set uniforms
            GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, mvpMatrix, 0)
            GLES20.glUniformMatrix4fv(textureMatrixHandle, 1, false, textureMatrix, 0)

            // Enable attributes
            GLES20.glEnableVertexAttribArray(positionHandle)
            GLES20.glEnableVertexAttribArray(textureCoordHandle)

            // Set vertex attributes
            GLES20.glVertexAttribPointer(
                positionHandle, 2, GLES20.GL_FLOAT, false, 0, vertexBuffer
            )
            GLES20.glVertexAttribPointer(
                textureCoordHandle, 2, GLES20.GL_FLOAT, false, 0, textureBuffer
            )

            // Bind the external OES texture - SurfaceTexture uses texture unit 0 by default
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, externalTextureId)
            GLES20.glUniform1i(textureSamplerHandle, 0)

            // Draw
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

            // Disable attributes
            GLES20.glDisableVertexAttribArray(positionHandle)
            GLES20.glDisableVertexAttribArray(textureCoordHandle)

            // Check for errors
            val error = GLES20.glGetError()
            if (error != GLES20.GL_NO_ERROR) {
                Timber.e("OpenGL error: $error")
                return false
            }

            return true

        } catch (e: Exception) {
            Timber.e(e, "Error rendering frame")
            return false
        }
    }

    /**
     * Release all resources
     */
    fun release() {
        Timber.d("Releasing GLRenderer")

        if (externalTextureId != 0) {
            GLES20.glDeleteTextures(1, intArrayOf(externalTextureId), 0)
            externalTextureId = 0
        }

        if (program != 0) {
            GLES20.glDeleteProgram(program)
            program = 0
        }

        initialized = false
        Timber.d("GLRenderer released")
    }

    private fun loadShader(type: Int, shaderCode: String): Int {
        val shader = GLES20.glCreateShader(type)
        if (shader == 0) {
            throw RuntimeException("Failed to create shader (type=$type)")
        }
        GLES20.glShaderSource(shader, shaderCode)
        GLES20.glCompileShader(shader)

        // Check compile status
        val compileStatus = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compileStatus, 0)

        if (compileStatus[0] != GLES20.GL_TRUE) {
            val error = GLES20.glGetShaderInfoLog(shader)
            GLES20.glDeleteShader(shader)
            throw RuntimeException("Failed to compile shader: $error")
        }

        return shader
    }
}
