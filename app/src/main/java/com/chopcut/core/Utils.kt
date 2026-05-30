package com.chopcut

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import android.util.LruCache
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber


// --- Merged from DispatcherProvider.kt ---


object DispatcherProvider {
    val main: CoroutineDispatcher = Dispatchers.Main
    val io: CoroutineDispatcher = Dispatchers.IO
    val default: CoroutineDispatcher = Dispatchers.Default
    val unconfined: CoroutineDispatcher = Dispatchers.Unconfined
}

// --- Merged from ThumbnailUtils.kt ---


object ThumbnailUtils {
    private val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
    private val cacheSize = maxMemory / 8 // Use 1/8th of available memory
    private val cache = object : LruCache<String, Bitmap>(cacheSize) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int {
            return bitmap.byteCount / 1024
        }
    }

    suspend fun getThumbnail(context: Context, videoUri: Uri, timeMs: Long): Bitmap? {
        val key = "${videoUri}_$timeMs"
        cache.get(key)?.let { return it }

        return withContext(Dispatchers.IO) {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, videoUri)
                // Retrieve frame at timeMs. 
                // OPTION_CLOSEST_SYNC is faster but less accurate. 
                // OPTION_CLOSEST is more accurate but slower.
                // For a timeline strip, fast is better, but let's try CLOSEST first for quality.
                // Actually, for scrolling performance, we need to be careful.
                // Let's use getScaledFrameAtTime if API 27+ for better performance on large 4k videos
                
                // Using a fixed height for the thumbnail strips usually looks best.
                // Assuming timeline height is around 100dp.
                
                val bitmap = retriever.getFrameAtTime(timeMs * 1000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                
                bitmap?.let {
                    // Create a scaled version to save memory if it's huge
                    val targetHeight = 120 // slightly larger than 100dp
                    val ratio = it.width.toFloat() / it.height.toFloat()
                    val targetWidth = (targetHeight * ratio).toInt()
                    
                    val scaled = Bitmap.createScaledBitmap(it, targetWidth, targetHeight, true)
                    if (scaled != it) {
                        it.recycle()
                    }
                    cache.put(key, scaled)
                    scaled
                }
            } catch (e: Exception) {
                null
            } finally {
                retriever.release()
            }
        }
    }
}

// --- Merged from TimeFormatter.kt ---


fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    val centis = (ms % 1000) / 10
    // Formato MM:SS.cc (Minutos totais : Segundos . Centésimos)
    return String.format(Locale.US, "%02d:%02d.%02d", minutes, seconds, centis)
}

// --- Merged from TimeTracker.kt ---


object TimeTracker {
    fun start(operation: String): TimeToken = TimeToken(operation)

    class TimeToken(private val operation: String) {
        private val startMs = System.currentTimeMillis()

        fun end(): Long {
            val elapsedMs = System.currentTimeMillis() - startMs
            Timber.d("⏱ %s: %dms (%.2fs)", operation, elapsedMs, elapsedMs / 1000.0)
            return elapsedMs
        }
    }
}

// --- Merged from TimelineLogger.kt ---


/**
 * Logger de telemetria assíncrono e de alta performance.
 *
 * Registra movimentações de posição da timeline em arquivo privado no Android
 * sem causar jank na thread principal a 120 FPS.
 */
object TimelineLogger {
    private const val TAG = "TimelineTelemetry"
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
    
    private val logScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val logChannel = Channel<String>(capacity = Channel.UNLIMITED)
    
    private var logFile: File? = null
    
    private var lastLogTimeNs = 0L
    private var lastPositionMs = -1L

    init {
        // Iniciar o worker assíncrono em background
        logScope.launch {
            for (logLine in logChannel) {
                writeLineToFile(logLine)
            }
        }
    }

    /**
     * Inicializa o arquivo físico de telemetria.
     * Limpa logs antigos para termos uma nova telemetria fresca a cada edição.
     */
    fun init(context: Context) {
        try {
            val externalDir = context.getExternalFilesDir(null) ?: context.filesDir
            val file = File(externalDir, "timeline_telemetry.log")
            
            if (file.exists()) {
                file.delete()
            }
            file.createNewFile()
            logFile = file
            
            lastLogTimeNs = System.nanoTime()
            lastPositionMs = -1L
            
            logSystem("==========================================================================")
            logSystem("Timeline Telemetry Logger Inicializado.")
            logSystem("Diretório do Log: ${file.absolutePath}")
            logSystem("==========================================================================")
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao inicializar arquivo de log", e)
        }
    }

    /**
     * Enfileira uma mensagem de movimentação de forma assíncrona.
     */
    fun logMovement(mode: String, positionMs: Long, reason: String) {
        val nowNs = System.nanoTime()
        val nowMs = System.currentTimeMillis()
        val formattedTime = timeFormat.format(Date(nowMs))
        
        // Variação de tempo decorrido desde a última telemetria
        val deltaTempoMs = (nowNs - lastLogTimeNs) / 1_000_000.0
        lastLogTimeNs = nowNs
        
        // Variação da posição de playback do vídeo
        val deltaPosMs = if (lastPositionMs == -1L) 0L else positionMs - lastPositionMs
        lastPositionMs = positionMs
        
        // Frequência instantânea do evento (Hz)
        val instantHz = if (deltaTempoMs > 0.0) 1000.0 / deltaTempoMs else 0.0
        
        val logLine = String.format(
            Locale.US,
            "[%s] [%-6s] Pos: %5dms (DeltaPos: %+4dms) | DeltaTempo: %5.1fms | Freq: %5.1fHz | Reason: %s",
            formattedTime,
            mode,
            positionMs,
            deltaPosMs,
            deltaTempoMs,
            instantHz,
            reason
        )
        
        logChannel.trySend(logLine)
        Log.d(TAG, logLine)
    }

