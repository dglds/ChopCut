package com.chopcut.data.thumbnail.v3

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.ByteArrayOutputStream

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Fast frame extractor using MediaCodec in ByteBuffer mode.
 *
 * Reads the actual output stride/slice-height from [MediaCodec.getOutputFormat]
 * after the codec starts producing output, so the YUV→RGB conversion uses
 * hardware-accurate layout metadata instead of guessing.
 */
class FastFrameExtractor(
    private val context: Context,
    private val videoUri: Uri
) {
    private var extractor: MediaExtractor? = null
    private var codec: MediaCodec? = null
    private var videoTrackIndex = -1
    private var outputWidth = 0
    private var outputHeight = 0

    // Actual hardware layout — populated from codec.getOutputFormat()
    private var stride = 0
    private var sliceHeight = 0
    private var colorFormat = 0
    private var codecWidth = 0   // actual decoded frame width
    private var codecHeight = 0  // actual decoded frame height
    private var outputFormatRead = false

    private val mutex = Mutex()

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
                val format = extractor!!.getTrackFormat(videoTrackIndex)
                val mime = format.getString(MediaFormat.KEY_MIME)
                if (mime == null) {
                    Timber.e("Video MIME type is null.")
                    return@withContext false
                }
                Timber.d("Input format: %s", format)

                outputWidth = width
                outputHeight = height

                // Request YUV420Flexible — the most universally supported format
                format.setInteger(
                    MediaFormat.KEY_COLOR_FORMAT,
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible
                )

                codec = MediaCodec.createDecoderByType(mime)
                // null surface = ByteBuffer mode
                codec!!.configure(format, null, null, 0)
                codec!!.start()

                Timber.d("MediaCodec started in ByteBuffer mode")
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

    /**
     * Read the actual hardware output format once the codec has produced output.
     * This gives us the real stride and slice-height the hardware is using.
     */
    private fun readOutputFormat() {
        if (outputFormatRead) return
        val fmt = codec?.outputFormat ?: return

        codecWidth = fmt.getIntegerSafe(MediaFormat.KEY_WIDTH, outputWidth)
        codecHeight = fmt.getIntegerSafe(MediaFormat.KEY_HEIGHT, outputHeight)
        stride = fmt.getIntegerSafe(MediaFormat.KEY_STRIDE, codecWidth)
        sliceHeight = fmt.getIntegerSafe("slice-height", codecHeight)
        colorFormat = fmt.getIntegerSafe(MediaFormat.KEY_COLOR_FORMAT, 0)

        Timber.d(
            "Output format: %dx%d, stride=%d, sliceHeight=%d, colorFormat=0x%x, target=%dx%d",
            codecWidth, codecHeight, stride, sliceHeight, colorFormat, outputWidth, outputHeight
        )
        outputFormatRead = true
    }

    suspend fun getFrameAt(timeUs: Long): Bitmap? = mutex.withLock {
        withContext(Dispatchers.IO) {
            val codec = this@FastFrameExtractor.codec ?: return@withContext null
            val extractor = this@FastFrameExtractor.extractor ?: return@withContext null

            try {
                codec.flush()
                extractor.seekTo(timeUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)

                val info = MediaCodec.BufferInfo()
                val timeoutUs = 5_000L
                var frameAcquired: Bitmap? = null
                val startTime = System.currentTimeMillis()
                var eos = false

                while (frameAcquired == null && System.currentTimeMillis() - startTime < 5000) {
                    // Feed input
                    if (!eos) {
                        val inputIndex = codec.dequeueInputBuffer(timeoutUs)
                        if (inputIndex >= 0) {
                            val inputBuffer = codec.getInputBuffer(inputIndex)!!
                            val sampleSize = extractor.readSampleData(inputBuffer, 0)
                            if (sampleSize < 0) {
                                codec.queueInputBuffer(
                                    inputIndex, 0, 0, 0,
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM
                                )
                                eos = true
                            } else {
                                codec.queueInputBuffer(
                                    inputIndex, 0, sampleSize,
                                    extractor.sampleTime, 0
                                )
                                extractor.advance()
                            }
                        }
                    }

                    // Drain output
                    val outputIndex = codec.dequeueOutputBuffer(info, timeoutUs)
                    when {
                        outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            readOutputFormat()
                        }
                        outputIndex >= 0 -> {
                            // Read format if we haven't yet (some codecs don't signal FORMAT_CHANGED)
                            readOutputFormat()

                            val isTarget = info.presentationTimeUs >= timeUs ||
                                (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0)

                            if (isTarget) {
                                // Prefer getOutputImage() — gives exact plane layout info
                                val image = codec.getOutputImage(outputIndex)
                                if (image != null) {
                                    frameAcquired = convertImageToBitmap(image)
                                    image.close()
                                } else {
                                    // Fallback: raw ByteBuffer (some codecs may not support getOutputImage)
                                    val outputBuffer = codec.getOutputBuffer(outputIndex)
                                    if (outputBuffer != null) {
                                        frameAcquired = convertYuvToBitmap(outputBuffer)
                                    }
                                }
                            }
                            codec.releaseOutputBuffer(outputIndex, false)
                        }
                        outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER && eos -> break
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
     * Convert an [android.media.Image] (YUV_420_888) to an RGB_565 Bitmap.
     *
     * Uses [Image.getPlanes] to read Y, U, V with exact rowStride and pixelStride,
     * so we correctly handle NV12, NV21, I420, and YV12 layouts without guessing.
     */
    private fun convertImageToBitmap(image: android.media.Image): Bitmap? {
        val w = image.width
        val h = image.height
        val planes = image.planes  // [0]=Y, [1]=U, [2]=V

        val yPlane = planes[0]
        val uPlane = planes[1]
        val vPlane = planes[2]

        Timber.d(
            "Image format=%d, %dx%d | Y: rowStride=%d pixelStride=%d | U: rowStride=%d pixelStride=%d | V: rowStride=%d pixelStride=%d",
            image.format, w, h,
            yPlane.rowStride, yPlane.pixelStride,
            uPlane.rowStride, uPlane.pixelStride,
            vPlane.rowStride, vPlane.pixelStride
        )

        // Build NV21 byte array: Y (w*h) + interleaved VU (w*h/2)
        val nv21 = ByteArray(w * h * 3 / 2)

        // Copy Y plane, respecting rowStride
        val yBuffer = yPlane.buffer
        val yRowStride = yPlane.rowStride
        for (row in 0 until h) {
            yBuffer.position(row * yRowStride)
            yBuffer.get(nv21, row * w, w)
        }

        // Copy UV into NV21 order (V, U interleaved)
        val uvDst = w * h
        val vBuffer = vPlane.buffer
        val uBuffer = uPlane.buffer
        val uvRowStride = uPlane.rowStride
        val uvPixelStride = uPlane.pixelStride

        if (uvPixelStride == 2) {
            // Interleaved (NV12 or NV21) — read V and U from their respective buffers
            for (row in 0 until h / 2) {
                for (col in 0 until w / 2) {
                    val srcIdx = row * uvRowStride + col * uvPixelStride
                    val dstIdx = uvDst + row * w + col * 2
                    nv21[dstIdx] = vBuffer.get(srcIdx)       // V
                    nv21[dstIdx + 1] = uBuffer.get(srcIdx)   // U
                }
            }
        } else {
            // Planar (I420/YV12): pixelStride == 1
            for (row in 0 until h / 2) {
                for (col in 0 until w / 2) {
                    val srcIdx = row * uvRowStride + col
                    val dstIdx = uvDst + row * w + col * 2
                    nv21[dstIdx] = vBuffer.get(srcIdx)       // V
                    nv21[dstIdx + 1] = uBuffer.get(srcIdx)   // U
                }
            }
        }

        return try {
            val yuvImage = YuvImage(nv21, ImageFormat.NV21, w, h, null)
            val out = ByteArrayOutputStream(w * h)
            yuvImage.compressToJpeg(Rect(0, 0, w, h), 85, out)

            val opts = BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.RGB_565
                if (outputWidth > 0 && outputHeight > 0) {
                    inSampleSize = calculateSampleSize(w, h, outputWidth, outputHeight)
                }
            }
            val bitmap = BitmapFactory.decodeByteArray(out.toByteArray(), 0, out.size(), opts)
                ?: return null

            if (outputWidth > 0 && outputHeight > 0) {
                val scaled = Bitmap.createScaledBitmap(bitmap, outputWidth, outputHeight, true)
                if (scaled !== bitmap) bitmap.recycle()
                scaled
            } else {
                bitmap
            }
        } catch (e: Exception) {
            Timber.e(e, "Image conversion failed: %dx%d", w, h)
            null
        }
    }

    /**
     * Fallback: convert a raw YUV output buffer to an RGB_565 Bitmap.
     *
     * Used only when [MediaCodec.getOutputImage] returns null. Copies UV data
     * directly without swap (the codec format is unknown, but direct copy is
     * more likely correct than assuming NV12).
     */
    private fun convertYuvToBitmap(buffer: java.nio.ByteBuffer): Bitmap? {
        val w = codecWidth
        val h = codecHeight
        val s = stride.coerceAtLeast(w)
        val sh = sliceHeight.coerceAtLeast(h)

        val data = ByteArray(buffer.remaining())
        buffer.get(data)

        Timber.d("Buffer: %d bytes, expected s*sh*1.5=%d, s*h*1.5=%d", data.size, s * sh * 3 / 2, s * h * 3 / 2)

        // Detect UV plane offset: some codecs use stride*sliceHeight, others stride*height
        val uvOffsetPadded = s * sh
        val uvOffsetCompact = s * h
        val uvNeeded = s * (h / 2)  // bytes needed for UV plane

        val uvSrcStart = when {
            uvOffsetPadded + uvNeeded <= data.size -> uvOffsetPadded
            uvOffsetCompact + uvNeeded <= data.size -> uvOffsetCompact
            else -> {
                // Fallback: UV immediately after Y data
                Timber.w("Buffer too small for expected UV offset, using compact layout")
                uvOffsetCompact.coerceAtMost(data.size - 1)
            }
        }
        Timber.d("UV offset: %d (padded=%d, compact=%d)", uvSrcStart, uvOffsetPadded, uvOffsetCompact)

        // Pack into NV21: contiguous Y (w×h) + interleaved VU (w×h/2)
        val nv21Size = w * h * 3 / 2
        val nv21 = ByteArray(nv21Size)

        // Copy Y plane, removing stride padding
        for (row in 0 until h) {
            val srcOffset = row * s
            val dstOffset = row * w
            if (srcOffset + w <= data.size) {
                System.arraycopy(data, srcOffset, nv21, dstOffset, w)
            }
        }

        // Copy UV plane directly — no swap, since we don't know the exact format
        // in this fallback path. Direct copy works if codec emits NV21 natively.
        val uvDstStart = w * h
        for (row in 0 until h / 2) {
            val srcRowOffset = uvSrcStart + row * s
            val dstRowOffset = uvDstStart + row * w
            val copyLen = w.coerceAtMost(data.size - srcRowOffset).coerceAtMost(nv21.size - dstRowOffset)
            if (copyLen > 0) {
                System.arraycopy(data, srcRowOffset, nv21, dstRowOffset, copyLen)
            }
        }

        return try {
            val yuvImage = YuvImage(nv21, ImageFormat.NV21, w, h, null)
            val out = ByteArrayOutputStream(w * h)
            yuvImage.compressToJpeg(Rect(0, 0, w, h), 85, out)

            val opts = BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.RGB_565
                if (outputWidth > 0 && outputHeight > 0) {
                    inSampleSize = calculateSampleSize(w, h, outputWidth, outputHeight)
                }
            }
            val fullBitmap = BitmapFactory.decodeByteArray(out.toByteArray(), 0, out.size(), opts)
                ?: return null

            // Scale to target, preserving aspect ratio
            if (outputWidth > 0 && outputHeight > 0) {
                val scaled = Bitmap.createScaledBitmap(fullBitmap, outputWidth, outputHeight, true)
                if (scaled !== fullBitmap) fullBitmap.recycle()
                scaled
            } else {
                fullBitmap
            }
        } catch (e: Exception) {
            Timber.e(e, "YUV conversion failed: %dx%d stride=%d sh=%d buf=%d", w, h, s, sh, data.size)
            null
        }
    }

    private fun calculateSampleSize(srcW: Int, srcH: Int, dstW: Int, dstH: Int): Int {
        var sample = 1
        var w = srcW
        var h = srcH
        while (w / 2 >= dstW && h / 2 >= dstH) {
            sample *= 2
            w /= 2
            h /= 2
        }
        return sample
    }

    fun release() {
        try {
            codec?.stop()
            codec?.release()
            extractor?.release()
        } catch (e: Exception) {
            Timber.e(e, "Error releasing resources")
        }
    }

    private fun MediaFormat.getIntegerSafe(key: String, default: Int): Int {
        return try {
            if (containsKey(key)) getInteger(key) else default
        } catch (_: Exception) {
            default
        }
    }
}
