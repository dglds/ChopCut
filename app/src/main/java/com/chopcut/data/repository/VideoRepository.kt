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
            val vw = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            val vh = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            
            val (width, height) = if (rotation == 90 || rotation == 270) {
                vh to vw
            } else {
                vw to vh
            }
            val bitrate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toLongOrNull() ?: 0

            // Check for audio track (reusing same retriever)
            val hasAudio = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) != null

            // Get file info
            val fileName = getFileName(uri)
            val mimeType = getContentMimeType(uri)
            val sizeBytes = getContentSize(uri)

            VideoInfo(
                uri = uri,
                fileName = fileName,
                mimeType = mimeType,
                durationUs = durationUs,
                width = width,
                height = height,
                rotation = rotation,
                bitrate = bitrate,
                frameRate = 30, // Default fallback
                videoCodec = null,
                audioCodec = null,
                hasAudio = hasAudio,
                sizeBytes = sizeBytes
            ).also {
            }
        } catch (e: Exception) {
            null
        } finally {
            try {
                retriever.release()
            } catch (e: Exception) {
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
            }
        } catch (e: Exception) {
            null
        } finally {
            try {
                extractor.release()
            } catch (e: Exception) {
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
        }
    }

    /**
     * Save processed video to gallery/storage
     * Tries to save to root "ChopCut" folder first, falls back to Movies/ChopCut
     */
    suspend fun saveToGallery(file: File, filename: String? = null): Uri = withContext(Dispatchers.IO) {
        val videoName = filename ?: "ChopCut_${System.currentTimeMillis()}.mp4"

        if (!file.exists() || file.length() == 0L) {
            throw IllegalStateException("Arquivo de origem não existe ou está vazio: ${file.absolutePath}")
        }

        // 1. Try to save to root /ChopCut folder (Legacy/Permissive)
        try {
            val root = Environment.getExternalStorageDirectory()
            val chopCutDir = File(root, "ChopCut")
            if (!chopCutDir.exists()) {
                if (!chopCutDir.mkdirs()) {
                    throw java.io.IOException("Cannot create directory")
                }
            }

            val destFile = File(chopCutDir, videoName)
            file.copyTo(destFile, overwrite = true)

            // Scan file to make it visible in gallery
            android.media.MediaScannerConnection.scanFile(
                context,
                arrayOf(destFile.absolutePath),
                arrayOf("video/mp4"),
                null
            )

            return@withContext Uri.fromFile(destFile)

        } catch (e: Exception) {
        }

        // 2. Fallback to MediaStore (Scoped Storage / Android 10+)
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }

        val contentValues = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, videoName)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.SIZE, file.length())

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.IS_PENDING, 1)
                put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/ChopCut")
            }
        }

        val uri = contentResolver.insert(collection, contentValues)
            ?: throw IllegalStateException("MediaStore insert retornou null para $videoName")

        val outputStream = contentResolver.openOutputStream(uri)
            ?: throw IllegalStateException("Não foi possível abrir OutputStream para $uri")

        outputStream.use { output ->
            file.inputStream().use { input ->
                input.copyTo(output)
            }
        }

        // Clear pending flag
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            contentValues.clear()
            contentValues.put(MediaStore.Video.Media.IS_PENDING, 0)
            contentResolver.update(uri, contentValues, null, null)
        }

        uri
    }

    /**
     * Delete a temporary file
     */
    suspend fun deleteTempFile(file: File): Boolean = withContext(Dispatchers.IO) {
        try {
            if (file.exists() && file.delete()) {
                true
            } else {
                false
            }
        } catch (e: Exception) {
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
            false
        } finally {
            try {
                retriever.release()
            } catch (e: Exception) {
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
            30 // Default fallback
        }
    }

    /**
     * Copy URI to internal storage for project persistence
     */
    suspend fun copyToInternalStorage(uri: Uri, projectId: String): File? = withContext(Dispatchers.IO) {
        val projectsDir = File(context.filesDir, "projects")
        if (!projectsDir.exists()) projectsDir.mkdirs()
        
        val projectDir = File(projectsDir, projectId)
        if (!projectDir.exists()) projectDir.mkdirs()
        
        val destFile = File(projectDir, "source.mp4")
        
        try {
            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }
            destFile
        } catch (e: Exception) {
            // Cleanup on failure
            if (destFile.exists()) destFile.delete()
            null
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
            tempFile
        } catch (e: Exception) {
            tempFile.delete()
            null
        }
    }

    /**
     * Delete all videos saved by ChopCut (Movies/ChopCut, DCIM/ChopCut, and MediaStore entries)
     * Uses MediaStore to ensure all videos are deleted, including those in scoped storage
     */
    suspend fun deleteSavedVideos(): Int = withContext(Dispatchers.IO) {
        var deletedCount = 0

        try {
            // Strategy: Delete all video files (.mp4) in ChopCut directories via MediaStore
            // This works for all Android versions and handles scoped storage properly
            deletedCount = deleteAllVideosInChopCutDirectories()

        } catch (e: Exception) {
            throw e
        }

        deletedCount
    }

    /**
     * Delete all videos in ChopCut directories by:
     * 1. Finding all .mp4 files in ChopCut folders
     * 2. For each file, finding and deleting its MediaStore entry
     * 3. Trying to delete the physical file as fallback
     */
    private fun deleteAllVideosInChopCutDirectories(): Int {
        var deletedCount = 0
        val deletedFiles = mutableSetOf<String>()

        // Known directories where ChopCut saves videos
        val directories = listOf(
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "ChopCut"),
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "ChopCut"),
            File(Environment.getExternalStorageDirectory(), "ChopCut")
        )

        directories.forEach { dir ->

            if (dir.exists() && dir.isDirectory) {
                try {
                    val files = dir.listFiles()

                    files?.forEach { file ->
                        // Only process video files
                        if (file.isFile && file.extension.lowercase() == "mp4") {
                            var deleted = false

                            // CRÍTICO: Scan file first to ensure it's registered in MediaStore
                            // This is necessary for files that might not be in MediaStore yet
                            scanFileToMediaStore(file)

                            // Method 1: Try to delete via MediaStore (most reliable)
                            deleted = deleteViaMediaStore(file)

                            // Method 2: Fallback to direct deletion
                            if (!deleted) {
                                deleted = deletePhysicalFile(file)
                            }

                            if (deleted) {
                                deletedCount++
                                deletedFiles.add(file.name)
                            } else {
                            }
                        }
                    }

                    // Try to delete the directory if empty
                    if (dir.listFiles()?.isEmpty() == true) {
                        dir.delete()
                    }
                } catch (e: Exception) {
                }
            }
        }

        return deletedCount
    }

    /**
     * Scan a file to MediaStore to ensure it's registered before deletion
     * This is necessary for files that might not be indexed yet
     */
    private fun scanFileToMediaStore(file: File) {
        try {
            android.media.MediaScannerConnection.scanFile(
                context,
                arrayOf(file.absolutePath),
                arrayOf("video/mp4")
            ) { path, uri ->
                // Callback é assíncrono, não precisamos de delay
                if (uri != null) {
                } else {
                }
            }
        } catch (e: Exception) {
        }
    }

    /**
     * Delete a video file via MediaStore API (proper way for Android 10+)
     */
    private fun deleteViaMediaStore(file: File): Boolean {
        try {
            // Query MediaStore for this specific file
            val projection = arrayOf(MediaStore.Video.Media._ID)
            val selection = "${MediaStore.Video.Media.DATA} = ?"
            val selectionArgs = arrayOf(file.absolutePath)

            val cursor = contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                null
            )

            cursor?.use {
                if (it.moveToFirst()) {
                    val id = it.getLong(it.getColumnIndexOrThrow(MediaStore.Video.Media._ID))
                    val videoUri = android.net.Uri.withAppendedPath(
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                        id.toString()
                    )

                    // Delete via ContentResolver
                    val rowsDeleted = contentResolver.delete(videoUri, null, null)
                    return rowsDeleted > 0
                }
            }

            return false
        } catch (e: Exception) {
            return false
        }
    }

    /**
     * Delete a video file directly from filesystem (fallback)
     */
    private fun deletePhysicalFile(file: File): Boolean {
        return try {
            val deleted = file.delete()
            if (deleted) {
                // Notify MediaStore about the deletion
                android.media.MediaScannerConnection.scanFile(
                    context,
                    arrayOf(file.absolutePath),
                    null,
                    null
                )
            } else {
            }
            deleted
        } catch (e: Exception) {
            false
        }
    }
}