    fun getLogFile(): File? = logFile

    private fun logSystem(message: String) {
        val logLine = String.format(
            Locale.US,
            "[%s] [SYSTEM] %s",
            timeFormat.format(Date()),
            message
        )
        logChannel.trySend(logLine)
        Log.i(TAG, logLine)
    }

    private fun writeLineToFile(line: String) {
        val file = logFile ?: return
        try {
            FileWriter(file, true).use { fw ->
                PrintWriter(fw).use { pw ->
                    pw.println(line)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao gravar linha de log no arquivo", e)
        }
    }
}

// --- Merged from VideoConstraints.kt ---

object VideoConstraints {
    const val MAX_DURATION_MS = 15 * 60 * 1000L // 15 minutos
    const val MAX_DURATION_SECONDS = 900
    
    fun isDurationValid(durationMs: Long): Boolean {
        // Desativado para testes conforme solicitado
        return true
    }
    
    fun getValidationMessage(durationMs: Long): String? {
        // Sem restrição de tempo para testes
        return null
    }
}

// --- Merged from VideoUtils.kt ---


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

    fun estimateExportSize(
        level: CompressionLevel,
        keepRanges: List<TimeRange>,
        originalDurationUs: Long,
        originalSizeBytes: Long,
        originalWidth: Int,
        originalHeight: Int,
        originalBitrateBps: Long
    ): Long {
        val totalKeepDurationMs = keepRanges.sumOf { it.endMs - it.startMs }
        val originalDurationMs = originalDurationUs / 1000L
        if (originalDurationMs <= 0L) return 0L

        val originalProportionalSize = (originalSizeBytes * totalKeepDurationMs) / originalDurationMs

        if (level == CompressionLevel.ORIGINAL) {
            return originalProportionalSize
        }

        val targetBitrate = if (originalBitrateBps > 0L) {
            Math.min(level.targetBitrateBps, originalBitrateBps)
        } else {
            level.targetBitrateBps
        }

        val totalBitrateBps = targetBitrate + 128_000L
        val durationSeconds = totalKeepDurationMs / 1000.0
        val estimatedBytes = (totalBitrateBps * durationSeconds / 8.0).toLong()

        return Math.min(estimatedBytes, originalProportionalSize).coerceAtLeast(1024L)
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

    fun resolveThumbnailDirectory(context: Context, fileName: String): File {
        val sanitizedName = fileName.substringBeforeLast(".")
            .replace("[^a-zA-Z0-9_\\-]".toRegex(), "_")
            .trim('_')
        val baseDir = context.getExternalFilesDir("extracted_frames") ?: context.filesDir
        return File(baseDir, sanitizedName)
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

// --- Merged from ActivityLogger.kt ---


object ActivityLogger {
    private const val TAG = "ChopCut.Activity"

    fun started(activity: AppActivity, vararg params: Pair<String, Any?>) {
    }

    fun finished(activity: AppActivity, vararg params: Pair<String, Any?>) {
    }

    fun failed(activity: AppActivity, vararg params: Pair<String, Any?>) {
    }

    private fun fmtParams(params: Array<out Pair<String, Any?>>) =
        if (params.isEmpty()) "" else " — " + params.joinToString(", ") { "${it.first}=${it.second}" }
}

sealed class AppActivity(val label: String) {
    object ThumbnailExtraction : AppActivity("ThumbnailExtraction")
    object StripAssembly : AppActivity("StripAssembly")
    object Trim : AppActivity("Trim")
    object AudioExport : AppActivity("AudioExport")
}

// --- Merged from FileLoggingTree.kt ---


class FileLoggingTree(context: Context) {

    private val logDir: File
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
    private val fileNameFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    
    init {
        val baseDir = context.getExternalFilesDir(null) ?: context.filesDir
        logDir = File(baseDir, "logs")
        if (!logDir.exists()) {
            logDir.mkdirs()
        }
    }

    private fun getCurrentLogFile(): File {
        val today = fileNameFormat.format(Date())
        return File(logDir, "app_errors_$today.txt")
    }

    fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        // Logging removed
    }

    private fun cleanOldLogs() {
        // Logging removed
    }

    fun getLogDir(): File = logDir
}

fun Context.getLogDir(): File {
    val baseDir = this.getExternalFilesDir(null) ?: this.filesDir
    return File(baseDir, "logs")
}

fun Context.getCurrentLogFile(): File {
    val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val today = dateFormat.format(Date())
    return File(this.getLogDir(), "app_errors_$today.txt")
}

// --- Merged from LocalFileLoggingTree.kt ---


class LocalFileLoggingTree {

    private val projectDir = File(System.getProperty("user.dir") ?: ".")
    private val logsDir = File(projectDir, "logs")
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
    private val fileNameFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    init {
        if (!logsDir.exists()) {
            logsDir.mkdirs()
        }
        cleanAllLogs()
    }

    private fun getCurrentLogFile(): File {
        val today = fileNameFormat.format(Date())
        return File(logsDir, "app_errors_$today.txt")
    }

    fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        // Logging removed
    }

    private fun cleanAllLogs() {
        // Logging removed
    }

    private fun cleanOldLogs() {
        // Logging removed
    }

    fun getLogDir(): File = logsDir
}
