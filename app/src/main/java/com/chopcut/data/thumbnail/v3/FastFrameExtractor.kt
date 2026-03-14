package com.chopcut.data.thumbnail.v3

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
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
import java.nio.ByteBuffer

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * A highly optimized frame extractor using MediaCodec in ByteBuffer mode.
 * Designed for MAX_PERFORMANCE by keeping the codec session open.
 * Converts YUV420Flexible output to RGB_565 Bitmaps.
 */
class FastFrameExtractor(
    private val context: Context,
    private val videoUri: Uri
) {
    private var extractor: MediaExtractor? = null
    private var codec: MediaCodec? = null
    private var videoTrackIndex = -1
    private var format: MediaFormat? = null
    private var videoWidth = 0
    private var videoHeight = 0

    private val mutex = Mutex()

    suspend fun prepare(width: Int, height: Int): Boolean = mutex.withLock {
        withContext(Dispatchers.IO) {
            Timber.d("Preparing for $videoUri")
            try {
                extractor = MediaExtractor().apply {
                    setDataSource(context, videoUri, null)
                }
                Timber.d("setDataSource successful.")

                videoTrackIndex = findVideoTrack(extractor!!)
                if (videoTrackIndex < 0) {
                    Timber.e("No video track found.")
                    return@withContext false
                }
                Timber.d("Video track found at index $videoTrackIndex.")

                extractor!!.selectTrack(videoTrackIndex)
                format = extractor!!.getTrackFormat(videoTrackIndex)
                val mime = format!!.getString(MediaFormat.KEY_MIME)
                if (mime == null) {
                    Timber.e("Video MIME type is null.")
                    return@withContext false
                }
                Timber.d("Video format: $format, MIME: $mime")

                // Store dimensions for YUV conversion
                this@FastFrameExtractor.videoWidth = width
                this@FastFrameExtractor.videoHeight = height

                // Configure format for ByteBuffer mode with YUV
                format!!.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)

                codec = MediaCodec.createDecoderByType(mime)
                codec!!.configure(format, null, null, 0)  // null surface = ByteBuffer mode
                codec!!.start()
                Timber.d("MediaCodec configured and started in ByteBuffer mode.")

                true
            } catch (e: Exception) {
                Timber.e(e, "Prepare failed for $videoUri")
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
            val width = this@FastFrameExtractor.videoWidth
            val height = this@FastFrameExtractor.videoHeight

            if (codec == null || extractor == null || width <= 0 || height <= 0) {
                Timber.e("Extractor not prepared. Codec:$codec, Extractor:$extractor, Dimensions:${width}x${height}")
                return@withContext null
            }

            Timber.d("Attempting to get frame at ${timeUs / 1000}ms")
            try {
                codec.flush()
                extractor.seekTo(timeUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
                Timber.d("Seeked to ${extractor.sampleTime / 1000}ms for target ${timeUs / 1000}ms")

                val info = MediaCodec.BufferInfo()
                val timeoutUs = 10000L
                var frameAcquired: Bitmap? = null

                val startTime = System.currentTimeMillis()
                var eos = false

                // Extraction loop
                while (frameAcquired == null && System.currentTimeMillis() - startTime < 2000) {
                    if (!eos) {
                        val inputIndex = codec.dequeueInputBuffer(timeoutUs)
                        if (inputIndex >= 0) {
                            val inputBuffer = codec.getInputBuffer(inputIndex)
                            val sampleSize = extractor.readSampleData(inputBuffer!!, 0)
                            if (sampleSize < 0) {
                                codec.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                eos = true
                                Timber.d("Input EOS reached.")
                            } else {
                                codec.queueInputBuffer(inputIndex, 0, sampleSize, extractor.sampleTime, 0)
                                extractor.advance()
                            }
                        }
                    }

                    val outputIndex = codec.dequeueOutputBuffer(info, timeoutUs)
                    if (outputIndex >= 0) {
                        // We want the first frame that comes after or at our seek point
                        val isTargetFrame = info.presentationTimeUs >= timeUs || (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0)

                        if (isTargetFrame) {
                            // Get the YUV data from the output buffer
                            val outputBuffer = codec.getOutputBuffer(outputIndex)
                            if (outputBuffer != null && outputBuffer.remaining() > 0) {
                                try {
                                    // YUV420Flexible requires proper stride handling
                                    val yuvData = ByteArray(outputBuffer.remaining())
                                    outputBuffer.get(yuvData)

                                    Timber.d("YUV buffer size: ${yuvData.size}, expected min: ${width * height * 3 / 2}")

                                    // Convert YUV420 semi-planar (NV21) to RGB_565
                                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
                                    convertYuv420ToRgb565(yuvData, width, height, bitmap)

                                    frameAcquired = bitmap
                                } catch (e: Exception) {
                                    Timber.e(e, "Failed to convert YUV frame at $timeUs, buffer size: ${outputBuffer.remaining()}")
                                }
                            } else {
                                Timber.w("Output buffer is null or empty at $timeUs")
                            }
                        }

                        codec.releaseOutputBuffer(outputIndex, false)  // false = don't render to surface
                    } else if (outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER && eos) {
                        break
                    }
                }
                frameAcquired
            } catch (e: Exception) {
                Timber.e(e, "Extraction failed for $timeUs")
                null
            }
        }
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

    /**
     * Convert YUV420 semi-planar (NV21) format to RGB_565 Bitmap.
     * Layout: Y plane (W×H) followed by interleaved UV plane (W/2×H/2, but stored as W×H/2)
     */
    private fun convertYuv420ToRgb565(yuvData: ByteArray, width: Int, height: Int, bitmap: Bitmap) {
        val frameSize = width * height
        val rgb565Data = IntArray(frameSize)

        for (y in 0 until height) {
            for (x in 0 until width) {
                val pixelIndex = y * width + x
                val yValue = yuvData[pixelIndex].toInt() and 0xFF

                // UV plane starts at frameSize, interleaved
                val uvIndex = frameSize + (y / 2) * width + (x / 2) * 2
                val u = (yuvData[uvIndex].toInt() and 0xFF) - 128
                val v = (yuvData[uvIndex + 1].toInt() and 0xFF) - 128

                // YUV to RGB conversion
                val r = (yValue + 1.402 * v).toInt().coerceIn(0, 255)
                val g = (yValue - 0.344136 * u - 0.714136 * v).toInt().coerceIn(0, 255)
                val b = (yValue + 1.772 * u).toInt().coerceIn(0, 255)

                // RGB to RGB565 (5 bits R, 6 bits G, 5 bits B)
                rgb565Data[pixelIndex] = ((r shr 3) shl 11) or ((g shr 2) shl 5) or (b shr 3)
            }
        }

        bitmap.setPixels(rgb565Data, 0, width, 0, 0, width, height)
    }
}
