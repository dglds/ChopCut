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
                            if (outputBuffer != null) {
                                try {
                                    // Copy YUV data (NV21 format from COLOR_FormatYUV420Flexible)
                                    val yuvData = ByteArray(outputBuffer.remaining())
                                    outputBuffer.get(yuvData)

                                    // Convert YUV to Bitmap
                                    val yuvImage = YuvImage(yuvData, ImageFormat.NV21, width, height, null)
                                    val out = ByteArrayOutputStream()
                                    yuvImage.compressToJpeg(android.graphics.Rect(0, 0, width, height), 100, out)
                                    val jpegData = out.toByteArray()
                                    out.close()

                                    // Decode JPEG to Bitmap
                                    val bitmap = BitmapFactory.decodeByteArray(jpegData, 0, jpegData.size)

                                    if (bitmap != null) {
                                        // Convert to RGB_565
                                        frameAcquired = bitmap.copy(Bitmap.Config.RGB_565, false)
                                        if (bitmap.config != Bitmap.Config.RGB_565) {
                                            bitmap.recycle()
                                        }
                                    }
                                } catch (e: Exception) {
                                    Timber.e(e, "Failed to convert YUV frame at $timeUs")
                                }
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
}
