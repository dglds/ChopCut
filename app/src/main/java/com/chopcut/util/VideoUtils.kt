package com.chopcut.util

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.chopcut.data.model.TimeRange

object TimeUtils {

    fun formatTimeMs(ms: Long): String {
        val totalSeconds = ms / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        val millis = ms % 1000
        return String.format("%02d:%02d.%03d", minutes, seconds, millis)
    }

    fun formatTimeShort(ms: Long): String {
        val totalSeconds = ms / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format("%02d:%02d", minutes, seconds)
    }

    fun formatDuration(ms: Long): String {
        val hours = ms / 3600000
        val minutes = (ms % 3600000) / 60000
        val seconds = (ms % 60000) / 1000
        return when {
            hours > 0 -> String.format("%d:%02d:%02d", hours, minutes, seconds)
            else -> String.format("%d:%02d", minutes, seconds)
        }
    }

    fun formatTimeWithMillis(ms: Long): String {
        val minutes = ms / 60000
        val seconds = (ms % 60000) / 1000
        val centiseconds = (ms % 1000) / 10
        return String.format("%02d:%02d:%02d", minutes, seconds, centiseconds)
    }
}

object FormatUtils {

    fun formatFileSize(bytes: Long): String {
        return when {
            bytes >= 1024 * 1024 * 1024 -> String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
            bytes >= 1024 * 1024 -> String.format("%.2f MB", bytes / (1024.0 * 1024.0))
            bytes >= 1024 -> String.format("%.2f KB", bytes / 1024.0)
            else -> "$bytes B"
        }
    }

    fun truncateFileName(fileName: String, maxLength: Int = 20): String {
        return if (fileName.length > maxLength) fileName.take(maxLength) + "…" else fileName
    }

    fun formatFileInfo(fileName: String, fileSizeBytes: Long, durationMs: Long, width: Int, height: Int): String {
        val truncatedName = truncateFileName(fileName)
        val sizeFormatted = formatFileSize(fileSizeBytes)
        val durationFormatted = TimeUtils.formatDuration(durationMs)
        val ratioFormatted = getAspectRatio(width, height)
        return "$truncatedName · $sizeFormatted · $durationFormatted · $ratioFormatted"
    }

    fun getAspectRatio(width: Int, height: Int): String {
        if (width <= 0 || height <= 0) return "N/D"
        
        // Normalizar para lidar com pequenas variações de codec
        val ratio = width.toFloat() / height
        return when {
            Math.abs(ratio - 1.0f) < 0.01f -> "1:1"
            Math.abs(ratio - 1.777f) < 0.02f -> "16:9"
            Math.abs(ratio - 0.562f) < 0.02f -> "9:16"
            Math.abs(ratio - 1.333f) < 0.02f -> "4:3"
            Math.abs(ratio - 0.75f) < 0.02f -> "3:4"
            Math.abs(ratio - 2.333f) < 0.03f -> "21:9"
            else -> {
                val gcd = findGcd(width, height)
                "${width / gcd}:${height / gcd}"
            }
        }
    }

    private fun findGcd(a: Int, b: Int): Int {
        var n1 = a
        var n2 = b
        while (n2 != 0) {
            val temp = n1 % n2
            n1 = n2
            n2 = temp
        }
        return n1
    }

    fun getFileInfo(context: Context, uri: Uri, durationMs: Long): String {
        var width = 0
        var height = 0
        
        try {
            android.media.MediaMetadataRetriever().run {
                setDataSource(context, uri)
                val vw = extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toInt() ?: 0
                val vh = extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toInt() ?: 0
                val rotation = extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toInt() ?: 0
                release()
                
                if (rotation == 90 || rotation == 270) {
                    width = vh
                    height = vw
                } else {
                    width = vw
                    height = vh
                }
            }
        } catch (e: Exception) {
            // Ignorar erros na extração de dimensões
        }

        val fileName = try {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
                } else {
                    null
                }
            } ?: uri.lastPathSegment?.substringAfterLast('/')
        } catch (e: Exception) {
            uri.lastPathSegment?.substringAfterLast('/')
        } ?: "desconhecido"

        val fileSizeBytes = try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                pfd.statSize
            } ?: 0L
        } catch (e: Exception) {
            0L
        }

        return formatFileInfo(fileName, fileSizeBytes, durationMs, width, height)
    }
}

object FileNameUtils {

    fun sanitizeFileName(fileName: String, maxLength: Int = 30): String {
        return fileName
            .take(maxLength)
            .replace(Regex("[^a-zA-Z0-9_-]"), "_")
            .removePrefix("ChopCut_")
    }

    fun generateTimestampedFileName(baseName: String = "video", extension: String = "mp4"): String {
        val timestamp = java.text.SimpleDateFormat("mmssSSS", java.util.Locale.getDefault())
            .format(java.util.Date())
        return "ChopCut_${timestamp}_${baseName}.$extension"
    }

    fun extractBaseNameFromUri(uri: android.net.Uri): String {
        val pathSegment = uri.lastPathSegment
        return pathSegment
            ?.substringAfterLast('/')
            ?.substringBeforeLast('.')
            ?.take(30)
            ?.replace(Regex("[^a-zA-Z0-9_-]"), "_")
            ?.removePrefix("ChopCut_")
            ?: "video"
    }
}

object RangeUtils {

    fun calculateKeepRanges(trimRanges: List<Pair<Long, Long>>, totalDurationMs: Long): List<TimeRange> {
        val sortedTrimRanges = trimRanges.sortedBy { it.first }
        val keepRanges = mutableListOf<Pair<Long, Long>>()
        var lastEndMs = 0L

        sortedTrimRanges.forEach { (start, end) ->
            if (start > lastEndMs) {
                keepRanges.add(lastEndMs to start)
            }
            lastEndMs = end
        }

        if (lastEndMs < totalDurationMs) {
            keepRanges.add(lastEndMs to totalDurationMs)
        }

        return keepRanges.map { (start, end) -> TimeRange(start, end) }
    }

    fun isPositionInRange(position: Long, ranges: List<Pair<Long, Long>>): Boolean {
        return ranges.any { (start, end) -> position in start..end }
    }

    fun mergeRanges(positions: List<Long>): List<Pair<Long, Long>> {
        if (positions.size < 2) return emptyList()

        val rawRanges = positions.chunked(2).mapNotNull { chunk ->
            if (chunk.size == 2) minOf(chunk[0], chunk[1]) to maxOf(chunk[0], chunk[1])
            else null
        }

        return rawRanges.sortedBy { it.first }
            .fold(emptyList<Pair<Long, Long>>()) { acc, range ->
                if (acc.isEmpty() || range.first > acc.last().second) {
                    acc + range
                } else {
                    acc.dropLast(1) + (acc.last().first to maxOf(acc.last().second, range.second))
                }
            }
    }
}
