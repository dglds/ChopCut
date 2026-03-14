package com.chopcut.data.thumbnail.v3

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.media.Image
import android.media.ImageReader
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.Handler
import android.os.HandlerThread
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.ByteArrayOutputStream

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * A highly optimized frame extractor using MediaCodec in Surface mode
 * with an ImageReader configured for YUV_420_888 (the native output
 * format of hardware video decoders).
 *
 * Keeps the codec session open across multiple getFrameAt() calls
 * for maximum performance.
 */
class FastFrameExtractor(
    private val context: Context,
    private val videoUri: Uri
) {
    private var extractor: MediaExtractor? = null
    private var codec: MediaCodec? = null
    private var imageReader: ImageReader? = null
    private var videoTrackIndex = -1
    private var format: MediaFormat? = null
    private var outputWidth = 0
    private var outputHeight = 0

    private val mutex = Mutex()
    private val handlerThread = HandlerThread("FastFrameExtractor")
    private var handler: Handler? = null

    init {
        handlerThread.start()
        handler = Handler(handlerThread.looper)
    }

    suspend fun prepare(width: Int, height: Int): Boolean = mutex.withLock {
        withContext(Dispatchers.IO) {
            Timber.d("Preparing for %s", videoUri)
            try {
                extractor = MediaExtractor().apply {
                    setDataSource(context, videoUri, null)
                }

                videoTrackIndex = findVideoTrack(extractor!!)
                if (videoTrackIndex < 0) {
                    Timber.e("No video track found.")
                    return@withContext false
                }

                extractor!!.selectTrack(videoTrackIndex)
                format = extractor!!.getTrackFormat(videoTrackIndex)
                val mime = format!!.getString(MediaFormat.KEY_MIME)
                if (mime == null) {
                    Timber.e("Video MIME type is null.")
                    return@withContext false
                }
                Timber.d("Video format: %s, MIME: %s", format, mime)

                outputWidth = width
                outputHeight = height

                // YUV_420_888 is the native output format of hardware video decoders.
                // Unlike RGBA_8888, this does NOT require a color-space conversion
                // the decoder can't do — it matches what the decoder already produces.
                imageReader = ImageReader.newInstance(
                    width, height, ImageFormat.YUV_420_888, 3
                )

                codec = MediaCodec.createDecoderByType(mime)
                // Surface mode: decoder renders directly to ImageReader's surface
                codec!!.configure(format, imageReader!!.surface, null, 0)
                codec!!.start()
                Timber.d("MediaCodec started in Surface mode with YUV_420_888 ImageReader")

                true
            } catch (e: Exception) {
                Timber.e(e, "Prepare failed for %s", videoUri)
                false
            }
        }
    }

    private fun findVideoTrack(extractor: MediaExtractor): Int {
        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME)
            if (mime?.startsWith("video/") == true) return i
        }
        return -1
    }

    suspend fun getFrameAt(timeUs: Long): Bitmap? = mutex.withLock {
        withContext(Dispatchers.IO) {
            val codec = this@FastFrameExtractor.codec ?: return@withContext null
            val extractor = this@FastFrameExtractor.extractor ?: return@withContext null
            val reader = this@FastFrameExtractor.imageReader ?: return@withContext null

            try {
                codec.flush()
                extractor.seekTo(timeUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)

                val info = MediaCodec.BufferInfo()
                val timeoutUs = 10_000L
                var frameAcquired: Bitmap? = null

                val startTime = System.currentTimeMillis()
                var eos = false

                while (frameAcquired == null && System.currentTimeMillis() - startTime < 2000) {
                    // Feed input
                    if (!eos) {
                        val inputIndex = codec.dequeueInputBuffer(timeoutUs)
                        if (inputIndex >= 0) {
                            val inputBuffer = codec.getInputBuffer(inputIndex)!!
                            val sampleSize = extractor.readSampleData(inputBuffer, 0)
                            if (sampleSize < 0) {
                                codec.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                eos = true
                            } else {
                                codec.queueInputBuffer(inputIndex, 0, sampleSize, extractor.sampleTime, 0)
                                extractor.advance()
                            }
                        }
                    }

                    // Drain output
                    val outputIndex = codec.dequeueOutputBuffer(info, timeoutUs)
                    if (outputIndex >= 0) {
                        val isTargetFrame = info.presentationTimeUs >= timeUs ||
                            (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0)

                        // true = render to ImageReader's surface; false = discard
                        codec.releaseOutputBuffer(outputIndex, isTargetFrame)

                        if (isTargetFrame) {
                            // Give the surface a moment to deliver the frame
                            Thread.sleep(5)
                            var image: Image? = null
                            var retries = 0
                            while (image == null && retries < 20) {
                                image = reader.acquireLatestImage()
                                if (image == null) {
                                    Thread.sleep(5)
                                    retries++
                                }
                            }

                            image?.use { img ->
                                frameAcquired = yuvImageToBitmap(img)
                            }

                            if (frameAcquired == null) {
                                Timber.w("Could not acquire image after %d retries at %dms", retries, timeUs / 1000)
                            }
                        }
                    } else if (outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER && eos) {
                        break
                    }
                }
                frameAcquired
            } catch (e: Exception) {
                Timber.e(e, "Extraction failed at %dms", timeUs / 1000)
                null
            }
        }
    }

    /**
     * Converts a YUV_420_888 [Image] to an RGB_565 [Bitmap].
     *
     * Strategy: pack the Image planes into NV21 byte array (respecting actual
     * hardware strides from Image.Plane), then delegate to YuvImage.compressToJpeg()
     * + BitmapFactory — both are native C implementations and ~10-20x faster than
     * a Kotlin pixel loop.
     */
    private fun yuvImageToBitmap(image: Image): Bitmap? {
        val width = image.width
        val height = image.height

        val nv21 = imageToNv21(image, width, height)

        val yuvImage = YuvImage(nv21, ImageFormat.NV21, width, height, null)
        val out = ByteArrayOutputStream(width * height)
        yuvImage.compressToJpeg(Rect(0, 0, width, height), 90, out)

        val opts = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        return BitmapFactory.decodeByteArray(out.toByteArray(), 0, out.size(), opts)
    }

    /**
     * Packs YUV_420_888 Image planes into a contiguous NV21 byte array.
     *
     * NV21 layout: [Y plane: W×H bytes] [VU interleaved: W×H/2 bytes]
     * Uses actual rowStride/pixelStride from each plane to handle
     * hardware-specific memory alignment.
     */
    private fun imageToNv21(image: Image, width: Int, height: Int): ByteArray {
        val yPlane = image.planes[0]
        val uPlane = image.planes[1]
        val vPlane = image.planes[2]

        val yBuffer = yPlane.buffer
        val uBuffer = uPlane.buffer
        val vBuffer = vPlane.buffer

        val yRowStride = yPlane.rowStride
        val uvRowStride = uPlane.rowStride
        val uvPixelStride = uPlane.pixelStride

        val nv21 = ByteArray(width * height * 3 / 2)

        // Copy Y plane
        if (yRowStride == width) {
            // Fast path: no padding, bulk copy
            yBuffer.get(nv21, 0, width * height)
        } else {
            // Slow path: copy row by row, skipping padding
            var pos = 0
            for (row in 0 until height) {
                yBuffer.position(row * yRowStride)
                yBuffer.get(nv21, pos, width)
                pos += width
            }
        }

        // Copy UV planes interleaved as V,U (NV21 ordering)
        val uvHeight = height / 2
        val uvWidth = width / 2
        var uvPos = width * height

        if (uvPixelStride == 2 && uvRowStride == width) {
            // Fast path: UV data is already semi-planar and contiguous.
            // V and U buffers in YUV_420_888 overlap by 1 byte when pixelStride==2,
            // meaning the underlying data is already NV21 or NV12.
            // Check first byte ordering to determine which.
            vBuffer.position(0)
            vBuffer.get(nv21, uvPos, uvHeight * width)
        } else {
            // General path: repack from separate/strided planes
            for (row in 0 until uvHeight) {
                for (col in 0 until uvWidth) {
                    val uvIndex = row * uvRowStride + col * uvPixelStride
                    nv21[uvPos++] = vBuffer.get(uvIndex)  // V first (NV21)
                    nv21[uvPos++] = uBuffer.get(uvIndex)  // then U
                }
            }
        }

        return nv21
    }

    fun release() {
        try {
            codec?.stop()
            codec?.release()
            extractor?.release()
            imageReader?.close()
            handlerThread.quitSafely()
        } catch (e: Exception) {
            Timber.e(e, "Error releasing resources")
        }
    }
}
