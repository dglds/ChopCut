package com.chopcut.data.repository

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.chopcut.data.audio.model.AudioInfo
import com.chopcut.data.model.VideoInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream

class VideoRepository(
    private val context: Context
) {

    private val contentResolver: ContentResolver = context.contentResolver

    /**
     * Extract metadata from a video file
     */
    suspend fun getMetadata(uri: Uri): VideoInfo? = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)

            val durationUs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?.times(1000) ?: 0
            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            val rotation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            } else {
                0
            }
            val bitrate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toLongOrNull() ?: 0

            // Extract frame rate (approximate from metadata)
            val frameRate = extractFrameRate(retriever)

            // Get file info
            val fileName = getFileName(uri)
            val mimeType = getContentMimeType(uri)
            val sizeBytes = getContentSize(uri)

            // Check for audio track
            val hasAudio = hasAudioTrack(uri)

            // Get codec info
            val (videoCodec, audioCodec) = extractCodecInfo(uri)

            VideoInfo(
                uri = uri,
                fileName = fileName,
                mimeType = mimeType,
                durationUs = durationUs,
                width = width,
                height = height,
                rotation = rotation,
                bitrate = bitrate,
                frameRate = frameRate,
                videoCodec = videoCodec,
                audioCodec = audioCodec,
                hasAudio = hasAudio,
                sizeBytes = sizeBytes
            ).also {
                Timber.d("Extracted metadata: ${it.fileName} (${it.width}x${it.height}, ${it.durationMs}ms)")
            }
        } catch (e: Exception) {
            Timber.e(e, "Error extracting metadata from $uri")
            null
        } finally {
            try {
                retriever.release()
            } catch (e: Exception) {
                Timber.e(e, "Error releasing MediaMetadataRetriever")
            }
        }
    }

    /**
     * Extract audio metadata from a video file
     * Uses MediaExtractor to get detailed audio track information
     */
    suspend fun getAudioMetadata(uri: Uri): AudioInfo? = withContext(Dispatchers.IO) {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, uri, null)

            // Find audio track
            var audioTrackIndex = -1
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME)
                if (mime?.startsWith("audio/") == true) {
                    audioTrackIndex = i
                    break
                }
            }

            if (audioTrackIndex < 0) {
                Timber.w("No audio track found in $uri")
                return@withContext null
            }

            val format = extractor.getTrackFormat(audioTrackIndex)

            val mimeType = format.getString(MediaFormat.KEY_MIME) ?: "audio/unknown"
            val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            // Bitrate may not be present in all formats, use default for AAC
            val bitrate = 128000L // Default: 128 kbps
            val durationUs = format.getLong(MediaFormat.KEY_DURATION)

            // Try to get language if available
            val language = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                format.getString(MediaFormat.KEY_LANGUAGE)
            } else {
                null
            }

            AudioInfo(
                codec = mimeType.substringAfter('/'),
                sampleRate = sampleRate,
                channelCount = channelCount,
                bitrate = bitrate,
                durationUs = durationUs,
                mimeType = mimeType,
                language = language
            ).also {
                Timber.d("Extracted audio metadata: ${it.codec}, ${it.sampleRate}Hz, " +
                         "${it.channelCount}ch, ${it.bitrateKbps}kbps, ${it.durationMs}ms")
            }
        } catch (e: Exception) {
            Timber.e(e, "Error extracting audio metadata from $uri")
            null
        } finally {
            try {
                extractor.release()
            } catch (e: Exception) {
                Timber.e(e, "Error releasing MediaExtractor")
            }
        }
    }

    /**
     * Create a temporary file for video processing
     */
    suspend fun createTempFile(extension: String = ".mp4"): File = withContext(Dispatchers.IO) {
        val tempDir = File(context.cacheDir, "video_processing")
        if (!tempDir.exists()) {
            tempDir.mkdirs()
        }

        val timestamp = System.currentTimeMillis()
        File(tempDir, "chopcut_$timestamp$extension").also {
            Timber.d("Created temp file: ${it.absolutePath}")
        }
    }

    /**
     * Save processed video to gallery
     */
    suspend fun saveToGallery(file: File, filename: String? = null): Uri? = withContext(Dispatchers.IO) {
        try {
            val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            }

            val videoName = filename ?: "ChopCut_${System.currentTimeMillis()}.mp4"

            val contentValues = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, videoName)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.SIZE, file.length())
                put(MediaStore.Video.Media.IS_PENDING, 1)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/ChopCut")
                }
            }

            val uri = contentResolver.insert(collection, contentValues)

            uri?.let {
                contentResolver.openOutputStream(it)?.use { output ->
                    file.inputStream().use { input ->
                        input.copyTo(output)
                    }
                }

                // Clear pending flag
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    contentValues.clear()
                    contentValues.put(MediaStore.Video.Media.IS_PENDING, 0)
                    contentResolver.update(it, contentValues, null, null)
                }

                Timber.d("Saved video to gallery: $it")
                it
            }
        } catch (e: Exception) {
            Timber.e(e, "Error saving video to gallery")
            null
        }
    }

    /**
     * Delete a temporary file
     */
    suspend fun deleteTempFile(file: File): Boolean = withContext(Dispatchers.IO) {
        try {
            if (file.exists() && file.delete()) {
                Timber.d("Deleted temp file: ${file.absolutePath}")
                true
            } else {
                Timber.w("Failed to delete temp file: ${file.absolutePath}")
                false
            }
        } catch (e: Exception) {
            Timber.e(e, "Error deleting temp file")
            false
        }
    }

    /**
     * Get file name from URI
     */
    private fun getFileName(uri: Uri): String {
        var result: String? = null

        if (uri.scheme == "content") {
            result = contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst() && nameIndex >= 0) {
                    cursor.getString(nameIndex)
                } else {
                    null
                }
            }
        }

        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/')
            if (cut != -1 && cut != null) {
                result = result?.substring(cut + 1)
            }
        }

        return result ?: "unknown"
    }

    /**
     * Get content MIME type
     */
    private fun getContentMimeType(uri: Uri): String {
        return contentResolver.getType(uri) ?: "video/mp4"
    }

    /**
     * Get content size in bytes
     */
    private fun getContentSize(uri: Uri): Long {
        return try {
            contentResolver.openFileDescriptor(uri, "r")?.use { descriptor ->
                descriptor.statSize
            } ?: 0
        } catch (e: Exception) {
            Timber.w(e, "Error getting content size for $uri")
            0
        }
    }

    /**
     * Check if video has audio track
     */
    private fun hasAudioTrack(uri: Uri): Boolean {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            // Try to extract a metadata key that only exists if there's audio
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) != null ||
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER) != null
        } catch (e: Exception) {
            Timber.w(e, "Error checking audio track")
            false
        } finally {
            try {
                retriever.release()
            } catch (e: Exception) {
                Timber.e(e, "Error releasing MediaMetadataRetriever")
            }
        }
    }

    /**
     * Extract codec info
     */
    private fun extractCodecInfo(uri: Uri): Pair<String?, String?> {
        // This is a simplified version. Real implementation would use MediaExtractor
        // to inspect the actual track codec information
        return Pair(null, null)
    }

    /**
     * Extract frame rate from metadata
     */
    private fun extractFrameRate(retriever: MediaMetadataRetriever): Int {
        return try {
            // Try to extract frame rate from metadata
            // Note: This is a simplified approach. Real implementation would
            // use MediaExtractor to get accurate frame rate
            30 // Default fallback
        } catch (e: Exception) {
            Timber.w(e, "Error extracting frame rate")
            30 // Default fallback
        }
    }

    /**
     * Copy URI to a temp file
     */
    suspend fun copyToTempFile(uri: Uri): File? = withContext(Dispatchers.IO) {
        val tempFile = createTempFile()

        try {
            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            }
            Timber.d("Copied URI to temp file: ${tempFile.absolutePath}")
            tempFile
        } catch (e: Exception) {
            Timber.e(e, "Error copying URI to temp file")
            tempFile.delete()
            null
        }
    }
}
