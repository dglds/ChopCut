package com.chopcut.ui.components.console

import android.content.Context
import android.content.Intent
import android.os.Environment
import androidx.core.content.FileProvider
import com.chopcut.util.debug.LogEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

enum class ExportFormat {
    JSON,
    CSV,
    TXT
}

data class ExportResult(
    val success: Boolean,
    val filePath: String? = null,
    val error: String? = null
)

data class ExportMetadata(
    val exportDate: String,
    val appVersion: String,
    val deviceModel: String,
    val androidVersion: String,
    val logCount: Int,
    val timeRange: TimeRange,
    val tags: Set<String>
)

data class TimeRange(
    val startTime: Long,
    val endTime: Long
) {
    fun durationMs(): Long = endTime - startTime
    
    fun formattedDuration(): String {
        val ms = durationMs()
        val seconds = ms / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        
        return when {
            hours > 0 -> "${hours}h ${minutes % 60}m"
            minutes > 0 -> "${minutes}m ${seconds % 60}s"
            else -> "${seconds}s"
        }
    }
}

class LogExporter(
    private val context: Context
) {
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault())
    private val iso8601Format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault()).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
    
    suspend fun exportLogs(
        logs: List<LogEntry>,
        format: ExportFormat,
        filename: String? = null
    ): ExportResult = withContext(Dispatchers.IO) {
        try {
            val timestamp = dateFormat.format(Date())
            val finalFilename = filename ?: "chopcut_logs_${timestamp}"
            val file = createExportFile(finalFilename, format.extension)
            
            when (format) {
                ExportFormat.JSON -> exportAsJSON(logs, file)
                ExportFormat.CSV -> exportAsCSV(logs, file)
                ExportFormat.TXT -> exportAsTXT(logs, file)
            }
            
            ExportResult(success = true, filePath = file.absolutePath)
        } catch (e: Exception) {
            ExportResult(success = false, error = e.message)
        }
    }
    
    private fun createExportFile(filename: String, extension: String): File {
        val exportDir = File(
            context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS),
            "ChopCutLogs"
        )
        if (!exportDir.exists()) {
            exportDir.mkdirs()
        }
        return File(exportDir, "$filename.$extension")
    }
    
    private fun exportAsJSON(logs: List<LogEntry>, file: File) {
        val metadata = ExportMetadata(
            exportDate = iso8601Format.format(Date()),
            appVersion = getAppVersion(),
            deviceModel = android.os.Build.MODEL,
            androidVersion = "Android ${android.os.Build.VERSION.RELEASE}",
            logCount = logs.size,
            timeRange = TimeRange(
                startTime = logs.firstOrNull()?.timestamp ?: System.currentTimeMillis(),
                endTime = logs.lastOrNull()?.timestamp ?: System.currentTimeMillis()
            ),
            tags = logs.map { it.tag }.toSet()
        )
        
        val jsonRoot = JSONObject()
        jsonRoot.put("metadata", JSONObject().apply {
            put("exportDate", metadata.exportDate)
            put("appVersion", metadata.appVersion)
            put("deviceModel", metadata.deviceModel)
            put("androidVersion", metadata.androidVersion)
            put("logCount", metadata.logCount)
            put("timeRange", JSONObject().apply {
                put("startTime", metadata.timeRange.startTime)
                put("endTime", metadata.timeRange.endTime)
                put("duration", metadata.timeRange.durationMs())
            })
            put("tags", JSONArray(metadata.tags))
        })
        
        val logsArray = JSONArray()
        logs.forEach { entry ->
            logsArray.put(JSONObject().apply {
                put("timestamp", entry.timestamp)
                put("timestampFormatted", iso8601Format.format(Date(entry.timestamp)))
                put("level", entry.level.toString())
                put("tag", entry.tag)
                put("message", entry.message)
                put("count", entry.count)
            })
        }
        jsonRoot.put("logs", logsArray)
        
        FileOutputStream(file).use { output ->
            output.write(jsonRoot.toString(2).toByteArray())
        }
    }
    
    private fun exportAsCSV(logs: List<LogEntry>, file: File) {
        FileOutputStream(file).use { output ->
            output.write("Timestamp,Level,Tag,Count,Message\n".toByteArray())
            logs.forEach { entry ->
                val timestamp = iso8601Format.format(Date(entry.timestamp))
                val level = entry.level.toString()
                val tag = entry.tag.replace(",", ";")
                val count = entry.count
                val message = entry.message.replace("\"", "\"\"").replace(",", ";")
                
                val line = "$timestamp,$level,\"$tag\",$count,\"$message\"\n"
                output.write(line.toByteArray())
            }
        }
    }
    
    private fun exportAsTXT(logs: List<LogEntry>, file: File) {
        FileOutputStream(file).use { output ->
            output.write("ChopCut Debug Logs\n".toByteArray())
            output.write("=".repeat(80).toByteArray() + "\n\n".toByteArray())
            
            output.write("Export Date: ${iso8601Format.format(Date())}\n".toByteArray())
            output.write("App Version: ${getAppVersion()}\n".toByteArray())
            output.write("Device: ${android.os.Build.MODEL}\n".toByteArray())
            output.write("Android: ${android.os.Build.VERSION.RELEASE}\n".toByteArray())
            output.write("Total Logs: ${logs.size}\n\n".toByteArray())
            
            logs.forEach { entry ->
                val timestamp = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
                    .format(Date(entry.timestamp))
                val line = "[$timestamp] [${entry.level}] [${entry.tag}] [${entry.count}] ${entry.message}\n"
                output.write(line.toByteArray())
            }
        }
    }
    
    suspend fun shareLogs(
        logs: List<LogEntry>,
        format: ExportFormat
    ): Boolean = withContext(Dispatchers.IO) {
        val result = exportLogs(logs, format)
        if (!result.success) return@withContext false
        
        val file = File(result.filePath ?: return@withContext false)
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = when (format) {
                ExportFormat.JSON -> "application/json"
                ExportFormat.CSV -> "text/csv"
                ExportFormat.TXT -> "text/plain"
            }
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        
        context.startActivity(Intent.createChooser(intent, "Share Logs"))
        true
    }
    
    private fun getAppVersion(): String {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            "${packageInfo.versionName} (${packageInfo.longVersionCode})"
        } catch (e: Exception) {
            "Unknown"
        }
    }
}

val ExportFormat.extension: String
    get() = when (this) {
        ExportFormat.JSON -> "json"
        ExportFormat.CSV -> "csv"
        ExportFormat.TXT -> "txt"
    }